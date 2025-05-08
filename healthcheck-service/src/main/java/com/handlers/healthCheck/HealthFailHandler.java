package com.photo.handlers;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import java.util.HashMap;
import java.util.Map;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

public class HealthFailHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent input, Context context) {
        context.getLogger().log("Simulating health check failure");

        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");

        // Timestamp for log
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
        dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        String timestamp = dateFormat.format(new Date());

        // Build failure message
        String responseBody = String.format(
                "{" +
                        "\"status\":\"unhealthy\"," +
                        "\"timestamp\":\"%s\"," +
                        "\"service\":\"photo-api\"," +
                        "\"message\":\"Simulated service degradation or failure.\"," +
                        "\"action\":\"Investigate possible causes such as memory leaks, downstream service errors, or infrastructure limits.\"," +
                        "\"environment\":\"%s\"" +
                        "}",
                timestamp,
                System.getenv("ENVIRONMENT") != null ? System.getenv("ENVIRONMENT") : "production"
        );

        return new APIGatewayProxyResponseEvent()
                .withStatusCode(503)
                .withHeaders(headers)
                .withBody(responseBody)
                .withIsBase64Encoded(false);
    }
}
