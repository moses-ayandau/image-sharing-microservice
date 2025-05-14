package handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import factories.DynamodbFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;
import utils.DynamoDbUtils;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.doReturn;

@ExtendWith(MockitoExtension.class)
class GetActiveImagesHandlerTest {

    @Mock
    private DynamoDbClient mockDynamoDbClient;

    @Mock
    private Context mockContext;

    private GetActiveImagesHandler handler;
    private APIGatewayProxyRequestEvent request;
    private final String TEST_TABLE_NAME = "test-image-table";
    private final String TEST_USER_ID = "user123";

    @BeforeEach
    void setUp() {
        // Set up environment
        System.setProperty("IMAGE_TABLE", TEST_TABLE_NAME);

        // Set up the request
        request = new APIGatewayProxyRequestEvent();
        Map<String, String> pathParams = new HashMap<>();
        pathParams.put("userId", TEST_USER_ID);
        request.setPathParameters(pathParams);

        // Mock the DynamodbFactory to return our mock client
        try (MockedStatic<DynamodbFactory> factoryMock = mockStatic(DynamodbFactory.class)) {
            factoryMock.when(DynamodbFactory::createClient).thenReturn(mockDynamoDbClient);
            handler = new GetActiveImagesHandler();
        }
    }

    @Test
    void testHandleRequest_Success() {
        List<Map<String, AttributeValue>> mockItems = new ArrayList<>();
        Map<String, AttributeValue> item1 = new HashMap<>();
        item1.put("imageId", AttributeValue.builder().s("img1").build());
        item1.put("userId", AttributeValue.builder().s(TEST_USER_ID).build());
        item1.put("status", AttributeValue.builder().s("active").build());
        item1.put("url", AttributeValue.builder().s("http://example.com/img1.jpg").build());
        mockItems.add(item1);

        Map<String, AttributeValue> item2 = new HashMap<>();
        item2.put("imageId", AttributeValue.builder().s("img2").build());
        item2.put("userId", AttributeValue.builder().s(TEST_USER_ID).build());
        item2.put("status", AttributeValue.builder().s("active").build());
        item2.put("url", AttributeValue.builder().s("http://example.com/img2.jpg").build());
        mockItems.add(item2);

        ScanResponse mockResponse = ScanResponse.builder()
                .items(mockItems)
                .build();

        when(mockDynamoDbClient.scan(any(ScanRequest.class))).thenReturn(mockResponse);

        // Mock DynamoDbUtils.convertDynamoItemToMap
        try (MockedStatic<DynamoDbUtils> utilsMock = mockStatic(DynamoDbUtils.class)) {
            Map<String, Object> convertedItem1 = new HashMap<>();
            convertedItem1.put("imageId", "img1");
            convertedItem1.put("userId", TEST_USER_ID);
            convertedItem1.put("status", "active");
            convertedItem1.put("url", "http://example.com/img1.jpg");

            Map<String, Object> convertedItem2 = new HashMap<>();
            convertedItem2.put("imageId", "img2");
            convertedItem2.put("userId", TEST_USER_ID);
            convertedItem2.put("status", "active");
            convertedItem2.put("url", "http://example.com/img2.jpg");

            utilsMock.when(() -> DynamoDbUtils.convertDynamoItemToMap(item1)).thenReturn(convertedItem1);
            utilsMock.when(() -> DynamoDbUtils.convertDynamoItemToMap(item2)).thenReturn(convertedItem2);

            // Act
            APIGatewayProxyResponseEvent response = handler.handleRequest(request, mockContext);

            // Assert
            assertEquals(200, response.getStatusCode().intValue());
            verify(mockDynamoDbClient).scan(any(ScanRequest.class));
            assertNotNull(response.getBody());
            assertTrue(response.getBody().contains("img1"));
            assertTrue(response.getBody().contains("img2"));
        }
    }

    @Test
    void testHandleRequest_EmptyResult() {
        List<Map<String, AttributeValue>> emptyItems = new ArrayList<>();
        ScanResponse mockResponse = ScanResponse.builder()
                .items(emptyItems)
                .build();

        when(mockDynamoDbClient.scan(any(ScanRequest.class))).thenReturn(mockResponse);

        // Act
        APIGatewayProxyResponseEvent response = handler.handleRequest(request, mockContext);

        // Assert
        assertEquals(200, response.getStatusCode().intValue());
        verify(mockDynamoDbClient).scan(any(ScanRequest.class));
        assertEquals("[]", response.getBody());
    }

    @Test
    void testHandleRequest_MissingUserId() {
        LambdaLogger mockLogger =
                mock(com.amazonaws.services.lambda.runtime.LambdaLogger.class);
        doReturn(mockLogger).when(mockContext).getLogger();
        request.setPathParameters(null);

        APIGatewayProxyResponseEvent response = handler.handleRequest(request, mockContext);

        assertEquals(400, response.getStatusCode().intValue());
        assertTrue(response.getBody().contains("User ID is required"));
        verify(mockDynamoDbClient, never()).scan(any(ScanRequest.class));
    }

    @Test
    void testHandleRequest_DynamoDbException() {
        // Arrange
        when(mockDynamoDbClient.scan(any(ScanRequest.class)))
                .thenThrow(DynamoDbException.builder().message("Test exception").build());

        // Create a mock LambdaLogger
        LambdaLogger mockLogger =
            mock(com.amazonaws.services.lambda.runtime.LambdaLogger.class);

        // Use doReturn instead of when().thenReturn()
        doReturn(mockLogger).when(mockContext).getLogger();
        
        // Act
        APIGatewayProxyResponseEvent response = handler.handleRequest(request, mockContext);
        
        // Assert
        assertEquals(500, response.getStatusCode().intValue());
        assertTrue(response.getBody().contains("Failed to fetch active images"));
    }

    @Test
    void testHandleRequest_VerifyScanRequest() {
        // Arrange
        ScanResponse mockResponse = ScanResponse.builder()
                .items(new ArrayList<>())
                .build();

        when(mockDynamoDbClient.scan(any(ScanRequest.class))).thenReturn(mockResponse);

        // Act
        handler.handleRequest(request, mockContext);

        // Assert - verify the scan request contains correct parameters
        verify(mockDynamoDbClient).scan(argThat((ScanRequest scanRequest) -> {
            // Verify the table name
            //assertEquals(TEST_TABLE_NAME, scanRequest.tableName());

            // Verify filter expression
            assertEquals("#status = :statusValue AND #userId = :userIdValue", scanRequest.filterExpression());

            // Verify expression attribute values
            Map<String, AttributeValue> expectedValues = new HashMap<>();
            expectedValues.put(":statusValue", AttributeValue.builder().s("active").build());
            expectedValues.put(":userIdValue", AttributeValue.builder().s(TEST_USER_ID).build());

            Map<String, AttributeValue> actualValues = scanRequest.expressionAttributeValues();
            assertEquals(expectedValues.keySet(), actualValues.keySet());
            assertEquals("active", actualValues.get(":statusValue").s());
            assertEquals(TEST_USER_ID, actualValues.get(":userIdValue").s());

            // Verify expression attribute names
            Map<String, String> expectedNames = new HashMap<>();
            expectedNames.put("#status", "status");
            expectedNames.put("#userId", "userId");

            assertEquals(expectedNames, scanRequest.expressionAttributeNames());

            return true;
        }));
    }
}