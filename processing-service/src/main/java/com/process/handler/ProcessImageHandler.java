package com.process.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.process.util.DynamoDbService;
import com.process.util.EmailService;
import com.process.util.ImageProcessor;
import com.process.util.S3Service;

public class ProcessImageHandler implements RequestHandler<SQSEvent, String> {

    private final S3Service s3Service;
    private final DynamoDbService dynamoDbService;
    private final EmailService emailService;
    private final ImageProcessor imageProcessor;
    private final String stagingBucket;

    public ProcessImageHandler() {
        String region = System.getenv("AWS_REGION");
        this.stagingBucket = System.getenv("STAGING_BUCKET");
        String processedBucket = System.getenv("PROCESSED_BUCKET");
        String imageTable = System.getenv("IMAGE_TABLE");

        this.s3Service = new S3Service(region, processedBucket);
        this.dynamoDbService = new DynamoDbService(region, imageTable);
        this.emailService = new EmailService(region);
        this.imageProcessor = new ImageProcessor();
    }

    @Override
    public String handleRequest(SQSEvent sqsEvent, Context context) {
        for (SQSEvent.SQSMessage message : sqsEvent.getRecords()) {
            context.getLogger().log("Processing retry message: " + message.getBody());

            try {
                // Parse the message
                String[] parts = message.getBody().split(",");
                if (parts.length < 6) {
                    context.getLogger().log("Invalid message format: " + message.getBody());
                    continue;
                }

                String bucket = parts[0];
                String key = parts[1];
                String userId = parts[2];
                String email = parts[3];
                String firstName = parts[4];
                String lastName = parts[5];

                // Check if the original file still exists
                if (!s3Service.objectExists(bucket, key)) {
                    context.getLogger().log("Original file no longer exists: " + bucket + "/" + key);
                    continue;
                }

                // Process the image again
                byte[] imageData = s3Service.getImageFromS3(bucket, key);
                byte[] watermarkedImage = imageProcessor.addWatermark(imageData, firstName, lastName);

                if (watermarkedImage != null) {
                    // Generate a unique ID for the processed image
                    String processedKey = "processed/" + java.util.UUID.randomUUID() + "-" +
                            key.substring(key.lastIndexOf("/") + 1);

                    // Upload the processed image to the processed bucket
                    s3Service.uploadToProcessedBucket(watermarkedImage, processedKey);

                    // Store image metadata in DynamoDB
                    dynamoDbService.storeImageMetadata(userId, processedKey, firstName, lastName);

                    // Delete the original image from the staging bucket
                    s3Service.deleteFromStagingBucket(bucket, key);

                    // Send completion email
                    emailService.sendProcessingCompleteEmail(email, firstName, processedKey);

                    context.getLogger().log("Retry successful for image: " + key);
                } else {
                    throw new RuntimeException("Failed to process image on retry: " + key);
                }
            } catch (Exception e) {
                context.getLogger().log("Retry failed: " + e.getMessage());

                // We could add another retry here, but for simplicity, we'll just log the error
                // In a production environment, you'd want to implement a more sophisticated retry strategy
                // or move the message to a dead-letter queue after several retries
            }
        }

        return "Retry processing complete";
    }
}