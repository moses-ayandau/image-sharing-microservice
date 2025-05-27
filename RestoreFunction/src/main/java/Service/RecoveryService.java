package Service;

import com.amazonaws.services.lambda.runtime.Context;
import com.google.gson.reflect.TypeToken;
import software.amazon.awssdk.services.cognitoidentityprovider.model.*;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import com.google.gson.Gson;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;

public class RecoveryService {

    public static String getLatestBackupId(Context context, String backupTable, String userPoolId, DynamoDbClient dynamoDbClient) {
        context.getLogger().log("Finding latest backup");

        // Query the TypeTimestampIndex to find the latest USERS backup
        QueryRequest queryRequest = QueryRequest.builder()
                .tableName(backupTable)
                .indexName("TypeTimestampIndex")
                .keyConditionExpression("BackupType = :backupType")
                .expressionAttributeValues(Map.of(
                        ":backupType", AttributeValue.builder().s("USERS").build()
                ))
                .scanIndexForward(false) // Sort in descending order (newest first)
                .limit(1) // We only need the most recent backup
                .build();

        QueryResponse queryResponse = dynamoDbClient.query(queryRequest);

        if (queryResponse.items().isEmpty()) {
            throw new RuntimeException("No user backups found in the table");
        }

        return queryResponse.items().get(0).get("BackupId").s();
    }

    public static int restoreUsersFromBackup(String backupId, Context context, String backupTable, String userPoolId, DynamoDbClient dynamoDbClient, Gson gson) {
        context.getLogger().log("Restoring users from backup: " + backupId);

        // First, get the metadata item to check if this is a chunked backup
        GetItemRequest metadataRequest = GetItemRequest.builder()
                .tableName(backupTable)
                .key(Map.of(
                        "BackupId", AttributeValue.builder().s(backupId).build(),
                        "ChunkId", AttributeValue.builder().s("0").build()
                ))
                .build();

        GetItemResponse metadataResponse = dynamoDbClient.getItem(metadataRequest);

        if (!metadataResponse.hasItem()) {
            throw new RuntimeException("Backup metadata not found for ID: " + backupId);
        }

        Map<String, AttributeValue> metadata = metadataResponse.item();
        boolean isChunked = metadata.containsKey("IsMetadata") && metadata.get("IsMetadata").bool();
        int totalChunks = Integer.parseInt(metadata.get("TotalChunks").n());

        context.getLogger().log("Backup has " + totalChunks + " chunks, isChunked: " + isChunked);

        // Collect all user data
        String userData = "";

        if (isChunked) {

            StringBuilder userDataBuilder = new StringBuilder();

            for (int i = 1; i <= totalChunks; i++) {
                GetItemRequest chunkRequest = GetItemRequest.builder()
                        .tableName(backupTable)
                        .key(Map.of(
                                "BackupId", AttributeValue.builder().s(backupId).build(),
                                "ChunkId", AttributeValue.builder().s(String.valueOf(i)).build()
                        ))
                        .build();

                GetItemResponse chunkResponse = dynamoDbClient.getItem(chunkRequest);

                if (!chunkResponse.hasItem()) {
                    throw new RuntimeException("Chunk " + i + " not found for backup ID: " + backupId);
                }

                userDataBuilder.append(chunkResponse.item().get("Data").s());
            }

            userData = userDataBuilder.toString();
        } else {
            // For non-chunked backups, the data is in the metadata item
            userData = metadata.get("Data").s();
        }

        // Parse the user data JSON
        List<Map<String, Object>> users = gson.fromJson(userData, new TypeToken<List<Map<String, Object>>>(){}.getType());

        context.getLogger().log("Found " + users.size() + " users to restore");

        // Restore each user
        int restoredCount = 0;
        for (Map<String, Object> user : users) {
            try {
                restoreUser(user, context, userPoolId, "DefaultPassword123!", CognitoIdentityProviderClient.builder().build());
                restoredCount++;

                if (restoredCount % 10 == 0) {
                    context.getLogger().log("Restored " + restoredCount + " users so far");
                }
            } catch (Exception e) {
                context.getLogger().log("Error restoring user: " + user.get("username") + " - " + e.getMessage());
            }
        }

        return restoredCount;
    }


    @SuppressWarnings("unchecked")
    private static void restoreUser(Map<String, Object> user, Context context, String restoreUserPool, String defaultPassword, CognitoIdentityProviderClient cognitoClient) {
        String username = (String) user.get("username");
        Map<String, String> attributes = (Map<String, String>) user.get("attributes");

        // Get email from attributes - this will be our username for Cognito
        String email = attributes.get("email");
        if (email == null) {
            context.getLogger().log("Skipping user " + username + " - no email attribute found");
            return;
        }

        // Create a list of AttributeType objects for the user
        List<AttributeType> cognitoAttributes = new ArrayList<>();

        for (Map.Entry<String, String> entry : attributes.entrySet()) {

            if (!entry.getKey().equals("sub")) {
                cognitoAttributes.add(AttributeType.builder()
                        .name(entry.getKey())
                        .value(entry.getValue())
                        .build());
            }

        }

        // Create the user in Cognito using email as username
        try {
            AdminCreateUserRequest createRequest = AdminCreateUserRequest.builder()
                    .userPoolId(restoreUserPool)
                    .username(email)
                    .temporaryPassword(defaultPassword)
                    .messageAction(MessageActionType.SUPPRESS)
                    .userAttributes(cognitoAttributes)
                    .build();

            AdminCreateUserResponse createResponse = cognitoClient.adminCreateUser(createRequest);

            // Set the user's password as permanent
            AdminSetUserPasswordRequest passwordRequest = AdminSetUserPasswordRequest.builder()
                    .userPoolId(restoreUserPool)
                    .username(email)
                    .password(defaultPassword)
                    .permanent(true)
                    .build();

            cognitoClient.adminSetUserPassword(passwordRequest);

            context.getLogger().log("Successfully restored user: " + username + " with email username: " + email);
        } catch (UsernameExistsException e) {
            AdminUpdateUserAttributesRequest updateRequest = AdminUpdateUserAttributesRequest.builder()
                    .userPoolId(restoreUserPool)
                    .username(email)
                    .userAttributes(cognitoAttributes)
                    .build();

            cognitoClient.adminUpdateUserAttributes(updateRequest);

            context.getLogger().log("Updated existing user: " + email);
        } catch (Exception e) {
            context.getLogger().log("Error restoring user with email " + email + ": " + e.getMessage());
            throw e;
        }
    }
}
