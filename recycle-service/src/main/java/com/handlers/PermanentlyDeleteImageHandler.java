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
    public static final String S_3_KEY = "S3Key";
    private final String tableName = System.getenv("IMAGE_TABLE");
    private final String bucketName = System.getenv("PRIMARY_BUCKET");

    private  S3Utils s3Utils = new S3Utils();
    private  DynamoDBUtils dynamoUtils = new DynamoDBUtils();

    public PermanentlyDeleteImageHandler() {
        this(new S3Utils(), new DynamoDBUtils());
    }

    public PermanentlyDeleteImageHandler(S3Utils s3Utils, DynamoDBUtils dynamoUtils) {
        this.s3Utils = s3Utils;
        this.dynamoUtils = dynamoUtils;
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent input, Context context) {
        try {
            if (input.getPathParameters() == null || input.getQueryStringParameters() == null) {
                return ResponseUtils.errorResponse(400, "Missing required path or query parameters");
            }

            String imageId = input.getPathParameters().get("imageId");
            String userId = input.getQueryStringParameters().get("userId");

            // Validate actual parameter values
            if (imageId == null || imageId.trim().isEmpty()) {
                return ResponseUtils.errorResponse(400, "Missing or empty 'imageId'");
            }

            if (userId == null || userId.trim().isEmpty()) {
                return ResponseUtils.errorResponse(400, "Missing or empty 'userId'");
            }

            Map<String, AttributeValue> item = dynamoUtils.getItemFromDynamo(tableName, imageId);
            if (!item.containsKey(S_3_KEY) || item.get(S_3_KEY) == null || item.get(S_3_KEY).s() == null || item.get(S_3_KEY).s().isEmpty()) {
                return ResponseUtils.errorResponse(400, "Missing or invalid S3Key");
            }

            s3Utils.validateOwnership(item, userId);
            String key = item.get(S_3_KEY).s();
            if(!key.startsWith("recycle/")){
                return ResponseUtils.errorResponse(403, "Image must be in recycle bin to be permanently deleted");
            }

            s3Utils.deleteObject(bucketName, key);
            dynamoUtils.deleteRecordFromDynamo(tableName, imageId);

            return ResponseUtils.successResponse(200, Map.of("message","Image permanently deleted"));

        } catch (Exception e) {
            context.getLogger().log("Error permanently deleting image: " + e.getMessage());
            return ResponseUtils.errorResponse(500, "Internal server error");
        }
    }

}