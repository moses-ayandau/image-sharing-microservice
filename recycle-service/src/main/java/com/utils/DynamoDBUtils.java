package com.utils;

import com.factories.AwsFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.util.HashMap;
import java.util.Map;

public class DynamoDBUtils {

    public static final String IMAGE_ID = "imageKey";
    private final DynamoDbClient dynamoDbClient;

    public DynamoDBUtils() {
        this.dynamoDbClient = AwsFactory.dynamoDbClient();
    }

private final Logger logger  = LoggerFactory.getLogger(DynamoDBUtils.class);

    public Map<String, AttributeValue> getItemFromDynamo(String tableName, String imageKey) {
        GetItemRequest request = GetItemRequest.builder()
                .tableName(tableName)
                .key(Map.of(IMAGE_ID, AttributeValue.fromS(imageKey)))
                .build();

        GetItemResponse response = dynamoDbClient.getItem(request);
        logger.info("request:  " + request);

        if (response.item().isEmpty()) {
            throw new RuntimeException("Image not found in database");
        }
        logger.info("Response: "+ response);
        return response.item();
    }


    public void deleteRecordFromDynamo(String tableName, String imageKey) {
        DeleteItemRequest request = DeleteItemRequest.builder()
                .tableName(tableName)
                .key(Map.of(IMAGE_ID, AttributeValue.fromS(imageKey)))
                .build();

        dynamoDbClient.deleteItem(request);
    }

    public void updateImageStatus(String tableName, String imageKey, String status) {
        Map<String, AttributeValue> values = new HashMap<>();
        values.put(":status", AttributeValue.fromS(status));

        UpdateItemRequest request = UpdateItemRequest.builder()
                .tableName(tableName)
                .key(Map.of(IMAGE_ID, AttributeValue.fromS(imageKey)))
                .updateExpression("SET #status = :status")
                .expressionAttributeNames(Map.of("#status", "status"))
                .expressionAttributeValues(values)
                .build();

        dynamoDbClient.updateItem(request);
    }

    public void updateS3Key(String tableName, String imageKey, String newS3Key) {
        Map<String, AttributeValue> key = Map.of(
                IMAGE_ID, AttributeValue.fromS(imageKey)
        );

        Map<String, AttributeValue> expressionAttributeValues = Map.of(
                ":newS3Key", AttributeValue.fromS(newS3Key)
        );

        Map<String, String> expressionAttributeNames = Map.of(
                "#s3Key", "S3Key"
        );

        UpdateItemRequest request = UpdateItemRequest.builder()
                .tableName(tableName)
                .key(key)
                .updateExpression("SET #s3Key = :newS3Key")
                .expressionAttributeNames(expressionAttributeNames)
                .expressionAttributeValues(expressionAttributeValues)
                .build();

        dynamoDbClient.updateItem(request);
    }

}
