package com.process.util;

import com.amazonaws.services.lambda.runtime.Context;

public class ProcessImage {
    private final DynamoDbService dynamoDbService;
    private final EmailService emailService;
    private final ImageProcessor imageProcessor;

    public ProcessImage(S3Service s3Service, DynamoDbService dynamoDbService, EmailService emailService, ImageProcessor imageProcessor) {
        this.dynamoDbService = dynamoDbService;
        this.emailService = emailService;
        this.imageProcessor = imageProcessor;
    }

    //    private final String stagingBucket;
    public void processImage(Context context, String bucket, String key, String userId,
                             String email, String firstName, String lastName, S3Service s3Service) {
        try {
            // Get image data with better error reporting
            context.getLogger().log("Retrieving image from S3: " + bucket + "/" + key);
            byte[] imageData = s3Service.getImageFromS3(bucket, key);

            if (imageData == null || imageData.length == 0) {
                context.getLogger().log("Retrieved empty image data from S3");
                return;
            }

            context.getLogger().log("Retrieved image data size: " + imageData.length + " bytes");

            // Add watermark to the image
            context.getLogger().log("Adding watermark to image");
            emailService.sendProcessingStartEmail(email, firstName);
            byte[] watermarkedImage = imageProcessor.addWatermark(imageData, firstName, lastName);

            if (watermarkedImage == null || watermarkedImage.length == 0) {
                context.getLogger().log("Watermarking process returned empty data");
                return;
            }

            context.getLogger().log("Watermarked image size: " + watermarkedImage.length + " bytes");

            // Generate a unique ID for the processed image
            String processedKey = "processed/" + java.util.UUID.randomUUID() + "-" +
                    key.substring(key.lastIndexOf("/") + 1);

            // Upload the processed image to the processed bucket
            context.getLogger().log("Uploading processed image to S3");
            s3Service.uploadToProcessedBucket(watermarkedImage, processedKey);

            // Store image metadata in DynamoDB
            context.getLogger().log("Storing image metadata in DynamoDB");
            dynamoDbService.storeImageMetadata(userId, processedKey, firstName, lastName);

            // Delete the original image from the staging bucket
            context.getLogger().log("Deleting original image from staging bucket");
            s3Service.deleteFromStagingBucket(bucket, key);

            // Send completion email
            context.getLogger().log("Sending completion email to: " + email);
            if (email != null && !email.trim().isEmpty()) {
                try {
                    emailService.sendProcessingCompleteEmail(email, firstName, processedKey);
                } catch (Exception e) {
                    context.getLogger().log("Failed to send email: " + e.getMessage());
                    // Continue processing even if email fails
                }
            }

            context.getLogger().log("Successfully processed image: " + key);
        } catch (Exception e) {
            context.getLogger().log("Error processing image: " + e.getClass().getName() + ": " + e.getMessage());
            if (e.getCause() != null) {
                context.getLogger().log("Caused by: " + e.getCause().getMessage());
            }

            // For debugging only - in production, don't print full stack traces to logs
            for (StackTraceElement element : e.getStackTrace()) {
                context.getLogger().log("  at " + element.toString());
            }
            emailService.sendProcessingFailureEmail(email, firstName );
            throw new RuntimeException("Failed to process image", e); // Re-throw to trigger retry or DLQ handling
        }
    }
}
