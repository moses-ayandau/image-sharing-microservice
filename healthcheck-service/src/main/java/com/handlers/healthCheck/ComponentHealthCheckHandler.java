package com.handlers.healthCheck;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.utils.checkUtils.ServiceHealthCheck;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ListUserPoolsRequest;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.ListTablesRequest;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ListBucketsRequest;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class ComponentHealthCheckHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static final int CONNECTION_TIMEOUT_SECONDS = 5;
    private final Region region;
    private final DynamoDbClient dynamoDbClient;
    private final S3Client s3Client;
    private final CognitoIdentityProviderClient cognitoClient;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    public ComponentHealthCheckHandler() {
        // Use us-east-1 as the default region since that's where most AWS services are created by default
        this.region = Region.US_EAST_1;
        this.dynamoDbClient = DynamoDbClient.builder().region(region).build();
        this.s3Client = S3Client.builder().region(region).build();
        this.cognitoClient = CognitoIdentityProviderClient.builder().region(region).build();
        this.objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent input, Context context) {
        context.getLogger().log("Processing component health check request");

        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("Access-Control-Allow-Origin", "*");

        // Get component to check from path parameter
        String component = input.getPathParameters() != null ?
                input.getPathParameters().get("component") : "all";

        Map<String, Object> healthResults = new HashMap<>();
        boolean allHealthy = true;

        try {
            switch (component.toLowerCase()) {
                case "cognito":
                    String userPoolId = System.getenv("COGNITO_USER_POOL_ID");
                    if (userPoolId == null || userPoolId.isEmpty()) {
                        context.getLogger().log("Warning: COGNITO_USER_POOL_ID environment variable not set");
                        return new APIGatewayProxyResponseEvent()
                                .withStatusCode(500)
                                .withHeaders(headers)
                                .withBody("{\"error\": \"Cognito User Pool ID not configured\"}")
                                .withIsBase64Encoded(false);
                    }
                    Map<String, Object> cognitoHealth = ServiceHealthCheck.checkCognitoHealth(context, cognitoClient, CONNECTION_TIMEOUT_SECONDS, userPoolId);
                    healthResults.put("cognito", cognitoHealth);
                    allHealthy &= "healthy".equals(cognitoHealth.get("status"));
                    break;
                case "s3":
                    String bucketName = System.getenv("PROCESSED_BUCKET");
                    if (bucketName == null || bucketName.isEmpty()) {
                        context.getLogger().log("Warning: PROCESSED_BUCKET environment variable not set");
                        return new APIGatewayProxyResponseEvent()
                                .withStatusCode(500)
                                .withHeaders(headers)
                                .withBody("{\"error\": \"Processed bucket name not configured\"}")
                                .withIsBase64Encoded(false);
                    }
                    // Create a new S3 client for each request to handle potential region redirects
                    S3Client requestS3Client = S3Client.builder()
                            .region(Region.US_EAST_1)  // Start with us-east-1
                            .build();
                    Map<String, Object> s3Health = ServiceHealthCheck.checkS3Health(context, requestS3Client, CONNECTION_TIMEOUT_SECONDS, bucketName);
                    healthResults.put("s3", s3Health);
                    allHealthy &= "healthy".equals(s3Health.get("status"));
                    break;
                case "dynamodb":
                    String tableName = System.getenv("IMAGE_TABLE_NAME");
                    if (tableName == null || tableName.isEmpty()) {
                        context.getLogger().log("Warning: IMAGE_TABLE_NAME environment variable not set, using default table name");
                        tableName = "photo"; // Default table name
                    }
                    Map<String, Object> dynamoHealth = ServiceHealthCheck.checkDynamoDBHealth(context, dynamoDbClient, CONNECTION_TIMEOUT_SECONDS, tableName);
                    healthResults.put("dynamodb", dynamoHealth);
                    allHealthy &= "healthy".equals(dynamoHealth.get("status"));
                    break;
                case "all":
                default:
                    String allUserPoolId = System.getenv("COGNITO_USER_POOL_ID");
                    if (allUserPoolId == null || allUserPoolId.isEmpty()) {
                        context.getLogger().log("Warning: COGNITO_USER_POOL_ID environment variable not set");
                        return new APIGatewayProxyResponseEvent()
                                .withStatusCode(500)
                                .withHeaders(headers)
                                .withBody("{\"error\": \"Cognito User Pool ID not configured\"}")
                                .withIsBase64Encoded(false);
                    }
                    Map<String, Object> allCognitoHealth = ServiceHealthCheck.checkCognitoHealth(context, cognitoClient, CONNECTION_TIMEOUT_SECONDS, allUserPoolId);
                    healthResults.put("cognito", allCognitoHealth);
                    allHealthy &= "healthy".equals(allCognitoHealth.get("status"));
                    
                    String allBucketName = System.getenv("PROCESSED_BUCKET");
                    if (allBucketName == null || allBucketName.isEmpty()) {
                        context.getLogger().log("Warning: PROCESSED_BUCKET environment variable not set");
                        return new APIGatewayProxyResponseEvent()
                                .withStatusCode(500)
                                .withHeaders(headers)
                                .withBody("{\"error\": \"Processed bucket name not configured\"}")
                                .withIsBase64Encoded(false);
                    }
                    // Create a new S3 client for each request to handle potential region redirects
                    S3Client allRequestS3Client = S3Client.builder()
                            .region(Region.US_EAST_1)  // Start with us-east-1
                            .build();
                    Map<String, Object> allS3Health = ServiceHealthCheck.checkS3Health(context, allRequestS3Client, CONNECTION_TIMEOUT_SECONDS, allBucketName);
                    healthResults.put("s3", allS3Health);
                    allHealthy &= "healthy".equals(allS3Health.get("status"));
                    
                    String allTableName = System.getenv("IMAGE_TABLE_NAME");
                    if (allTableName == null || allTableName.isEmpty()) {
                        context.getLogger().log("Warning: IMAGE_TABLE_NAME environment variable not set, using default table name");
                        allTableName = "photo"; // Default table name
                    }
                    Map<String, Object> allDynamoHealth = ServiceHealthCheck.checkDynamoDBHealth(context, dynamoDbClient, CONNECTION_TIMEOUT_SECONDS, allTableName);
                    healthResults.put("dynamodb", allDynamoHealth);
                    allHealthy &= "healthy".equals(allDynamoHealth.get("status"));
            }

            // Convert health results to JSON
            String responseBody;
            try {
                responseBody = objectMapper.writeValueAsString(healthResults);
            } catch (JsonProcessingException e) {
                context.getLogger().log("Error serializing health results: " + e.getMessage());
                return new APIGatewayProxyResponseEvent()
                        .withStatusCode(500)
                        .withHeaders(headers)
                        .withBody("{\"error\": \"Failed to serialize health check results\"}")
                        .withIsBase64Encoded(false);
            }

            // Return 500 if any service is unhealthy, 200 if all are healthy
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(allHealthy ? 200 : 500)
                    .withHeaders(headers)
                    .withBody(responseBody)
                    .withIsBase64Encoded(false);

        } catch (Exception e) {
            context.getLogger().log("Error during health checks: " + e.getMessage());
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(500)
                    .withHeaders(headers)
                    .withBody("{\"error\": \"Health check failed\", \"message\": \"" + e.getMessage() + "\"}")
                    .withIsBase64Encoded(false);
        }
    }

//    private Map<String, Object> checkCognitoHealth(Context context) {
//        Map<String, Object> result = new HashMap<>();
//        try {
//            CompletableFuture<Boolean> future = CompletableFuture.supplyAsync(() -> {
//                try {
//                    cognitoClient.listUserPools(ListUserPoolsRequest.builder().maxResults(1).build());
//                    return true;
//                } catch (Exception e) {
//                    context.getLogger().log("Cognito health check failed: " + e.getMessage());
//                    return false;
//                }
//            });
//            boolean isHealthy = future.get(CONNECTION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
//            result.put("status", isHealthy ? "healthy" : "unhealthy");
//            result.put("timestamp", System.currentTimeMillis());
//            result.put("component", "cognito");
//        } catch (Exception e) {
//            result.put("status", "unhealthy");
//            result.put("error", e.getMessage());
//            result.put("timestamp", System.currentTimeMillis());
//            result.put("component", "cognito");
//        }
//        return result;
//    }

//    private Map<String, Object> checkS3Health(Context context) {
//        Map<String, Object> result = new HashMap<>();
//        try {
//            CompletableFuture<Boolean> future = CompletableFuture.supplyAsync(() -> {
//                try {
//                    s3Client.listBuckets();
//                    return true;
//                } catch (Exception e) {
//                    context.getLogger().log("S3 health check failed: " + e.getMessage());
//                    return false;
//                }
//            });
//            boolean isHealthy = future.get(CONNECTION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
//            result.put("status", isHealthy ? "healthy" : "unhealthy");
//            result.put("timestamp", System.currentTimeMillis());
//            result.put("component", "s3");
//        } catch (Exception e) {
//            result.put("status", "unhealthy");
//            result.put("error", e.getMessage());
//            result.put("timestamp", System.currentTimeMillis());
//            result.put("component", "s3");
//        }
//        return result;
//    }

//    private Map<String, Object> checkDynamoDBHealth(Context context) {
//        Map<String, Object> result = new HashMap<>();
//        try {
//            CompletableFuture<Boolean> future = CompletableFuture.supplyAsync(() -> {
//                try {
//                    dynamoDbClient.listTables(ListTablesRequest.builder().limit(1).build());
//                    return true;
//                } catch (Exception e) {
//                    context.getLogger().log("DynamoDB health check failed: " + e.getMessage());
//                    return false;
//                }
//            });
//            boolean isHealthy = future.get(CONNECTION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
//            result.put("status", isHealthy ? "healthy" : "unhealthy");
//            result.put("timestamp", System.currentTimeMillis());
//            result.put("component", "dynamodb");
//        } catch (Exception e) {
//            result.put("status", "unhealthy");
//            result.put("error", e.getMessage());
//            result.put("timestamp", System.currentTimeMillis());
//            result.put("component", "dynamodb");
//        }
//        return result;
//    }
}