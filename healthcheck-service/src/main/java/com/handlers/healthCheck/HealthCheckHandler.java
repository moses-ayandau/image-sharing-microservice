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
    private final Region region;

    public HealthCheckHandler() {
        this.region = Region.EU_CENTRAL_1;

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


        // Get system information
        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory() / (1024 * 1024);
        long freeMemory = runtime.freeMemory() / (1024 * 1024);
        long totalMemory = runtime.totalMemory() / (1024 * 1024);

        // Determine overall health status

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
                timestamp,
                System.getenv("ENVIRONMENT") != null ? System.getenv("ENVIRONMENT") : "production",
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
                .withStatusCode(200)
                .withHeaders(headers)
                .withBody(responseBody)
                .withIsBase64Encoded(false);
    }
}