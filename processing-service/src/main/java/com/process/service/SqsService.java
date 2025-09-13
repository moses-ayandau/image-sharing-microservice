package com.process.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlResponse;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;
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

    // Maximum number of retries before giving up
    private static final int MAX_RETRIES = 5;
    // Base delay in seconds (5 minutes)
    private static final int BASE_DELAY_SECONDS = 300;

    private static final Logger logger = Logger.getLogger(SqsService.class.getName());

    public SqsService(Region region, String queueName) {
        this.sqsClient = SqsClient.builder().region(region).build();
        this.queueName = queueName;

        logger.info("SqsService initialized with queueName: " + queueName + " in region: " + region.id());
    }

    private String getQueueUrl() {
        if (queueUrl != null) {
            return queueUrl;
        }

        if (queueName == null || queueName.isEmpty()) {
            throw new IllegalStateException("Queue name is null or empty. Please check the environment variable.");
        }

        int maxRetries = 5;
        int retryCount = 0;
        int waitTimeMs = 1000;

        while (retryCount < maxRetries) {
            try {
                GetQueueUrlRequest getQueueRequest = GetQueueUrlRequest.builder()
                        .queueName(queueName)
                        .build();
                GetQueueUrlResponse getQueueResponse = sqsClient.getQueueUrl(getQueueRequest);
                queueUrl = getQueueResponse.queueUrl();
                logger.info("Successfully retrieved queue URL: " + queueUrl);
                return queueUrl;
            } catch (QueueDoesNotExistException e) {
                logger.severe("Queue does not exist: " + queueName);
                logger.severe("This is a configuration error. The queue must be created before using this service.");
                throw new RuntimeException("Queue " + queueName + " does not exist. Please check AWS SQS configuration.", e);
            } catch (SqsException e) {
                retryCount++;
                logger.warning("Attempt " + retryCount + " - Error getting queue URL for queue " + queueName + ": " + e.getMessage());

                if (retryCount >= maxRetries) {
                    throw new RuntimeException("Failed to get queue URL after " + maxRetries + " attempts", e);
                }

                try {
                    logger.info("Waiting " + waitTimeMs + "ms before retry...");
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

    /**
     * Original method for backward compatibility - defaults retry count to 1
     */
    public void queueForRetry(String bucket, String key, String userId, String email,
                              String firstName, String lastName, String imageTitle) {
        // Default to first retry attempt
        queueForRetry(bucket, key, userId, email, firstName, lastName, imageTitle, 1);
    }

    /**
     * Enhanced version with retry counter
     */
    public void queueForRetry(String bucket, String key, String userId, String email,
                              String firstName, String lastName, String imageTitle, int retryCount) {
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

            // Log retry information
            logger.info("Preparing retry #" + retryCount + " for image: " + key);

            // Check if we've exceeded max retries
            if (retryCount > MAX_RETRIES) {
                logger.warning("Maximum retry count (" + MAX_RETRIES + ") reached for " + key + ". Giving up.");
                // You could implement additional handling here:
                // - Send to a permanent failure queue
                // - Send notification to admin
                // - Log to a specialized error tracking system
                return;
            }

            // Convert map to JSON string
            String messageBody = objectMapper.writeValueAsString(messageData);

            // Add retry count as message attribute
            Map<String, MessageAttributeValue> messageAttributes = new HashMap<>();
            messageAttributes.put("RetryCount", MessageAttributeValue.builder()
                    .dataType("Number")
                    .stringValue(String.valueOf(retryCount))
                    .build());

            // Calculate exponential backoff delay
            // First retry: 5 minutes (300s)
            // Second retry: 10 minutes (600s)
            // Third retry: 20 minutes (1200s)
            int delaySeconds = BASE_DELAY_SECONDS;
            if (retryCount > 1) {
                delaySeconds = BASE_DELAY_SECONDS * (int)Math.pow(2, retryCount - 1);
            }

            // Cap at 15 minutes (900 seconds) which is SQS maximum
            delaySeconds = Math.min(delaySeconds, 900);

            logger.info("Setting delay of " + delaySeconds + " seconds for retry #" + retryCount);

            SendMessageRequest sendMsgRequest = SendMessageRequest.builder()
                    .queueUrl(url)
                    .messageBody(messageBody)
                    .messageAttributes(messageAttributes)
                    .delaySeconds(delaySeconds)
                    .build();

            sqsClient.sendMessage(sendMsgRequest);
            logger.info("Message successfully queued for retry #" + retryCount + ": " + key);
        } catch (Exception e) {
            logger.warning("WARNING: Image was uploaded successfully but could not be queued for processing: " +
                    e.getMessage());
            e.printStackTrace();
            logger.info("The image will need to be processed manually.");
        }
    }
}