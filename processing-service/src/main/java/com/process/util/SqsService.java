package com.process.util;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import java.util.HashMap;
import java.util.Map;

public class SqsService {

    private final SqsClient sqsClient;
    private final String retryQueue;

    public SqsService(String regionName, String retryQueue) {
        Region region = Region.of(regionName);
        this.sqsClient = SqsClient.builder().region(region).build();
        this.retryQueue = retryQueue;
    }

    /**
     * Queues a failed image processing job for retry
     *
     * @param bucket Source bucket name
     * @param key Object key of the failed image
     * @param userId User ID
     * @param email User's email address
     * @param firstName User's first name
     * @param lastName User's last name
     */
    public void queueForRetry(String bucket, String key, String userId, String email, String firstName, String lastName) {
        // Create a message with all the necessary information for retry
        Map<String, String> messageAttributes = new HashMap<>();
        messageAttributes.put("bucket", bucket);
        messageAttributes.put("key", key);
        messageAttributes.put("userId", userId);
        messageAttributes.put("email", email);
        messageAttributes.put("firstName", firstName);
        messageAttributes.put("lastName", lastName);

        String messageBody = String.join(",", messageAttributes.values());

        SendMessageRequest sendMessageRequest = SendMessageRequest.builder()
                .queueUrl(retryQueue)
                .messageBody(messageBody)
                .delaySeconds(300) // 5 minutes delay
                .build();

        sqsClient.sendMessage(sendMessageRequest);
    }
}