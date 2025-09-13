package utils;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
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
    public static APIGatewayProxyResponseEvent createResponse(APIGatewayProxyRequestEvent input) {
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("X-Custom-Header", "application/json");
        headers.put("Access-Control-Allow-Origin", "*");
        headers.put("Access-Control-Allow-Methods", "OPTIONS,POST,GET, PUT, DELETE, PATCH");
        headers.put("Access-Control-Allow-Headers", "Content-Type,Authorization,X-Amz-Date,X-Api-Key,X-Amz-Security-Token");
        //headers.put("Access-Control-Allow-Credentials", "true");


        APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent()
                .withHeaders(headers);

        if ("OPTIONS".equalsIgnoreCase(input.getHttpMethod())) {
            response.setStatusCode(200);
            response.setBody("{}");
            return response;
        }

        return response;
    }
    
    /**
     * Creates a success response with the provided data
     * @param data The data to include in the response body
     * @return A configured APIGatewayProxyResponseEvent with status 200
     * @throws JsonProcessingException If the data cannot be serialized to JSON
     */
    public static APIGatewayProxyResponseEvent successResponse(Object data, int statusCode, APIGatewayProxyRequestEvent input) throws JsonProcessingException {
        return createResponse(input)
                .withStatusCode(statusCode)
                .withBody(objectMapper.writeValueAsString(data));
    }
    
    /**
     * Creates an error response with the provided message
     * @param message The error message
     * @param statusCode The HTTP status code
     * @return A configured APIGatewayProxyResponseEvent with the specified status code
     */
    public static APIGatewayProxyResponseEvent errorResponse(String message, int statusCode, APIGatewayProxyRequestEvent input) {
        Map<String, String> errorBody = new HashMap<>();
        errorBody.put("error", message);
        
        try {
            return createResponse(input)
                    .withStatusCode(statusCode)
                    .withBody(objectMapper.writeValueAsString(errorBody));
        } catch (JsonProcessingException e) {
            // Fallback if JSON serialization fails
            return createResponse(input)
                    .withStatusCode(statusCode)
                    .withBody("{\"error\":\"" + message + "\"}");
        }
    }
}