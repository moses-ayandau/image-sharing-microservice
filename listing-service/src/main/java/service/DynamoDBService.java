package service;

import factories.DynamodbFactory;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;
import software.amazon.awssdk.services.dynamodb.model.ScanResponse;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DynamoDBService {
    private static final DynamoDbClient dynamoDbClient = DynamodbFactory.createClient();
    private static final String tableName = System.getenv("IMAGE_TABLE");;

    public static List<Map<String, AttributeValue>> getActiveImages(String userId) {
        Map<String, AttributeValue> expressionAttributeValues = new HashMap<>();
        expressionAttributeValues.put(":statusValue", AttributeValue.builder().s("active").build());
        expressionAttributeValues.put(":userIdValue", AttributeValue.builder().s(userId).build());

        Map<String, String> expressionAttributeNames = new HashMap<>();
        expressionAttributeNames.put("#status", "status");
        expressionAttributeNames.put("#userId", "userId");

        ScanRequest scanRequest = ScanRequest.builder()
                .tableName(tableName)
                .filterExpression("#status = :statusValue AND #userId = :userIdValue")
                .expressionAttributeValues(expressionAttributeValues)
                .expressionAttributeNames(expressionAttributeNames)
                .build();

        ScanResponse scanResponse = dynamoDbClient.scan(scanRequest);
        return scanResponse.items();
    }

    public static List<Map<String, AttributeValue>> getDeletedImages(String userId) {
        // Create a scan request to filter for inactive images for this user
        Map<String, AttributeValue> expressionAttributeValues = new HashMap<>();
        expressionAttributeValues.put(":statusValue", AttributeValue.builder().s("inactive").build());
        expressionAttributeValues.put(":userIdValue", AttributeValue.builder().s(userId).build());

        // Use expression attribute names to handle reserved keywords
        Map<String, String> expressionAttributeNames = new HashMap<>();
        expressionAttributeNames.put("#status", "status");
        expressionAttributeNames.put("#userId", "userId");

        ScanRequest scanRequest = ScanRequest.builder()
                .tableName(tableName)
                .filterExpression("#status = :statusValue AND #userId = :userIdValue")
                .expressionAttributeValues(expressionAttributeValues)
                .expressionAttributeNames(expressionAttributeNames)
                .build();

        ScanResponse scanResponse = dynamoDbClient.scan(scanRequest);
        return scanResponse.items();
    }

    public static boolean isImageActive(String imageKey){
        Map<String, AttributeValue> expressionAttributeValues = new HashMap<>();
        expressionAttributeValues.put(":statusValue", AttributeValue.builder().s("active").build());
        expressionAttributeValues.put(":imageKey", AttributeValue.builder().s(imageKey).build());

        Map<String, String> expressionAttributeNames = new HashMap<>();
        expressionAttributeNames.put("#status", "status");
        expressionAttributeNames.put("#imageKey", "imageKey");

        ScanRequest scanRequest = ScanRequest.builder()
                .tableName(tableName)
                .filterExpression("#status = :statusValue AND #imageKey = :imageKey")
                .expressionAttributeValues(expressionAttributeValues)
                .expressionAttributeNames(expressionAttributeNames)
                .build();

        ScanResponse scanResponse = dynamoDbClient.scan(scanRequest);
        return !scanResponse.items().isEmpty();
    }
}
