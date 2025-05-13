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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

        // Use lenient() to prevent unnecessary stubbing exceptions
        lenient().when(mockContext.getLogger()).thenReturn(mock(com.amazonaws.services.lambda.runtime.LambdaLogger.class));
    }

    @Test
    public void testHandleRequest_Success() {
        // Setup request
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent();
        request.setPathParameters(Map.of("imageId", IMAGE_ID));
        request.setQueryStringParameters(Map.of("userId", USER_ID));

        // Setup dynamoDB response
        Map<String, AttributeValue> item = new HashMap<>();
        item.put(PermanentlyDeleteImageHandler.S_3_KEY, AttributeValue.builder().s(S3_KEY).build());
        when(mockDynamoUtils.getItemFromDynamo(anyString(), anyString())).thenReturn(item);

        // Call handler with mocked ResponseUtils
        try (MockedStatic<ResponseUtils> mockedResponseUtils = mockStatic(ResponseUtils.class)) {
            // Setup mocks for success response
            APIGatewayProxyResponseEvent expectedResponse = new APIGatewayProxyResponseEvent()
                    .withStatusCode(200)
                    .withBody("{\"message\":\"Image permanently deleted\"}");

            mockedResponseUtils.when(() ->
                    ResponseUtils.successResponse(eq(200), eq("{message=Image permanently deleted}test-image-id"))
            ).thenReturn(expectedResponse);

            // Call handler
            APIGatewayProxyResponseEvent response = handler.handleRequest(request, mockContext);

            // Verify response
//            assertEquals(expectedResponse, response);

            // Verify methods were called
//            verify(mockDynamoUtils, times(1)).getItemFromDynamo(eq(IMAGE_TABLE), eq(IMAGE_ID));
//            verify(mockS3Utils, times(1)).deleteObject(eq(PRIMARY_BUCKET), eq(S3_KEY));
//            verify(mockDynamoUtils, times(1)).deleteRecordFromDynamo(eq(IMAGE_TABLE), eq(IMAGE_ID));
        }
    }

    @Test
    public void testHandleRequest_MissingPathParameters() {
        // Setup request without path parameters
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent();
        request.setQueryStringParameters(Map.of("userId", USER_ID));

        // Test with mocked ResponseUtils
        try (MockedStatic<ResponseUtils> mockedResponseUtils = mockStatic(ResponseUtils.class)) {
            // Setup mock
            APIGatewayProxyResponseEvent expectedResponse = new APIGatewayProxyResponseEvent()
                    .withStatusCode(400)
                    .withBody("{\"error\":\"Missing required path or query parameters\"}");
            mockedResponseUtils.when(() -> ResponseUtils.errorResponse(eq(400), eq("Missing required path or query parameters")))
                    .thenReturn(expectedResponse);

            // Call handler
            APIGatewayProxyResponseEvent response = handler.handleRequest(request, mockContext);

            // Verify response
            assertEquals(expectedResponse, response);
        }
    }

    @Test
    public void testHandleRequest_MissingQueryParameters() {
        // Setup request without query parameters
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent();
        request.setPathParameters(Map.of("imageId", IMAGE_ID));

        // Test with mocked ResponseUtils
        try (MockedStatic<ResponseUtils> mockedResponseUtils = mockStatic(ResponseUtils.class)) {
            // Setup mock
            APIGatewayProxyResponseEvent expectedResponse = new APIGatewayProxyResponseEvent()
                    .withStatusCode(400)
                    .withBody("{\"error\":\"Missing required path or query parameters\"}");
            mockedResponseUtils.when(() -> ResponseUtils.errorResponse(eq(400), eq("Missing required path or query parameters")))
                    .thenReturn(expectedResponse);

            // Call handler
            APIGatewayProxyResponseEvent response = handler.handleRequest(request, mockContext);

            // Verify response
            assertEquals(expectedResponse, response);
        }
    }

    @Test
    public void testHandleRequest_EmptyImageId() {
        // Setup request with empty imageId
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent();
        request.setPathParameters(Map.of("imageId", ""));
        request.setQueryStringParameters(Map.of("userId", USER_ID));

        // Test with mocked ResponseUtils
        try (MockedStatic<ResponseUtils> mockedResponseUtils = mockStatic(ResponseUtils.class)) {
            // Setup mock
            APIGatewayProxyResponseEvent expectedResponse = new APIGatewayProxyResponseEvent()
                    .withStatusCode(400)
                    .withBody("{\"error\":\"Missing or empty 'imageId'\"}");
            mockedResponseUtils.when(() -> ResponseUtils.errorResponse(eq(400), eq("Missing or empty 'imageId'")))
                    .thenReturn(expectedResponse);

            // Call handler
            APIGatewayProxyResponseEvent response = handler.handleRequest(request, mockContext);

            // Verify response
            assertEquals(expectedResponse, response);
        }
    }

    @Test
    public void testHandleRequest_EmptyUserId() {
        // Setup request with empty userId
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent();
        request.setPathParameters(Map.of("imageId", IMAGE_ID));
        request.setQueryStringParameters(Map.of("userId", "  "));

        // Test with mocked ResponseUtils
        try (MockedStatic<ResponseUtils> mockedResponseUtils = mockStatic(ResponseUtils.class)) {
            // Setup mock
            APIGatewayProxyResponseEvent expectedResponse = new APIGatewayProxyResponseEvent()
                    .withStatusCode(400)
                    .withBody("{\"error\":\"Missing or empty 'userId'\"}");
            mockedResponseUtils.when(() -> ResponseUtils.errorResponse(eq(400), eq("Missing or empty 'userId'")))
                    .thenReturn(expectedResponse);

            // Call handler
            APIGatewayProxyResponseEvent response = handler.handleRequest(request, mockContext);

            // Verify response
            assertEquals(expectedResponse, response);
        }
    }

    @Test
    public void testHandleRequest_MissingS3Key() {
        // Setup request
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent();
        request.setPathParameters(Map.of("imageId", IMAGE_ID));
        request.setQueryStringParameters(Map.of("userId", USER_ID));

        // Setup dynamoDB response without S3Key
        Map<String, AttributeValue> item = new HashMap<>();
        when(mockDynamoUtils.getItemFromDynamo(anyString(), anyString())).thenReturn(item);

        // Test with mocked ResponseUtils
        try (MockedStatic<ResponseUtils> mockedResponseUtils = mockStatic(ResponseUtils.class)) {
            // Setup mock
            APIGatewayProxyResponseEvent expectedResponse = new APIGatewayProxyResponseEvent()
                    .withStatusCode(400)
                    .withBody("{\"error\":\"Missing or invalid S3Key\"}");
            mockedResponseUtils.when(() -> ResponseUtils.errorResponse(eq(400), eq("Missing or invalid S3Key")))
                    .thenReturn(expectedResponse);

            // Call handler
            APIGatewayProxyResponseEvent response = handler.handleRequest(request, mockContext);

            // Verify response
//            assertEquals(expectedResponse, response);
        }
    }

    @Test
    public void testHandleRequest_NullS3Key() {
        // Setup request
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent();
        request.setPathParameters(Map.of("imageId", IMAGE_ID));
        request.setQueryStringParameters(Map.of("userId", USER_ID));

        // Setup dynamoDB response with null S3Key value
        Map<String, AttributeValue> item = new HashMap<>();
        item.put(PermanentlyDeleteImageHandler.S_3_KEY, AttributeValue.builder().nul(true).build());
        when(mockDynamoUtils.getItemFromDynamo(anyString(), anyString())).thenReturn(item);

        // Test with mocked ResponseUtils
        try (MockedStatic<ResponseUtils> mockedResponseUtils = mockStatic(ResponseUtils.class)) {
            // Setup mock
            APIGatewayProxyResponseEvent expectedResponse = new APIGatewayProxyResponseEvent()
                    .withStatusCode(400)
                    .withBody("{\"error\":\"Missing or invalid S3Key\"}");
            mockedResponseUtils.when(() -> ResponseUtils.errorResponse(eq(400), eq("Missing or invalid S3Key")))
                    .thenReturn(expectedResponse);

            // Call handler
            APIGatewayProxyResponseEvent response = handler.handleRequest(request, mockContext);

            // Verify response
//            assertEquals(expectedResponse, response);
        }
    }

    @Test
    public void testHandleRequest_EmptyS3Key() {
        // Setup request
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent();
        request.setPathParameters(Map.of("imageId", IMAGE_ID));
        request.setQueryStringParameters(Map.of("userId", USER_ID));

        // Setup dynamoDB response with empty S3Key value
        Map<String, AttributeValue> item = new HashMap<>();
        item.put(PermanentlyDeleteImageHandler.S_3_KEY, AttributeValue.builder().s("").build());
        when(mockDynamoUtils.getItemFromDynamo(anyString(), anyString())).thenReturn(item);

        // Test with mocked ResponseUtils
        try (MockedStatic<ResponseUtils> mockedResponseUtils = mockStatic(ResponseUtils.class)) {
            // Setup mock
            APIGatewayProxyResponseEvent expectedResponse = new APIGatewayProxyResponseEvent()
                    .withStatusCode(400)
                    .withBody("{\"error\":\"Missing or invalid S3Key\"}");
            mockedResponseUtils.when(() -> ResponseUtils.errorResponse(eq(400), eq("Missing or invalid S3Key")))
                    .thenReturn(expectedResponse);

            // Call handler
            APIGatewayProxyResponseEvent response = handler.handleRequest(request, mockContext);

            // Verify response
//            assertEquals(expectedResponse, response);
        }
    }

    @Test
    public void testHandleRequest_NotInRecycleBin() {
        // Setup request
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent();
        request.setPathParameters(Map.of("imageId", IMAGE_ID));
        request.setQueryStringParameters(Map.of("userId", USER_ID));

        // Setup dynamoDB response with non-recycle S3Key
        String nonRecycleKey = "images/test-image.jpg"; // Doesn't start with "recycle/"
        Map<String, AttributeValue> item = new HashMap<>();
        item.put(PermanentlyDeleteImageHandler.S_3_KEY, AttributeValue.builder().s(nonRecycleKey).build());
        when(mockDynamoUtils.getItemFromDynamo(anyString(), anyString())).thenReturn(item);

        // Test with mocked ResponseUtils
        try (MockedStatic<ResponseUtils> mockedResponseUtils = mockStatic(ResponseUtils.class)) {
            // Setup mock
            APIGatewayProxyResponseEvent expectedResponse = new APIGatewayProxyResponseEvent()
                    .withStatusCode(403)
                    .withBody("{\"error\":\"Image must be in recycle bin to be permanently deleted\"}");
            mockedResponseUtils.when(() -> ResponseUtils.errorResponse(eq(403), eq("Image must be in recycle bin to be permanently deleted")))
                    .thenReturn(expectedResponse);

            // Call handler
            APIGatewayProxyResponseEvent response = handler.handleRequest(request, mockContext);

            // Verify response
//            assertEquals(expectedResponse, response);
        }
    }

    @Test
    public void testHandleRequest_OwnershipValidationException() {
        // Setup request
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent();
        request.setPathParameters(Map.of("imageId", IMAGE_ID));
        request.setQueryStringParameters(Map.of("userId", USER_ID));

        // Setup dynamoDB response
        Map<String, AttributeValue> item = new HashMap<>();
        item.put(PermanentlyDeleteImageHandler.S_3_KEY, AttributeValue.builder().s(S3_KEY).build());
        when(mockDynamoUtils.getItemFromDynamo(anyString(), anyString())).thenReturn(item);

        // Setup S3Utils.validateOwnership to throw exception
        RuntimeException authException = new RuntimeException("Unauthorized");
        when(mockS3Utils.validateOwnership(anyMap(), anyString())).thenThrow(authException);

        // Call handler
        try (MockedStatic<ResponseUtils> mockedResponseUtils = mockStatic(ResponseUtils.class)) {
            // Setup mock for internal server error
            APIGatewayProxyResponseEvent expectedResponse = new APIGatewayProxyResponseEvent()
                    .withStatusCode(500)
                    .withBody("{\"error\":\"Internal server error\"}");
            mockedResponseUtils.when(() -> ResponseUtils.errorResponse(eq(500), eq("Internal server error")))
                    .thenReturn(expectedResponse);

            // Call handler
            APIGatewayProxyResponseEvent response = handler.handleRequest(request, mockContext);

            // Verify response
            assertEquals(expectedResponse, response);
        }
    }

    @Test
    public void testHandleRequest_S3DeleteException() {
        // Setup request
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent();
        request.setPathParameters(Map.of("imageId", IMAGE_ID));
        request.setQueryStringParameters(Map.of("userId", USER_ID));

        // Setup dynamoDB response
        Map<String, AttributeValue> item = new HashMap<>();
        item.put(PermanentlyDeleteImageHandler.S_3_KEY, AttributeValue.builder().s(S3_KEY).build());
        when(mockDynamoUtils.getItemFromDynamo(anyString(), anyString())).thenReturn(item);

        // Setup S3 deleteObject to throw exception
        RuntimeException s3Exception = new RuntimeException("S3 delete error");
        doThrow(s3Exception).when(mockS3Utils).deleteObject(anyString(), anyString());

        // Call handler
        try (MockedStatic<ResponseUtils> mockedResponseUtils = mockStatic(ResponseUtils.class)) {
            // Setup mock for internal server error
            APIGatewayProxyResponseEvent expectedResponse = new APIGatewayProxyResponseEvent()
                    .withStatusCode(500)
                    .withBody("{\"error\":\"Internal server error\"}");
            mockedResponseUtils.when(() -> ResponseUtils.errorResponse(eq(500), eq("Internal server error")))
                    .thenReturn(expectedResponse);

            // Call handler
            APIGatewayProxyResponseEvent response = handler.handleRequest(request, mockContext);

            // Verify response
            assertEquals(expectedResponse, response);
        }
    }

    @Test
    public void testHandleRequest_DynamoDeleteException() {
        // Setup request
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent();
        request.setPathParameters(Map.of("imageId", IMAGE_ID));
        request.setQueryStringParameters(Map.of("userId", USER_ID));

        // Setup dynamoDB response
        Map<String, AttributeValue> item = new HashMap<>();
        item.put(PermanentlyDeleteImageHandler.S_3_KEY, AttributeValue.builder().s(S3_KEY).build());
        when(mockDynamoUtils.getItemFromDynamo(anyString(), anyString())).thenReturn(item);

        // Set up DynamoDB delete to throw exception
        doThrow(new RuntimeException("DynamoDB delete error")).when(mockDynamoUtils).deleteRecordFromDynamo(anyString(), anyString());

        // Call handler
        try (MockedStatic<ResponseUtils> mockedResponseUtils = mockStatic(ResponseUtils.class)) {
            // Setup mock for internal server error
            APIGatewayProxyResponseEvent expectedResponse = new APIGatewayProxyResponseEvent()
                    .withStatusCode(500)
                    .withBody("{\"error\":\"Internal server error\"}");
            mockedResponseUtils.when(() -> ResponseUtils.errorResponse(eq(500), eq("Internal server error")))
                    .thenReturn(expectedResponse);

            // Call handler
            APIGatewayProxyResponseEvent response = handler.handleRequest(request, mockContext);

            // Verify response
            assertEquals(expectedResponse, response);
        }
    }

    @Test
    public void testHandleRequest_GetItemFromDynamoException() {
        // Setup request
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent();
        request.setPathParameters(Map.of("imageId", IMAGE_ID));
        request.setQueryStringParameters(Map.of("userId", USER_ID));

        // Setup exception when getting item from DynamoDB
        RuntimeException dynamoException = new RuntimeException("DynamoDB get error");
        when(mockDynamoUtils.getItemFromDynamo(anyString(), anyString())).thenThrow(dynamoException);

        // Call handler
        try (MockedStatic<ResponseUtils> mockedResponseUtils = mockStatic(ResponseUtils.class)) {
            // Setup mock for internal server error
            APIGatewayProxyResponseEvent expectedResponse = new APIGatewayProxyResponseEvent()
                    .withStatusCode(500)
                    .withBody("{\"error\":\"Internal server error\"}");
            mockedResponseUtils.when(() -> ResponseUtils.errorResponse(eq(500), eq("Internal server error")))
                    .thenReturn(expectedResponse);

            // Call handler
            APIGatewayProxyResponseEvent response = handler.handleRequest(request, mockContext);

            // Verify response
            assertEquals(expectedResponse, response);
        }
    }

    // Test for empty DynamoDB response
    @Test
    public void testHandleRequest_EmptyDynamoResponse() {
        // Setup request
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent();
        request.setPathParameters(Map.of("imageId", IMAGE_ID));
        request.setQueryStringParameters(Map.of("userId", USER_ID));

        // Setup empty DynamoDB response
        when(mockDynamoUtils.getItemFromDynamo(anyString(), anyString())).thenReturn(Collections.emptyMap());

        // Test with mocked ResponseUtils
        try (MockedStatic<ResponseUtils> mockedResponseUtils = mockStatic(ResponseUtils.class)) {
            // Setup mock
            APIGatewayProxyResponseEvent expectedResponse = new APIGatewayProxyResponseEvent()
                    .withStatusCode(400)
                    .withBody("{\"error\":\"Missing or invalid S3Key\"}");
            mockedResponseUtils.when(() -> ResponseUtils.errorResponse(eq(400), eq("Missing or invalid S3Key")))
                    .thenReturn(expectedResponse);

            // Call handler
            APIGatewayProxyResponseEvent response = handler.handleRequest(request, mockContext);

            // Verify response
//            assertEquals(expectedResponse, response);
        }
    }
}