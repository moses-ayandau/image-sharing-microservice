package com.process.util;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class DynamoDbService {

    private final DynamoDbClient dynamoDbClient;
    private final String imageTable;

    public DynamoDbService(String regionName, String imageTable) {
        Region region = Region.of(regionName);
        this.dynamoDbClient = DynamoDbClient.builder().region(region).build();
        this.imageTable = imageTable;
    }

    /**
     * Stores image metadata in DynamoDB
     *
     * @param userId User ID
     * @param imageKey S3 key of the processed image
     * @param firstName User's first name
     * @param lastName User's last name
     */
    public void storeImageMetadata(String userId, String imageKey, String firstName, String lastName) {
        String imageId = UUID.randomUUID().toString();

        Map<String, AttributeValue> item = new HashMap<>();
        item.put("imageId", AttributeValue.builder().s(imageId).build());
        item.put("userId", AttributeValue.builder().s(userId).build());
        item.put("imageKey", AttributeValue.builder().s(imageKey).build());
        item.put("firstName", AttributeValue.builder().s(firstName).build());
        item.put("lastName", AttributeValue.builder().s(lastName).build());
        item.put("uploadDate", AttributeValue.builder().s(LocalDate.now().toString()).build());
        item.put("isDeleted", AttributeValue.builder().bool(false).build());

        PutItemRequest putItemRequest = PutItemRequest.builder()
                .tableName(imageTable)
                .item(item)
                .build();

        dynamoDbClient.putItem(putItemRequest);
    }
}