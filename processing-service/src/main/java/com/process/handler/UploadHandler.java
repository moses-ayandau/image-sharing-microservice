package com.process.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.process.util.SqsService;
import software.amazon.awssdk.regions.Region;

import java.io.ByteArrayInputStream;
import java.text.SimpleDateFormat;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class UploadHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private final AmazonS3 s3Client = AmazonS3ClientBuilder.standard().build();
    private final String bucketName = System.getenv("STAGING_BUCKET");
    private final String retryQueueName = System.getenv("RETRY_QUEUE");
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SqsService sqsService;

    public UploadHandler() {
        // Initialize SQS service
        Region region = Region.of(System.getenv("AWS_REGION"));
        this.sqsService = new SqsService(region, retryQueueName);
    }

    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent input, Context context) {
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("Access-Control-Allow-Origin", "*");
        headers.put("Access-Control-Allow-Methods", "POST, OPTIONS");
        headers.put("Access-Control-Allow-Headers", "Content-Type");

        try {
            String body = input.getBody();
            Map<String, String> queryParams = input.getQueryStringParameters();

            // Default values
            String firstName = "";
            String lastName = "";
            String email = "";
            String imageTitle = "";
            byte[] imageData = null;

            // Try to get parameters from query string
            if (queryParams != null) {
                firstName = queryParams.getOrDefault("firstname", "");
                lastName = queryParams.getOrDefault("lastname", "");
                email = queryParams.getOrDefault("email", "");
            }

            // Get content type from headers
            String contentTypeHeader = null;
            if (input.getHeaders() != null) {
                contentTypeHeader = input.getHeaders().get("Content-Type");
                context.getLogger().log("Content-Type header: " + contentTypeHeader);
            }

            // Handle image data based on request format
            if (Boolean.TRUE.equals(input.getIsBase64Encoded())) {
                // For base64 encoded requests
                context.getLogger().log("Request is base64 encoded");
                imageData = Base64.getDecoder().decode(body);
                context.getLogger().log("Decoded base64 data, length: " + imageData.length);
            } else if (contentTypeHeader != null && contentTypeHeader.startsWith("application/json")) {
                // For JSON requests
                context.getLogger().log("Processing JSON request");
                try {
                    Map<String, Object> jsonBody = objectMapper.readValue(body, Map.class);

                    // Extract form fields if available in JSON
                    if (jsonBody.containsKey("firstName")) firstName = (String) jsonBody.get("firstName");
                    if (jsonBody.containsKey("lastName")) lastName = (String) jsonBody.get("lastName");
                    if (jsonBody.containsKey("email")) email = (String) jsonBody.get("email");
                    if (jsonBody.containsKey("imageTitle")) imageTitle = (String) jsonBody.get("imageTitle");

                    // Get image from JSON as base64
                    if (jsonBody.containsKey("image")) {
                        String base64Image = (String) jsonBody.get("image");
                        context.getLogger().log("Found image in JSON");

                        // Remove data URL prefix if present
                        if (base64Image.contains(",")) {
                            context.getLogger().log("Removing data URL prefix");
                            base64Image = base64Image.split(",")[1];
                        }

                        imageData = Base64.getDecoder().decode(base64Image);
                        context.getLogger().log("Decoded image from JSON, length: " + imageData.length);
                    }
                } catch (Exception e) {
                    context.getLogger().log("Error parsing JSON: " + e.getMessage());
                    return new APIGatewayProxyResponseEvent()
                            .withStatusCode(400)
                            .withHeaders(headers)
                            .withBody("{ \"error\": \"Invalid JSON format: " + e.getMessage() + "\" }");
                }
            } else if (contentTypeHeader != null && contentTypeHeader.startsWith("multipart/form-data")) {
                // For multipart form data - this is complex and requires a library
                context.getLogger().log("Multipart form data detected - this requires special handling");
                return new APIGatewayProxyResponseEvent()
                        .withStatusCode(400)
                        .withHeaders(headers)
                        .withBody("{ \"error\": \"Multipart form data is not supported directly. Please use base64 encoding.\" }");
            } else {
                // For binary data or unknown format
                context.getLogger().log("Treating body as raw binary data");
                imageData = body.getBytes();
            }

            // Validate image data
            if (imageData == null || imageData.length == 0) {
                context.getLogger().log("No image data found");
                return new APIGatewayProxyResponseEvent()
                        .withStatusCode(400)
                        .withHeaders(headers)
                        .withBody("{ \"error\": \"No image data provided\" }");
            }

            // Detect content type
            String detectedContentType = detectContentType(imageData);
            context.getLogger().log("Detected content type: " + detectedContentType);

            // Get file extension
            String fileExtension = getFileExtension(detectedContentType);
            context.getLogger().log("Using file extension: " + fileExtension);

            // Generate unique filename
            String fileName = UUID.randomUUID().toString() + fileExtension;

            // Get current date
            String dateOfUpload = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'").format(new Date());

            // Set metadata
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(imageData.length);
            metadata.setContentType(detectedContentType);

            // Add custom metadata
            metadata.addUserMetadata("firstname", firstName);
            metadata.addUserMetadata("lastname", lastName);
            metadata.addUserMetadata("email", email);
            metadata.addUserMetadata("dateofupload", dateOfUpload);

            // Upload to S3
            context.getLogger().log("Uploading to S3: bucket=" + bucketName + ", key=" + fileName +
                    ", content type=" + detectedContentType + ", size=" + imageData.length);

            s3Client.putObject(
                    bucketName,
                    fileName,
                    new ByteArrayInputStream(imageData),
                    metadata
            );

            // Generate unique userId for this upload if not provided
            String userId = UUID.randomUUID().toString();

            // Queue for processing via SQS
            context.getLogger().log("Queueing image for processing");
            sqsService.queueForRetry(bucketName, fileName, userId, email, firstName, lastName, imageTitle);

            // Return success response
            String fileUrl = s3Client.getUrl(bucketName, fileName).toString();
            Map<String, Object> responseBody = new HashMap<>();
            responseBody.put("message", "Upload successful and queued for processing");
            responseBody.put("fileUrl", fileUrl);
            responseBody.put("metadata", Map.of(
                    "firstName", firstName,
                    "lastName", lastName,
                    "email", email,
                    "dateOfUpload", dateOfUpload
            ));

            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(200)
                    .withHeaders(headers)
                    .withBody(objectMapper.writeValueAsString(responseBody));

        } catch (Exception e) {
            context.getLogger().log("Error: " + e.getMessage());
            e.printStackTrace();
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(500)
                    .withHeaders(headers)
                    .withBody(String.format("{ \"error\": \"%s\" }", e.getMessage().replace("\"", "\\\"")));
        }
    }

    /**
     * Detects the content type of an image based on its binary signature
     * @param imageData The image byte array
     * @return Content type string (e.g., "image/jpeg", "image/png")
     */
    private String detectContentType(byte[] imageData) {
        if (imageData == null || imageData.length < 8) {
            return "application/octet-stream";
        }

        // Print the first few bytes for debugging
        StringBuilder hexBytes = new StringBuilder();
        for (int i = 0; i < Math.min(16, imageData.length); i++) {
            hexBytes.append(String.format("%02X ", imageData[i] & 0xFF));
        }
        System.out.println("First bytes: " + hexBytes.toString());

        try {
            // Check for JPEG signature (starts with FF D8)
            if (imageData[0] == (byte) 0xFF && imageData[1] == (byte) 0xD8) {
                return "image/jpeg";
            }

            // Check for PNG signature (starts with 89 50 4E 47 0D 0A 1A 0A)
            if (imageData[0] == (byte) 0x89 && imageData[1] == (byte) 0x50 &&
                    imageData[2] == (byte) 0x4E && imageData[3] == (byte) 0x47 &&
                    imageData[4] == (byte) 0x0D && imageData[5] == (byte) 0x0A &&
                    imageData[6] == (byte) 0x1A && imageData[7] == (byte) 0x0A) {
                return "image/png";
            }

            // Check for GIF signature (starts with GIF87a or GIF89a)
            if (imageData.length >= 6 &&
                    imageData[0] == (byte) 'G' && imageData[1] == (byte) 'I' && imageData[2] == (byte) 'F' &&
                    imageData[3] == (byte) '8' && (imageData[4] == (byte) '7' || imageData[4] == (byte) '9') &&
                    imageData[5] == (byte) 'a') {
                return "image/gif";
            }

            // Check for BMP signature (starts with BM)
            if (imageData.length >= 2 &&
                    imageData[0] == (byte) 'B' && imageData[1] == (byte) 'M') {
                return "image/bmp";
            }

            // Check for WEBP signature
            if (imageData.length >= 12 &&
                    imageData[0] == (byte) 'R' && imageData[1] == (byte) 'I' &&
                    imageData[2] == (byte) 'F' && imageData[3] == (byte) 'F' &&
                    imageData[8] == (byte) 'W' && imageData[9] == (byte) 'E' &&
                    imageData[10] == (byte) 'B' && imageData[11] == (byte) 'P') {
                return "image/webp";
            }

            // Try to parse it as text and see if it contains an image/xxx content-type string
            // This helps with direct API calls where content-type info is passed but lost
            if (imageData.length > 100) {
                String start = new String(imageData, 0, Math.min(100, imageData.length));
                if (start.contains("image/jpeg") || start.contains("image/jpg")) {
                    return "image/jpeg";
                } else if (start.contains("image/png")) {
                    return "image/png";
                } else if (start.contains("image/gif")) {
                    return "image/gif";
                } else if (start.contains("image/bmp")) {
                    return "image/bmp";
                } else if (start.contains("image/webp")) {
                    return "image/webp";
                }
            }

        } catch (Exception e) {
            System.out.println("Error in content type detection: " + e.getMessage());
        }

        // Default to octet-stream if unknown
        return "application/octet-stream";
    }

    /**
     * Gets the appropriate file extension for a content type
     * @param contentType The content type string
     * @return The file extension with leading dot
     */
    private String getFileExtension(String contentType) {
        switch (contentType) {
            case "image/jpeg":
                return ".jpg";
            case "image/png":
                return ".png";
            case "image/gif":
                return ".gif";
            case "image/bmp":
                return ".bmp";
            case "image/webp":
                return ".webp";
            default:
                return ".bin";
        }
    }
}