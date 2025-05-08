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

    public static Map<String, Object> checkCognitoHealth(Context context, CognitoIdentityProviderClient cognitoClient, int connectionTimeout) {
        Map<String, Object> result = new HashMap<>();
        try {
            CompletableFuture<Boolean> future = CompletableFuture.supplyAsync(() -> {
                try {
                    cognitoClient.listUserPools(ListUserPoolsRequest.builder().maxResults(1).build());
                    return true;
                } catch (Exception e) {
                    context.getLogger().log("Cognito health check failed: " + e.getMessage());
                    return false;
                }
            });
            boolean isHealthy = future.get(connectionTimeout, TimeUnit.SECONDS);
            result.put("status", isHealthy ? "healthy" : "unhealthy");
            result.put("timestamp", System.currentTimeMillis());
            result.put("component", "cognito");
        } catch (Exception e) {
            result.put("status", "unhealthy");
            result.put("error", e.getMessage());
            result.put("timestamp", System.currentTimeMillis());
            result.put("component", "cognito");
        }
        return result;
    }

    public static Map<String, Object> checkS3Health(Context context, software.amazon.awssdk.services.s3.S3Client s3Client, int connectionTimeout) {
        Map<String, Object> result = new HashMap<>();
        try {
            CompletableFuture<Boolean> future = CompletableFuture.supplyAsync(() -> {
                try {
                    s3Client.listBuckets();
                    return true;
                } catch (Exception e) {
                    context.getLogger().log("S3 health check failed: " + e.getMessage());
                    return false;
                }
            });
            boolean isHealthy = future.get(connectionTimeout, TimeUnit.SECONDS);
            result.put("status", isHealthy ? "healthy" : "unhealthy");
            result.put("timestamp", System.currentTimeMillis());
            result.put("component", "s3");
        } catch (Exception e) {
            result.put("status", "unhealthy");
            result.put("error", e.getMessage());
            result.put("timestamp", System.currentTimeMillis());
            result.put("component", "s3");
        }
        return result;
    }

    public static Map<String, Object> checkDynamoDBHealth(Context context, DynamoDbClient dynamoDbClient, int connectionTimeout) {
        Map<String, Object> result = new HashMap<>();
        try {
            CompletableFuture<Boolean> future = CompletableFuture.supplyAsync(() -> {
                try {
                    dynamoDbClient.listTables(ListTablesRequest.builder().limit(1).build());
                    return true;
                } catch (Exception e) {
                    context.getLogger().log("DynamoDB health check failed: " + e.getMessage());
                    return false;
                }
            });
            boolean isHealthy = future.get(connectionTimeout, TimeUnit.SECONDS);
            result.put("status", isHealthy ? "healthy" : "unhealthy");
            result.put("timestamp", System.currentTimeMillis());
            result.put("component", "dynamodb");
        } catch (Exception e) {
            result.put("status", "unhealthy");
            result.put("error", e.getMessage());
            result.put("timestamp", System.currentTimeMillis());
            result.put("component", "dynamodb");
        }
        return result;
    }

}
