package com.utils;

import com.factories.AwsFactory;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.util.HashMap;
import java.util.Map;

public class DynamoDBUtils {
    private static final String TABLE_NAME = "ImageMetadata";

    private final DynamoDbClient dynamoDbClient;

    public DynamoDBUtils() {
        this.dynamoDbClient = AwsFactory.dynamoDbClient();
    }


    public Map<String, AttributeValue> getItemFromDynamo(String imageId) {
        GetItemRequest request = GetItemRequest.builder()
                .tableName(TABLE_NAME)
                .key(Map.of("ImageId", AttributeValue.fromS(imageId)))
                .build();

        GetItemResponse response = dynamoDbClient.getItem(request);
        if (response.item().isEmpty()) {
            throw new RuntimeException("Image not found in database");
        }
        return response.item();
    }


    public void deleteRecordFromDynamo(String imageId) {
        DeleteItemRequest request = DeleteItemRequest.builder()
                .tableName(TABLE_NAME)
                .key(Map.of("ImageId", AttributeValue.fromS(imageId)))
                .build();

        dynamoDbClient.deleteItem(request);
    }

    public void updateImageStatus(String imageId) {
        Map<String, AttributeValue> values = new HashMap<>();
        values.put(":status", AttributeValue.fromS("deleted"));

        UpdateItemRequest request = UpdateItemRequest.builder()
                .tableName(TABLE_NAME)
                .key(Map.of("ImageId", AttributeValue.fromS(imageId)))
                .updateExpression("SET #status = :status")
                .expressionAttributeNames(Map.of("#status", "Status"))
                .expressionAttributeValues(values)
                .build();

        dynamoDbClient.updateItem(request);
    }

}
