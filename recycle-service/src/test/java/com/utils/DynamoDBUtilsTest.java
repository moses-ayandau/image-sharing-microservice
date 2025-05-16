package com.utils;

import com.factories.AwsFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class DynamoDBUtilsTest {

    @Mock
    private DynamoDbClient mockDynamoDbClient;

    private DynamoDBUtils dynamoDBUtils;

    private static final String TABLE_NAME = "test-table";
    private static final String IMAGE_ID_VALUE = "test-image-id";

    @BeforeEach
    void setUp() {
        try (MockedStatic<AwsFactory> mockedStatic = Mockito.mockStatic(AwsFactory.class)) {
            mockedStatic.when(AwsFactory::dynamoDbClient).thenReturn(mockDynamoDbClient);

            dynamoDBUtils = new DynamoDBUtils();
        }
    }

    @Test
    void testGetItemFromDynamo_Success() {
        Map<String, AttributeValue> expectedItem = new HashMap<>();
        expectedItem.put("imageKey", AttributeValue.fromS(IMAGE_ID_VALUE));
        expectedItem.put("status", AttributeValue.fromS("PROCESSED"));

        GetItemResponse mockResponse = GetItemResponse.builder()
                .item(expectedItem)
                .build();

        when(mockDynamoDbClient.getItem(any(GetItemRequest.class))).thenReturn(mockResponse);

        Map<String, AttributeValue> result = dynamoDBUtils.getItemFromDynamo(TABLE_NAME, IMAGE_ID_VALUE);
        assertEquals(expectedItem, result);
        verify(mockDynamoDbClient).getItem(any(GetItemRequest.class));
    }

    @Test
    void testGetItemFromDynamo_ItemNotFound() {
        GetItemResponse mockResponse = GetItemResponse.builder()
                .item(Collections.emptyMap())
                .build();

        when(mockDynamoDbClient.getItem(any(GetItemRequest.class))).thenReturn(mockResponse);
        Exception exception = assertThrows(RuntimeException.class, () -> {
            dynamoDBUtils.getItemFromDynamo(TABLE_NAME, IMAGE_ID_VALUE);
        });

        assertEquals("Image not found in database", exception.getMessage());
    }

    @Test
    void testDeleteRecordFromDynamo() {
        dynamoDBUtils.deleteRecordFromDynamo(TABLE_NAME, IMAGE_ID_VALUE);

        ArgumentCaptor<DeleteItemRequest> requestCaptor = ArgumentCaptor.forClass(DeleteItemRequest.class);
        verify(mockDynamoDbClient).deleteItem(requestCaptor.capture());

        DeleteItemRequest capturedRequest = requestCaptor.getValue();
        assertEquals(TABLE_NAME, capturedRequest.tableName());
        assertEquals(IMAGE_ID_VALUE, capturedRequest.key().get(DynamoDBUtils.IMAGE_ID).s());
    }

    @Test
    void testUpdateImageStatus() {
        String status = "COMPLETED";

        dynamoDBUtils.updateImageStatus(TABLE_NAME, IMAGE_ID_VALUE, status);

        ArgumentCaptor<UpdateItemRequest> requestCaptor = ArgumentCaptor.forClass(UpdateItemRequest.class);
        verify(mockDynamoDbClient).updateItem(requestCaptor.capture());

        UpdateItemRequest capturedRequest = requestCaptor.getValue();
        assertEquals(TABLE_NAME, capturedRequest.tableName());
        assertEquals(IMAGE_ID_VALUE, capturedRequest.key().get(DynamoDBUtils.IMAGE_ID).s());
        assertEquals("SET #status = :status", capturedRequest.updateExpression());
        assertTrue(capturedRequest.expressionAttributeNames().containsKey("#status"));
        assertEquals("status", capturedRequest.expressionAttributeNames().get("#status"));
        assertTrue(capturedRequest.expressionAttributeValues().containsKey(":status"));
        assertEquals(status, capturedRequest.expressionAttributeValues().get(":status").s());
    }

    @Test
    void testUpdateS3Key() {
        String newS3Key = "new/s3/key/path.jpg";

        dynamoDBUtils.updateS3Key(TABLE_NAME, IMAGE_ID_VALUE, newS3Key);

        ArgumentCaptor<UpdateItemRequest> requestCaptor = ArgumentCaptor.forClass(UpdateItemRequest.class);
        verify(mockDynamoDbClient).updateItem(requestCaptor.capture());

        UpdateItemRequest capturedRequest = requestCaptor.getValue();
        assertEquals(TABLE_NAME, capturedRequest.tableName());
        assertEquals(IMAGE_ID_VALUE, capturedRequest.key().get(DynamoDBUtils.IMAGE_ID).s());
        assertEquals("SET #s3Key = :newS3Key", capturedRequest.updateExpression());
        assertTrue(capturedRequest.expressionAttributeNames().containsKey("#s3Key"));
        assertEquals("S3Key", capturedRequest.expressionAttributeNames().get("#s3Key"));
        assertTrue(capturedRequest.expressionAttributeValues().containsKey(":newS3Key"));
        assertEquals(newS3Key, capturedRequest.expressionAttributeValues().get(":newS3Key").s());
    }
}