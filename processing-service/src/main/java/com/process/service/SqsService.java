package com.process.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlResponse;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.QueueDoesNotExistException;
import software.amazon.awssdk.services.sqs.model.SqsException;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

public class SqsService {
    private final SqsClient sqsClient;
    private final String queueName;
    private String queueUrl;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SqsService(Region region, String queueName) {
        this.sqsClient = SqsClient.builder().region(region).build();
        this.queueName = queueName;

        System.out.println("SqsService initialized with queueName: " + queueName + " in region: " + region.id());
    }

    private String getQueueUrl() {
        if (queueUrl != null) {
            return queueUrl;
        }

        if (queueName == null || queueName.isEmpty()) {
            throw new IllegalStateException("Queue name is null or empty. Please check the environment variable.");
        }

        int maxRetries = 3;
        int retryCount = 0;
        int waitTimeMs = 1000;

        while (retryCount < maxRetries) {
            try {
                GetQueueUrlRequest getQueueRequest = GetQueueUrlRequest.builder()
                        .queueName(queueName)
                        .build();
                GetQueueUrlResponse getQueueResponse = sqsClient.getQueueUrl(getQueueRequest);
                queueUrl = getQueueResponse.queueUrl();
                System.out.println("Successfully retrieved queue URL: " + queueUrl);
                return queueUrl;
            } catch (QueueDoesNotExistException e) {
                System.err.println("Queue does not exist: " + queueName);
                System.err.println("This is a configuration error. The queue must be created before using this service.");
                throw new RuntimeException("Queue " + queueName + " does not exist. Please check AWS SQS configuration.", e);
            } catch (SqsException e) {
                retryCount++;
                System.err.println("Attempt " + retryCount + " - Error getting queue URL for queue " + queueName + ": " + e.getMessage());

                if (retryCount >= maxRetries) {
                    throw new RuntimeException("Failed to get queue URL after " + maxRetries + " attempts", e);
                }

                try {
                    System.out.println("Waiting " + waitTimeMs + "ms before retry...");
                    Thread.sleep(waitTimeMs);
                    waitTimeMs *= 2;
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Thread interrupted while waiting to retry", ie);
                }
            }
        }

        throw new RuntimeException("Could not get queue URL for " + queueName + " after " + maxRetries + " attempts");
    }

    public void queueForRetry(String bucket, String key, String userId, String email, String firstName, String lastName, String imageTitle) {
        try {
            String url = getQueueUrl();

            // Create a map for message data - this handles missing values gracefully
            Map<String, String> messageData = new HashMap<>();
            messageData.put("bucket", bucket);
            messageData.put("key", key);
            messageData.put("userId", userId);
            messageData.put("email", email);
            messageData.put("firstName", firstName);
            messageData.put("lastName", lastName);
            messageData.put("imageTitle", imageTitle);

            // Convert map to JSON string
            String messageBody = objectMapper.writeValueAsString(messageData);

            SendMessageRequest sendMsgRequest = SendMessageRequest.builder()
                    .queueUrl(url)
                    .messageBody(messageBody)
                    .build();

            sqsClient.sendMessage(sendMsgRequest);
            System.out.println("Message successfully queued for processing: " + key);
        } catch (Exception e) {
            Logger.getAnonymousLogger().warning("WARNING: Image was uploaded successfully but could not be queued for processing: " +
                    e.getMessage());
            e.printStackTrace();
            Logger.getAnonymousLogger().info("The image will need to be processed manually.");
        }
    }
}