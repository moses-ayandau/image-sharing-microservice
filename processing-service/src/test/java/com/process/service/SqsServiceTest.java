package com.process.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.*;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SqsServiceTest {

    @Mock
    private SqsClient sqsClient;

    private SqsService sqsService;

    @BeforeEach
    void setUp() throws Exception {

        sqsService = new SqsService(Region.US_EAST_1, "test-queue");

        Field clientField = SqsService.class.getDeclaredField("sqsClient");
        clientField.setAccessible(true);
        clientField.set(sqsService, sqsClient);
    }

    @Test
    void testQueueForRetry_DefaultRetryCount() {

        GetQueueUrlResponse queueUrlResponse = GetQueueUrlResponse.builder()
                .queueUrl("https://sqs.us-east-1.amazonaws.com/123456789012/test-queue")
                .build();
        when(sqsClient.getQueueUrl(any(GetQueueUrlRequest.class))).thenReturn(queueUrlResponse);
        when(sqsClient.sendMessage(any(SendMessageRequest.class)))
                .thenReturn(SendMessageResponse.builder().build());


        assertDoesNotThrow(() -> sqsService.queueForRetry(
                "test-bucket", "test-key", "user123", "test@example.com",
                "John", "Doe", "Test Image"));


        verify(sqsClient).sendMessage(any(SendMessageRequest.class));
    }

    @Test
    void testQueueForRetry_WithSpecificRetryCount() {

        GetQueueUrlResponse queueUrlResponse = GetQueueUrlResponse.builder()
                .queueUrl("https://sqs.us-east-1.amazonaws.com/123456789012/test-queue")
                .build();
        when(sqsClient.getQueueUrl(any(GetQueueUrlRequest.class))).thenReturn(queueUrlResponse);
        when(sqsClient.sendMessage(any(SendMessageRequest.class)))
                .thenReturn(SendMessageResponse.builder().build());


        assertDoesNotThrow(() -> sqsService.queueForRetry(
                "test-bucket", "test-key", "user123", "test@example.com",
                "John", "Doe", "Test Image", 3));


        verify(sqsClient).sendMessage(any(SendMessageRequest.class));
    }

    @Test
    void testQueueForRetry_ExceedsMaxRetries() {

        GetQueueUrlResponse queueUrlResponse = GetQueueUrlResponse.builder()
                .queueUrl("https://sqs.us-east-1.amazonaws.com/123456789012/test-queue")
                .build();
        when(sqsClient.getQueueUrl(any(GetQueueUrlRequest.class))).thenReturn(queueUrlResponse);


        assertDoesNotThrow(() -> sqsService.queueForRetry(
                "test-bucket", "test-key", "user123", "test@example.com",
                "John", "Doe", "Test Image", 6));

        verify(sqsClient, never()).sendMessage(any(SendMessageRequest.class));
    }

    @Test
    void testQueueForRetry_HandlesException() {

        when(sqsClient.getQueueUrl(any(GetQueueUrlRequest.class)))
                .thenThrow(new RuntimeException("Network error"));

        assertDoesNotThrow(() -> sqsService.queueForRetry(
                "test-bucket", "test-key", "user123", "test@example.com",
                "John", "Doe", "Test Image"));

        verify(sqsClient, never()).sendMessage(any(SendMessageRequest.class));
    }
}