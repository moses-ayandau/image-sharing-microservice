package com;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.CopyObjectRequest;
import com.amazonaws.services.s3.model.DeleteObjectRequest;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.UpdateItemRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;


import java.util.HashMap;
import java.util.Map;

public class DeleteImageHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {
    private static final Log log = LogFactory.getLog(DeleteImageHandler.class);
    private final AmazonS3 s3Client;

    public DeleteImageHandler() {
        // Initialize AWS clients with region for multi-region support
        String regionName = System.getenv("AWS_REGION");
        if (regionName != null && !regionName.isEmpty()) {
            this.s3Client = AmazonS3ClientBuilder.standard()
                    .withRegion(regionName)
                    .build();
        } else {
            this.s3Client = AmazonS3ClientBuilder.standard().build();
        }
    }
    private final AmazonDynamoDB dynamoDB = AmazonDynamoDBClientBuilder.standard().build();

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent input, Context context) {
        if(input == null || input.getBody() == null || input.getBody().isEmpty()) {
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(400)
                    .withBody("{\"error\":\"Invalid request\"}");
        }

        log.info("Received request: " + input.getBody());

        String userId = input.getHeaders().get("userId"); // Assume authenticated userId from API Gateway authorizer
        String imageId; // Assume JSON body with imageId (e.g., {"imageId": "photo.jpg"})
        try {
            JsonNode bodyJson = new ObjectMapper().readTree(input.getBody());
            imageId = bodyJson.get("imageId").asText();
            log.info("Image ID: " + imageId);
        } catch (JsonProcessingException e) {
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(400)
                    .withBody("{\"error\":\"Invalid request body\"}");
        }
        String primaryBucket = System.getenv("PRIMARY_BUCKET");
        String recycleBucket = System.getenv("RECYCLE_BUCKET");
        String metadataTable = System.getenv("METADATA_TABLE");
        String originalKey = "images/" + userId + "/" + imageId;
        String recycleKey = userId + "/" + imageId;

        Map<String, Object> responseBody = new HashMap<>();
        int statusCode = 200;

        log.info("Deleting image: " + imageId);
        log.info("Primary bucket: " + primaryBucket);
        log.info("Recycle bucket: " + recycleBucket);
        log.info("Metadata table: " + metadataTable);
        log.info("Original key: " + originalKey);
        log.info("Recycle key: " + recycleKey);

        try {
            // Move to recycle bin (separate bucket with disaster recovery)
            s3Client.copyObject(new CopyObjectRequest(primaryBucket, originalKey, recycleBucket, recycleKey));
            s3Client.deleteObject(new DeleteObjectRequest(primaryBucket, originalKey));

            // Update DynamoDB status to "recycled"
            Map<String, AttributeValue> key = new HashMap<>();
            key.put("userId", new AttributeValue(userId));
            key.put("imageId", new AttributeValue(imageId));
            Map<String, AttributeValue> expressionAttributeValues = new HashMap<>();
            expressionAttributeValues.put(":status", new AttributeValue("recycled"));
            dynamoDB.updateItem(new UpdateItemRequest()
                    .withTableName(metadataTable)
                    .withKey(key)
                    .withUpdateExpression("SET #status = :status")
                    .withExpressionAttributeNames(Map.of("#status", "status"))
                    .withExpressionAttributeValues(expressionAttributeValues));

            responseBody.put("message", "Image moved to recycle bin: " + imageId);
        } catch (Exception e) {
            statusCode = 500;
            responseBody.put("error", "Delete operation failed: " + e.getMessage());
        }

        try {
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(statusCode)
                    .withBody(new ObjectMapper().writeValueAsString(responseBody));
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(500)
                    .withBody("{\"error\":\"Failed to serialize response\"}");
        }
    }
}