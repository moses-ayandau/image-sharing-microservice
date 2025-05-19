package handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;

import service.DynamoDBService;
import service.S3Service;
import software.amazon.awssdk.services.dynamodb.model.*;
import utils.ResponseUtils;


import java.util.List;
import java.util.Map;

/**
 * Handler for requests to Lambda function.
 */
public class GetDeletedImagesHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    public APIGatewayProxyResponseEvent handleRequest(final APIGatewayProxyRequestEvent input, final Context context) {
        try {
            String userId = input.getPathParameters().get("userId");
            if (userId == null) {
                return ResponseUtils.errorResponse("User ID is required", 400, input);
            }

            List<Map<String, AttributeValue>> items = DynamoDBService.getUserImages(userId, "inactive");
            
            List<Map<String, Object>> inactiveImages = S3Service.attachPresignedUrls(items);
            
            return ResponseUtils.successResponse(inactiveImages, 200, input);
                    
        } catch (Exception e) {
            context.getLogger().log("Error fetching inactive images: " + e.getMessage());
            return ResponseUtils.errorResponse("Failed to fetch inactive images", 500, input);
        }
    }
}
