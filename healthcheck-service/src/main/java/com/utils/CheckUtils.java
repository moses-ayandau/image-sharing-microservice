package com.utils;

import com.amazonaws.services.lambda.runtime.Context;
import com.handlers.healthCheck.ComponentHealthCheckHandler;
import com.service.checkService.ServiceHealthCheck;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.s3.S3Client;

import java.util.Map;

public class CheckUtils {
    private static final int CONNECTION_TIMEOUT_SECONDS = 5;
    private static final Region REGION = Region.US_EAST_1;
    private static final String DEFAULT_TABLE_NAME = "photo";

    private final Region region;
    private final DynamoDbClient dynamoDbClient;
    private final S3Client s3Client;
    private final CognitoIdentityProviderClient cognitoClient;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;
    private final ComponentHealthCheckHandler.EnvironmentProvider environmentProvider;

    public CheckUtils(
            ComponentHealthCheckHandler.EnvironmentProvider environmentProvider
    ){
        this.environmentProvider = environmentProvider;
        this.region = REGION;
        this.dynamoDbClient = DynamoDbClient.builder().region(region).build();
        this.s3Client = S3Client.builder().region(region).build();
        this.cognitoClient = CognitoIdentityProviderClient.builder().region(region).build();
        this.objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
    }
    public boolean checkCognitoHealth(Context context, Map<String, Object> healthResults) {
        String userPoolId = getRequiredEnvVar("COGNITO_USER_POOL_ID", "Cognito User Pool ID not configured");
        Map<String, Object> cognitoHealth = ServiceHealthCheck.checkCognitoHealth(
                context, cognitoClient, CONNECTION_TIMEOUT_SECONDS, userPoolId);
        healthResults.put("cognito", cognitoHealth);
        return "healthy".equals(cognitoHealth.get("status"));
    }

     public boolean checkS3Health(Context context, Map<String, Object> healthResults) {
        String bucketName = getRequiredEnvVar("PROCESSED_BUCKET", "Processed bucket name not configured");
        S3Client requestS3Client = S3Client.builder().region(REGION).build();
        Map<String, Object> s3Health = ServiceHealthCheck.checkS3Health(
                context, requestS3Client, CONNECTION_TIMEOUT_SECONDS, bucketName);
        healthResults.put("s3", s3Health);
        return "healthy".equals(s3Health.get("status"));
    }

    public boolean checkDynamoDBHealth(Context context, Map<String, Object> healthResults) {
        String tableName = environmentProvider.getEnv("IMAGE_TABLE_NAME");
        if (tableName == null || tableName.isEmpty()) {
            context.getLogger().log("Warning: IMAGE_TABLE_NAME environment variable not set, using default table name");
            tableName = DEFAULT_TABLE_NAME;
        }
        Map<String, Object> dynamoHealth = ServiceHealthCheck.checkDynamoDBHealth(
                context, dynamoDbClient, CONNECTION_TIMEOUT_SECONDS, tableName);
        healthResults.put("dynamodb", dynamoHealth);
        return "healthy".equals(dynamoHealth.get("status"));
    }

    public boolean checkAllComponents(Context context, Map<String, Object> healthResults) {
        boolean cognitoHealthy = checkCognitoHealth(context, healthResults);
        boolean s3Healthy = checkS3Health(context, healthResults);
        boolean dynamoHealthy = checkDynamoDBHealth(context, healthResults);
        return cognitoHealthy && s3Healthy && dynamoHealthy;
    }

    private String getRequiredEnvVar(String name, String errorMessage) {
        String value = environmentProvider.getEnv(name);
        if (value == null || value.isEmpty()) {
            throw new IllegalStateException(errorMessage);
        }
        return value;
    }
}
