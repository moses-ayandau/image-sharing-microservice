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
            Map<String, String> queryParams = input.getQueryStringParameters();
            if (queryParams == null || !queryParams.containsKey("userId")) {
                return ResponseUtils.errorResponse("User ID is required", 400);
            }
            
            String userId = queryParams.get("userId");

            List<Map<String, AttributeValue>> items = DynamoDBService.getDeletedImages(userId);
            
            List<Map<String, Object>> inactiveImages = S3Service.attachPresignedUrls(items);
            
            return ResponseUtils.successResponse(inactiveImages, 200);
                    
        } catch (Exception e) {
            context.getLogger().log("Error fetching inactive images: " + e.getMessage());
            return ResponseUtils.errorResponse("Failed to fetch inactive images", 500);
        }
    }
}
