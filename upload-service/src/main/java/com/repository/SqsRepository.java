package com.repository;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageResponse;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class SqsRepository {

    private final SqsClient sqsClient;
    private final String queueUrl;
    private final ObjectMapper objectMapper;

    public SqsRepository() {
        // Get region from environment variable or use default
        String regionName = System.getenv("AWS_REGION");
        Region region = (regionName != null && !regionName.isEmpty()) 
            ? Region.of(regionName) 
            : Region.US_EAST_1;
            
        this.sqsClient = SqsClient.builder()
                .region(region)
                .build();
        this.queueUrl = System.getenv("QUEUE_URL");
        this.objectMapper = new ObjectMapper();
    }

    public SqsRepository(SqsClient sqsClient, String queueUrl) {
        this.sqsClient = sqsClient;
        this.queueUrl = queueUrl;
        this.objectMapper = new ObjectMapper();
    }

    public Map<String, Object> sendMessage(Map<String, String> messageAttributes) throws Exception {
        Map<String, Object> result = new HashMap<>();
        messageAttributes.put("messageType", "userUpload");

        String messageBody = objectMapper.writeValueAsString(messageAttributes);

        Map<String, MessageAttributeValue> sqsMessageAttributes = new HashMap<>();
        for (Map.Entry<String, String> entry : messageAttributes.entrySet()) {
            sqsMessageAttributes.put(entry.getKey(),
                    MessageAttributeValue.builder()
                            .dataType("String")
                            .stringValue(entry.getValue())
                            .build());
        }

        String messageDeduplicationId = UUID.randomUUID().toString();

        SendMessageRequest.Builder requestBuilder = SendMessageRequest.builder()
                .queueUrl(queueUrl)
                .messageBody(messageBody)
                .messageAttributes(sqsMessageAttributes);

        // Only add FIFO-specific attributes if using a FIFO queue
        if (queueUrl != null && queueUrl.endsWith(".fifo")) {
            requestBuilder.messageGroupId("userUploads")
                    .messageDeduplicationId(messageDeduplicationId);
        }

        try {
            SendMessageResponse sendResult = sqsClient.sendMessage(requestBuilder.build());

            result.put("success", true);
            result.put("messageId", sendResult.messageId());
            result.put("messageAttributes", new HashMap<>(messageAttributes));
            result.put("messageDeduplicationId", messageDeduplicationId);

            return result;
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
            result.put("messageAttributes", new HashMap<>(messageAttributes));
            throw e;
        }
    }
}
