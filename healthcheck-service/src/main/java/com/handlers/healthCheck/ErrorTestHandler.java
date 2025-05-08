package com.handlers.healthCheck;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * Lambda function that intentionally generates errors to test CloudWatch alarms
 */
public class ErrorTestHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private final Random random = new Random();

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent input, Context context) {
        context.getLogger().log("Processing error test request");

        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("X-Custom-Header", "Error Test");
        headers.put("Access-Control-Allow-Origin", "*");

        // Get error type from query string parameter, default to "500"
        String errorType = "500";
        if (input.getQueryStringParameters() != null && input.getQueryStringParameters().containsKey("type")) {
            errorType = input.getQueryStringParameters().get("type");
        }

        // Allow specifying probability of error, default to 100%
        int errorProbability = 100;
        if (input.getQueryStringParameters() != null && input.getQueryStringParameters().containsKey("probability")) {
            try {
                errorProbability = Integer.parseInt(input.getQueryStringParameters().get("probability"));
                // Ensure value is within valid range
                errorProbability = Math.max(0, Math.min(100, errorProbability));
            } catch (NumberFormatException e) {
                context.getLogger().log("Invalid probability parameter, using default 100%");
            }
        }

        // Determine if we should throw an error based on probability
        boolean shouldThrowError = random.nextInt(100) < errorProbability;

        if (shouldThrowError) {
            // Return different error responses based on the error type parameter
            switch (errorType) {
                case "400":
                    return new APIGatewayProxyResponseEvent()
                            .withStatusCode(400)
                            .withHeaders(headers)
                            .withBody("{\"error\": \"Bad Request\", \"message\": \"Test 400 error\"}")
                            .withIsBase64Encoded(false);
                case "401":
                    return new APIGatewayProxyResponseEvent()
                            .withStatusCode(401)
                            .withHeaders(headers)
                            .withBody("{\"error\": \"Unauthorized\", \"message\": \"Test 401 error\"}")
                            .withIsBase64Encoded(false);
                case "403":
                    return new APIGatewayProxyResponseEvent()
                            .withStatusCode(403)
                            .withHeaders(headers)
                            .withBody("{\"error\": \"Forbidden\", \"message\": \"Test 403 error\"}")
                            .withIsBase64Encoded(false);
                case "404":
                    return new APIGatewayProxyResponseEvent()
                            .withStatusCode(404)
                            .withHeaders(headers)
                            .withBody("{\"error\": \"Not Found\", \"message\": \"Test 404 error\"}")
                            .withIsBase64Encoded(false);
                case "500":
                default:
                    return new APIGatewayProxyResponseEvent()
                            .withStatusCode(500)
                            .withHeaders(headers)
                            .withBody("{\"error\": \"Internal Server Error\", \"message\": \"Test 500 error\"}")
                            .withIsBase64Encoded(false);
                case "502":
                    return new APIGatewayProxyResponseEvent()
                            .withStatusCode(502)
                            .withHeaders(headers)
                            .withBody("{\"error\": \"Bad Gateway\", \"message\": \"Test 502 error\"}")
                            .withIsBase64Encoded(false);
                case "503":
                    return new APIGatewayProxyResponseEvent()
                            .withStatusCode(503)
                            .withHeaders(headers)
                            .withBody("{\"error\": \"Service Unavailable\", \"message\": \"Test 503 error\"}")
                            .withIsBase64Encoded(false);
                case "504":
                    return new APIGatewayProxyResponseEvent()
                            .withStatusCode(504)
                            .withHeaders(headers)
                            .withBody("{\"error\": \"Gateway Timeout\", \"message\": \"Test 504 error\"}")
                            .withIsBase64Encoded(false);
            }
        }

        // If we're not throwing an error (based on probability), return success
        return new APIGatewayProxyResponseEvent()
                .withStatusCode(200)
                .withHeaders(headers)
                .withBody("{\"status\": \"success\", \"message\": \"No error generated this time\"}")
                .withIsBase64Encoded(false);
    }
}