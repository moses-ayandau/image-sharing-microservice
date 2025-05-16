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

public class ProcessImageHandler implements RequestHandler<SQSEvent, String> {

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
        context.getLogger().log("Starting to process " + sqsEvent.getRecords().size() + " messages");

        for (SQSEvent.SQSMessage message : sqsEvent.getRecords()) {
            context.getLogger().log("Processing message: " + message.getBody());

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

                // Log extracted values
                context.getLogger().log("Message parts:");
                context.getLogger().log("  Bucket: " + bucket);
                context.getLogger().log("  Key: " + key);
                context.getLogger().log("  UserId: " + userId);
                context.getLogger().log("  FirstName: " + firstName);
                context.getLogger().log("  LastName: " + lastName);
                context.getLogger().log("  ImageTitle: " + imageTitle);

                // Validate key is present
                if (key == null || key.isEmpty()) {
                    context.getLogger().log("Missing required field 'key' in message");
                    continue;
                }

                if (!s3Service.objectExists(bucket, key)) {
                    context.getLogger().log("Original file no longer exists: " + bucket + "/" + key);
                    continue;
                }

                processImage.processImage(context, bucket, key, userId, email, firstName, lastName, imageTitle);

            } catch (Exception e) {
                context.getLogger().log("Error processing message: " + e.getMessage());
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