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

public class ShareImageHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {
    private final S3Presigner presigner;
    private final DynamoDbClient dynamoDbClient;
    private final ObjectMapper objectMapper;
    private final String bucketName;
    private final String tableName;

    public ShareImageHandler() {
        this.presigner = S3Presigner.builder()
                .region(Region.EU_CENTRAL_1)
                .build();
        this.dynamoDbClient = DynamodbFactory.createClient();
        this.objectMapper = new ObjectMapper();
        this.bucketName = constants.PROCESSED_IMAGES_BUCKET;
        this.tableName = constants.IMAGE_TABLE;
    }
    
    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent input, Context context) {
        try {
            // Parse the request body to get the image URL
            JsonNode requestBody = objectMapper.readTree(input.getBody());
            String imageUrl = requestBody.get("imageUrl").asText();
            
            if (imageUrl == null || imageUrl.isEmpty()) {
                return ResponseUtils.errorResponse("Image URL is required", 400);
            }
            
            // Extract object key from the URL
            String objectKey = extractObjectKeyFromUrl(imageUrl);
            if (objectKey == null) {
                return ResponseUtils.errorResponse("Invalid image URL format", 400);
            }
            
            // Find the image by URL using a scan operation with a filter
            Map<String, AttributeValue> expressionAttributeValues = new HashMap<>();
            expressionAttributeValues.put(":urlValue", AttributeValue.builder().s(imageUrl).build());
            
            Map<String, String> expressionAttributeNames = new HashMap<>();
            expressionAttributeNames.put("#imageUrl", "imageUrl");
            
            ScanRequest scanRequest = ScanRequest.builder()
                    .tableName(tableName)
                    .filterExpression("#imageUrl = :urlValue")
                    .expressionAttributeValues(expressionAttributeValues)
                    .expressionAttributeNames(expressionAttributeNames)
                    .build();
            
            ScanResponse scanResponse = dynamoDbClient.scan(scanRequest);
            List<Map<String, AttributeValue>> items = scanResponse.items();
            
            // If no image found with this URL
            if (items.isEmpty()) {
                return ResponseUtils.errorResponse("Image not found", 404);
            }
            
            // Get the first matching image
            Map<String, AttributeValue> imageItem = items.getFirst();
            
            // Check if image is active
            String status = imageItem.get("status").s();
            if (!"active".equals(status)) {
                return ResponseUtils.errorResponse("Cannot share inactive or deleted images", 403);
            }
            
            // Generate a presigned URL for the S3 object
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(objectKey)
                    .build();
            
            // Set the presigned URL to expire after 1 hour
            GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                    .signatureDuration(Duration.ofHours(constants.PRESIGNED_URL_EXPIRATION))
                    .getObjectRequest(getObjectRequest)
                    .build();
            
            PresignedGetObjectRequest presignedRequest = presigner.presignGetObject(presignRequest);
            String presignedUrl = presignedRequest.url().toString();
            
            // Create the response with the presigned URL
            Map<String, String> responseBody = new HashMap<>();
            responseBody.put("presignedUrl", presignedUrl);
            
            return ResponseUtils.successResponse(responseBody, 200);
            
        } catch (Exception e) {
            context.getLogger().log("Error generating presigned URL: " + e.getMessage());
            return ResponseUtils.errorResponse("Failed to generate presigned URL", 500);
        }
    }

    /**
     * Extracts the object key from an S3 URL
     * Example URL: https://dev-group2-processed-bucket.s3.eu-central-1.amazonaws.com/tag-nine.jpg
     */
    private String extractObjectKeyFromUrl(String imageUrl) {
        try {
            URL url = new URL(imageUrl);
            String path = url.getPath();
            // Remove leading slash if present
            return path.startsWith("/") ? path.substring(1) : path;
        } catch (Exception e) {
            return null;
        }
    }
}
