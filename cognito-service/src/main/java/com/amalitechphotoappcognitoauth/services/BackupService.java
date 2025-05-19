package com.amalitechphotoappcognitoauth.services;

import com.amazonaws.services.lambda.runtime.Context;
import com.google.gson.Gson;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.*;
import software.amazon.awssdk.services.cognitoidentityprovider.paginators.ListUsersIterable;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * The type Backup service.
 */
public class BackupService {

    private static final int MAX_DYNAMODB_ITEM_SIZE = 390000; // 390KB, leaving margin for attribute names and overhead

    /**
     * Backup user pool configuration.
     *
     * @param cognitoClient  the cognito client
     * @param dynamoDbClient the dynamo db client
     * @param userPoolId     the user pool id
     * @param backupTable    the backup table
     * @param gson           the gson
     * @param context        the context
     */
    public static void backupUserPoolConfiguration(CognitoIdentityProviderClient cognitoClient,
                                                   DynamoDbClient dynamoDbClient,
                                                   String userPoolId,
                                                   String backupTable,
                                                   Gson gson,
                                                   Context context) {
        context.getLogger().log("Backing up user pool configuration");

        DescribeUserPoolResponse userPoolDetails = cognitoClient.describeUserPool(
                DescribeUserPoolRequest.builder()
                        .userPoolId(userPoolId)
                        .build());

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss"));
        String backupId = UUID.randomUUID().toString();

        String userPoolJson = gson.toJson(userPoolDetails.userPool());

        // Store in DynamoDB
        storeInDynamoDB(backupId, "CONFIG", timestamp, userPoolJson, context, dynamoDbClient, backupTable, userPoolId);
    }

    /**
     * Backup user data.
     *
     * @param cognitoClient  the cognito client
     * @param dynamoDbClient the dynamo db client
     * @param userPoolId     the user pool id
     * @param backupTable    the backup table
     * @param gson           the gson
     * @param context        the context
     */
    public static void backupUserData(CognitoIdentityProviderClient cognitoClient,
                                      DynamoDbClient dynamoDbClient,
                                      String userPoolId,
                                      String backupTable,
                                      Gson gson,
                                      Context context) {
        context.getLogger().log("Backing up user data");

        List<Map<String, Object>> users = new ArrayList<>();

        // Set up request to list users
        ListUsersRequest listUsersRequest = ListUsersRequest.builder()
                .userPoolId(userPoolId)
                .limit(60)
                .build();

        ListUsersIterable listUsersResponses = cognitoClient.listUsersPaginator(listUsersRequest);
        int userCount = 0;

        // Process each page of results
        for (ListUsersResponse response : listUsersResponses) {
            for (UserType user : response.users()) {
                // Convert each user to a Map
                Map<String, Object> userMap = new HashMap<>();
                userMap.put("username", user.username());
                userMap.put("userStatus", user.userStatusAsString());
                userMap.put("enabled", user.enabled());
                userMap.put("userCreateDate", user.userCreateDate().toString());
                userMap.put("userLastModifiedDate", user.userLastModifiedDate().toString());

                Map<String, String> attributes = new HashMap<>();
                if (user.hasAttributes()) {
                    attributes = user.attributes().stream()
                            .collect(Collectors.toMap(
                                    AttributeType::name,
                                    AttributeType::value,
                                    (v1, v2) -> v1 // In case of duplicate keys
                            ));
                }
                userMap.put("attributes", attributes);

                // Process MFA settings if available
                if (user.hasMfaOptions()) {
                    userMap.put("mfaOptions", user.mfaOptions());
                }

                users.add(userMap);
                userCount++;
            }
        }

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss"));
        String backupId = UUID.randomUUID().toString();

        String usersJson = gson.toJson(users);

        // Store in DynamoDB, potentially splitting into chunks if needed
        storeInDynamoDBWithChunking(backupId, "USERS", timestamp, usersJson, userCount, context, dynamoDbClient, backupTable, userPoolId);

        context.getLogger().log("Backed up " + userCount + " users");
    }

    /**
     * Store in dynamo db.
     *
     * @param backupId       the backup id
     * @param backupType     the backup type
     * @param timestamp      the timestamp
     * @param data           the data
     * @param context        the context
     * @param dynamoDbClient the dynamo db client
     * @param backupTable    the backup table
     * @param userPoolId     the user pool id
     */
    public static void storeInDynamoDB(String backupId,
                                       String backupType,
                                       String timestamp,
                                       String data,
                                       Context context,
                                       DynamoDbClient dynamoDbClient,
                                       String backupTable,
                                       String userPoolId) {
        try {
            Map<String, AttributeValue> item = new HashMap<>();
            item.put("BackupId", AttributeValue.builder().s(backupId).build());
            item.put("ChunkId", AttributeValue.builder().s("0").build());
            item.put("BackupType", AttributeValue.builder().s(backupType).build());
            item.put("Timestamp", AttributeValue.builder().s(timestamp).build());
            item.put("UserPoolId", AttributeValue.builder().s(userPoolId).build());
            item.put("Data", AttributeValue.builder().s(data).build());
            item.put("TotalChunks", AttributeValue.builder().n("1").build());

            PutItemRequest request = PutItemRequest.builder()
                    .tableName(backupTable)
                    .item(item)
                    .build();

            dynamoDbClient.putItem(request);
            context.getLogger().log("Successfully stored " + backupType + " backup in DynamoDB with ID: " + backupId);
        } catch (Exception e) {
            context.getLogger().log("Error storing in DynamoDB: " + e.getMessage());
            throw e;
        }
    }

    /**
     * Store in dynamo db with chunking.
     *
     * @param backupId       the backup id
     * @param backupType     the backup type
     * @param timestamp      the timestamp
     * @param data           the data
     * @param userCount      the user count
     * @param context        the context
     * @param dynamoDbClient the dynamo db client
     * @param backupTable    the backup table
     * @param userPoolId     the user pool id
     */
    public static void storeInDynamoDBWithChunking(String backupId,
                                                   String backupType,
                                                   String timestamp,
                                                   String data,
                                                   int userCount,
                                                   Context context,
                                                   DynamoDbClient dynamoDbClient,
                                                   String backupTable,
                                                   String userPoolId) {
        try {
            // Check if we need to chunk the data
            if (data.length() <= MAX_DYNAMODB_ITEM_SIZE) {
                // Data fits in a single item
                Map<String, AttributeValue> item = new HashMap<>();
                item.put("BackupId", AttributeValue.builder().s(backupId).build());
                item.put("ChunkId", AttributeValue.builder().s("0").build());
                item.put("BackupType", AttributeValue.builder().s(backupType).build());
                item.put("Timestamp", AttributeValue.builder().s(timestamp).build());
                item.put("UserPoolId", AttributeValue.builder().s(userPoolId).build());
                item.put("Data", AttributeValue.builder().s(data).build());
                item.put("TotalChunks", AttributeValue.builder().n("1").build());
                item.put("UserCount", AttributeValue.builder().n(String.valueOf(userCount)).build());

                PutItemRequest request = PutItemRequest.builder()
                        .tableName(backupTable)
                        .item(item)
                        .build();

                dynamoDbClient.putItem(request);
                context.getLogger().log("Successfully stored " + backupType + " backup in DynamoDB with ID: " + backupId);
            } else {
                // Need to split the data into chunks
                int totalChunks = (int) Math.ceil((double) data.length() / MAX_DYNAMODB_ITEM_SIZE);
                context.getLogger().log("Splitting backup into " + totalChunks + " chunks");

                // Store metadata in chunk 0
                Map<String, AttributeValue> metadataItem = new HashMap<>();
                metadataItem.put("BackupId", AttributeValue.builder().s(backupId).build());
                metadataItem.put("ChunkId", AttributeValue.builder().s("0").build());
                metadataItem.put("BackupType", AttributeValue.builder().s(backupType).build());
                metadataItem.put("Timestamp", AttributeValue.builder().s(timestamp).build());
                metadataItem.put("UserPoolId", AttributeValue.builder().s(userPoolId).build());
                metadataItem.put("TotalChunks", AttributeValue.builder().n(String.valueOf(totalChunks)).build());
                metadataItem.put("UserCount", AttributeValue.builder().n(String.valueOf(userCount)).build());
                metadataItem.put("IsMetadata", AttributeValue.builder().bool(true).build());

                PutItemRequest metadataRequest = PutItemRequest.builder()
                        .tableName(backupTable)
                        .item(metadataItem)
                        .build();

                dynamoDbClient.putItem(metadataRequest);

                // Store data chunks
                for (int i = 0; i < totalChunks; i++) {
                    int start = i * MAX_DYNAMODB_ITEM_SIZE;
                    int end = Math.min(start + MAX_DYNAMODB_ITEM_SIZE, data.length());
                    String chunkData = data.substring(start, end);

                    Map<String, AttributeValue> chunkItem = new HashMap<>();
                    chunkItem.put("BackupId", AttributeValue.builder().s(backupId).build());
                    chunkItem.put("ChunkId", AttributeValue.builder().s(String.valueOf(i + 1)).build());
                    chunkItem.put("BackupType", AttributeValue.builder().s(backupType).build());
                    chunkItem.put("Timestamp", AttributeValue.builder().s(timestamp).build());
                    chunkItem.put("UserPoolId", AttributeValue.builder().s(userPoolId).build());
                    chunkItem.put("Data", AttributeValue.builder().s(chunkData).build());
                    chunkItem.put("ChunkIndex", AttributeValue.builder().n(String.valueOf(i)).build());
                    chunkItem.put("TotalChunks", AttributeValue.builder().n(String.valueOf(totalChunks)).build());

                    PutItemRequest chunkRequest = PutItemRequest.builder()
                            .tableName(backupTable)
                            .item(chunkItem)
                            .build();

                    dynamoDbClient.putItem(chunkRequest);
                }

                context.getLogger().log("Successfully stored chunked " + backupType + " backup in DynamoDB with ID: " + backupId);
            }
        } catch (Exception e) {
            context.getLogger().log("Error storing in DynamoDB with chunking: " + e.getMessage());
            throw e;
        }
    }
}
