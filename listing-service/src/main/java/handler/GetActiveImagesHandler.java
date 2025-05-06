package handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;

import constants.constants;
import factories.DynamodbFactory;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import utils.DynamoDbUtils;
import utils.ResponseUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Handler for requests to Lambda function.
 */
public class GetActiveImagesHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {
    private final DynamoDbClient dynamoDbClient;
    private final String tableName;

    public GetActiveImagesHandler() {
        this.dynamoDbClient = DynamodbFactory.createClient();
        this.tableName = constants.IMAGE_TABLE;
    }

    public APIGatewayProxyResponseEvent handleRequest(final APIGatewayProxyRequestEvent input, final Context context) {
        try {
            // Create a scan request to filter for active images
            Map<String, AttributeValue> expressionAttributeValues = new HashMap<>();
            expressionAttributeValues.put(":statusValue", AttributeValue.builder().s("active").build());
            
            // Use expression attribute names to handle reserved keywords
            Map<String, String> expressionAttributeNames = new HashMap<>();
            expressionAttributeNames.put("#status", "status");
            
            ScanRequest scanRequest = ScanRequest.builder()
                    .tableName(tableName)
                    .filterExpression("#status = :statusValue")
                    .expressionAttributeValues(expressionAttributeValues)
                    .expressionAttributeNames(expressionAttributeNames)
                    .build();
            
            ScanResponse scanResponse = dynamoDbClient.scan(scanRequest);
            List<Map<String, AttributeValue>> items = scanResponse.items();
            
            // Convert DynamoDB items to a more friendly format
            List<Map<String, Object>> activeImages = items.stream()
                    .map(DynamoDbUtils::convertDynamoItemToMap)
                    .collect(Collectors.toList());
            
            return ResponseUtils.successResponse(activeImages, 200);
                    
        } catch (Exception e) {
            context.getLogger().log("Error fetching active images: " + e.getMessage());
            return ResponseUtils.errorResponse("Failed to fetch active images", 500);
        }
    }
}
