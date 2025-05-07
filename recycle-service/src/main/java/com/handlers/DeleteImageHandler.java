package com.handlers;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.utils.DynamoDBUtils;
import com.utils.ResponseUtils;
import com.utils.S3Utils;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.Map;


public class DeleteImageHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {


    private final S3Utils s3Utils = new S3Utils();
    private final DynamoDBUtils dynamoUtils = new DynamoDBUtils();

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent request, Context context) {
        try {
            if (request.getPathParameters() == null || request.getQueryStringParameters() == null) {
                return ResponseUtils.errorResponse(400, "Missing path or query parameters");
            }

            String imageId = request.getPathParameters().get("imageId");
            String ownerId = request.getQueryStringParameters().get("ownerId");

            if (imageId == null || imageId.trim().isEmpty()) {
                return ResponseUtils.errorResponse(400, "Missing or empty imageId");
            }

            if (ownerId == null || ownerId.trim().isEmpty()) {
                return ResponseUtils.errorResponse(400, "Missing or empty ownerId");
            }

            Map<String, AttributeValue> item;

            try {
                item = dynamoUtils.getItemFromDynamo(imageId);
            } catch (RuntimeException e) {
                return ResponseUtils.errorResponse(404, "Image not found in database");
            }
            s3Utils.validateOwnership(item, ownerId);

            if (!item.containsKey("S3Key") || item.get("S3Key") == null || item.get("S3Key").s() == null || item.get("S3Key").s().isEmpty()) {
                return ResponseUtils.errorResponse(500, "Corrupt image record: missing or invalid S3Key");
            }
            String oldKey = item.get("S3Key").s();
            String newKey = oldKey.replaceFirst("main/", "deleted/");

            s3Utils.copyObject(oldKey, newKey);
            s3Utils.deleteObject(oldKey);
            dynamoUtils.updateImageStatus(imageId);

            return ResponseUtils.successResponse(200, "Image moved to recycle bin");
        } catch (Exception e) {
            context.getLogger().log("Error deleting image: " + e.getMessage());
            return ResponseUtils.errorResponse(500, "Internal server error");
        }
    }


}