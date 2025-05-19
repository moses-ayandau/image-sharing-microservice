package com.handlers;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.utils.DynamoDBUtils;
import com.utils.S3Utils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class RecoverImageHandlerTest {

    private final String tableName = "TestImageTable";
    private final String bucketName = "TestBucket";
    private S3Utils s3Utils;
    private DynamoDBUtils dynamoDBUtils;
    private ObjectMapper mapper;
    private RecoverImageHandler handler;
    private Context context;

    @BeforeEach
    void setUp() {
        s3Utils = mock(S3Utils.class);
        dynamoDBUtils = mock(DynamoDBUtils.class);
        mapper = new ObjectMapper();
        handler = new RecoverImageHandler(tableName, bucketName, s3Utils, dynamoDBUtils, mapper);
        context = mock(Context.class);
    }

    @Test
    void testHandleRequest_NullRequest() {
        APIGatewayProxyResponseEvent response = handler.handleRequest(null, context);
        assertEquals(400, response.getStatusCode());
        assertTrue(response.getBody().contains("Invalid request"));
    }

    @Test
    void testHandleRequest_MissingPathParameters() {
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent();
        request.setQueryStringParameters(Map.of("userId", "user1"));
        request.setPathParameters(null);

        APIGatewayProxyResponseEvent response = handler.handleRequest(request, context);
        assertEquals(400, response.getStatusCode());
        assertTrue(response.getBody().contains("Missing path parameter"));
    }

    @Test
    void testHandleRequest_MissingImageKey() {
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent();
        request.setPathParameters(new HashMap<>());
        request.setQueryStringParameters(Map.of("userId", "user1"));

        APIGatewayProxyResponseEvent response = handler.handleRequest(request, context);
        assertEquals(400, response.getStatusCode());
        assertTrue(response.getBody().contains("Missing path parameter"));
    }

    @Test
    void testHandleRequest_MissingQueryParameters() {
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent();
        request.setPathParameters(Map.of("imageKey", "test.jpg"));
        request.setQueryStringParameters(null);

        APIGatewayProxyResponseEvent response = handler.handleRequest(request, context);
        assertEquals(400, response.getStatusCode());
        assertTrue(response.getBody().contains("Missing query parameter"));
    }

    @Test
    void testHandleRequest_MissingUserId() {
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent();
        request.setPathParameters(Map.of("imageKey", "test.jpg"));
        request.setQueryStringParameters(new HashMap<>());

        APIGatewayProxyResponseEvent response = handler.handleRequest(request, context);
        assertEquals(400, response.getStatusCode());
        assertTrue(response.getBody().contains("Missing query parameter"));
    }

    @Test
    void testHandleRequest_SuccessfulRecovery() throws Exception {
        String imageKey = "test.jpg";
        String userId = "user1";
        String recycleKey = "recycle/" + imageKey;

        Map<String, AttributeValue> fakeItem = Map.of("userId", AttributeValue.fromS("user1"));

        when(dynamoDBUtils.getItemFromDynamo(tableName, imageKey)).thenReturn(fakeItem);

        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent()
                .withPathParameters(Map.of("imageKey", imageKey))
                .withQueryStringParameters(Map.of("userId", userId));

        APIGatewayProxyResponseEvent response = handler.handleRequest(request, context);

        assertEquals(200, response.getStatusCode());
        assertTrue(response.getBody().contains("Image recovered"));

        verify(dynamoDBUtils).getItemFromDynamo(tableName, imageKey);
        verify(s3Utils).validateOwnership(fakeItem, userId);
        verify(s3Utils).copyObject(bucketName, recycleKey, imageKey);
        verify(s3Utils).deleteObject(bucketName, recycleKey);
        verify(dynamoDBUtils).updateImageStatus(tableName, imageKey, "active");
        verify(dynamoDBUtils).updateS3Key(tableName, imageKey, imageKey);
    }

}
