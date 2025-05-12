package com;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.ObjectMapper;

import upload.model.ImageUploadRequest;
import upload.model.ImageUploadResponse;
import upload.service.ImageService;

import java.util.HashMap;
import java.util.Map;

/**
 * Lambda handler for image upload API requests.
 */
public class App implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {
    private final ImageService imageService;
    private final ObjectMapper objectMapper;
    
    public App() {
        this.imageService = new ImageService();
        this.objectMapper = new ObjectMapper();
    }
    
    /**
     * Processes API Gateway requests for image uploads.
     */
    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent input, Context context) {
        Map<String, String> headers = getCorsHeaders();
        
        try {
            if (input.getHttpMethod().equals("OPTIONS")) {
                return handleOptions(input, context);
            }
            
            String body = input.getBody();
            
            ImageUploadRequest request = objectMapper.readValue(body, ImageUploadRequest.class);
            
            ImageUploadResponse response = imageService.processImageUpload(request);
            
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
     * Creates error response with specified message.
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
     * Handles OPTIONS requests for CORS.
     */
    public APIGatewayProxyResponseEvent handleOptions(APIGatewayProxyRequestEvent input, Context context) {
        return new APIGatewayProxyResponseEvent()
            .withStatusCode(200)
            .withHeaders(getCorsHeaders())
            .withBody("");
    }
    
    /**
     * Creates CORS headers.
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
