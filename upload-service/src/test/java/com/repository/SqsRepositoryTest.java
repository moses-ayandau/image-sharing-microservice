package com.repository;

import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.model.MessageAttributeValue;
import com.amazonaws.services.sqs.model.SendMessageRequest;
import com.amazonaws.services.sqs.model.SendMessageResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class SqsRepositoryTest {

    @Mock
    private AmazonSQS sqsClient;
    
    private SqsRepository sqsRepository;
    private final String queueUrl = "https://sqs.eu-central-1.amazonaws.com/123456789012/test-queue";
    
    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        sqsRepository = new SqsRepository(sqsClient, queueUrl);
    }
    
    @Test
    public void testSendMessage() throws Exception {
        // Setup
        Map<String, String> messageAttributes = new HashMap<>();
        messageAttributes.put("firstName", "John");
        messageAttributes.put("lastName", "Doe");
        messageAttributes.put("email", "john.doe@example.com");
        messageAttributes.put("objectKey", "uploads/test-file.jpg");
        
        // Mock SQS client behavior
        when(sqsClient.sendMessage(any(SendMessageRequest.class)))
                .thenReturn(new SendMessageResult().withMessageId("test-message-id"));
        
        // Test
        Map<String, Object> result = sqsRepository.sendMessage(messageAttributes);
        
        // Verify
        assertNotNull(result);
        assertTrue(result.containsKey("messageId"));
        assertEquals("test-message-id", result.get("messageId"));
        
        // Verify correct message attributes were set
        ArgumentCaptor<SendMessageRequest> requestCaptor = ArgumentCaptor.forClass(SendMessageRequest.class);
        verify(sqsClient).sendMessage(requestCaptor.capture());
        
        SendMessageRequest capturedRequest = requestCaptor.getValue();
        assertEquals(queueUrl, capturedRequest.getQueueUrl());
        
        // Verify message body contains all attributes
        ObjectMapper objectMapper = new ObjectMapper();
        Map<String, String> bodyMap = objectMapper.readValue(capturedRequest.getMessageBody(), Map.class);
        assertEquals("John", bodyMap.get("firstName"));
        assertEquals("Doe", bodyMap.get("lastName"));
        assertEquals("john.doe@example.com", bodyMap.get("email"));
        assertEquals("uploads/test-file.jpg", bodyMap.get("objectKey"));
        assertEquals("userUpload", bodyMap.get("messageType"));
        
        // Verify message attributes
        Map<String, MessageAttributeValue> sqsAttributes = capturedRequest.getMessageAttributes();
        assertEquals("String", sqsAttributes.get("firstName").getDataType());
        assertEquals("John", sqsAttributes.get("firstName").getStringValue());
        assertEquals("Doe", sqsAttributes.get("lastName").getStringValue());
        assertEquals("john.doe@example.com", sqsAttributes.get("email").getStringValue());
        assertEquals("userUpload", sqsAttributes.get("messageType").getStringValue());
    }
    
    @Test
    public void testSendMessageToFifoQueue() throws Exception {
        // Setup for FIFO queue
        String fifoQueueUrl = "https://sqs.eu-central-1.amazonaws.com/123456789012/test-queue.fifo";
        sqsRepository = new SqsRepository(sqsClient, fifoQueueUrl);
        
        Map<String, String> messageAttributes = new HashMap<>();
        messageAttributes.put("firstName", "John");
        messageAttributes.put("lastName", "Doe");
        
        // Mock SQS client behavior
        when(sqsClient.sendMessage(any(SendMessageRequest.class)))
                .thenReturn(new SendMessageResult().withMessageId("test-message-id"));
        
        // Test
        sqsRepository.sendMessage(messageAttributes);
        
        // Verify FIFO specific attributes were set
        ArgumentCaptor<SendMessageRequest> requestCaptor = ArgumentCaptor.forClass(SendMessageRequest.class);
        verify(sqsClient).sendMessage(requestCaptor.capture());
        
        SendMessageRequest capturedRequest = requestCaptor.getValue();
        assertEquals("userUploads", capturedRequest.getMessageGroupId());
        assertNotNull(capturedRequest.getMessageDeduplicationId());
    }
}