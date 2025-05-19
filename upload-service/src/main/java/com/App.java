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
    private ImageService imageService;
    private final ObjectMapper objectMapper;

    public App() {
        this.imageService = new ImageService();
        this.objectMapper = new ObjectMapper();
    }
    
    // Setter for ImageService - needed for testing
    public void setImageService(ImageService imageService) {
        this.imageService = imageService;
    }

    /**
     * Handles incoming API Gateway requests for image uploads.
     * Processes the request, extracts user information from JWT token,
     * and delegates to ImageService for image processing.
     *
     * @param input   The API Gateway request event
     * @param context The Lambda execution context
     * @return API Gateway response with status code and body.
     */
    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent input, Context context) {
        Map<String, String> headers = getCorsHeaders();
        
        // Get the logger from the context
        final var logger = context.getLogger();
        
        try {
            if (input.getHttpMethod().equals("OPTIONS")) {
                logger.log("Handling OPTIONS request");
                return handleOptions(input, context);
            }    
            String body = input.getBody();
            JsonNode requestJson = objectMapper.readTree(body);
            
            // Get token from request body if available, otherwise try header
            String token;
            if (requestJson.has("token")) {
                token = requestJson.get("token").asText();
            } else {
                // Fall back to header
                token = input.getHeaders().get("Authorization");
                if (token != null && token.startsWith("Bearer ")) {
                    token = token.substring(7);
                } else {
                    logger.log("ERROR: No valid token found in request");
                    throw new IllegalArgumentException("Authentication token is required");
                }
            }
            
            String name = extractNameFromToken(token, logger);
            String email = extractEmailFromToken(token, logger);
            String userId = extractSubFromToken(token, logger);

            String imageBase64 = requestJson.has("image") ? requestJson.get("image").asText() : null;
            String contentType = requestJson.has("contentType") ? requestJson.get("contentType").asText() : null;
            
            // Extract imageTitle if provided
            String imageTitle = null;
            if (requestJson.has("imageTitle")) {
                imageTitle = requestJson.get("imageTitle").asText();
            }
            
            if (imageBase64 == null || imageBase64.isEmpty()) {
                logger.log("ERROR: Image data is required but was missing or empty");
                throw new IllegalArgumentException("Image data is required");
            }
            
            Map<String, Object> response = imageService.processImageUpload(name, email, imageBase64, contentType, imageTitle, userId, context);
            logger.log("Image upload response from upload lambda: " + response);
            
            // Ensure name and email are in the response
            if (!response.containsKey("name")) {
                response.put("name", name);
            }
            if (!response.containsKey("email")) {
                response.put("email", email);
            }

            return new APIGatewayProxyResponseEvent()
                .withStatusCode(200)
                .withHeaders(headers)
                .withBody(objectMapper.writeValueAsString(response));
        } catch (IllegalArgumentException e) {
            logger.log("ERROR: Bad request - " + e.getMessage());
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getMessage());

            try {
                return new APIGatewayProxyResponseEvent()
                        .withStatusCode(400)
                        .withHeaders(headers)
                        .withBody(objectMapper.writeValueAsString(errorResponse));
            } catch (Exception ex) {
                logger.log("ERROR: Failed to serialize error response - " + ex.getMessage());
                return getErrorResponse(headers, ex);
            }
        } catch (Exception e) {
            logger.log("ERROR: Internal server error - " + e.getMessage());
            e.printStackTrace();
            return getErrorResponse(headers, e);
        }
    }

    /**
     * Extracts the name from a JWT token.
     * Attempts to find name in common JWT claim fields.
     *
     * @param token The JWT token string
     * @param logger The Lambda logger
     * @return The extracted name
     */
    private String extractNameFromToken(String token, com.amazonaws.services.lambda.runtime.LambdaLogger logger) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                logger.log("Invalid JWT token format");
                return "unknown-user";
            }
            
            try {
                String payload = new String(Base64.getUrlDecoder().decode(parts[1]));
                JsonNode payloadJson = objectMapper.readTree(payload);
                
                // Look for "name" first, then fall back to other fields
                if (payloadJson.has("name")) {
                    return payloadJson.get("name").asText();
                } else if (payloadJson.has("sub")) {
                    return payloadJson.get("sub").asText();
                } else if (payloadJson.has("cognito:name")) {
                    return payloadJson.get("cognito:name").asText();
                }
                
                logger.log("No name field found in token");
                return "unknown-user";
            } catch (Exception e) {
                logger.log("Error decoding token payload: " + e.getMessage());
                return "unknown-user";
            }
        } catch (Exception e) {
            logger.log("Failed to extract name from token: " + e.getMessage());
            return "unknown-user";
        }
    }

    /**
     * Extracts the email from a JWT token.
     *
     * @param token The JWT token string
     * @param logger The Lambda logger
     * @return The extracted email
     */
    private String extractEmailFromToken(String token, com.amazonaws.services.lambda.runtime.LambdaLogger logger) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                logger.log("Invalid JWT token format");
                return "unknown-email";
            }
            
            try {
                String payload = new String(Base64.getUrlDecoder().decode(parts[1]));
                JsonNode payloadJson = objectMapper.readTree(payload);
                
                if (payloadJson.has("email")) {
                    return payloadJson.get("email").asText();
                } else if (payloadJson.has("mail")) {
                    return payloadJson.get("mail").asText();
                }
                
                logger.log("No email field found in token");
                return "unknown-email";
            } catch (Exception e) {
                logger.log("Error decoding token payload: " + e.getMessage());
                return "unknown-email";
            }
        } catch (Exception e) {
            logger.log("Failed to extract email from token: " + e.getMessage());
            return "unknown-email";
        }
    }


    /**
     * Extracts the subject (sub) from a JWT token.
     * The 'sub' claim is a unique identifier for the user.
     *
     * @param token The JWT token string
     * @param logger The Lambda logger
     * @return The extracted subject identifier
     */
    private String extractSubFromToken(String token, com.amazonaws.services.lambda.runtime.LambdaLogger logger) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                logger.log("Invalid JWT token format");
                return "unknown-sub";
            }

            try {
                String payload = new String(Base64.getUrlDecoder().decode(parts[1]));
                JsonNode payloadJson = objectMapper.readTree(payload);

                if (payloadJson.has("sub")) {
                    return payloadJson.get("sub").asText();
                } else if (payloadJson.has("cognito:sub")) {
                    return payloadJson.get("cognito:sub").asText();
                } else if (payloadJson.has("user_id")) {
                    return payloadJson.get("user_id").asText();
                }

                logger.log("No subject identifier found in token");
                return "unknown-sub";
            } catch (Exception e) {
                logger.log("Error decoding token payload: " + e.getMessage());
                return "unknown-sub";
            }
        } catch (Exception e) {
            logger.log("Failed to extract subject from token: " + e.getMessage());
            return "unknown-sub";
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
        context.getLogger().log("Handling OPTIONS request with CORS headers");
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
