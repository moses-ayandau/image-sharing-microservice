package com.process.service;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.DynamoDbException;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;

import java.time.Instant;
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


    public void storeImageMetadata(String userId, String imageKey, String imageTitle,  String imageUrl) {
        try {
            Map<String, AttributeValue> item = new HashMap<>();

            item.put("userId", AttributeValue.builder().s(userId).build());
            item.put("imageKey", AttributeValue.builder().s(imageKey).build());


//            item.put("firstName", AttributeValue.builder().s(firstName).build());
//            item.put("lastName", AttributeValue.builder().s(lastName).build());
            item.put("processedDate", AttributeValue.builder().s(Instant.now().toString()).build());
            item.put("imageUrl", AttributeValue.builder().s(imageUrl).build());
            item.put("status", AttributeValue.fromS("active"));
            item.put("title", AttributeValue.builder().s(imageTitle).build());


            PutItemRequest request = PutItemRequest.builder()
                    .tableName(tableName)
                    .item(item)
                    .build();

            dynamoDbClient.putItem(request);
            System.out.println("Successfully stored metadata for image: " + imageKey);

        } catch (DynamoDbException e) {
            System.err.println("Error storing image metadata in DynamoDB: " + e.getMessage());
            throw e;
        }
    }
}