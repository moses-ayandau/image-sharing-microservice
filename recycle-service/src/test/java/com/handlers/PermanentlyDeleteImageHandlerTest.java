package com.handlers;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.utils.DynamoDBUtils;
import com.utils.ResponseUtils;
import com.utils.S3Utils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class PermanentlyDeleteImageHandlerTest {

    @Mock
    private S3Utils mockS3Utils;

    @Mock
    private DynamoDBUtils mockDynamoUtils;

    @Mock
    private Context mockContext;

    private PermanentlyDeleteImageHandler handler;
    private final String IMAGE_TABLE = "test-image-table";
    private final String PRIMARY_BUCKET = "test-bucket";
    private final String IMAGE_ID = "test-image-id";
    private final String USER_ID = "test-user-id";
    private final String S3_KEY = "recycle/test-image.jpg";

    @BeforeEach
    public void setUp() {
        System.setProperty("IMAGE_TABLE", IMAGE_TABLE);
        System.setProperty("PRIMARY_BUCKET", PRIMARY_BUCKET);
        handler = new PermanentlyDeleteImageHandler(mockS3Utils, mockDynamoUtils);

        lenient().when(mockContext.getLogger()).thenReturn(mock(com.amazonaws.services.lambda.runtime.LambdaLogger.class));
    }

    @Test
    public void testHandleRequest_Success() {
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent();
        request.setPathParameters(Map.of("imageKey", IMAGE_ID));
        request.setQueryStringParameters(Map.of("userId", USER_ID));

        String IMAGE_TABLE = System.getenv("IMAGE_TABLE");
        Map<String, AttributeValue> item = new HashMap<>();
        item.put(PermanentlyDeleteImageHandler.S_3_KEY, AttributeValue.builder().s(S3_KEY).build());
        item.put("userId", AttributeValue.builder().s(USER_ID).build());
        when(mockDynamoUtils.getItemFromDynamo(IMAGE_TABLE, IMAGE_ID)).thenReturn(item);

        PermanentlyDeleteImageHandler spyHandler = spy(handler);

        try (MockedStatic<ResponseUtils> mockedResponseUtils = mockStatic(ResponseUtils.class)) {
            APIGatewayProxyResponseEvent successResponse = new APIGatewayProxyResponseEvent()
                    .withStatusCode(200)
                    .withBody("{\"message\":\"Image permanently deleted\"}");

            APIGatewayProxyResponseEvent errorResponse = new APIGatewayProxyResponseEvent()
                    .withStatusCode(400);

            mockedResponseUtils.when(() -> ResponseUtils.errorResponse(anyInt(), anyString()))
                    .thenReturn(errorResponse);
            mockedResponseUtils.when(() -> ResponseUtils.successResponse(eq(200), any()))
                    .thenReturn(successResponse);

            APIGatewayProxyResponseEvent response = spyHandler.handleRequest(request, mockContext);

            assertEquals(successResponse, response);

            verify(mockDynamoUtils).getItemFromDynamo(eq(IMAGE_TABLE), eq(IMAGE_ID));
            verify(mockS3Utils).validateOwnership(eq(item), eq(USER_ID));
            verify(mockS3Utils).deleteObject(eq(System.getenv("PRIMARY_BUCKET")), eq(S3_KEY));
            verify(mockDynamoUtils).deleteRecordFromDynamo(eq(IMAGE_TABLE), eq(IMAGE_ID));
        }
    }

    @Test
    public void testHandleRequest_MissingPathParameters() {
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent();
        request.setQueryStringParameters(Map.of("userId", USER_ID));

        try (MockedStatic<ResponseUtils> mockedResponseUtils = mockStatic(ResponseUtils.class)) {
            APIGatewayProxyResponseEvent expectedResponse = new APIGatewayProxyResponseEvent()
                    .withStatusCode(400)
                    .withBody("{\"error\":\"Missing required path or query parameters\"}");
            mockedResponseUtils.when(() -> ResponseUtils.errorResponse(any(Integer.class), any(String.class)))
                    .thenReturn(expectedResponse);

            APIGatewayProxyResponseEvent response = handler.handleRequest(request, mockContext);

            assertEquals(expectedResponse, response);
        }
    }

    @Test
    public void testHandleRequest_MissingQueryParameters() {
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent();
        request.setPathParameters(Map.of("imageKey", IMAGE_ID));

        try (MockedStatic<ResponseUtils> mockedResponseUtils = mockStatic(ResponseUtils.class)) {
            APIGatewayProxyResponseEvent expectedResponse = new APIGatewayProxyResponseEvent()
                    .withStatusCode(400)
                    .withBody("{\"error\":\"Missing required path or query parameters\"}");
            mockedResponseUtils.when(() -> ResponseUtils.errorResponse(any(Integer.class), any(String.class)))
                    .thenReturn(expectedResponse);

            APIGatewayProxyResponseEvent response = handler.handleRequest(request, mockContext);

            assertEquals(expectedResponse, response);
        }
    }

    @Test
    public void testHandleRequest_EmptyImageId() {
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent();
        request.setPathParameters(Map.of("imageKey", ""));
        request.setQueryStringParameters(Map.of("userId", USER_ID));

        try (MockedStatic<ResponseUtils> mockedResponseUtils = mockStatic(ResponseUtils.class)) {
            APIGatewayProxyResponseEvent expectedResponse = new APIGatewayProxyResponseEvent()
                    .withStatusCode(400)
                    .withBody("{\"error\":\"Missing or empty 'imageKey'\"}");
            mockedResponseUtils.when(() -> ResponseUtils.errorResponse(any(Integer.class), any(String.class)))
                    .thenReturn(expectedResponse);

            APIGatewayProxyResponseEvent response = handler.handleRequest(request, mockContext);

            assertEquals(expectedResponse, response);
        }
    }

    @Test
    public void testHandleRequest_EmptyUserId() {
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent();
        request.setPathParameters(Map.of("imageKey", IMAGE_ID));
        request.setQueryStringParameters(Map.of("userId", "  "));

        try (MockedStatic<ResponseUtils> mockedResponseUtils = mockStatic(ResponseUtils.class)) {
            APIGatewayProxyResponseEvent expectedResponse = new APIGatewayProxyResponseEvent()
                    .withStatusCode(400)
                    .withBody("{\"error\":\"Missing or empty 'userId'\"}");
            mockedResponseUtils.when(() -> ResponseUtils.errorResponse(any(Integer.class), any(String.class)))
                    .thenReturn(expectedResponse);

            APIGatewayProxyResponseEvent response = handler.handleRequest(request, mockContext);

            assertEquals(expectedResponse, response);
        }
    }

    @Test
    public void testHandleRequest_MissingS3Key() {
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent();
        request.setPathParameters(Map.of("imageKey", IMAGE_ID));
        request.setQueryStringParameters(Map.of("userId", USER_ID));

        Map<String, AttributeValue> item = new HashMap<>();
        when(mockDynamoUtils.getItemFromDynamo(anyString(), anyString())).thenReturn(item);

        try (MockedStatic<ResponseUtils> mockedResponseUtils = mockStatic(ResponseUtils.class)) {
            APIGatewayProxyResponseEvent expectedResponse = new APIGatewayProxyResponseEvent()
                    .withStatusCode(400)
                    .withBody("{\"error\":\"Missing or invalid S3Key\"}");
            mockedResponseUtils.when(() -> ResponseUtils.errorResponse(any(Integer.class), any(String.class)))
                    .thenReturn(expectedResponse);

            APIGatewayProxyResponseEvent response = handler.handleRequest(request, mockContext);

            assertEquals(expectedResponse, response);
        }
    }

    @Test
    public void testHandleRequest_NullS3Key() {
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent();
        request.setPathParameters(Map.of("imageKey", IMAGE_ID));
        request.setQueryStringParameters(Map.of("userId", USER_ID));

        Map<String, AttributeValue> item = new HashMap<>();
        item.put(PermanentlyDeleteImageHandler.S_3_KEY, AttributeValue.builder().nul(true).build());
        when(mockDynamoUtils.getItemFromDynamo(anyString(), anyString())).thenReturn(item);

        try (MockedStatic<ResponseUtils> mockedResponseUtils = mockStatic(ResponseUtils.class)) {
            APIGatewayProxyResponseEvent expectedResponse = new APIGatewayProxyResponseEvent()
                    .withStatusCode(400)
                    .withBody("{\"error\":\"Missing or invalid S3Key\"}");
            mockedResponseUtils.when(() -> ResponseUtils.errorResponse(any(Integer.class), any(String.class)))
                    .thenReturn(expectedResponse);

            APIGatewayProxyResponseEvent response = handler.handleRequest(request, mockContext);

            assertEquals(expectedResponse, response);
        }
    }

    @Test
    public void testHandleRequest_EmptyS3Key() {
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent();
        request.setPathParameters(Map.of("imageKey", IMAGE_ID));
        request.setQueryStringParameters(Map.of("userId", USER_ID));

        Map<String, AttributeValue> item = new HashMap<>();
        item.put(PermanentlyDeleteImageHandler.S_3_KEY, AttributeValue.builder().s("").build());
        when(mockDynamoUtils.getItemFromDynamo(anyString(), anyString())).thenReturn(item);

        try (MockedStatic<ResponseUtils> mockedResponseUtils = mockStatic(ResponseUtils.class)) {
            APIGatewayProxyResponseEvent expectedResponse = new APIGatewayProxyResponseEvent()
                    .withStatusCode(400)
                    .withBody("{\"error\":\"Missing or invalid S3Key\"}");
            mockedResponseUtils.when(() -> ResponseUtils.errorResponse(any(Integer.class), any(String.class)))
                    .thenReturn(expectedResponse);

            APIGatewayProxyResponseEvent response = handler.handleRequest(request, mockContext);

            assertEquals(expectedResponse, response);
        }
    }

    @Test
    public void testHandleRequest_NotInRecycleBin() {
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent();
        request.setPathParameters(Map.of("imageKey", IMAGE_ID));
        request.setQueryStringParameters(Map.of("userId", USER_ID));

        String nonRecycleKey = "images/test-image.jpg";
        Map<String, AttributeValue> item = new HashMap<>();
        item.put(PermanentlyDeleteImageHandler.S_3_KEY, AttributeValue.builder().s(nonRecycleKey).build());
        when(mockDynamoUtils.getItemFromDynamo(anyString(), anyString())).thenReturn(item);

        try (MockedStatic<ResponseUtils> mockedResponseUtils = mockStatic(ResponseUtils.class)) {
            APIGatewayProxyResponseEvent expectedResponse = new APIGatewayProxyResponseEvent()
                    .withStatusCode(403)
                    .withBody("{\"error\":\"Image must be in recycle bin to be permanently deleted\"}");
            mockedResponseUtils.when(() -> ResponseUtils.errorResponse(any(Integer.class), any(String.class)))
                    .thenReturn(expectedResponse);

            APIGatewayProxyResponseEvent response = handler.handleRequest(request, mockContext);

            assertEquals(expectedResponse, response);
        }
    }

    @Test
    public void testHandleRequest_OwnershipValidationException() {
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent();
        request.setPathParameters(Map.of("imageKey", IMAGE_ID));
        request.setQueryStringParameters(Map.of("userId", USER_ID));

        Map<String, AttributeValue> item = new HashMap<>();
        item.put(PermanentlyDeleteImageHandler.S_3_KEY, AttributeValue.builder().s(S3_KEY).build());
        when(mockDynamoUtils.getItemFromDynamo(anyString(), anyString())).thenReturn(item);

        RuntimeException authException = new RuntimeException("Unauthorized");
        when(mockS3Utils.validateOwnership(anyMap(), anyString())).thenThrow(authException);

        try (MockedStatic<ResponseUtils> mockedResponseUtils = mockStatic(ResponseUtils.class)) {
            APIGatewayProxyResponseEvent expectedResponse = new APIGatewayProxyResponseEvent()
                    .withStatusCode(500)
                    .withBody("{\"error\":\"Internal server error\"}");
            mockedResponseUtils.when(() -> ResponseUtils.errorResponse(any(Integer.class), any(String.class)))
                    .thenReturn(expectedResponse);

            APIGatewayProxyResponseEvent response = handler.handleRequest(request, mockContext);

            assertEquals(expectedResponse, response);
        }
    }

    @Test
    public void testHandleRequest_S3DeleteException() {
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent();
        request.setPathParameters(Map.of("imageKey", IMAGE_ID));
        request.setQueryStringParameters(Map.of("userId", USER_ID));

        Map<String, AttributeValue> item = new HashMap<>();
        item.put(PermanentlyDeleteImageHandler.S_3_KEY, AttributeValue.builder().s(S3_KEY).build());
        when(mockDynamoUtils.getItemFromDynamo(anyString(), anyString())).thenReturn(item);

        RuntimeException s3Exception = new RuntimeException("S3 delete error");
        doThrow(s3Exception).when(mockS3Utils).deleteObject(anyString(), anyString());

        try (MockedStatic<ResponseUtils> mockedResponseUtils = mockStatic(ResponseUtils.class)) {
            APIGatewayProxyResponseEvent expectedResponse = new APIGatewayProxyResponseEvent()
                    .withStatusCode(500)
                    .withBody("{\"error\":\"Internal server error\"}");
            mockedResponseUtils.when(() -> ResponseUtils.errorResponse(any(Integer.class), any(String.class)))
                    .thenReturn(expectedResponse);

            APIGatewayProxyResponseEvent response = handler.handleRequest(request, mockContext);

            assertEquals(expectedResponse, response);
        }
    }

    @Test
    public void testHandleRequest_DynamoDeleteException() {
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent();
        request.setPathParameters(Map.of("imageKey", IMAGE_ID));
        request.setQueryStringParameters(Map.of("userId", USER_ID));

        Map<String, AttributeValue> item = new HashMap<>();
        item.put(PermanentlyDeleteImageHandler.S_3_KEY, AttributeValue.builder().s(S3_KEY).build());
        when(mockDynamoUtils.getItemFromDynamo(anyString(), anyString())).thenReturn(item);

        doThrow(new RuntimeException("DynamoDB delete error")).when(mockDynamoUtils).deleteRecordFromDynamo(anyString(), anyString());

        try (MockedStatic<ResponseUtils> mockedResponseUtils = mockStatic(ResponseUtils.class)) {
            APIGatewayProxyResponseEvent expectedResponse = new APIGatewayProxyResponseEvent()
                    .withStatusCode(500)
                    .withBody("{\"error\":\"Internal server error\"}");
            mockedResponseUtils.when(() -> ResponseUtils.errorResponse(any(Integer.class), any(String.class)))
                    .thenReturn(expectedResponse);

            APIGatewayProxyResponseEvent response = handler.handleRequest(request, mockContext);

            assertEquals(expectedResponse, response);
        }
    }


    @Test
    public void testHandleRequest_GetItemFromDynamoException() {
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent();
        request.setPathParameters(Map.of("imageKey", IMAGE_ID));
        request.setQueryStringParameters(Map.of("userId", USER_ID));

        RuntimeException dynamoException = new RuntimeException("DynamoDB get error");
        when(mockDynamoUtils.getItemFromDynamo(anyString(), anyString())).thenThrow(dynamoException);

        try (MockedStatic<ResponseUtils> mockedResponseUtils = mockStatic(ResponseUtils.class)) {
            APIGatewayProxyResponseEvent expectedResponse = new APIGatewayProxyResponseEvent()
                    .withStatusCode(500)
                    .withBody("{\"error\":\"Internal server error\"}");
            mockedResponseUtils.when(() -> ResponseUtils.errorResponse(any(Integer.class), any(String.class)))
                    .thenReturn(expectedResponse);

            APIGatewayProxyResponseEvent response = handler.handleRequest(request, mockContext);

            assertEquals(expectedResponse, response);
        }
    }

    @Test
    public void testHandleRequest_EmptyDynamoResponse() {
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent();
        request.setPathParameters(Map.of("imageKey", IMAGE_ID));
        request.setQueryStringParameters(Map.of("userId", USER_ID));

        when(mockDynamoUtils.getItemFromDynamo(anyString(), anyString())).thenReturn(Collections.emptyMap());

        try (MockedStatic<ResponseUtils> mockedResponseUtils = mockStatic(ResponseUtils.class)) {
            APIGatewayProxyResponseEvent expectedResponse = new APIGatewayProxyResponseEvent()
                    .withStatusCode(400)
                    .withBody("{\"error\":\"Missing or invalid S3Key\"}");
            mockedResponseUtils.when(() -> ResponseUtils.errorResponse(any(Integer.class), any(String.class)))
                    .thenReturn(expectedResponse);

            APIGatewayProxyResponseEvent response = handler.handleRequest(request, mockContext);

            assertEquals(expectedResponse, response);
        }
    }
}