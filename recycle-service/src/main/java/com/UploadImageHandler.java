package com;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.PutItemRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.ByteArrayInputStream;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.time.Instant;

/**
 * Handler for uploading images to S3 bucket.
 */
public class UploadImageHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private final AmazonS3 s3Client;
    private final AmazonDynamoDB dynamoDB;
    
    public UploadImageHandler() {
        // Initialize AWS clients with region
        String regionName = System.getenv("AWS_REGION");
        if (regionName != null && !regionName.isEmpty()) {
            this.s3Client = AmazonS3ClientBuilder.standard()
                    .withRegion(regionName)
                    .build();
                    
            this.dynamoDB = AmazonDynamoDBClientBuilder.standard()
                    .withRegion(regionName)
                    .build();
        } else {
            this.s3Client = AmazonS3ClientBuilder.standard().build();
            this.dynamoDB = AmazonDynamoDBClientBuilder.standard().build();
        }
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent input, Context context) {
        // Get user ID from headers or cognito authorizer
        String userId = input.getHeaders() != null ? input.getHeaders().get("userId") : null;
        if (userId == null || userId.isEmpty()) {
            // Try to get from authorizer claims if using Cognito
            if (input.getRequestContext() != null && 
                input.getRequestContext().getAuthorizer() != null) {
                Map<String, Object> claims = (Map<String, Object>) input.getRequestContext().getAuthorizer().get("claims");
                if (claims != null) {
                    userId = (String) claims.get("sub");
                }
            }
        }
        
        if (userId == null || userId.isEmpty()) {
            return createErrorResponse(400, "User ID not found in request");
        }
        
        // Parse the request body
        String imageData;
        String imageId;
        String contentType;
        String fileName;
        
        try {
            if (input.getBody() == null || input.getBody().isEmpty()) {
                return createErrorResponse(400, "Request body is required");
            }
            
            JsonNode bodyJson = OBJECT_MAPPER.readTree(input.getBody());
            
            // Get base64 encoded image data
            JsonNode imageDataNode = bodyJson.get("imageData");
            if (imageDataNode == null || imageDataNode.asText().isEmpty()) {
                return createErrorResponse(400, "Image data is required");
            }
            imageData = imageDataNode.asText();
            
            // Get content type
            JsonNode contentTypeNode = bodyJson.get("contentType");
            if (contentTypeNode == null || contentTypeNode.asText().isEmpty()) {
                return createErrorResponse(400, "Content type is required");
            }
            contentType = contentTypeNode.asText();
            
            // Get file name or generate one
            JsonNode fileNameNode = bodyJson.get("fileName");
            if (fileNameNode != null && !fileNameNode.asText().isEmpty()) {
                fileName = fileNameNode.asText();
            } else {
                fileName = "image-" + UUID.randomUUID().toString();
            }
            
            // Generate image ID (use filename without extension as image ID)
            int extensionIndex = fileName.lastIndexOf('.');
            if (extensionIndex > 0) {
                imageId = fileName.substring(0, extensionIndex);
            } else {
                imageId = fileName;
            }
            
            // Ensure imageId is URL-safe
            imageId = imageId.replaceAll("[^a-zA-Z0-9-_]", "_");
            
        } catch (Exception e) {
            context.getLogger().log("Error parsing request body: " + e.getMessage());
            return createErrorResponse(400, "Invalid request body: " + e.getMessage());
        }

        // Get environment variables
        String primaryBucket = System.getenv("PRIMARY_BUCKET");
        String metadataTable = System.getenv("METADATA_TABLE");
        
        if (primaryBucket == null || primaryBucket.isEmpty()) {
            return createErrorResponse(500, "PRIMARY_BUCKET environment variable is not set");
        }
        
        if (metadataTable == null || metadataTable.isEmpty()) {
            return createErrorResponse(500, "METADATA_TABLE environment variable is not set");
        }
        
        // Define the S3 key using the same structure as other handlers
        String s3Key = "images/" + userId + "/" + imageId;
        
        try {
            // Decode base64 image data
            byte[] decodedImage = Base64.getDecoder().decode(imageData);
            
            // Set up metadata for S3 object
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(decodedImage.length);
            metadata.setContentType(contentType);
            
            // Upload to S3
            PutObjectRequest putRequest = new PutObjectRequest(
                    primaryBucket,
                    s3Key,
                    new ByteArrayInputStream(decodedImage),
                    metadata
            );
            s3Client.putObject(putRequest);
            
            // Generate S3 URL
            String imageUrl = s3Client.getUrl(primaryBucket, s3Key).toString();
            
            // Store metadata in DynamoDB
            Map<String, AttributeValue> item = new HashMap<>();
            item.put("userId", new AttributeValue(userId));
            item.put("imageId", new AttributeValue(imageId));
            item.put("fileName", new AttributeValue(fileName));
            item.put("contentType", new AttributeValue(contentType));
            item.put("s3Key", new AttributeValue(s3Key));
            item.put("status", new AttributeValue("active"));
            item.put("uploadTime", new AttributeValue(Instant.now().toString()));
            
            dynamoDB.putItem(new PutItemRequest()
                    .withTableName(metadataTable)
                    .withItem(item));
            
            // Create success response
            Map<String, Object> responseBody = new HashMap<>();
            responseBody.put("message", "Image uploaded successfully");
            responseBody.put("imageId", imageId);
            responseBody.put("imageUrl", imageUrl);
            
            return createSuccessResponse(responseBody);
            
        } catch (Exception e) {
            context.getLogger().log("Error uploading image: " + e.getMessage());
            return createErrorResponse(500, "Failed to upload image: " + e.getMessage());
        }
    }
    
    /**
     * Helper method to create a success response.
     */
    private APIGatewayProxyResponseEvent createSuccessResponse(Map<String, Object> responseBody) {
        try {
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(200)
                    .withHeaders(Collections.singletonMap("Content-Type", "application/json"))
                    .withBody(OBJECT_MAPPER.writeValueAsString(responseBody));
        } catch (JsonProcessingException e) {
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(500)
                    .withHeaders(Collections.singletonMap("Content-Type", "application/json"))
                    .withBody("{\"error\":\"Failed to serialize response\"}");
        }
    }
    
    /**
     * Helper method to create an error response.
     */
    private APIGatewayProxyResponseEvent createErrorResponse(int statusCode, String errorMessage) {
        Map<String, String> responseBody = Collections.singletonMap("error", errorMessage);
        try {
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(statusCode)
                    .withHeaders(Collections.singletonMap("Content-Type", "application/json"))
                    .withBody(OBJECT_MAPPER.writeValueAsString(responseBody));
        } catch (JsonProcessingException e) {
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(500)
                    .withHeaders(Collections.singletonMap("Content-Type", "application/json"))
                    .withBody("{\"error\":\"Internal server error\"}");
        }
    }
}
