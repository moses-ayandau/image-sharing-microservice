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
public class PermanentlyDeleteImageHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {
    private final String TABLE_NAME = System.getenv("IMAGE_TABLE");
    private final String RECYCLE_BUCKET = System.getenv("RECYCLE_BUCKET");
    private final S3Utils s3Utils = new S3Utils();
    private final DynamoDBUtils dynamoUtils = new DynamoDBUtils();

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent input, Context context) {
        try {
            if (input.getPathParameters() == null || input.getQueryStringParameters() == null) {
                return ResponseUtils.errorResponse(400, "Missing required path or query parameters");
            }

            String imageId = input.getPathParameters().get("imageId");
            String ownerId = input.getQueryStringParameters().get("ownerId");

            log.info("Owner and ImageId {}, {} ", ownerId, imageId);
            // Validate actual parameter values
            if (imageId == null || imageId.trim().isEmpty()) {
                return ResponseUtils.errorResponse(400, "Missing or empty 'imageId'");
            }

            if (ownerId == null || ownerId.trim().isEmpty()) {
                return ResponseUtils.errorResponse(400, "Missing or empty 'ownerId'");
            }

            Map<String, AttributeValue> item = dynamoUtils.getItemFromDynamo(TABLE_NAME, imageId);
            s3Utils.validateOwnership(item, ownerId);

            log.info(item.toString());
            String key = item.get("S3Key").s();
            s3Utils.deleteObject(RECYCLE_BUCKET, key);
            dynamoUtils.deleteRecordFromDynamo(TABLE_NAME, imageId);

            return ResponseUtils.successResponse(200, "Image permanently deleted");

        } catch (Exception e) {
            context.getLogger().log("Error permanently deleting image: " + e.getMessage());
            return ResponseUtils.errorResponse(500, "Internal server error");
        }
    }

}