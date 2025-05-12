package com.handlers.healthCheck;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.CreateUserPoolRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.DeleteUserPoolRequest;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.CreateTableRequest;
import software.amazon.awssdk.services.dynamodb.model.DeleteTableRequest;
import software.amazon.awssdk.services.dynamodb.model.DescribeTableRequest;
import software.amazon.awssdk.services.dynamodb.model.TableStatus;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "AWS_ACCESS_KEY_ID", matches = ".+")
@EnabledIfEnvironmentVariable(named = "AWS_SECRET_ACCESS_KEY", matches = ".+")
class ComponentHealthCheckHandlerIntegrationTest {

    private static final String TEST_PREFIX = "test-health-check-";
    private static final Region TEST_REGION = Region.US_EAST_1;
    private ComponentHealthCheckHandler handler;
    private Context context;
    private String userPoolId;
    private String bucketName;
    private String tableName;
    private CognitoIdentityProviderClient cognitoClient;
    private S3Client s3Client;
    private DynamoDbClient dynamoDbClient;
    private TestEnvironmentProvider envProvider;

    private static class TestEnvironmentProvider implements ComponentHealthCheckHandler.EnvironmentProvider {
        private final Map<String, String> envVars = new HashMap<>();

        public void setEnv(String key, String value) {
            envVars.put(key, value);
        }

        @Override
        public String getEnv(String name) {
            return envVars.get(name);
        }
    }

    @BeforeEach
    void setUp() {
        // Initialize AWS clients with specific region
        cognitoClient = CognitoIdentityProviderClient.builder()
                .region(TEST_REGION)
                .build();
        s3Client = S3Client.builder()
                .region(TEST_REGION)
                .build();
        dynamoDbClient = DynamoDbClient.builder()
                .region(TEST_REGION)
                .build();

        // Create test resources
        createTestResources();

        // Set up environment provider
        envProvider = new TestEnvironmentProvider();
        envProvider.setEnv("COGNITO_USER_POOL_ID", userPoolId);
        envProvider.setEnv("PROCESSED_BUCKET", bucketName);
        envProvider.setEnv("IMAGE_TABLE_NAME", tableName);

        // Create handler with test environment provider
        handler = new ComponentHealthCheckHandler(envProvider);

        // Create test context
        context = new Context() {
            @Override
            public String getAwsRequestId() {
                return "test-request-id";
            }

            @Override
            public String getLogGroupName() {
                return "test-log-group";
            }

            @Override
            public String getLogStreamName() {
                return "test-log-stream";
            }

            @Override
            public String getFunctionName() {
                return "test-function";
            }

            @Override
            public String getFunctionVersion() {
                return "test-version";
            }

            @Override
            public String getInvokedFunctionArn() {
                return "test-arn";
            }

            @Override
            public LambdaLogger getLogger() {
                return new LambdaLogger() {
                    @Override
                    public void log(String message) {
                        System.out.println(message);
                    }

                    @Override
                    public void log(byte[] message) {
                        System.out.println(new String(message));
                    }
                };
            }

            @Override
            public int getRemainingTimeInMillis() {
                return 1000;
            }

            @Override
            public int getMemoryLimitInMB() {
                return 128;
            }

            @Override
            public com.amazonaws.services.lambda.runtime.ClientContext getClientContext() {
                return null;
            }

            @Override
            public com.amazonaws.services.lambda.runtime.CognitoIdentity getIdentity() {
                return null;
            }
        };
    }

    @AfterEach
    void tearDown() {
        cleanupTestResources();
    }

    private void createTestResources() {
        try {
            // Create Cognito User Pool
            String userPoolName = TEST_PREFIX + UUID.randomUUID().toString();
            userPoolId = cognitoClient.createUserPool(CreateUserPoolRequest.builder()
                    .poolName(userPoolName)
                    .build())
                    .userPool().id();

            // Create S3 Bucket
            bucketName = TEST_PREFIX + UUID.randomUUID().toString().toLowerCase();
            s3Client.createBucket(CreateBucketRequest.builder()
                    .bucket(bucketName)
                    .build());

            // Wait for bucket to be available
            waitForBucketAvailability(bucketName);

            // Create DynamoDB Table
            tableName = TEST_PREFIX + UUID.randomUUID().toString();
            dynamoDbClient.createTable(CreateTableRequest.builder()
                    .tableName(tableName)
                    .attributeDefinitions(attr -> attr
                            .attributeName("id")
                            .attributeType("S"))
                    .keySchema(key -> key
                            .attributeName("id")
                            .keyType("HASH"))
                    .provisionedThroughput(throughput -> throughput
                            .readCapacityUnits(5L)
                            .writeCapacityUnits(5L))
                    .build());

            // Wait for table to be active
            waitForTableAvailability(tableName);

        } catch (Exception e) {
            System.err.println("Error creating test resources: " + e.getMessage());
            cleanupTestResources();
            throw new RuntimeException("Failed to create test resources", e);
        }
    }

    private void waitForBucketAvailability(String bucketName) {
        int maxAttempts = 10;
        int attempt = 0;
        while (attempt < maxAttempts) {
            try {
                s3Client.headBucket(HeadBucketRequest.builder().bucket(bucketName).build());
                return;
            } catch (Exception e) {
                attempt++;
                if (attempt == maxAttempts) {
                    throw new RuntimeException("Failed to wait for bucket availability: " + e.getMessage());
                }
                try {
                    TimeUnit.SECONDS.sleep(2);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Interrupted while waiting for bucket availability", ie);
                }
            }
        }
    }

    private void waitForTableAvailability(String tableName) {
        int maxAttempts = 10;
        int attempt = 0;
        while (attempt < maxAttempts) {
            try {
                String status = dynamoDbClient.describeTable(DescribeTableRequest.builder()
                        .tableName(tableName)
                        .build())
                        .table()
                        .tableStatusAsString();
                if (TableStatus.ACTIVE.toString().equals(status)) {
                    return;
                }
            } catch (Exception e) {
                // Ignore and retry
            }
            attempt++;
            if (attempt == maxAttempts) {
                throw new RuntimeException("Failed to wait for table availability");
            }
            try {
                TimeUnit.SECONDS.sleep(2);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted while waiting for table availability", ie);
            }
        }
    }

    private void cleanupTestResources() {
        try {
            // Delete Cognito User Pool
            if (userPoolId != null) {
                cognitoClient.deleteUserPool(DeleteUserPoolRequest.builder()
                        .userPoolId(userPoolId)
                        .build());
            }

            // Delete S3 Bucket
            if (bucketName != null) {
                s3Client.deleteBucket(DeleteBucketRequest.builder()
                        .bucket(bucketName)
                        .build());
            }

            // Delete DynamoDB Table
            if (tableName != null) {
                dynamoDbClient.deleteTable(DeleteTableRequest.builder()
                        .tableName(tableName)
                        .build());
            }
        } catch (Exception e) {
            System.err.println("Error cleaning up test resources: " + e.getMessage());
        }
    }

    @Test
    void testHandleRequest_AllComponents() {
        // Arrange
        APIGatewayProxyRequestEvent input = new APIGatewayProxyRequestEvent();
        Map<String, String> pathParams = new HashMap<>();
        pathParams.put("component", "all");
        input.setPathParameters(pathParams);

        // Act
        APIGatewayProxyResponseEvent response = handler.handleRequest(input, context);

        // Assert
        assertEquals(200, response.getStatusCode());
        assertTrue(response.getBody().contains("cognito"));
        assertTrue(response.getBody().contains("s3"));
        assertTrue(response.getBody().contains("dynamodb"));
        
        // Verify response structure
        ObjectMapper mapper = new ObjectMapper();
        try {
            Map<String, Object> healthResults = mapper.readValue(response.getBody(), Map.class);
            assertTrue(healthResults.containsKey("cognito"));
            assertTrue(healthResults.containsKey("s3"));
            assertTrue(healthResults.containsKey("dynamodb"));
            
            Map<String, Object> cognitoHealth = (Map<String, Object>) healthResults.get("cognito");
            Map<String, Object> s3Health = (Map<String, Object>) healthResults.get("s3");
            Map<String, Object> dynamoHealth = (Map<String, Object>) healthResults.get("dynamodb");
            
            assertEquals("healthy", cognitoHealth.get("status"));
            assertEquals("healthy", s3Health.get("status"));
            assertEquals("healthy", dynamoHealth.get("status"));
        } catch (Exception e) {
            fail("Failed to parse response body: " + e.getMessage());
        }
    }

    @Test
    void testHandleRequest_CognitoOnly() {
        // Arrange
        APIGatewayProxyRequestEvent input = new APIGatewayProxyRequestEvent();
        Map<String, String> pathParams = new HashMap<>();
        pathParams.put("component", "cognito");
        input.setPathParameters(pathParams);

        // Act
        APIGatewayProxyResponseEvent response = handler.handleRequest(input, context);

        // Assert
        assertEquals(200, response.getStatusCode());
        assertTrue(response.getBody().contains("cognito"));
        assertFalse(response.getBody().contains("s3"));
        assertFalse(response.getBody().contains("dynamodb"));
    }

    @Test
    void testHandleRequest_S3Only() {
        // Arrange
        APIGatewayProxyRequestEvent input = new APIGatewayProxyRequestEvent();
        Map<String, String> pathParams = new HashMap<>();
        pathParams.put("component", "s3");
        input.setPathParameters(pathParams);

        // Act
        APIGatewayProxyResponseEvent response = handler.handleRequest(input, context);

        // Assert
        assertEquals(200, response.getStatusCode());
        assertFalse(response.getBody().contains("cognito"));
        assertTrue(response.getBody().contains("s3"));
        assertFalse(response.getBody().contains("dynamodb"));
    }

    @Test
    void testHandleRequest_DynamoDBOnly() {
        // Arrange
        APIGatewayProxyRequestEvent input = new APIGatewayProxyRequestEvent();
        Map<String, String> pathParams = new HashMap<>();
        pathParams.put("component", "dynamodb");
        input.setPathParameters(pathParams);

        // Act
        APIGatewayProxyResponseEvent response = handler.handleRequest(input, context);

        // Assert
        assertEquals(200, response.getStatusCode());
        assertFalse(response.getBody().contains("cognito"));
        assertFalse(response.getBody().contains("s3"));
        assertTrue(response.getBody().contains("dynamodb"));
    }

    @Test
    void testHandleRequest_MissingEnvironmentVariables() {
        // Arrange
        APIGatewayProxyRequestEvent input = new APIGatewayProxyRequestEvent();
        Map<String, String> pathParams = new HashMap<>();
        pathParams.put("component", "all");
        input.setPathParameters(pathParams);

        // Clear environment variables
        envProvider.setEnv("COGNITO_USER_POOL_ID", null);
        envProvider.setEnv("PROCESSED_BUCKET", null);
        envProvider.setEnv("IMAGE_TABLE_NAME", null);

        // Act
        APIGatewayProxyResponseEvent response = handler.handleRequest(input, context);

        // Assert
        assertEquals(500, response.getStatusCode());
        assertTrue(response.getBody().contains("error"));
    }

    @Test
    void testHandleRequest_InvalidComponent() {
        // Arrange
        APIGatewayProxyRequestEvent input = new APIGatewayProxyRequestEvent();
        Map<String, String> pathParams = new HashMap<>();
        pathParams.put("component", "invalid");
        input.setPathParameters(pathParams);

        // Act
        APIGatewayProxyResponseEvent response = handler.handleRequest(input, context);

        // Assert
        assertEquals(500, response.getStatusCode());
        assertTrue(response.getBody().contains("error"));
    }

    @Test
    void testHandleRequest_UnhealthyComponent() {
        // Arrange
        APIGatewayProxyRequestEvent input = new APIGatewayProxyRequestEvent();
        Map<String, String> pathParams = new HashMap<>();
        pathParams.put("component", "all");
        input.setPathParameters(pathParams);

        // Delete a resource to make it unhealthy
        cognitoClient.deleteUserPool(DeleteUserPoolRequest.builder()
                .userPoolId(userPoolId)
                .build());

        // Act
        APIGatewayProxyResponseEvent response = handler.handleRequest(input, context);

        // Assert
        assertEquals(500, response.getStatusCode());
        assertTrue(response.getBody().contains("cognito"));
        assertTrue(response.getBody().contains("s3"));
        assertTrue(response.getBody().contains("dynamodb"));
        assertTrue(response.getBody().contains("unhealthy"));
    }
}