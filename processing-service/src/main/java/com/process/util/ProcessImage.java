package com.process.util;

import com.amazonaws.services.lambda.runtime.Context;
import com.process.service.DynamoDbService;
import com.process.service.EmailService;
import com.process.service.S3Service;
import com.process.service.SqsService;

import java.util.UUID;
import java.util.logging.Logger;

public class ProcessImage {
    private static final Logger logger = Logger.getLogger(ProcessImage.class.getName());

    private final DynamoDbService dynamoDbService;
    private final EmailService emailService;
    private final ImageProcessor imageProcessor;
    private final S3Service s3Service;
    private final SqsService sqsService;

    public ProcessImage(S3Service s3Service, DynamoDbService dynamoDbService, EmailService emailService,
                        ImageProcessor imageProcessor, SqsService sqsService) {
        this.dynamoDbService = dynamoDbService;
        this.emailService = emailService;
        this.imageProcessor = imageProcessor;
        this.s3Service = s3Service;
        this.sqsService = sqsService;
    }

    public void processImage(Context context, String bucket, String key, String userId,
                             String email, String firstName, String lastName, String imageTitle) {
        // Use retry count = 1 as default
        processImage(context, bucket, key, userId, email, firstName, lastName, imageTitle, 1);
    }

    public void processImage(Context context, String bucket, String key, String userId,
                             String email, String firstName, String lastName, String imageTitle,
                             int retryCount) {
        try {
            logger.info("Starting image processing " + (retryCount > 1 ? "(retry attempt #" + retryCount + ")" : ""));
            logger.info("Retrieving image from S3: " + bucket + "/" + key);

            byte[] imageData = s3Service.getImageFromS3(bucket, key);

            if (imageData == null || imageData.length == 0) {
                logger.warning("Retrieved empty image data from S3");
                return;
            }

            logger.info("Retrieved image data size: " + imageData.length + " bytes");

            logger.info("Adding watermark to image");

            // Only send processing start email on first attempt
            if (retryCount == 1) {
                emailService.sendProcessingStartEmail(email, firstName);
            }

            byte[] watermarkedImage = imageProcessor.addWatermark(imageData, firstName, lastName);

            if (watermarkedImage == null || watermarkedImage.length == 0) {
                logger.warning("Watermarking process returned empty data");
                return;
            }

            logger.info("Watermarked image size: " + watermarkedImage.length + " bytes");
            String processedKey = UUID.randomUUID().toString().replace("-", "").substring(0, 16);

            logger.info("Uploading processed image to S3");
            s3Service.uploadToProcessedBucket(watermarkedImage, processedKey);

            logger.info("Storing image metadata in DynamoDB");
            String imageUrl = "https://" + System.getenv("PROCESSED_BUCKET") + ".s3." +
                    System.getenv("AWS_REGION") + ".amazonaws.com/" + processedKey;

            dynamoDbService.storeImageMetadata(userId, processedKey, imageTitle, imageUrl);

            logger.info("Deleting original image from staging bucket");
            s3Service.deleteFromStagingBucket(bucket, key);

            logger.info("Sending completion email to: " + email);
            if (email != null && !email.trim().isEmpty()) {
                try {
                    emailService.sendProcessingCompleteEmail(email, firstName, processedKey);
                    logger.info("Email sent to the receiver");
                } catch (Exception e) {
                    logger.warning("Failed to send email: " + e.getMessage());
                }
            }

            logger.info("Successfully processed image: " + key +
                    (retryCount > 1 ? " (after " + retryCount + " attempts)" : ""));

        } catch (Exception e) {
            logger.severe("Error processing image: " + e.getClass().getName() + ": " + e.getMessage());
            if (e.getCause() != null) {
                logger.severe("Caused by: " + e.getCause().getMessage());
            }

            for (StackTraceElement element : e.getStackTrace()) {
                logger.fine("  at " + element.toString());
            }

            // Send failure email - only on first failure or final attempt
            boolean isFinalAttempt = retryCount >= 5;
            if (retryCount == 1 || isFinalAttempt) {
                try {
                    emailService.sendProcessingFailureEmail(email, firstName);

                    if (isFinalAttempt) {
                        logger.warning("Final attempt failed - no more retries for image: " + key);
                    }
                } catch (Exception emailEx) {
                    logger.warning("Could not send failure email: " + emailEx.getMessage());
                }
            }

            // Increment retry count and queue message for RetryQueue
            int newRetryCount = retryCount + 1;
            logger.info("Queuing image for retry attempt #" + newRetryCount + ": " + key);
            sqsService.queueForRetry(bucket, key, userId, email, firstName, lastName, imageTitle, newRetryCount);

            throw new RuntimeException("Failed to process image (retry attempt #" + retryCount + ")", e);
        }
    }
}