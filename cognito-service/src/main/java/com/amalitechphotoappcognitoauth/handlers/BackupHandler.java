package com.amalitechphotoappcognitoauth.handlers;

import com.amalitechphotoappcognitoauth.services.BackupService;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.ScheduledEvent;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import java.io.IOException;
import java.time.Instant;

public class BackupHandler implements RequestHandler<ScheduledEvent, String> {

    private final CognitoIdentityProviderClient cognitoClient;
    private final DynamoDbClient dynamoDbClient;
    private final String userPoolId;
    private final String backupTable;
    private final Gson gson;

    public BackupHandler() {
        // Get environment variables
        this.userPoolId = System.getenv("USER_POOL_ID");
        this.backupTable = System.getenv("BACKUP_TABLE");
        
        // Get region from environment or use the same region as the Lambda function
        String region = System.getenv("AWS_REGION") != null ? System.getenv("AWS_REGION") : "us-east-1";
        
        // Initialize AWS clients with the correct region
        this.cognitoClient = CognitoIdentityProviderClient.builder()
                .region(Region.of(region))
                .build();
        this.dynamoDbClient = DynamoDbClient.builder()
                .region(Region.of(region))
                .build();

        // Initialize Gson with custom type adapter for Instant
        this.gson = new GsonBuilder()
                .setPrettyPrinting()
                .registerTypeAdapter(Instant.class, new InstantTypeAdapter())
                .create();
    }

    // Custom TypeAdapter for java.time.Instant
    private static class InstantTypeAdapter extends TypeAdapter<Instant> {
        @Override
        public void write(JsonWriter out, Instant instant) throws IOException {
            if (instant == null) {
                out.nullValue();
            } else {
                out.value(instant.toString());
            }
        }

        @Override
        public Instant read(JsonReader in) throws IOException {
            return Instant.parse(in.nextString());
        }
    }

    @Override
    public String handleRequest(ScheduledEvent event, Context context) {
        context.getLogger().log("Starting Cognito user pool backup for pool: " + userPoolId);

        try {
            // Backup user pool configuration
            BackupService.backupUserPoolConfiguration(cognitoClient, dynamoDbClient, userPoolId, backupTable, gson, context);

            // Backup user data
            BackupService.backupUserData(cognitoClient, dynamoDbClient, userPoolId, backupTable, gson, context);

            String successMessage = "Cognito user pool backup completed successfully";
            context.getLogger().log(successMessage);
            return successMessage;
        } catch (Exception e) {
            String errorMessage = "Error during Cognito backup: " + e.getMessage();
            context.getLogger().log(errorMessage);
            throw new RuntimeException(errorMessage, e);
        }
    }
}
