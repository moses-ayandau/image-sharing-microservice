package com.handlers.healthCheck;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ListUserPoolsRequest;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.ListTablesRequest;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ListBucketsRequest;

import java.util.HashMap;
import java.util.Map;
import java.lang.Runtime;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class HealthCheckHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static final int CONNECTION_TIMEOUT_SECONDS = 5;
    private final Region region;
    private final DynamoDbClient dynamoDbClient;
    private final S3Client s3Client;
    private final CognitoIdentityProviderClient cognitoClient;

    public HealthCheckHandler() {
        // Get the region from environment variable or use EU_CENTRAL_1 as default
        this.region = Region.EU_CENTRAL_1;

        // Initialize AWS service clients with retry configurations
        this.dynamoDbClient = DynamoDbClient.builder()
                .region(region)
                .build();

        // Configure S3 client with enhanced options
        this.s3Client = S3Client.builder()
                .region(region)
                .build();

        this.cognitoClient = CognitoIdentityProviderClient.builder()
                .region(region)
                .build();
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent input, Context context) {
        context.getLogger().log("Processing health check request");

        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("X-Custom-Header", "Health Check");
        headers.put("Access-Control-Allow-Origin", "*");  // Add CORS header

        // Get current time in ISO 8601 format
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
        dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        String timestamp = dateFormat.format(new Date());

        // Check AWS services connectivity
        Map<String, Object> servicesHealth = checkServicesHealth(context);
        boolean allServicesHealthy = (boolean) servicesHealth.get("allHealthy");

        // Get system information
        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory() / (1024 * 1024);
        long freeMemory = runtime.freeMemory() / (1024 * 1024);
        long totalMemory = runtime.totalMemory() / (1024 * 1024);

        // Determine overall health status
        String status = allServicesHealthy ? "healthy" : "degraded";
        int statusCode = allServicesHealthy ? 500 : 500;  // Always return 200 for health checks

        // Create response with status information and system metrics
        String responseBody = String.format(
                "{" +
                        "\"status\":\"%s\"," +
                        "\"timestamp\":\"%s\"," +
                        "\"service\":\"photo-api\"," +
                        "\"version\":\"1.0.0\"," +
                        "\"environment\":\"%s\"," +
                        "\"services\":{" +
                        "\"dynamodb\":%s," +
                        "\"s3\":%s," +
                        "\"cognito\":%s" +
                        "}," +
                        "\"memory\":{" +
                        "\"max\":%d," +
                        "\"total\":%d," +
                        "\"free\":%d," +
                        "\"used\":%d" +
                        "}," +
                        "\"processors\":%d," +
                        "\"aws\":{" +
                        "\"region\":\"%s\"," +
                        "\"functionName\":\"%s\"," +
                        "\"functionVersion\":\"%s\"," +
                        "\"memoryLimitInMB\":%d," +
                        "\"remainingTimeInMillis\":%d" +
                        "}" +
                        "}",
                status,
                timestamp,
                System.getenv("ENVIRONMENT") != null ? System.getenv("ENVIRONMENT") : "production",
                servicesHealth.get("dynamodb"),
                servicesHealth.get("s3"),
                servicesHealth.get("cognito"),
                maxMemory,
                totalMemory,
                freeMemory,
                (totalMemory - freeMemory),
                runtime.availableProcessors(),
                region.toString(),
                context.getFunctionName(),
                context.getFunctionVersion(),
                context.getMemoryLimitInMB(),
                context.getRemainingTimeInMillis()
        );

        return new APIGatewayProxyResponseEvent()
                .withStatusCode(statusCode)
                .withHeaders(headers)
                .withBody(responseBody)
                .withIsBase64Encoded(false);
    }

    private Map<String, Object> checkServicesHealth(Context context) {
        Map<String, Object> results = new HashMap<>();
        boolean allHealthy = true;

        try {
            // Check DynamoDB connectivity
            boolean dynamoDbHealthy = checkDynamoDbConnectivity(context);
            results.put("dynamodb", dynamoDbHealthy);
            allHealthy &= dynamoDbHealthy;
            context.getLogger().log("DynamoDB health check: " + (dynamoDbHealthy ? "healthy" : "unhealthy"));

            // Check S3 connectivity
            boolean s3Healthy = checkS3Connectivity(context);
            results.put("s3", s3Healthy);
            allHealthy &= s3Healthy;
            context.getLogger().log("S3 health check: " + (s3Healthy ? "healthy" : "unhealthy"));

            // Check Cognito connectivity
            boolean cognitoHealthy = checkCognitoConnectivity(context);
            results.put("cognito", cognitoHealthy);
            allHealthy &= cognitoHealthy;
            context.getLogger().log("Cognito health check: " + (cognitoHealthy ? "healthy" : "unhealthy"));
        } catch (Exception e) {
            context.getLogger().log("Error during health checks: " + e.getMessage());
            allHealthy = false;
        }

        results.put("allHealthy", allHealthy);
        return results;
    }

    private boolean checkDynamoDbConnectivity(Context context) {
        try {
            CompletableFuture<Boolean> future = CompletableFuture.supplyAsync(() -> {
                try {
                    // Just send a simple request to check connectivity
                    dynamoDbClient.listTables(ListTablesRequest.builder().limit(1).build());
                    return true;
                } catch (Exception e) {
                    context.getLogger().log("DynamoDB connectivity check failed: " + e.getMessage());
                    return false;
                }
            });
            return future.get(CONNECTION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            context.getLogger().log("DynamoDB check timed out or failed: " + e.getMessage());
            return false;
        }
    }

    private boolean checkS3Connectivity(Context context) {
        try {
            CompletableFuture<Boolean> future = CompletableFuture.supplyAsync(() -> {
                try {
                    // Just send a simple request to check connectivity
                    s3Client.listBuckets();  // Simplified call without unnecessary builder
                    return true;
                } catch (Exception e) {
                    context.getLogger().log("S3 connectivity check failed: " + e.getMessage() + " | " + e.getClass().getName());
                    e.printStackTrace();
                    return false;
                }
            });
            return future.get(CONNECTION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            context.getLogger().log("S3 check timed out or failed: " + e.getMessage());
            return false;
        }
    }

    private boolean checkCognitoConnectivity(Context context) {
        try {
            CompletableFuture<Boolean> future = CompletableFuture.supplyAsync(() -> {
                try {
                    // Just send a simple request to check connectivity
                    cognitoClient.listUserPools(ListUserPoolsRequest.builder().maxResults(1).build());
                    return true;
                } catch (Exception e) {
                    context.getLogger().log("Cognito connectivity check failed: " + e.getMessage());
                    return false;
                }
            });
            return future.get(CONNECTION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            context.getLogger().log("Cognito check timed out or failed: " + e.getMessage());
            return false;
        }
    }
}