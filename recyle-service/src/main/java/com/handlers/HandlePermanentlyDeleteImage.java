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

public class PermanentlyDeleteImage implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private final DynamoDBUtils dynamoDBUtils;
    private final S3Utils s3Utils;

    public PermanentlyDeleteImage(DynamoDBUtils dynamoDBUtils, S3Utils s3Utils) {
        this.dynamoDBUtils = dynamoDBUtils;
        this.s3Utils = s3Utils;
    }


    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent input, Context context) {
        try {
            String imageId = input.getPathParameters().get("imageId");
            String ownerId = input.getQueryStringParameters().get("ownerId");

            Map<String, AttributeValue> item = dynamoDBUtils.getItemFromDynamo(imageId);
            s3Utils.validateOwnership(item, ownerId);

            String key = item.get("S3Key").s();
            s3Utils.deleteObject(key);
            dynamoDBUtils.deleteRecordFromDynamo(imageId);

            return ResponseUtils.successResponse(200, "Image permanently deleted");

        } catch (Exception e) {
            context.getLogger().log("Error permanently deleting image: " + e.getMessage());
            return ResponseUtils.errorResponse(500, "Internal server error");
        }
    }

}