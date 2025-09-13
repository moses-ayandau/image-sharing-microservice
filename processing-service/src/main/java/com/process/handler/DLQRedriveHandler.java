package com.process.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.ScheduledEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Lambda function that moves messages from Dead Letter Queue back to the main retry queue
 * with a configured delay to allow for retry processing of failed image processing requests.
 */
public class DLQRedriveHandler implements RequestHandler<ScheduledEvent, Map<String, Object>> {

    private static final Logger logger = LoggerFactory.getLogger(DLQRedriveHandler.class);
    private static final int DELAY_SECONDS = 300;
    private static final int MAX_MESSAGES_PER_BATCH = 10;

    private final SqsClient sqsClient;
    private final String dlqUrl;
    private final String retryQueueUrl;
    private final int maxMessagesToRedrive;

    public DLQRedriveHandler() {
        this.sqsClient = SqsClient.create();
        this.dlqUrl = System.getenv("DLQ_URL");
        this.retryQueueUrl = System.getenv("RETRY_QUEUE_URL");

        String maxMessagesEnv = System.getenv("MAX_MESSAGES_TO_REDRIVE");
        this.maxMessagesToRedrive = (maxMessagesEnv != null) ?
                Integer.parseInt(maxMessagesEnv) : 100;

        logger.info("Initialized DLQ Redrive Handler with DLQ: {}, Retry Queue: {}, Max Messages: {}",
                dlqUrl, retryQueueUrl, maxMessagesToRedrive);
    }

    @Override
    public Map<String, Object> handleRequest(ScheduledEvent scheduledEvent, Context context) {
        logger.info("Starting DLQ redrive process");
        Map<String, Object> result = new HashMap<>();

        int totalMessagesRedriven = 0;
        int successCount = 0;
        int failureCount = 0;

        try {
            GetQueueAttributesResponse queueAttributes = sqsClient.getQueueAttributes(
                    GetQueueAttributesRequest.builder()
                            .queueUrl(dlqUrl)
                            .attributeNames(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES)
                            .build());

            String approximateNumberOfMessagesStr = queueAttributes.attributes()
                    .get(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES.toString());
            int approximateNumberOfMessages = Integer.parseInt(approximateNumberOfMessagesStr);

            logger.info("Found approximately {} messages in DLQ", approximateNumberOfMessages);

            // Determine how many messages to process
            int messagesToProcess = Math.min(approximateNumberOfMessages, maxMessagesToRedrive);
            int batchesNeeded = (int) Math.ceil((double) messagesToProcess / MAX_MESSAGES_PER_BATCH);

            for (int batch = 0; batch < batchesNeeded && totalMessagesRedriven < maxMessagesToRedrive; batch++) {
                // Receive messages from DLQ
                ReceiveMessageResponse receiveResponse = sqsClient.receiveMessage(
                        ReceiveMessageRequest.builder()
                                .queueUrl(dlqUrl)
                                .maxNumberOfMessages(MAX_MESSAGES_PER_BATCH)
                                .visibilityTimeout(30)
                                .waitTimeSeconds(1)
                                .build());

                List<Message> messages = receiveResponse.messages();

                if (messages.isEmpty()) {
                    logger.info("No more messages in DLQ to process");
                    break;
                }

                logger.info("Processing batch {} with {} messages", batch + 1, messages.size());

                // Process messages - send to retry queue and delete from DLQ
                for (Message message : messages) {
                    try {
                        sqsClient.sendMessage(
                                SendMessageRequest.builder()
                                        .queueUrl(retryQueueUrl)
                                        .messageBody(message.body())
                                        .delaySeconds(DELAY_SECONDS)
                                        .build());

                        sqsClient.deleteMessage(
                                DeleteMessageRequest.builder()
                                        .queueUrl(dlqUrl)
                                        .receiptHandle(message.receiptHandle())
                                        .build());

                        successCount++;
                        logger.debug("Successfully redrove message {}", message.messageId());
                    } catch (Exception e) {
                        failureCount++;
                        logger.error("Failed to redrive message {}: {}",
                                message.messageId(), e.getMessage(), e);
                    }

                    totalMessagesRedriven++;

                    if (totalMessagesRedriven >= maxMessagesToRedrive) {
                        logger.info("Reached maximum number of messages to redrive: {}", maxMessagesToRedrive);
                        break;
                    }
                }
            }

            logger.info("DLQ redrive completed. Total: {}, Success: {}, Failure: {}",
                    totalMessagesRedriven, successCount, failureCount);

            result.put("status", "success");
            result.put("messagesProcessed", totalMessagesRedriven);
            result.put("successCount", successCount);
            result.put("failureCount", failureCount);

        } catch (Exception e) {
            logger.error("Error in DLQ redrive process: {}", e.getMessage(), e);
            result.put("status", "error");
            result.put("error", e.getMessage());
        }

        return result;
    }
}