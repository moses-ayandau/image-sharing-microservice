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

    public void processImage(Context context, String bucket, String key, String userId,
                             String email, String firstName, String lastName, S3Service s3Service) {
        try {
            context.getLogger().log("Retrieving image from S3: " + bucket + "/" + key);
            byte[] imageData = s3Service.getImageFromS3(bucket, key);

            if (imageData == null || imageData.length == 0) {
                context.getLogger().log("Retrieved empty image data from S3");
                return;
            }

            context.getLogger().log("Retrieved image data size: " + imageData.length + " bytes");

            context.getLogger().log("Adding watermark to image");
            emailService.sendProcessingStartEmail(email, firstName);
            byte[] watermarkedImage = imageProcessor.addWatermark(imageData, firstName, lastName);

            if (watermarkedImage == null || watermarkedImage.length == 0) {
                context.getLogger().log("Watermarking process returned empty data");
                return;
            }

            context.getLogger().log("Watermarked image size: " + watermarkedImage.length + " bytes");

            String processedKey = "processed/" + java.util.UUID.randomUUID() + "-" +
                    key.substring(key.lastIndexOf("/") + 1);

            context.getLogger().log("Uploading processed image to S3");
            s3Service.uploadToProcessedBucket(watermarkedImage, processedKey);

            context.getLogger().log("Storing image metadata in DynamoDB");
            String imageUrl = "https://" + System.getenv("PROCESSED_BUCKET")+ ".s3." + System.getenv("AWS_REGION") + ".amazonaws.com/" + processedKey;

            dynamoDbService.storeImageMetadata(userId, processedKey, firstName, lastName, imageUrl);

            context.getLogger().log("Deleting original image from staging bucket");
            s3Service.deleteFromStagingBucket(bucket, key);

            context.getLogger().log("Sending completion email to: " + email);
            if (email != null && !email.trim().isEmpty()) {
                try {
                    emailService.sendProcessingCompleteEmail(email, firstName, processedKey);
                    context.getLogger().log("Email sent to the receiver");
                } catch (Exception e) {
                    context.getLogger().log("Failed to send email: " + e.getMessage());
                }

            }

            context.getLogger().log("Successfully processed image: " + key);
        } catch (Exception e) {
            context.getLogger().log("Error processing image: " + e.getClass().getName() + ": " + e.getMessage());
            if (e.getCause() != null) {
                context.getLogger().log("Caused by: " + e.getCause().getMessage());
            }

            for (StackTraceElement element : e.getStackTrace()) {
                context.getLogger().log("  at " + element.toString());
            }
            emailService.sendProcessingFailureEmail(email, firstName );
            throw new RuntimeException("Failed to process image", e);
        }
    }
}
