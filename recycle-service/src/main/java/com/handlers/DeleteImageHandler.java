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
    private static final String S3_KEY = "S3Key";
    private final String tableName;
    private final String bucketName;

   private final S3Utils s3Utils;
   private final DynamoDBUtils dynamoUtils;

    public DeleteImageHandler() {
        this.tableName = System.getenv("IMAGE_TABLE");
        this.bucketName = System.getenv("PRIMARY_BUCKET");
        this.s3Utils = new S3Utils();
        this.dynamoUtils = new DynamoDBUtils();
    }

    // for test injection
    public DeleteImageHandler(String tableName, String bucketName, S3Utils s3Utils, DynamoDBUtils dynamoUtils) {
        this.tableName = tableName;
        this.bucketName = bucketName;
        this.s3Utils = s3Utils;
        this.dynamoUtils = dynamoUtils;
    }

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
                item = dynamoUtils.getItemFromDynamo(tableName, imageId);
            } catch (RuntimeException e) {
                return ResponseUtils.errorResponse(404, "Image not found in database");
            }

            s3Utils.validateOwnership(item, userId);

            if (!item.containsKey(S3_KEY) || item.get(S3_KEY) == null || item.get(S3_KEY).s() == null || item.get(S3_KEY).s().isEmpty()) {
                return ResponseUtils.errorResponse(404, "Corrupt image record: missing or invalid S3Key");
            }
            String oldKey = item.get(S3_KEY).s();
            String newKey = oldKey.replaceFirst("main/", "recycle/");

            s3Utils.copyObject(bucketName, oldKey, newKey);
            s3Utils.deleteObject(bucketName, oldKey);
            dynamoUtils.updateImageStatus(tableName, imageId, "recycle");
            dynamoUtils.updateS3Key(tableName, imageId, newKey);

            return ResponseUtils.successResponse(200, Map.of("message", "Image moved to recycle bin: "+ imageId));
        } catch (Exception e) {
            context.getLogger().log("Error deleting image: " + e.getMessage());
            return ResponseUtils.errorResponse(500, "Internal server error");
        }
    }
}