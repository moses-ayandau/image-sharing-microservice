package com.handlers.healthCheck;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.service.checkService.ServiceHealthCheck;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClientBuilder;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClientBuilder;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.regions.Region;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class ComponentHealthCheckHandlerTest {

    @Mock
    private Context context;
    
    @Mock
    private DynamoDbClient dynamoDbClient;
    
    @Mock
    private S3Client s3Client;
    
    @Mock
    private CognitoIdentityProviderClient cognitoClient;

    @Mock
    private DynamoDbClientBuilder dynamoDbClientBuilder;

    @Mock
    private S3ClientBuilder s3ClientBuilder;

    @Mock
    private CognitoIdentityProviderClientBuilder cognitoClientBuilder;

    private ComponentHealthCheckHandler handler;
    private MockedStatic<DynamoDbClient> mockedDynamoClient;
    private MockedStatic<S3Client> mockedS3Client;
    private MockedStatic<CognitoIdentityProviderClient> mockedCognitoClient;
    private MockedStatic<ServiceHealthCheck> mockedServiceHealthCheck;
    private ComponentHealthCheckHandler.EnvironmentProvider mockEnvProvider;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        
        // Mock AWS clients using try-with-resources to ensure proper cleanup
        mockedDynamoClient = mockStatic(DynamoDbClient.class);
        mockedS3Client = mockStatic(S3Client.class);
        mockedCognitoClient = mockStatic(CognitoIdentityProviderClient.class);
        mockedServiceHealthCheck = mockStatic(ServiceHealthCheck.class);

        // Set up builder mocks with completed stubbing
        when(dynamoDbClientBuilder.region(any(Region.class))).thenReturn(dynamoDbClientBuilder);
        when(dynamoDbClientBuilder.build()).thenReturn(dynamoDbClient);
        mockedDynamoClient.when(DynamoDbClient::builder).thenReturn(dynamoDbClientBuilder);

        when(s3ClientBuilder.region(any(Region.class))).thenReturn(s3ClientBuilder);
        when(s3ClientBuilder.build()).thenReturn(s3Client);
        mockedS3Client.when(S3Client::builder).thenReturn(s3ClientBuilder);

        when(cognitoClientBuilder.region(any(Region.class))).thenReturn(cognitoClientBuilder);
        when(cognitoClientBuilder.build()).thenReturn(cognitoClient);
        mockedCognitoClient.when(CognitoIdentityProviderClient::builder).thenReturn(cognitoClientBuilder);
        
        // Create a mock environment provider
        mockEnvProvider = mock(ComponentHealthCheckHandler.EnvironmentProvider.class);
        
        // Create a proper LambdaLogger mock
        LambdaLogger logger = new LambdaLogger() {
            @Override
            public void log(String message) {
                System.out.println(message);
            }

            @Override
            public void log(byte[] message) {
                System.out.println(new String(message));
            }
        };
        when(context.getLogger()).thenReturn(logger);

        // Create handler with mocked clients and environment provider
        handler = new ComponentHealthCheckHandler(mockEnvProvider);
    }

    @AfterEach
    void tearDown() {
        mockedServiceHealthCheck.close();
        mockedCognitoClient.close();
        mockedS3Client.close();
        mockedDynamoClient.close();
    }

    @Test
    void testHandleRequest_AllComponents() {
        // Arrange
        APIGatewayProxyRequestEvent input = new APIGatewayProxyRequestEvent();
        Map<String, String> pathParams = new HashMap<>();
        pathParams.put("component", "all");
        input.setPathParameters(pathParams);

        // Set up environment variable mocks
        when(mockEnvProvider.getEnv("COGNITO_USER_POOL_ID")).thenReturn("test-pool-id");
        when(mockEnvProvider.getEnv("PROCESSED_BUCKET")).thenReturn("test-bucket");
        when(mockEnvProvider.getEnv("IMAGE_TABLE_NAME")).thenReturn("test-table");

        // Mock health check responses
        Map<String, Object> cognitoHealth = new HashMap<>();
        cognitoHealth.put("status", "healthy");
        Map<String, Object> s3Health = new HashMap<>();
        s3Health.put("status", "healthy");
        Map<String, Object> dynamoHealth = new HashMap<>();
        dynamoHealth.put("status", "healthy");

        // Mock ServiceHealthCheck responses
        mockedServiceHealthCheck.when(() -> ServiceHealthCheck.checkCognitoHealth(any(), any(), anyInt(), anyString()))
                .thenReturn(cognitoHealth);
        mockedServiceHealthCheck.when(() -> ServiceHealthCheck.checkS3Health(any(), any(), anyInt(), anyString()))
                .thenReturn(s3Health);
        mockedServiceHealthCheck.when(() -> ServiceHealthCheck.checkDynamoDBHealth(any(), any(), anyInt(), anyString()))
                .thenReturn(dynamoHealth);

        // Act
        APIGatewayProxyResponseEvent response = handler.handleRequest(input, context);

        // Assert
        assertEquals(200, response.getStatusCode());
        assertTrue(response.getBody().contains("cognito"));
        assertTrue(response.getBody().contains("s3"));
        assertTrue(response.getBody().contains("dynamodb"));
    }

    @Test
    void testHandleRequest_CognitoOnly() {
        // Arrange
        APIGatewayProxyRequestEvent input = new APIGatewayProxyRequestEvent();
        Map<String, String> pathParams = new HashMap<>();
        pathParams.put("component", "cognito");
        input.setPathParameters(pathParams);

        // Set up environment variable mocks
        when(mockEnvProvider.getEnv("COGNITO_USER_POOL_ID")).thenReturn("test-pool-id");

        // Mock health check response
        Map<String, Object> cognitoHealth = new HashMap<>();
        cognitoHealth.put("status", "healthy");

        // Mock ServiceHealthCheck response
        mockedServiceHealthCheck.when(() -> ServiceHealthCheck.checkCognitoHealth(any(), any(), anyInt(), anyString()))
                .thenReturn(cognitoHealth);

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

        // Set up environment variable mocks
        when(mockEnvProvider.getEnv("PROCESSED_BUCKET")).thenReturn("test-bucket");

        // Mock health check response
        Map<String, Object> s3Health = new HashMap<>();
        s3Health.put("status", "healthy");

        // Mock ServiceHealthCheck response
        mockedServiceHealthCheck.when(() -> ServiceHealthCheck.checkS3Health(any(), any(), anyInt(), anyString()))
                .thenReturn(s3Health);

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

        // Set up environment variable mocks
        when(mockEnvProvider.getEnv("IMAGE_TABLE_NAME")).thenReturn("test-table");

        // Mock health check response
        Map<String, Object> dynamoHealth = new HashMap<>();
        dynamoHealth.put("status", "healthy");

        // Mock ServiceHealthCheck response
        mockedServiceHealthCheck.when(() -> ServiceHealthCheck.checkDynamoDBHealth(any(), any(), anyInt(), anyString()))
                .thenReturn(dynamoHealth);

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

        // Set up environment variable mocks to return null
        when(mockEnvProvider.getEnv(anyString())).thenReturn(null);

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

        // Set up environment variable mocks
        when(mockEnvProvider.getEnv("COGNITO_USER_POOL_ID")).thenReturn("test-pool-id");
        when(mockEnvProvider.getEnv("PROCESSED_BUCKET")).thenReturn("test-bucket");
        when(mockEnvProvider.getEnv("IMAGE_TABLE_NAME")).thenReturn("test-table");

        // Mock health check responses with one unhealthy component
        Map<String, Object> cognitoHealth = new HashMap<>();
        cognitoHealth.put("status", "healthy");
        Map<String, Object> s3Health = new HashMap<>();
        s3Health.put("status", "unhealthy");
        Map<String, Object> dynamoHealth = new HashMap<>();
        dynamoHealth.put("status", "healthy");

        // Mock ServiceHealthCheck responses
        mockedServiceHealthCheck.when(() -> ServiceHealthCheck.checkCognitoHealth(any(), any(), anyInt(), anyString()))
                .thenReturn(cognitoHealth);
        mockedServiceHealthCheck.when(() -> ServiceHealthCheck.checkS3Health(any(), any(), anyInt(), anyString()))
                .thenReturn(s3Health);
        mockedServiceHealthCheck.when(() -> ServiceHealthCheck.checkDynamoDBHealth(any(), any(), anyInt(), anyString()))
                .thenReturn(dynamoHealth);

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