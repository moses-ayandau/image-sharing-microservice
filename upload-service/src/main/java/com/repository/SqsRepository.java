package com.repository;

import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.AmazonSQSClientBuilder;
import com.amazonaws.services.sqs.model.MessageAttributeValue;
import com.amazonaws.services.sqs.model.SendMessageRequest;
import com.amazonaws.services.sqs.model.SendMessageResult;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.Map;

public class SqsRepository {
    private final AmazonSQS sqsClient;
    private final String queueUrl;
    private final ObjectMapper objectMapper;

    public SqsRepository() {
        this.sqsClient = AmazonSQSClientBuilder.standard()
                .withRegion(Region.US_EAST_1)
                .build();
        this.queueUrl = System.getenv("QUEUE_URL");
        this.objectMapper = new ObjectMapper();
    }

    public SqsRepository(AmazonSQS sqsClient, String queueUrl) {
        this.sqsClient = sqsClient;
        this.queueUrl = queueUrl;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Sends a message to the SQS queue with metadata attributes.
     *
     * @param messageAttributes Map containing metadata to include with the message
     * @return Map containing details about the SQS operation
     * @throws Exception If sending the message fails
     */
    public Map<String, Object> sendMessage(Map<String, String> messageAttributes) throws Exception {
        Map<String, Object> result = new HashMap<>();

        // Add a message type to distinguish from S3 notifications
        messageAttributes.put("messageType", "userUpload");

        // Create message body from attributes
        String messageBody = objectMapper.writeValueAsString(messageAttributes);

        // Create SQS message attributes
        Map<String, MessageAttributeValue> sqsMessageAttributes = new HashMap<>();

        // Ensure username and email are always included as message attributes
        for (Map.Entry<String, String> entry : messageAttributes.entrySet()) {
            sqsMessageAttributes.put(entry.getKey(),
                    new MessageAttributeValue()
                            .withDataType("String")
                            .withStringValue(entry.getValue()));
        }

        // Create and send the message
        String messageDeduplicationId = java.util.UUID.randomUUID().toString();
        SendMessageRequest sendMessageRequest = new SendMessageRequest()
                .withQueueUrl(queueUrl)
                .withMessageBody(messageBody)
                .withMessageAttributes(sqsMessageAttributes);
        
        // Check if the queue is FIFO (ends with .fifo)
        if (queueUrl != null && queueUrl.endsWith(".fifo")) {
            sendMessageRequest.withMessageGroupId("userUploads")
                              .withMessageDeduplicationId(messageDeduplicationId);
        }

        try {
            SendMessageResult sendResult = sqsClient.sendMessage(sendMessageRequest);

            // Add information about the successful operation to the result
            result.put("success", true);
            result.put("messageId", sendResult.getMessageId());
            result.put("messageAttributes", new HashMap<>(messageAttributes));
            result.put("messageDeduplicationId", messageDeduplicationId);

            return result;
        } catch (Exception e) {
            // Add information about the failed operation to the result
            result.put("success", false);
            result.put("error", e.getMessage());
            result.put("messageAttributes", new HashMap<>(messageAttributes));

            throw e;
        }
    }
}