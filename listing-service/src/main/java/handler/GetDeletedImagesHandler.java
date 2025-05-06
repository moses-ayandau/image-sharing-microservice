package handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;

import factories.DynamodbFactory;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;
import constants.constants;
import utils.DynamoDbUtils;
import utils.ResponseUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Handler for requests to Lambda function.
 */
public class GetDeletedImagesHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {
    private final DynamoDbClient dynamoDbClient;
    private final String tableName;

    public GetDeletedImagesHandler() {
        this.dynamoDbClient = DynamodbFactory.createClient();
        this.tableName = constants.IMAGE_TABLE;
    }

    public APIGatewayProxyResponseEvent handleRequest(final APIGatewayProxyRequestEvent input, final Context context) {
        try {
            // Get the user ID from the request
            Map<String, String> queryParams = input.getQueryStringParameters();
            if (queryParams == null || !queryParams.containsKey("userId")) {
                return ResponseUtils.errorResponse("User ID is required", 400);
            }
            
            String userId = queryParams.get("userId");
            
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
            List<Map<String, AttributeValue>> items = scanResponse.items();
            
            // Convert DynamoDB items to a more friendly format
            List<Map<String, Object>> inactiveImages = items.stream()
                    .map(DynamoDbUtils::convertDynamoItemToMap)
                    .collect(Collectors.toList());
            
            return ResponseUtils.successResponse(inactiveImages, 200);
                    
        } catch (Exception e) {
            context.getLogger().log("Error fetching inactive images: " + e.getMessage());
            return ResponseUtils.errorResponse("Failed to fetch inactive images", 500);
        }
    }
}
