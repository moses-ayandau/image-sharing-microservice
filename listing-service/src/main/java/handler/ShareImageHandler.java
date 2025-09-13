package handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;

import factories.DynamodbFactory;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;
import software.amazon.awssdk.services.dynamodb.model.ScanResponse;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;

import utils.ResponseUtils;
import constants.constants;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.net.URL;
import java.util.List;

import static service.DynamoDBService.isImageActive;
import static service.S3Service.generatePresignedUrl;

public class ShareImageHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {
    private final ObjectMapper objectMapper;

    public ShareImageHandler() {
        this.objectMapper = new ObjectMapper();
    }
    
    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent input, Context context) {
        try {
            JsonNode requestBody = objectMapper.readTree(input.getBody());
            String imageKey = requestBody.get("imageKey").asText();
            
            if (imageKey == null || imageKey.isEmpty()) {
                return ResponseUtils.errorResponse("Image key is required", 400, input);
            }

            if (!isImageActive(imageKey)) {
                return ResponseUtils.errorResponse("Cannot share inactive or deleted images", 403, input);
            }
            
            String presignedUrl = generatePresignedUrl(imageKey, constants.PRESIGNED_URL_EXPIRATION);
            Map<String, String> responseBody = new HashMap<>();
            responseBody.put("presignedUrl", presignedUrl);
            
            return ResponseUtils.successResponse(responseBody, 200, input);
            
        } catch (Exception e) {
            context.getLogger().log("Error generating presigned URL: " + e.getMessage());
            return ResponseUtils.errorResponse("Failed to generate presigned URL", 500, input);
        }
    }
}
