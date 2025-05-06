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
    private static final Log log = LogFactory.getLog(RecoverImageHandler.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    private final S3Utils s3Utils = new S3Utils();
    private final DynamoDBUtils dynamoUtils = new DynamoDBUtils();

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent request, Context context) {
        if (request == null || request.getBody() == null || request.getBody().isEmpty()) {
            return ResponseUtils.errorResponse(400, "Invalid request");
        }

        String userId = request.getHeaders().get("userId");
        if (userId == null || userId.isEmpty()) {
            return ResponseUtils.errorResponse(400, "Missing userId header");
        }

        String imageId;
        try {
            JsonNode bodyJson = mapper.readTree(request.getBody());
            imageId = bodyJson.get("imageId").asText();
            if (imageId == null || imageId.isEmpty()) {
                return ResponseUtils.errorResponse(400, "Missing imageId");
            }
        } catch (Exception e) {
            return ResponseUtils.errorResponse(400, "Invalid JSON body: " + e.getMessage());
        }

        String originalKey = "images/" + userId + "/" + imageId;
        String recycleKey = userId + "/" + imageId;

        try {
            // Fetch metadata and validate user ownership
            Map<String, AttributeValue> item = dynamoUtils.getItemFromDynamo(imageId);
            s3Utils.validateOwnership(item, userId);

            // Recover image: move from recycle location to main
            s3Utils.copyObject(recycleKey, originalKey);
            s3Utils.deleteObject(recycleKey);

            // Update DynamoDB status to active
            dynamoUtils.updateImageStatus(imageId); // you may want to parameterize this with "active"

            return ResponseUtils.successResponse(200, Map.of("message", "Image recovered: " + imageId));
        } catch (Exception e) {
            log.error("Failed to recover image: " + e.getMessage(), e);
            return ResponseUtils.errorResponse(500, "Recovery failed: " + e.getMessage());
        }
    }
}
