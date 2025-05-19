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


    private final ObjectMapper mapper;
    private final S3Utils s3Utils;
    private final DynamoDBUtils dynamoUtils;

    public RecoverImageHandler(){
        this.s3Utils = new S3Utils();
        this.dynamoUtils = new DynamoDBUtils();
        this.mapper = new ObjectMapper();
    }

    public RecoverImageHandler(String tableName, String bucketName, S3Utils s3Utils, DynamoDBUtils dynamoUtils, ObjectMapper mapper) {
        this.s3Utils = s3Utils;
        this.dynamoUtils = dynamoUtils;
        this.mapper = mapper;
        this.tableName = tableName;
        this.bucketName = bucketName;
    }


    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent request, Context context) {
        String imageKey;
        String userId;
        if (request == null || request.getBody() == null || request.getBody().isEmpty()) {
            return ResponseUtils.errorResponse(400, "Invalid request");
        }
        userId = request.getHeaders().get("userId");
        if (userId == null || userId.isEmpty()) {
            return ResponseUtils.errorResponse(400, "Missing userId header");
        }

        try {
            JsonNode bodyJson = mapper.readTree(request.getBody());
            imageKey = bodyJson.get("imageKey").asText();
            if (imageKey == null || imageKey.isEmpty()) {
                return ResponseUtils.errorResponse(400, "Missing imageKey");
            }

            String originalKey = imageKey;
            String recycleKey = "recycle/" + imageKey;

            log.info("Original Key: " + originalKey);
            log.info("recycle key: " + recycleKey);

            Map<String, AttributeValue> item = dynamoUtils.getItemFromDynamo(tableName, imageKey);
            s3Utils.validateOwnership(item, userId);

            s3Utils.copyObject(bucketName, recycleKey, originalKey);
            s3Utils.deleteObject(bucketName, recycleKey);

            dynamoUtils.updateImageStatus(tableName, imageKey, "active");
            dynamoUtils.updateS3Key(tableName, imageKey, originalKey);
            return ResponseUtils.successResponse(200, Map.of("message", "Image recovered: " + imageKey));
        } catch (Exception e) {
            log.error("Failed to recover image: " + e.getMessage(), e);
            return ResponseUtils.errorResponse(500, "Recovery failed: " + e.getMessage());
        }
    }
}
