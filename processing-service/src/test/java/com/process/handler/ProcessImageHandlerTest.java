package com.process.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.process.service.S3Service;
import com.process.util.ProcessImage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProcessImageHandlerTest {

    @Mock
    private S3Service s3Service;

    @Mock
    private ProcessImage processImage;

    @Mock
    private Context context;

    private ProcessImageHandler handler;

    @BeforeEach
    void setUp() throws Exception {
        System.setProperty("AWS_REGION", "us-east-1");
        System.setProperty("STAGING_BUCKET", "staging-bucket");
        System.setProperty("PROCESSED_BUCKET", "processed-bucket");
        System.setProperty("IMAGE_TABLE", "image-table");
        System.setProperty("RETRY_QUEUE", "retry-queue");

        handler = new ProcessImageHandler();

        Field s3ServiceField = ProcessImageHandler.class.getDeclaredField("s3Service");
        s3ServiceField.setAccessible(true);
        s3ServiceField.set(handler, s3Service);

        Field processImageField = ProcessImageHandler.class.getDeclaredField("processImage");
        processImageField.setAccessible(true);
        processImageField.set(handler, processImage);
    }

    @AfterEach
    void tearDown() {
        System.clearProperty("AWS_REGION");
        System.clearProperty("STAGING_BUCKET");
        System.clearProperty("PROCESSED_BUCKET");
        System.clearProperty("IMAGE_TABLE");
        System.clearProperty("RETRY_QUEUE");
    }

    @Test
    void testHandleRequest_SingleMessage_Success() {
        SQSEvent sqsEvent = createSQSEvent(
                "{\"bucket\":\"test-bucket\",\"key\":\"test-key\",\"userId\":\"user123\"," +
                        "\"email\":\"test@example.com\",\"firstName\":\"John\",\"lastName\":\"Doe\"," +
                        "\"imageTitle\":\"Test Image\"}"
        );

        when(s3Service.objectExists("test-bucket", "test-key")).thenReturn(true);
        doNothing().when(processImage).processImage(any(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyString(), anyString(), anyInt());

        String result = handler.handleRequest(sqsEvent, context);

        assertEquals("Processing complete", result);
        verify(s3Service).objectExists("test-bucket", "test-key");
        verify(processImage).processImage(context, "test-bucket", "test-key", "user123",
                "test@example.com", "John", "Doe", "Test Image", 1);
    }


    @Test
    void testHandleRequest_MissingKey_SkipsMessage() {
        SQSEvent sqsEvent = createSQSEvent(
                "{\"bucket\":\"test-bucket\",\"userId\":\"user123\"}"
        );

        String result = handler.handleRequest(sqsEvent, context);

        assertEquals("Processing complete", result);
        verify(s3Service, never()).objectExists(anyString(), anyString());
        verify(processImage, never()).processImage(any(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyString(), anyString(), anyInt());
    }

    @Test
    void testHandleRequest_EmptyKey_SkipsMessage() {
        SQSEvent sqsEvent = createSQSEvent(
                "{\"bucket\":\"test-bucket\",\"key\":\"\",\"userId\":\"user123\"}"
        );

        String result = handler.handleRequest(sqsEvent, context);

        verify(s3Service, never()).objectExists(anyString(), anyString());
        verify(processImage, never()).processImage(any(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyString(), anyString(), anyInt());
    }

    @Test
    void testHandleRequest_ObjectDoesNotExist_SkipsMessage() {
        SQSEvent sqsEvent = createSQSEvent(
                "{\"bucket\":\"test-bucket\",\"key\":\"missing-key\",\"userId\":\"user123\"}"
        );

        when(s3Service.objectExists("test-bucket", "missing-key")).thenReturn(false);

        String result = handler.handleRequest(sqsEvent, context);

        assertEquals("Processing complete", result);
        verify(s3Service).objectExists("test-bucket", "missing-key");
        verify(processImage, never()).processImage(any(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyString(), anyString(), anyInt());
    }

    @Test
    void testHandleRequest_WithRetryCount() {
        SQSEvent sqsEvent = createSQSEventWithRetryCount(
                "{\"bucket\":\"test-bucket\",\"key\":\"test-key\",\"userId\":\"user123\"}",
                3
        );

        when(s3Service.objectExists("test-bucket", "test-key")).thenReturn(true);

        String result = handler.handleRequest(sqsEvent, context);

        assertEquals("Processing complete", result);
        verify(processImage).processImage(context, "test-bucket", "test-key", "user123",
                "", "", "", "", 3);
    }

    @Test
    void testHandleRequest_InvalidJsonMessage_ContinuesProcessing() {
        SQSEvent sqsEvent = createSQSEvent("invalid json");

        String result = handler.handleRequest(sqsEvent, context);

        assertEquals("Processing complete", result);
        verify(s3Service, never()).objectExists(anyString(), anyString());
        verify(processImage, never()).processImage(any(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyString(), anyString(), anyInt());
    }

    @Test
    void testHandleRequest_ProcessImageThrowsException_ContinuesProcessing() {
        SQSEvent sqsEvent = createSQSEvent(
                "{\"bucket\":\"test-bucket\",\"key\":\"test-key\",\"userId\":\"user123\"}"
        );

        when(s3Service.objectExists("test-bucket", "test-key")).thenReturn(true);
        doThrow(new RuntimeException("Processing failed")).when(processImage)
                .processImage(any(), anyString(), anyString(), anyString(), anyString(),
                        anyString(), anyString(), anyString(), anyInt());

        String result = handler.handleRequest(sqsEvent, context);

        assertEquals("Processing complete", result);
        verify(processImage).processImage(any(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyString(), anyInt());
    }

    @Test
    void testHandleRequest_DefaultValues() {
        SQSEvent sqsEvent = createSQSEvent(
                "{\"key\":\"test-key\"}"
        );

        when(s3Service.objectExists(anyString(), anyString())).thenReturn(true);

        String result = handler.handleRequest(sqsEvent, context);

        assertEquals("Processing complete", result);
        verify(processImage).processImage(eq(context), eq("staging-bucket"), eq("test-key"),
                eq(""), eq(""), eq(""), eq(""), eq(""), eq(1));
    }

    @Test
    void testGetConfigValue_FallsBackToSystemProperty() throws Exception {
        System.setProperty("TEST_CONFIG", "test-value");

        ProcessImageHandler testHandler = new ProcessImageHandler();
        java.lang.reflect.Method method = ProcessImageHandler.class.getDeclaredMethod("getConfigValue", String.class);
        method.setAccessible(true);
        String result = (String) method.invoke(testHandler, "TEST_CONFIG");

        assertEquals("test-value", result);

        System.clearProperty("TEST_CONFIG");
    }

    private SQSEvent createSQSEvent(String messageBody) {
        SQSEvent sqsEvent = new SQSEvent();
        sqsEvent.setRecords(Arrays.asList(createSQSMessage(messageBody)));
        return sqsEvent;
    }

    private SQSEvent.SQSMessage createSQSMessage(String messageBody) {
        SQSEvent.SQSMessage message = new SQSEvent.SQSMessage();
        message.setBody(messageBody);
        return message;
    }

    private SQSEvent createSQSEventWithRetryCount(String messageBody, int retryCount) {
        SQSEvent sqsEvent = new SQSEvent();
        SQSEvent.SQSMessage message = new SQSEvent.SQSMessage();
        message.setBody(messageBody);

        Map<String, SQSEvent.MessageAttribute> attributes = new HashMap<>();
        SQSEvent.MessageAttribute retryAttr = new SQSEvent.MessageAttribute();
        retryAttr.setStringValue(String.valueOf(retryCount));
        retryAttr.setDataType("Number");
        attributes.put("RetryCount", retryAttr);
        message.setMessageAttributes(attributes);

        sqsEvent.setRecords(Arrays.asList(message));
        return sqsEvent;
    }
}