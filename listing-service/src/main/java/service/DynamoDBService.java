package service;

import factories.DynamodbFactory;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.util.List;
import java.util.Map;

public class DynamoDBService {
    private static final DynamoDbClient dynamoDbClient = DynamodbFactory.createClient();
    private static final String tableName = System.getenv("IMAGE_TABLE");

    public static List<Map<String, AttributeValue>> getActiveImages(String userId) {
        QueryRequest queryRequest = QueryRequest.builder()
                .tableName(tableName)
                .indexName("OwnerStatusIndex")
                .keyConditionExpression("userId = :userId AND #s = :statusValue")
                .expressionAttributeNames(Map.of("#s", "status"))
                .expressionAttributeValues(Map.of(
                        ":userId", AttributeValue.builder().s(userId).build(),
                        ":statusValue", AttributeValue.builder().s("active").build()))
                .build();

        QueryResponse response = dynamoDbClient.query(queryRequest);
        return response.items();
    }

    public static List<Map<String, AttributeValue>> getDeletedImages(String userId) {
        QueryRequest queryRequest = QueryRequest.builder()
                .tableName(tableName)
                .indexName("OwnerStatusIndex")
                .keyConditionExpression("userId = :userId AND #s = :statusValue")
                .expressionAttributeNames(Map.of("#s", "status"))
                .expressionAttributeValues(Map.of(
                        ":userId", AttributeValue.builder().s(userId).build(),
                        ":statusValue", AttributeValue.builder().s("inactive").build()))
                .build();

        QueryResponse response = dynamoDbClient.query(queryRequest);
        return response.items();
    }

    public static boolean isImageActive(String imageKey) {
        String imageUrl = "https://image-processed-bucket-prod-861276111046.s3.us-east-1.amazonaws.com/" + imageKey;


        GetItemRequest getItemRequest = GetItemRequest.builder()
                .tableName(tableName)
                .key(Map.of(
                        "imageKey", AttributeValue.builder().s(imageKey).build(),
                        "imageUrl", AttributeValue.builder().s(imageUrl).build()))
                .projectionExpression("#s")
                .expressionAttributeNames(Map.of("#s", "status"))
                .build();

        GetItemResponse response = dynamoDbClient.getItem(getItemRequest);
        Map<String, AttributeValue> item = response.item();

        return item != null && "active".equals(item.get("status").s());
    }
}