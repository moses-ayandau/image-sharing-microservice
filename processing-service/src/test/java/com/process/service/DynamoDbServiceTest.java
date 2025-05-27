package com.process.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.DynamoDbException;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemResponse;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DynamoDbServiceTest {

    @Mock
    private DynamoDbClient dynamoDbClient;

    private DynamoDbService dynamoDbService;

    @BeforeEach
    void setUp() throws Exception {

        dynamoDbService = new DynamoDbService("us-east-1", "test-table");

        Field clientField = DynamoDbService.class.getDeclaredField("dynamoDbClient");
        clientField.setAccessible(true);
        clientField.set(dynamoDbService, dynamoDbClient);
    }

    @Test
    void testStoreImageMetadata_Success() {

        when(dynamoDbClient.putItem(any(PutItemRequest.class)))
                .thenReturn(PutItemResponse.builder().build());


        assertDoesNotThrow(() -> dynamoDbService.storeImageMetadata(
                "user123", "image-key", "My Image", "https://example.com/image.jpg"));


        verify(dynamoDbClient).putItem(any(PutItemRequest.class));
    }

    @Test
    void testStoreImageMetadata_WithNullValues() {

        when(dynamoDbClient.putItem(any(PutItemRequest.class)))
                .thenReturn(PutItemResponse.builder().build());


        assertDoesNotThrow(() -> dynamoDbService.storeImageMetadata(
                null, null, null, null));

        verify(dynamoDbClient).putItem(any(PutItemRequest.class));
    }

    @Test
    void testStoreImageMetadata_WithEmptyStrings() {

        when(dynamoDbClient.putItem(any(PutItemRequest.class)))
                .thenReturn(PutItemResponse.builder().build());


        assertDoesNotThrow(() -> dynamoDbService.storeImageMetadata(
                "", "", "", ""));

        verify(dynamoDbClient).putItem(any(PutItemRequest.class));
    }

    @Test
    void testStoreImageMetadata_DynamoDbException() {

        when(dynamoDbClient.putItem(any(PutItemRequest.class)))
                .thenThrow(DynamoDbException.builder().message("Access denied").build());


        assertThrows(DynamoDbException.class, () ->
                dynamoDbService.storeImageMetadata(
                        "user123", "image-key", "My Image", "https://example.com/image.jpg"));
    }


}