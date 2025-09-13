package com.process.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.process.service.DynamoDbService;
import com.process.service.EmailService;
import com.process.service.S3Service;
import com.process.service.SqsService;
import com.process.util.*;

import java.util.Map;
import java.util.logging.Logger;

public class ProcessImageHandler implements RequestHandler<SQSEvent, String> {

    private static final Logger logger = Logger.getLogger(ProcessImageHandler.class.getName());

    private final S3Service s3Service;
    private final ProcessImage processImage;
    private final String retryQueueName;
    private final String stagingBucket;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ProcessImageHandler() {
        String region = System.getenv("AWS_REGION");
        this.stagingBucket = System.getenv("STAGING_BUCKET");
        String processedBucket = System.getenv("PROCESSED_BUCKET");
        String imageTable = System.getenv("IMAGE_TABLE");
        this.retryQueueName = System.getenv("RETRY_QUEUE");

        this.s3Service = new S3Service(region, processedBucket);
        DynamoDbService dynamoDbService = new DynamoDbService(region, imageTable);
        EmailService emailService = new EmailService(region);
        ImageProcessor imageProcessor = new ImageProcessor();
        SqsService sqsService = new SqsService(software.amazon.awssdk.regions.Region.of(region), retryQueueName);

        this.processImage = new ProcessImage(s3Service, dynamoDbService, emailService, imageProcessor, sqsService);
    }

    @Override
    public String handleRequest(SQSEvent sqsEvent, Context context) {
        logger.info("Starting to process " + sqsEvent.getRecords().size() + " messages");

        for (SQSEvent.SQSMessage message : sqsEvent.getRecords()) {
            logger.info("Processing message: " + message.getBody());

            try {
                // Parse JSON message body
                Map<String, String> messageData = objectMapper.readValue(message.getBody(), Map.class);

                // Extract values with null/empty checks
                String bucket = getValueOrDefault(messageData, "bucket", stagingBucket);
                String key = getValueOrDefault(messageData, "key", null);
                String userId = getValueOrDefault(messageData, "userId", "");
                String email = getValueOrDefault(messageData, "email", "");
                String firstName = getValueOrDefault(messageData, "firstName", "");
                String lastName = getValueOrDefault(messageData, "lastName", "");
                String imageTitle = getValueOrDefault(messageData, "imageTitle", "");

                // Get retry count from message attributes (or default to 1 if not present)
                int retryCount = 1;
                if (message.getMessageAttributes() != null &&
                        message.getMessageAttributes().containsKey("RetryCount")) {
                    retryCount = Integer.parseInt(
                            message.getMessageAttributes().get("RetryCount").getStringValue());
                }

                // Log extracted values
                logger.info("Message parts:");
                logger.info("  Bucket: " + bucket);
                logger.info("  Key: " + key);
                logger.info("  UserId: " + userId);
                logger.info("  FirstName: " + firstName);
                logger.info("  LastName: " + lastName);
                logger.info("  ImageTitle: " + imageTitle);
                logger.info("  RetryCount: " + retryCount);

                // Validate key is present
                if (key == null || key.isEmpty()) {
                    logger.warning("Missing required field 'key' in message");
                    continue;
                }

                if (!s3Service.objectExists(bucket, key)) {
                    logger.warning("Original file no longer exists: " + bucket + "/" + key);
                    continue;
                }

                // Process image with retry count information
                processImage.processImage(context, bucket, key, userId, email, firstName,
                        lastName, imageTitle, retryCount);

            } catch (Exception e) {
                logger.severe("Error processing message: " + e.getMessage());
                e.printStackTrace();
            }
        }

        return "Processing complete";
    }

    /**
     * Helper method to safely extract values from the message map
     */
    private String getValueOrDefault(Map<String, String> map, String key, String defaultValue) {
        if (map == null || !map.containsKey(key) || map.get(key) == null) {
            return defaultValue;
        }
        return map.get(key);
    }
}