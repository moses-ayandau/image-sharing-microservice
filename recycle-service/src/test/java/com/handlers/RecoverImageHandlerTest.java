package com.handlers;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.utils.DynamoDBUtils;
import com.utils.S3Utils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class RecoverImageHandlerTest {

    private final String tableName = "TestImageTable";
    private final String bucketName = "TestBucket";

    @Mock
    private S3Utils mockS3Utils;

    @Mock
    private DynamoDBUtils mockDynamoDBUtils;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private Context mockContext;

    @InjectMocks
    private RecoverImageHandler handler;



    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        objectMapper = new ObjectMapper();
        handler = new RecoverImageHandler(tableName, bucketName, mockS3Utils, mockDynamoDBUtils, objectMapper);
    }

    @Test
    void testHandleRequest_NullRequest() {
        APIGatewayProxyResponseEvent response = handler.handleRequest(null, mockContext);
        assertEquals(400, response.getStatusCode());
        assertTrue(response.getBody().contains("Invalid request"));
    }

    @Test
    void testHandleRequest_EmptyBody() {
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent().withBody("");
        request.setHeaders(Map.of("userId", "user123"));

        APIGatewayProxyResponseEvent response = handler.handleRequest(request, mockContext);
        assertEquals(400, response.getStatusCode());
        assertTrue(response.getBody().contains("Invalid request"));
    }

    @Test
    void testHandleRequest_MissingUserId() {
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent()
                .withBody("{\"imageKey\": \"image123\"}")
                .withHeaders(new HashMap<>());

        APIGatewayProxyResponseEvent response = handler.handleRequest(request, mockContext);
        assertEquals(400, response.getStatusCode());
        assertTrue(response.getBody().contains("Missing userId header"));
    }

    @Test
    void testHandleRequest_MissingImageId() {
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent()
                .withBody("{}")
                .withHeaders(Map.of("userId", "user123"));

        APIGatewayProxyResponseEvent response = handler.handleRequest(request, mockContext);
        assertEquals(500, response.getStatusCode());
        assertFalse(response.getBody().contains("Missing imageKey"));
    }

    @Test
    void testHandleRequest_SuccessfulRecovery() {
        String userId = "user123";
        String imageKey = "image123";
        String recycleKey = "recycle/" + userId + "/" + imageKey;
        String originalKey = "main/" + userId + "/" + imageKey;

        Map<String, AttributeValue> mockItem = Map.of("userId", AttributeValue.fromS(userId));

        when(mockDynamoDBUtils.getItemFromDynamo(tableName, imageKey)).thenReturn(mockItem);
        when(mockS3Utils.validateOwnership(mockItem, userId)).thenReturn(null);

        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent()
                .withHeaders(Map.of("userId", userId))
                .withBody("{\"imageKey\": \"" + imageKey + "\"}");

        APIGatewayProxyResponseEvent response = handler.handleRequest(request, mockContext);

        verify(mockS3Utils).validateOwnership(mockItem, userId);
        verify(mockS3Utils).copyObject(bucketName, recycleKey, originalKey);
        verify(mockS3Utils).deleteObject(bucketName, recycleKey);
        verify(mockDynamoDBUtils).updateImageStatus(tableName, imageKey, "active");
        verify(mockDynamoDBUtils).updateS3Key(tableName, imageKey, originalKey);

        assertEquals(200, response.getStatusCode());
        assertTrue(response.getBody().contains("Image recovered: " + imageKey));
    }



}
