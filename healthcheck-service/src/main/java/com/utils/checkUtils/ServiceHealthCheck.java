package com.utils.checkUtils;

import com.amazonaws.services.lambda.runtime.Context;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ListUserPoolsRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.dynamodb.model.ListTablesRequest;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class ServiceHealthCheck {

    public static Map<String, Object> checkCognitoHealth(Context context, CognitoIdentityProviderClient cognitoClient, int connectionTimeout, String userPoolId) {
        Map<String, Object> result = new HashMap<>();
        long startTime = System.currentTimeMillis();
        try {
            CompletableFuture<Boolean> future = CompletableFuture.supplyAsync(() -> {
                try {
                    // Check if the specific user pool exists and is accessible
                    cognitoClient.describeUserPool(software.amazon.awssdk.services.cognitoidentityprovider.model.DescribeUserPoolRequest.builder()
                            .userPoolId(userPoolId)
                            .build());
                    return true;
                } catch (Exception e) {
                    context.getLogger().log("Cognito user pool health check failed for pool " + userPoolId + ": " + e.getMessage());
                    result.put("error", e.getMessage());
                    result.put("errorType", e.getClass().getSimpleName());
                    result.put("stackTrace", e.getStackTrace());
                    return false;
                }
            });
            boolean isHealthy = future.get(connectionTimeout, TimeUnit.SECONDS);
            long endTime = System.currentTimeMillis();
            result.put("status", isHealthy ? "healthy" : "unhealthy");
            result.put("timestamp", endTime);
            result.put("latency", endTime - startTime);
            result.put("component", "cognito");
            result.put("userPoolId", userPoolId);
            result.put("region", cognitoClient.serviceClientConfiguration().region().toString());
        } catch (Exception e) {
            long endTime = System.currentTimeMillis();
            result.put("status", "unhealthy");
            result.put("error", e.getMessage());
            result.put("errorType", e.getClass().getSimpleName());
            result.put("stackTrace", e.getStackTrace());
            result.put("timestamp", endTime);
            result.put("latency", endTime - startTime);
            result.put("component", "cognito");
            result.put("userPoolId", userPoolId);
            result.put("region", cognitoClient.serviceClientConfiguration().region().toString());
        }
        return result;
    }

    public static Map<String, Object> checkS3Health(Context context, software.amazon.awssdk.services.s3.S3Client s3Client, int connectionTimeout, String bucketName) {
        Map<String, Object> result = new HashMap<>();
        long startTime = System.currentTimeMillis();
        try {
            CompletableFuture<Boolean> future = CompletableFuture.supplyAsync(() -> {
                try {
                    // Check if the bucket exists and is accessible by listing objects
                    s3Client.listObjects(software.amazon.awssdk.services.s3.model.ListObjectsRequest.builder()
                            .bucket(bucketName)
                            .maxKeys(1)  // Only need to check if we can access the bucket
                            .build());
                    return true;
                } catch (Exception e) {
                    context.getLogger().log("S3 bucket health check failed for bucket " + bucketName + ": " + e.getMessage());
                    result.put("error", e.getMessage());
                    result.put("errorType", e.getClass().getSimpleName());
                    result.put("stackTrace", e.getStackTrace());
                    return false;
                }
            });
            boolean isHealthy = future.get(connectionTimeout, TimeUnit.SECONDS);
            long endTime = System.currentTimeMillis();
            result.put("status", isHealthy ? "healthy" : "unhealthy");
            result.put("timestamp", endTime);
            result.put("latency", endTime - startTime);
            result.put("component", "s3");
            result.put("bucketName", bucketName);
            result.put("region", s3Client.serviceClientConfiguration().region().toString());
        } catch (Exception e) {
            long endTime = System.currentTimeMillis();
            result.put("status", "unhealthy");
            result.put("error", e.getMessage());
            result.put("errorType", e.getClass().getSimpleName());
            result.put("stackTrace", e.getStackTrace());
            result.put("timestamp", endTime);
            result.put("latency", endTime - startTime);
            result.put("component", "s3");
            result.put("bucketName", bucketName);
            result.put("region", s3Client.serviceClientConfiguration().region().toString());
        }
        return result;
    }

    public static Map<String, Object> checkDynamoDBHealth(Context context, DynamoDbClient dynamoDbClient, int connectionTimeout, String tableName) {
        Map<String, Object> result = new HashMap<>();
        long startTime = System.currentTimeMillis();
        try {
            CompletableFuture<Boolean> future = CompletableFuture.supplyAsync(() -> {
                try {
                    // Check if the specific table exists and is accessible
                    dynamoDbClient.describeTable(software.amazon.awssdk.services.dynamodb.model.DescribeTableRequest.builder()
                            .tableName(tableName)
                            .build());
                    return true;
                } catch (Exception e) {
                    context.getLogger().log("DynamoDB table health check failed for table " + tableName + ": " + e.getMessage());
                    result.put("error", e.getMessage());
                    result.put("errorType", e.getClass().getSimpleName());
                    result.put("stackTrace", e.getStackTrace());
                    return false;
                }
            });
            boolean isHealthy = future.get(connectionTimeout, TimeUnit.SECONDS);
            long endTime = System.currentTimeMillis();
            result.put("status", isHealthy ? "healthy" : "unhealthy");
            result.put("timestamp", endTime);
            result.put("latency", endTime - startTime);
            result.put("component", "dynamodb");
            result.put("tableName", tableName);
            result.put("region", dynamoDbClient.serviceClientConfiguration().region().toString());
        } catch (Exception e) {
            long endTime = System.currentTimeMillis();
            result.put("status", "unhealthy");
            result.put("error", e.getMessage());
            result.put("errorType", e.getClass().getSimpleName());
            result.put("stackTrace", e.getStackTrace());
            result.put("timestamp", endTime);
            result.put("latency", endTime - startTime);
            result.put("component", "dynamodb");
            result.put("tableName", tableName);
            result.put("region", dynamoDbClient.serviceClientConfiguration().region().toString());
        }
        return result;
    }

}
