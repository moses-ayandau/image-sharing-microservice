package com.process.util;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.DynamoDbException;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;

public class DynamoDbService {
    private final DynamoDbClient dynamoDbClient;
    private final String tableName;

    public DynamoDbService(String region, String tableName) {
        this.dynamoDbClient = DynamoDbClient.builder()
                .region(Region.of(region))
                .build();
        this.tableName = tableName;

        System.out.println("DynamoDbService initialized with tableName: " + tableName + " in region: " + region);
    }

    /**
     * Stores image metadata in DynamoDB
     *
     * @param userId    The user ID
     * @param imageKey  The image key in the processed bucket
     * @param firstName The user's first name
     * @param lastName  The user's last name
     */
    public void storeImageMetadata(String userId, String imageKey, String firstName, String lastName, String imageUrl) {
        try {
            // Create item attributes
            Map<String, AttributeValue> item = new HashMap<>();

            // Primary key attributes
            item.put("userId", AttributeValue.builder().s(userId).build());
            item.put("imageKey", AttributeValue.builder().s(imageKey).build());

            // Other attributes
            item.put("firstName", AttributeValue.builder().s(firstName).build());
            item.put("lastName", AttributeValue.builder().s(lastName).build());
            item.put("processedDate", AttributeValue.builder().s(Instant.now().toString()).build());
            item.put("imageUrl", AttributeValue.builder().s(imageUrl).build());


            // Set TTL for 30 days (if used)
            long ttl = Instant.now().plus(30, ChronoUnit.DAYS).getEpochSecond();
            item.put("ttl", AttributeValue.builder().n(String.valueOf(ttl)).build());

            // Create request
            PutItemRequest request = PutItemRequest.builder()
                    .tableName(tableName)
                    .item(item)
                    .build();

            // Put item in the table
            dynamoDbClient.putItem(request);
            System.out.println("Successfully stored metadata for image: " + imageKey);

        } catch (DynamoDbException e) {
            System.err.println("Error storing image metadata in DynamoDB: " + e.getMessage());
            throw e;
        }
    }
}