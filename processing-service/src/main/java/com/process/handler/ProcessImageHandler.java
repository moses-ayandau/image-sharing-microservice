package com.process.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.process.util.*;

public class ProcessImageHandler implements RequestHandler<SQSEvent, String> {

    private final S3Service s3Service;
    private final ProcessImage processImage;

    private final String stagingBucket;
    public ProcessImageHandler() {
        String region = System.getenv("AWS_REGION");
        this.stagingBucket = System.getenv("STAGING_BUCKET");
        String processedBucket = System.getenv("PROCESSED_BUCKET");
        String imageTable = System.getenv("IMAGE_TABLE");

        this.s3Service = new S3Service(region, processedBucket);
        DynamoDbService dynamoDbService = new DynamoDbService(region, imageTable);
        EmailService emailService = new EmailService(region);
        ImageProcessor imageProcessor = new ImageProcessor();

        this.processImage = new ProcessImage(s3Service, dynamoDbService, emailService, imageProcessor);
    }

    public ProcessImageHandler(ProcessImage processImage) {
        this.processImage = processImage;
        String region = System.getenv("AWS_REGION");
        this.stagingBucket = System.getenv("STAGING_BUCKET");
        String processedBucket = System.getenv("PROCESSED_BUCKET");
        String imageTable = System.getenv("IMAGE_TABLE");

        this.s3Service = new S3Service(region, processedBucket);

    }

    @Override
    public String handleRequest(SQSEvent sqsEvent, Context context) {
        context.getLogger().log("Starting to process " + sqsEvent.getRecords().size() + " messages");

        for (SQSEvent.SQSMessage message : sqsEvent.getRecords()) {
            context.getLogger().log("Processing message: " + message.getBody());

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

                // Log all parts for debugging
                context.getLogger().log("Message parts:");
                context.getLogger().log("  Bucket: " + bucket);
                context.getLogger().log("  Key: " + key);
                context.getLogger().log("  UserId: " + userId);
                context.getLogger().log("  Email: " + email);
                context.getLogger().log("  FirstName: " + firstName);
                context.getLogger().log("  LastName: " + lastName);

                // Check if the original file still exists
                if (!s3Service.objectExists(bucket, key)) {
                    context.getLogger().log("Original file no longer exists: " + bucket + "/" + key);
                    continue;
                }

                // Process the image
                processImage.processImage(context, bucket, key, userId, email, firstName, lastName, s3Service);

            } catch (Exception e) {
                context.getLogger().log("Error processing message: " + e.getMessage());
                // In production, consider re-queueing with a backoff or moving to DLQ
            }
        }

        return "Processing complete";
    }


}