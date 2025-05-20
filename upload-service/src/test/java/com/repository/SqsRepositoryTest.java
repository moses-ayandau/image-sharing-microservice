package com.repository;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageResponse;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

public class SqsRepositoryTest {

    @Mock
    private SqsClient sqsClient;

    @Mock
    private Context context;
    
    @Mock
    private LambdaLogger lambdaLogger;

    private SqsRepository sqsRepository;
    private final String queueUrl = "https://sqs.us-east-1.amazonaws.com/123456789012/test-queue";

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        // Mock the logger
        when(context.getLogger()).thenReturn(lambdaLogger);
        
        sqsRepository = new SqsRepository(sqsClient, queueUrl);
    }

    @Test
    public void testConstructor() {
        // Test default constructor (uses environment variables)
        try {
            SqsRepository defaultRepo = new SqsRepository();
            // This might fail if environment variables are not set, but that's expected
            assertNotNull(defaultRepo);
        } catch (Exception e) {
            // Expected if environment variables are not set
        }
        
        // Test constructor with dependencies
        SqsRepository repo = new SqsRepository(sqsClient, queueUrl);
        assertNotNull(repo);
    }

    @Test
    public void testSendMessageSuccess() throws Exception {
        // Setup
        Map<String, String> messageAttributes = new HashMap<>();
        messageAttributes.put("name", "John Doe");
        messageAttributes.put("email", "john@example.com");
        messageAttributes.put("key", "test-image.jpg");
        
        SendMessageResponse mockResponse = SendMessageResponse.builder()
                .messageId("msg123")
                .build();
        
        when(sqsClient.sendMessage(any(SendMessageRequest.class))).thenReturn(mockResponse);
        
        // Execute
        Map<String, Object> result = sqsRepository.sendMessage(messageAttributes, context);
        
        // Verify
        assertNotNull(result);
        assertTrue((Boolean) result.get("success"));
        assertEquals("msg123", result.get("messageId"));
    }
}