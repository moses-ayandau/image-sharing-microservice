package com.utils;

import com.factories.AwsFactory;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.util.HashMap;
import java.util.Map;

public class DynamoDBUtils {

    private static final Log log = LogFactory.getLog(DynamoDBUtils.class);
    public static final String IMAGE_ID = "imageId";
    private final DynamoDbClient dynamoDbClient;

    public DynamoDBUtils() {
        this.dynamoDbClient = AwsFactory.dynamoDbClient();
    }



    public Map<String, AttributeValue> getItemFromDynamo(String tableName, String imageId) {
        log.info(tableName);
        GetItemRequest request = GetItemRequest.builder()
                .tableName(tableName)
                .key(Map.of(IMAGE_ID, AttributeValue.fromS(imageId)))
                .build();

        GetItemResponse response = dynamoDbClient.getItem(request);
        if (response.item().isEmpty()) {
            throw new RuntimeException("Image not found in database");
        }
        return response.item();
    }


    public void deleteRecordFromDynamo(String tableName, String imageId) {
        DeleteItemRequest request = DeleteItemRequest.builder()
                .tableName(tableName)
                .key(Map.of(IMAGE_ID, AttributeValue.fromS(imageId)))
                .build();

        dynamoDbClient.deleteItem(request);
    }

    public void updateImageStatus(String tableName, String imageId, String status) {
        Map<String, AttributeValue> values = new HashMap<>();
        values.put(":status", AttributeValue.fromS(status));

        UpdateItemRequest request = UpdateItemRequest.builder()
                .tableName(tableName)
                .key(Map.of(IMAGE_ID, AttributeValue.fromS(imageId)))
                .updateExpression("SET #status = :status")
                .expressionAttributeNames(Map.of("#status", "status"))
                .expressionAttributeValues(values)
                .build();

        dynamoDbClient.updateItem(request);
    }

    public void updateS3Key(String tableName, String imageId, String newS3Key) {
        Map<String, AttributeValue> key = Map.of(
                IMAGE_ID, AttributeValue.fromS(imageId)
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
