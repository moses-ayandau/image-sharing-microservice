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

public class HandleDeleteImage implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private final DynamoDBUtils dynamoDBUtils;
    private final S3Utils s3Utils;

    public HandleDeleteImage(DynamoDBUtils dynamoDBUtils, S3Utils s3Utils) {
        this.s3Utils = s3Utils;
        this.dynamoDBUtils = dynamoDBUtils;
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent request, Context context) {
        try {
            String imageId = request.getPathParameters().get("imageId");
            String ownerId = request.getQueryStringParameters().get("ownerId");

            Map<String, AttributeValue> item = dynamoDBUtils.getItemFromDynamo(imageId);
            s3Utils.validateOwnership(item, ownerId);

            String oldKey = item.get("S3Key").s();
            String newKey = oldKey.replaceFirst("main/", "deleted/");

            s3Utils.copyObject(oldKey, newKey);
            s3Utils.deleteObject(oldKey);
            dynamoDBUtils.updateImageStatus(imageId);

            return ResponseUtils.successResponse(200, "Image moved to recycle bin");
        } catch (Exception e) {
            context.getLogger().log("Error deleting image: " + e.getMessage());
            return ResponseUtils.errorResponse(500, "Internal server error");
        }
    }


}