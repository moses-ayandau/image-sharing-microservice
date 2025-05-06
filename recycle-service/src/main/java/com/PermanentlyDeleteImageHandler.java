package com;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.DeleteObjectRequest;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.DeleteItemRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;

import java.util.HashMap;
import java.util.Map;

public class PermanentlyDeleteImageHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {
    private final AmazonS3 s3Client;

    public PermanentlyDeleteImageHandler() {
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
        String userId = input.getHeaders().get("userId"); // Assume authenticated userId from API Gateway authorizer
        String imageId = input.getBody().split(",")[0]; // Assume JSON body with imageId (e.g., {"imageId": "photo.jpg"})

        String recycleBucket = System.getenv("RECYCLE_BUCKET");
        String metadataTable = System.getenv("METADATA_TABLE");
        String recycleKey = userId + "/" + imageId;

        Map<String, Object> responseBody = new HashMap<>();
        int statusCode = 200;

        try {
            // Permanently delete from recycle bin (separate bucket with disaster recovery)
            s3Client.deleteObject(new DeleteObjectRequest(recycleBucket, recycleKey));

            // Remove from DynamoDB
            Map<String, AttributeValue> key = new HashMap<>();
            key.put("userId", new AttributeValue(userId));
            key.put("imageId", new AttributeValue(imageId));
            dynamoDB.deleteItem(new DeleteItemRequest()
                    .withTableName(metadataTable)
                    .withKey(key));

            responseBody.put("message", "Image permanently deleted: " + imageId);
        } catch (Exception e) {
            statusCode = 500;
            responseBody.put("error", "Permanent delete operation failed: " + e.getMessage());
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