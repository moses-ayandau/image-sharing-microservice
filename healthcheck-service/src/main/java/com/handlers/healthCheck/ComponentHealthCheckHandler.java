package com.handlers.healthCheck;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.service.checkService.ServiceHealthCheck;
import com.utils.CheckUtils;
import com.utils.Helper;
import com.utils.ResponseUtil;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.s3.S3Client;

import java.util.HashMap;
import java.util.Map;

public class ComponentHealthCheckHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static final int CONNECTION_TIMEOUT_SECONDS = 5;
    private static final Region REGION = Region.US_EAST_1;
    private static final String DEFAULT_TABLE_NAME = "photo";

    private final Region region;
//    private final DynamoDbClient dynamoDbClient;
//    private final S3Client s3Client;
//    private final CognitoIdentityProviderClient cognitoClient;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;
    private final EnvironmentProvider environmentProvider;

    public interface EnvironmentProvider {
        String getEnv(String name);
    }

    public static class DefaultEnvironmentProvider implements EnvironmentProvider {
        @Override
        public String getEnv(String name) {
            return System.getenv(name);
        }
    }

    public ComponentHealthCheckHandler() {
        this(new DefaultEnvironmentProvider());
    }

    public ComponentHealthCheckHandler(EnvironmentProvider environmentProvider) {
        this.environmentProvider = environmentProvider;
        this.region = REGION;
//        this.dynamoDbClient = DynamoDbClient.builder().region(region).build();
//        this.s3Client = S3Client.builder().region(region).build();
//        this.cognitoClient = CognitoIdentityProviderClient.builder().region(region).build();
        this.objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent input, Context context) {
        context.getLogger().log("Processing component health check request");

        Map<String, String> headers = Helper.createResponseHeaders();
        String component = Helper.getComponentFromPath(input);
        Map<String, Object> healthResults = new HashMap<>();
        boolean allHealthy = true;

        try {
            CheckUtils checkHealth = new CheckUtils(environmentProvider);
            switch (component.toLowerCase()) {
                case "cognito":
                    allHealthy = checkHealth.checkCognitoHealth(context, healthResults);
                    break;
                case "s3":
                    allHealthy = checkHealth.checkS3Health(context, healthResults);
                    break;
                case "dynamodb":
                    allHealthy = checkHealth.checkDynamoDBHealth(context, healthResults);
                    break;
                case "all":
                    allHealthy = checkHealth.checkAllComponents(context, healthResults);
                    break;
                default:
                    return ResponseUtil.createErrorResponse(headers, "Invalid component specified: " + component);
            }

            return  ResponseUtil.createSuccessResponse(headers, healthResults, allHealthy, objectMapper);

        } catch (Exception e) {
            context.getLogger().log("Error during health checks: " + e.getMessage());
            return ResponseUtil.createErrorResponse(headers, "Health check failed: " + e.getMessage());
        }
    }

//    private Map<String, String> createResponseHeaders() {
//        Map<String, String> headers = new HashMap<>();
//        headers.put("Content-Type", "application/json");
//        headers.put("Access-Control-Allow-Origin", "*");
//        return headers;
//    }

//    private String getComponentFromPath(APIGatewayProxyRequestEvent input) {
//        return input.getPathParameters() != null ?
//               input.getPathParameters().get("component") : "all";
//    }

//    private boolean checkCognitoHealth(Context context, Map<String, Object> healthResults) {
//        String userPoolId = getRequiredEnvVar("COGNITO_USER_POOL_ID", "Cognito User Pool ID not configured");
//        Map<String, Object> cognitoHealth = ServiceHealthCheck.checkCognitoHealth(
//                context, cognitoClient, CONNECTION_TIMEOUT_SECONDS, userPoolId);
//        healthResults.put("cognito", cognitoHealth);
//        return "healthy".equals(cognitoHealth.get("status"));
//    }
//
//    private boolean checkS3Health(Context context, Map<String, Object> healthResults) {
//        String bucketName = getRequiredEnvVar("PROCESSED_BUCKET", "Processed bucket name not configured");
//        S3Client requestS3Client = S3Client.builder().region(REGION).build();
//        Map<String, Object> s3Health = ServiceHealthCheck.checkS3Health(
//                context, requestS3Client, CONNECTION_TIMEOUT_SECONDS, bucketName);
//        healthResults.put("s3", s3Health);
//        return "healthy".equals(s3Health.get("status"));
//    }
//
//    private boolean checkDynamoDBHealth(Context context, Map<String, Object> healthResults) {
//        String tableName = environmentProvider.getEnv("IMAGE_TABLE_NAME");
//        if (tableName == null || tableName.isEmpty()) {
//            context.getLogger().log("Warning: IMAGE_TABLE_NAME environment variable not set, using default table name");
//            tableName = DEFAULT_TABLE_NAME;
//        }
//        Map<String, Object> dynamoHealth = ServiceHealthCheck.checkDynamoDBHealth(
//                context, dynamoDbClient, CONNECTION_TIMEOUT_SECONDS, tableName);
//        healthResults.put("dynamodb", dynamoHealth);
//        return "healthy".equals(dynamoHealth.get("status"));
//    }
//
//    private boolean checkAllComponents(Context context, Map<String, Object> healthResults) {
//        boolean cognitoHealthy = checkCognitoHealth(context, healthResults);
//        boolean s3Healthy = checkS3Health(context, healthResults);
//        boolean dynamoHealthy = checkDynamoDBHealth(context, healthResults);
//        return cognitoHealthy && s3Healthy && dynamoHealthy;
//    }
//
//    private String getRequiredEnvVar(String name, String errorMessage) {
//        String value = environmentProvider.getEnv(name);
//        if (value == null || value.isEmpty()) {
//            throw new IllegalStateException(errorMessage);
//        }
//        return value;
//    }

//    private APIGatewayProxyResponseEvent createSuccessResponse(
//            Map<String, String> headers, Map<String, Object> healthResults, boolean allHealthy) {
//        try {
//            String responseBody = objectMapper.writeValueAsString(healthResults);
//            return new APIGatewayProxyResponseEvent()
//                    .withStatusCode(allHealthy ? 200 : 500)
//                    .withHeaders(headers)
//                    .withBody(responseBody)
//                    .withIsBase64Encoded(false);
//        } catch (JsonProcessingException e) {
//            return createErrorResponse(headers, "Failed to serialize health check results");
//        }
//    }
//
//    private APIGatewayProxyResponseEvent createErrorResponse(Map<String, String> headers, String errorMessage) {
//        return new APIGatewayProxyResponseEvent()
//                .withStatusCode(500)
//                .withHeaders(headers)
//                .withBody("{\"error\": \"" + errorMessage + "\"}")
//                .withIsBase64Encoded(false);
//    }
}