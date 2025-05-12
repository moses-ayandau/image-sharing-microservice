package com.utils;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;

public class ResponseUtil {

    public static APIGatewayProxyResponseEvent createSuccessResponse(
            Map<String, String> headers, Map<String, Object> healthResults, boolean allHealthy, ObjectMapper objectMapper) {
        try {
            String responseBody = objectMapper.writeValueAsString(healthResults);
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(allHealthy ? 200 : 500)
                    .withHeaders(headers)
                    .withBody(responseBody)
                    .withIsBase64Encoded(false);
        } catch (JsonProcessingException e) {
            return createErrorResponse(headers, "Failed to serialize health check results");
        }
    }

    public static APIGatewayProxyResponseEvent createErrorResponse(Map<String, String> headers, String errorMessage) {
        return new APIGatewayProxyResponseEvent()
                .withStatusCode(500)
                .withHeaders(headers)
                .withBody("{\"error\": \"" + errorMessage + "\"}")
                .withIsBase64Encoded(false);
    }
}
