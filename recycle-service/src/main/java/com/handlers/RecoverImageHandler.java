package com.handlers;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.utils.DynamoDBUtils;
import com.utils.ResponseUtils;
import com.utils.S3Utils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.Map;

public class RecoverImageHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {
    private String tableName = System.getenv("IMAGE_TABLE");
    private String bucketName = System.getenv("PRIMARY_BUCKET");

    private static final Log log = LogFactory.getLog(RecoverImageHandler.class);

    private final S3Utils s3Utils;
    private final  DynamoDBUtils dynamoDBUtils;
    private final  ObjectMapper objectMapper;

    public RecoverImageHandler(){
        this.s3Utils = new S3Utils();
        this.dynamoDBUtils = new DynamoDBUtils();
        this.objectMapper = new ObjectMapper();

    }

    //for testing
    public RecoverImageHandler(String tableName, String bucketName, S3Utils s3Utils, DynamoDBUtils dynamoDBUtils, ObjectMapper objectMapper){
        this.tableName = tableName;
        this.bucketName = bucketName;
        this.s3Utils = s3Utils;
        this.dynamoDBUtils = dynamoDBUtils;
        this.objectMapper = objectMapper;
    }



    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent request, Context context) {
        String imageId;
        String userId;
        if (request == null || request.getBody() == null || request.getBody().isEmpty()) {
            return ResponseUtils.errorResponse(400, "Invalid request");
        }
        userId = request.getHeaders().get("userId");
        if (userId == null || userId.isEmpty()) {
            return ResponseUtils.errorResponse(400, "Missing userId header");
        }

        try {
            JsonNode bodyJson = objectMapper.readTree(request.getBody());
            JsonNode imageIdNode = bodyJson.get("imageId");
            if (imageIdNode == null || imageIdNode.isEmpty()) {
                return ResponseUtils.errorResponse(400, "Missing imageId");
            }

            imageId  = imageIdNode.asText();

            String originalKey = "main/" + userId + "/" + imageId;
            String recycleKey = "recycle/" + userId + "/" + imageId;


            Map<String, AttributeValue> item = dynamoDBUtils.getItemFromDynamo(tableName, imageId);
            s3Utils.validateOwnership(item, userId);

            s3Utils.copyObject(bucketName, recycleKey, originalKey);
            s3Utils.deleteObject(bucketName, recycleKey);

            dynamoDBUtils.updateImageStatus(tableName, imageId, "active");
            dynamoDBUtils.updateS3Key(tableName, imageId, originalKey);
            return ResponseUtils.successResponse(200, Map.of("message", "Image recovered: " + imageId));
        } catch (Exception e) {
            log.error("Failed to recover image: " + e.getMessage(), e);
            return ResponseUtils.errorResponse(500, "Recovery failed: " + e.getMessage());
        }
    }
}
