package com;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.service.ImageService;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

public class App implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {
    private final ImageService imageService;
    private final ObjectMapper objectMapper;
    
    public App() {
        this.imageService = new ImageService();
        this.objectMapper = new ObjectMapper();
    }
    
    /**
     * Handles incoming API Gateway requests for image uploads.
     * Processes the request, extracts user information from JWT token,
     * and delegates to ImageService for image processing.
     *
     * @param input   The API Gateway request event
     * @param context The Lambda execution context
     * @return API Gateway response with status code and body
     */
    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent input, Context context) {
        Map<String, String> headers = getCorsHeaders();
        
        try {
            if (input.getHttpMethod().equals("OPTIONS")) {
                return handleOptions(input, context);
            }    
            String body = input.getBody();
            JsonNode requestJson = objectMapper.readTree(body);
            

            String token = input.getHeaders().get("Authorization");
            if (token != null && token.startsWith("Bearer ")) {
                token = token.substring(7); // Remove "Bearer " prefix
            } else {
                throw new IllegalArgumentException("Missing or invalid Authorization token");
            }
            
            
            String username = extractUsernameFromToken(token);
            
            
            String imageBase64 = requestJson.has("image") ? requestJson.get("image").asText() : null;
            String contentType = requestJson.has("contentType") ? requestJson.get("contentType").asText() : null;
            
            if (imageBase64 == null || imageBase64.isEmpty()) {
                throw new IllegalArgumentException("Image data is required");
            }
            
            Map<String, Object> response = imageService.processImageUpload(username, imageBase64, contentType);

            return new APIGatewayProxyResponseEvent()
                .withStatusCode(200)
                .withHeaders(headers)
                .withBody(objectMapper.writeValueAsString(response));
                
        } catch (IllegalArgumentException e) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getMessage());
            
            try {
                return new APIGatewayProxyResponseEvent()
                    .withStatusCode(400) 
                    .withHeaders(headers)
                    .withBody(objectMapper.writeValueAsString(errorResponse));
            } catch (Exception ex) {
                return getErrorResponse(headers, ex);
            }
        } catch (Exception e) {
            return getErrorResponse(headers, e);
        }
    }
    
    /**
     * Extracts the username from a JWT token.
     * Attempts to find username in common JWT claim fields.
     *
     * @param token The JWT token string
     * @return The extracted username
     * @throws IllegalArgumentException If username cannot be extracted
     */
    private String extractUsernameFromToken(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                throw new IllegalArgumentException("Invalid JWT token format");
            }
            
            String payload = new String(Base64.getUrlDecoder().decode(parts[1]));
            JsonNode payloadJson = objectMapper.readTree(payload);
            

            if (payloadJson.has("username")) {
                return payloadJson.get("username").asText();
            } else if (payloadJson.has("sub")) {
                return payloadJson.get("sub").asText();
            } else if (payloadJson.has("preferred_username")) {
                return payloadJson.get("preferred_username").asText();
            }
            
            throw new IllegalArgumentException("Username not found in token");
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to extract username from token: " + e.getMessage());
        }
    }
    
    /**
     * Creates an error response with the given exception message.
     *
     * @param headers The HTTP headers to include in the response
     * @param e The exception that caused the error
     * @return API Gateway response with error details
     */
    private APIGatewayProxyResponseEvent getErrorResponse(Map<String, String> headers, Exception e) {
        Map<String, String> errorResponse = new HashMap<>();
        errorResponse.put("error", e.getMessage());
        
        try {
            return new APIGatewayProxyResponseEvent()
                .withStatusCode(500)
                .withHeaders(headers)
                .withBody(objectMapper.writeValueAsString(errorResponse));
        } catch (Exception ex) {
            return new APIGatewayProxyResponseEvent()
                .withStatusCode(500)
                .withHeaders(headers)
                .withBody("{\"error\": \"Internal server error\"}");
        }
    }
    
    /**
     * Handles OPTIONS requests for CORS preflight.
     *
     * @param input The API Gateway request event
     * @param context The Lambda execution context
     * @return API Gateway response with CORS headers
     */
    public APIGatewayProxyResponseEvent handleOptions(APIGatewayProxyRequestEvent input, Context context) {
        return new APIGatewayProxyResponseEvent()
            .withStatusCode(200)
            .withHeaders(getCorsHeaders())
            .withBody("");
    }
    
    /**
     * Creates a map of CORS headers for cross-origin requests.
     *
     * @return Map of HTTP headers for CORS support
     */
    private Map<String, String> getCorsHeaders() {
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("Access-Control-Allow-Origin", "*");
        headers.put("Access-Control-Allow-Methods", "POST, OPTIONS");
        headers.put("Access-Control-Allow-Headers", "Content-Type,X-Amz-Date,Authorization,X-Api-Key");
        return headers;
    }
}
