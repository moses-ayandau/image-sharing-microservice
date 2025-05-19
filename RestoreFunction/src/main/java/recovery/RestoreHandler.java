package recovery;

import Service.RecoveryService;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.*;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.util.*;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

public class RestoreHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private final CognitoIdentityProviderClient cognitoClient;
    private final DynamoDbClient dynamoDbClient;
    private final String backupTable;
    private final String restoreUserPool;
    private final String defaultPassword;
    private final Gson gson;

    public RestoreHandler() {
        // Initialize AWS clients
        this.cognitoClient = CognitoIdentityProviderClient.builder()
                .region(Region.of(System.getenv("AWS_REGION")))
                .build();
        this.dynamoDbClient = DynamoDbClient.builder()
                .region(Region.of(System.getenv("AWS_REGION")))
                .build();

        // Get environment variables
        this.backupTable = System.getenv("BACKUP_TABLE");
        this.restoreUserPool = System.getenv("RESTORE_USER_POOL");
        this.defaultPassword = System.getenv("DEFAULT_PASSWORD");

        // Initialize Gson
        this.gson = new GsonBuilder().setPrettyPrinting().create();
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent event, Context context) {
        context.getLogger().log("Starting Cognito user pool restore to pool: " + restoreUserPool);
        
        APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent();
        
        try {
            // Get the backup ID from the request
            String backupId = null;
            
            // Check if backupId is provided in the path parameters
            if (event.getPathParameters() != null && event.getPathParameters().containsKey("backupId")) {
                backupId = event.getPathParameters().get("backupId");
            } 
            // Check if backupId is provided in the query string parameters
            else if (event.getQueryStringParameters() != null && event.getQueryStringParameters().containsKey("backupId")) {
                backupId = event.getQueryStringParameters().get("backupId");
            }
            // Check if backupId is provided in the request body
            else if (event.getBody() != null && !event.getBody().isEmpty()) {
                Map<String, String> body = gson.fromJson(event.getBody(), new TypeToken<Map<String, String>>(){}.getType());
                backupId = body.get("backupId");
            }
            
            if (backupId == null) {
                // If no specific backup ID is provided, find the latest backup
                backupId = RecoveryService.getLatestBackupId(context, backupTable, restoreUserPool, dynamoDbClient);
                context.getLogger().log("No backup ID provided, using latest: " + backupId);
            }
            
            // Restore users from the backup
            int restoredCount = RecoveryService.restoreUsersFromBackup(backupId, context, backupTable, restoreUserPool, dynamoDbClient, gson);
            
            String successMessage = "Successfully restored " + restoredCount + " users to Cognito user pool: " + restoreUserPool;
            context.getLogger().log(successMessage);
            
            response.setStatusCode(200);
            response.setBody(gson.toJson(Map.of(
                "message", successMessage,
                "backupId", backupId,
                "restoredUsers", restoredCount
            )));
            
        } catch (Exception e) {
            String errorMessage = "Error during Cognito restore: " + e.getMessage();
            context.getLogger().log(errorMessage);
            
            response.setStatusCode(500);
            response.setBody(gson.toJson(Map.of("error", errorMessage)));
        }
        
        return response;
    }
}