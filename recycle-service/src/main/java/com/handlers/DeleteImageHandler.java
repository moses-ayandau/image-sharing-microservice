package com.handlers;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.utils.DynamoDBUtils;
import com.utils.ResponseUtils;
import com.utils.S3Utils;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.Map;


@Slf4j
public class DeleteImageHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {
    private final String TABLE_NAME = System.getenv("IMAGE_TABLE");
    private final String PRIMARY_BUCKET = System.getenv("PRIMARY_BUCKET");
    private final String RECYCLE_BUCKET = System.getenv("RECYCLE_BUCKET");

    private final S3Utils s3Utils = new S3Utils();
    private final DynamoDBUtils dynamoUtils = new DynamoDBUtils();

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent request, Context context) {
        try {
            if (request.getPathParameters() == null || request.getQueryStringParameters() == null) {
                return ResponseUtils.errorResponse(400, "Missing path or query parameters");
            }
            String imageId = request.getPathParameters().get("imageId");
            String userId = request.getQueryStringParameters().get("userId");

            if (imageId == null || imageId.trim().isEmpty()) {
                return ResponseUtils.errorResponse(400, "Missing or empty imageId");
            }

            if (userId == null || userId.trim().isEmpty()) {
                return ResponseUtils.errorResponse(400, "Missing or empty userId");
            }

            Map<String, AttributeValue> item;

            try {
                item = dynamoUtils.getItemFromDynamo(TABLE_NAME, imageId);
            } catch (RuntimeException e) {
                return ResponseUtils.errorResponse(404, "Image not found in database");
            }
            s3Utils.validateOwnership(item, userId);

            if (!item.containsKey("S3Key") || item.get("S3Key") == null || item.get("S3Key").s() == null || item.get("S3Key").s().isEmpty()) {
                return ResponseUtils.errorResponse(500, "Corrupt image record: missing or invalid S3Key");
            }
            String oldKey = item.get("S3Key").s();
            String newKey = oldKey.replaceFirst("main/", "deleted/");

            s3Utils.copyObject(PRIMARY_BUCKET, RECYCLE_BUCKET, oldKey, newKey);
            s3Utils.deleteObject(PRIMARY_BUCKET, oldKey);
            dynamoUtils.updateImageStatus(TABLE_NAME, imageId, "deleted");
            dynamoUtils.updateS3Key(TABLE_NAME, imageId, newKey);

            return ResponseUtils.successResponse(200, "Image moved to recycle bin");
        } catch (Exception e) {
            context.getLogger().log("Error deleting image: " + e.getMessage());
            return ResponseUtils.errorResponse(500, "Internal server error");
        }
    }


}