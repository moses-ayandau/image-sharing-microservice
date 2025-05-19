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
            String imageKey = request.getPathParameters().get("imageKey");
            String userId = request.getQueryStringParameters().get("userId");

            if (imageKey == null || imageKey.trim().isEmpty()) {
                return ResponseUtils.errorResponse(400, "Missing or empty imageKey");
            }

            if (userId == null || userId.trim().isEmpty()) {
                return ResponseUtils.errorResponse(400, "Missing or empty userId");
            }

            context.getLogger().log("Image Key: " + imageKey);
            context.getLogger().log("Image table: "+ tableName);

            Map<String, AttributeValue> item;

            try {
                item = dynamoUtils.getItemFromDynamo(tableName, imageKey);
                context.getLogger().log("Item:  " + item);
            } catch (RuntimeException e) {
                context.getLogger().log("Error:   " + e.getMessage());
                return ResponseUtils.errorResponse(404, "Image not found in database");
            }

            s3Utils.validateOwnership(item, userId);

            if (!item.containsKey(S3_KEY) || item.get(S3_KEY) == null || item.get(S3_KEY).s() == null || item.get(S3_KEY).s().isEmpty()) {
                return ResponseUtils.errorResponse(404, "Corrupt image record: missing or invalid S3Key");
            }
            String oldKey = item.get(S3_KEY).s();
            String newKey = "recycle/" + oldKey;

            s3Utils.copyObject(bucketName, oldKey, newKey);
            s3Utils.deleteObject(bucketName, oldKey);
            dynamoUtils.updateImageStatus(tableName, imageKey, "inactive");
            dynamoUtils.updateS3Key(tableName, imageKey, newKey);

            return ResponseUtils.successResponse(200, Map.of("message", "Image moved to recycle bin: "+ imageKey));
        } catch (Exception e) {
            context.getLogger().log("Error deleting image: " + e.getMessage());
            return ResponseUtils.errorResponse(500, "Internal server error");
        }
    }

}