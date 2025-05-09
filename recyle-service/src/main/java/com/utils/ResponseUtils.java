package com.utils;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;

import java.util.HashMap;
import java.util.Map;

public class ResponseUtils {
    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Creates a standard API Gateway response with common headers
     * @return A pre-configured APIGatewayProxyResponseEvent
     */
    public static APIGatewayProxyResponseEvent createResponse() {
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("X-Custom-Header", "application/json");
        headers.put("Access-Control-Allow-Origin", "*");
        headers.put("Access-Control-Allow-Methods", "OPTIONS,POST,GET, PUT, DELETE, PATCH");
        headers.put("Access-Control-Allow-Headers", "Content-Type,Authorization,X-Amz-Date,X-Api-Key,X-Amz-Security-Token");
        headers.put("Access-Control-Allow-Credentials", "true");


        return new APIGatewayProxyResponseEvent()
                .withHeaders(headers);
    }

    public static APIGatewayProxyResponseEvent successResponse( int statusCode, Object data) throws JsonProcessingException {
        return createResponse()
                .withStatusCode(statusCode)
                .withBody(objectMapper.writeValueAsString(data));
    }

    public static APIGatewayProxyResponseEvent errorResponse( int statusCode, String message) {
        Map<String, String> errorBody = new HashMap<>();
        errorBody.put("error", message);

        try {
            return createResponse()
                    .withStatusCode(statusCode)
                    .withBody(objectMapper.writeValueAsString(errorBody));
        } catch (JsonProcessingException e) {
            // Fallback if JSON serialization fails
            return createResponse()
                    .withStatusCode(statusCode)
                    .withBody("{\"error\":\"" + message + "\"}");
        }
    }
}