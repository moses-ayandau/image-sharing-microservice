
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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class DeleteImageHandlerTest {

    @Mock
    private S3Utils s3Utils;

    @Mock
    private DynamoDBUtils dynamoUtils;

    @Mock
    private Context context;

    @InjectMocks
    private DeleteImageHandler handler;

    private APIGatewayProxyRequestEvent request;
    private Map<String, AttributeValue> dynamoItem;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);

        // Reset environment variables before each test
        mockEnvironmentVariables();

        // Create a new request for each test
        request = new APIGatewayProxyRequestEvent();

        // Set up common DynamoDB item
        dynamoItem = new HashMap<>();
        dynamoItem.put("S3Key", AttributeValue.builder().s("main/user123/image123.jpg").build());

        // Set up path and query parameters
        Map<String, String> pathParams = new HashMap<>();
        pathParams.put("imageId", "image123");

        Map<String, String> queryParams = new HashMap<>();
        queryParams.put("userId", "user123");

        request.setPathParameters(pathParams);
        request.setQueryStringParameters(queryParams);

        // Set up default mock behaviors
        when(dynamoUtils.getItemFromDynamo(anyString(), anyString())).thenReturn(dynamoItem);
        // Since validateOwnership is not void, we need to use when() instead of doNothing()
//        when(s3Utils.validateOwnership(any(), anyString())).thenReturn(true);
    }

    private void mockEnvironmentVariables() {
        handler = new DeleteImageHandler() {
            @Override
            public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent request, Context context) {
                return super.handleRequest(request, context);
            }
        };
        handler.s3Utils = s3Utils;
        handler.dynamoUtils = dynamoUtils;
    }

    @Test
    public void testSuccessfulImageDeletion() {
        // Execute
        APIGatewayProxyResponseEvent response = handler.handleRequest(request, context);

        // Verify
        assertEquals(200, response.getStatusCode());

        // Verify all method calls
        verify(dynamoUtils).getItemFromDynamo(anyString(), eq("image123"));
        verify(s3Utils).validateOwnership(dynamoItem, "user123");
        verify(s3Utils).copyObject(anyString(), eq("main/user123/image123.jpg"), eq("recycle/user123/image123.jpg"));
        verify(s3Utils).deleteObject(anyString(), eq("main/user123/image123.jpg"));
        verify(dynamoUtils).updateImageStatus(anyString(), eq("image123"), eq("recycle"));
        verify(dynamoUtils).updateS3Key(anyString(), eq("image123"), eq("recycle/user123/image123.jpg"));
    }

    @Test
    public void testMissingPathParameters() {
        // Setup
        request.setPathParameters(null);

        // Execute
        APIGatewayProxyResponseEvent response = handler.handleRequest(request, context);

        // Verify
        assertEquals(400, response.getStatusCode());
        assertEquals("{\"message\":\"Missing path or query parameters\"}", response.getBody());

        // Verify no interactions with dependencies
        verifyNoInteractions(s3Utils);
        verifyNoInteractions(dynamoUtils);
    }

    @Test
    public void testMissingQueryParameters() {
        // Setup
        request.setQueryStringParameters(null);

        // Execute
        APIGatewayProxyResponseEvent response = handler.handleRequest(request, context);

        // Verify
        assertEquals(400, response.getStatusCode());
        assertEquals("{\"message\":\"Missing path or query parameters\"}", response.getBody());

        // Verify no interactions with dependencies
        verifyNoInteractions(s3Utils);
        verifyNoInteractions(dynamoUtils);
    }

    @Test
    public void testMissingImageId() {
        // Setup
        Map<String, String> pathParams = new HashMap<>();
        pathParams.put("imageId", ""); // Empty imageId
        request.setPathParameters(pathParams);

        // Execute
        APIGatewayProxyResponseEvent response = handler.handleRequest(request, context);

        // Verify
        assertEquals(400, response.getStatusCode());
        assertEquals("{\"message\":\"Missing or empty imageId\"}", response.getBody());
    }

    @Test
    public void testMissingUserId() {
        // Setup
        Map<String, String> queryParams = new HashMap<>();
        queryParams.put("userId", ""); // Empty userId
        request.setQueryStringParameters(queryParams);

        // Execute
        APIGatewayProxyResponseEvent response = handler.handleRequest(request, context);

        // Verify
        assertEquals(400, response.getStatusCode());
        assertEquals("{\"message\":\"Missing or empty userId\"}", response.getBody());
    }

    @Test
    public void testImageNotFoundInDatabase() {
        // Setup
        when(dynamoUtils.getItemFromDynamo(anyString(), anyString())).thenThrow(new RuntimeException("Image not found"));

        // Execute
        APIGatewayProxyResponseEvent response = handler.handleRequest(request, context);

        // Verify
        assertEquals(404, response.getStatusCode());
        assertEquals("{\"message\":\"Image not found in database\"}", response.getBody());
    }

    @Test
    public void testCorruptImageRecord() {
        // Setup
        Map<String, AttributeValue> emptyItem = new HashMap<>();
        when(dynamoUtils.getItemFromDynamo(anyString(), anyString())).thenReturn(emptyItem);

        // Execute
        APIGatewayProxyResponseEvent response = handler.handleRequest(request, context);

        // Verify
        assertEquals(404, response.getStatusCode());
        assertEquals("{\"message\":\"Corrupt image record: missing or invalid S3Key\"}", response.getBody());
    }

    @Test
    public void testNullS3Key() {
        // Setup
        Map<String, AttributeValue> itemWithNullS3Key = new HashMap<>();
        itemWithNullS3Key.put("S3Key", AttributeValue.builder().nul(true).build());
        when(dynamoUtils.getItemFromDynamo(anyString(), anyString())).thenReturn(itemWithNullS3Key);

        // Execute
        APIGatewayProxyResponseEvent response = handler.handleRequest(request, context);

        // Verify
        assertEquals(404, response.getStatusCode());
        assertEquals("{\"message\":\"Corrupt image record: missing or invalid S3Key\"}", response.getBody());
    }

    @Test
    public void testEmptyS3Key() {
        // Setup
        Map<String, AttributeValue> itemWithEmptyS3Key = new HashMap<>();
        itemWithEmptyS3Key.put("S3Key", AttributeValue.builder().s("").build());
        when(dynamoUtils.getItemFromDynamo(anyString(), anyString())).thenReturn(itemWithEmptyS3Key);

        // Execute
        APIGatewayProxyResponseEvent response = handler.handleRequest(request, context);

        // Verify
        assertEquals(404, response.getStatusCode());
        assertEquals("{\"message\":\"Image not found in database\"}", response.getBody());
    }

    @Test
    public void testValidateOwnershipFailure() {
        // Setup
        when(s3Utils.validateOwnership(any(), anyString())).thenThrow(new RuntimeException("Unauthorized access"));

        // Execute
        APIGatewayProxyResponseEvent response = handler.handleRequest(request, context);

        // Verify
        assertEquals(500, response.getStatusCode());
        assertEquals("{\"message\":\"Internal server error\"}", response.getBody());

        // Verify we called validateOwnership but didn't proceed further
        verify(s3Utils).validateOwnership(any(), anyString());
        verify(s3Utils, never()).copyObject(anyString(), anyString(), anyString());
    }

    @Test
    public void testS3CopyFailure() {
        // Setup
        doThrow(new RuntimeException("S3 copy failed")).when(s3Utils).copyObject(anyString(), anyString(), anyString());

        // Execute
        APIGatewayProxyResponseEvent response = handler.handleRequest(request, context);

        // Verify
        assertEquals(500, response.getStatusCode());
        assertEquals("{\"message\":\"Internal server error\"}", response.getBody());

        // Verify we tried to copy but didn't proceed further
        verify(s3Utils).copyObject(anyString(), anyString(), anyString());
        verify(s3Utils, never()).deleteObject(anyString(), anyString());
    }

    @Test
    public void testS3DeleteFailure() {
        // Setup
        doThrow(new RuntimeException("S3 delete failed")).when(s3Utils).deleteObject(anyString(), anyString());

        // Execute
        APIGatewayProxyResponseEvent response = handler.handleRequest(request, context);

        // Verify
        assertEquals(500, response.getStatusCode());
        assertEquals("{\"message\":\"Internal server error\"}", response.getBody());

        // Verify we tried to delete but didn't proceed further
        verify(s3Utils).deleteObject(anyString(), anyString());
        verify(dynamoUtils, never()).updateImageStatus(anyString(), anyString(), anyString());
    }

    @Test
    public void testUpdateStatusFailure() {
        // Setup
        doThrow(new RuntimeException("Update status failed")).when(dynamoUtils).updateImageStatus(anyString(), anyString(), anyString());

        // Execute
        APIGatewayProxyResponseEvent response = handler.handleRequest(request, context);

        // Verify
        assertEquals(500, response.getStatusCode());
        assertEquals("{\"message\":\"Internal server error\"}", response.getBody());

        // Verify we tried to update status but didn't proceed further
        verify(dynamoUtils).updateImageStatus(anyString(), anyString(), anyString());
        verify(dynamoUtils, never()).updateS3Key(anyString(), anyString(), anyString());
    }

    @Test
    public void testUpdateS3KeyFailure() {
        // Setup
        doThrow(new RuntimeException("Update S3Key failed")).when(dynamoUtils).updateS3Key(anyString(), anyString(), anyString());

        // Execute
        APIGatewayProxyResponseEvent response = handler.handleRequest(request, context);

        // Verify
        assertEquals(404, response.getStatusCode());
        assertEquals("{\"message\":\"Internal server error\"}", response.getBody());
    }

    @Test
    public void testNullImageIdInPathParams() {
        // Setup
        Map<String, String> pathParams = new HashMap<>();
        pathParams.put("wrongKey", "image123"); // No imageId key
        request.setPathParameters(pathParams);

        // Execute
        APIGatewayProxyResponseEvent response = handler.handleRequest(request, context);

        // Verify
        assertEquals(400, response.getStatusCode());
        assertEquals("{\"message\":\"Missing or empty imageId\"}", response.getBody());
    }

    @Test
    public void testNullUserIdInQueryParams() {
        // Setup
        Map<String, String> queryParams = new HashMap<>();
        queryParams.put("wrongKey", "user123"); // No userId key
        request.setQueryStringParameters(queryParams);

        // Execute
        APIGatewayProxyResponseEvent response = handler.handleRequest(request, context);

        // Verify
        assertEquals(400, response.getStatusCode());
        assertEquals("{\"message\":\"Missing or empty userId\"}", response.getBody());
    }
}