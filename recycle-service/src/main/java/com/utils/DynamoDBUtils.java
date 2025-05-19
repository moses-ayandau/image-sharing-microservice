package com.utils;

import com.amazonaws.services.lambda.runtime.Context;
import com.factories.AwsFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.util.HashMap;
import java.util.Map;

public class DynamoDBUtils {
    String imageUrl = "https://image-processed-bucket-prod-861276111046.s3.us-east-1.amazonaws.com/";
    public static final String IMAGE_ID = "imageKey";
    private static DynamoDbClient dynamoDbClient = AwsFactory.dynamoDbClient();

    public DynamoDBUtils() {
        this.dynamoDbClient = AwsFactory.dynamoDbClient();
    }

    private static final Logger logger  = LoggerFactory.getLogger(DynamoDBUtils.class);

    public  Map<String, AttributeValue> getItemFromDynamo(String tableName, String imageKey) {
        String url = imageUrl + imageKey;
        GetItemRequest getItemRequest = GetItemRequest.builder()
                .tableName(tableName)
                .key(Map.of(
                        "imageKey", AttributeValue.builder().s(imageKey).build(),
                        "imageUrl", AttributeValue.builder().s(url).build()))
                .build();

        GetItemResponse response = dynamoDbClient.getItem(getItemRequest);
        Map<String, AttributeValue> item = response.item();
        return response.item();
    }


    public void deleteRecordFromDynamo(String tableName, String imageKey) {
        String url = imageUrl + imageKey;
        DeleteItemRequest request = DeleteItemRequest.builder()
                .tableName(tableName)
                .key(Map.of(
                        "imageKey", AttributeValue.builder().s(imageKey).build(),
                        "imageUrl", AttributeValue.builder().s(url).build()))
                .build();

        dynamoDbClient.deleteItem(request);
    }

    public void updateImageStatus(String tableName, String imageKey, String status) {
        String url = imageUrl + imageKey;
        Map<String, AttributeValue> values = new HashMap<>();
        values.put(":status", AttributeValue.fromS(status));

        UpdateItemRequest request = UpdateItemRequest.builder()
                .tableName(tableName)
                .key(Map.of(
                        "imageKey", AttributeValue.builder().s(imageKey).build(),
                        "imageUrl", AttributeValue.builder().s(url).build()))
                .updateExpression("SET #status = :status")
                .expressionAttributeNames(Map.of("#status", "status"))
                .expressionAttributeValues(values)
                .build();

        dynamoDbClient.updateItem(request);
    }

    public void updateS3Key(String tableName, String imageKey, String newS3Key) {
        String url = imageUrl + imageKey;
        Map<String, AttributeValue> key = Map.of(
                "imageKey", AttributeValue.builder().s(imageKey).build(),
                "imageUrl", AttributeValue.builder().s(url).build());

        Map<String, AttributeValue> expressionAttributeValues = Map.of(
                ":newS3Key", AttributeValue.fromS(newS3Key)
        );

        Map<String, String> expressionAttributeNames = Map.of(
                "#newS3KeyField", "s3Key"
        );

        UpdateItemRequest request = UpdateItemRequest.builder()
                .tableName(tableName)
                .key(key)
                .updateExpression("SET #newS3KeyField = :newS3Key")
                .expressionAttributeNames(expressionAttributeNames)
                .expressionAttributeValues(expressionAttributeValues)
                .build();

        dynamoDbClient.updateItem(request);
    }

}
