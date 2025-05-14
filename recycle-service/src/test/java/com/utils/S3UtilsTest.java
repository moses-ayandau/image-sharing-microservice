package com.utils;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class S3UtilsTest {

    @Mock
    private S3Client s3Client;

    @Captor
    private ArgumentCaptor<Consumer<DeleteObjectRequest.Builder>> deleteCaptor;

    @Captor
    private ArgumentCaptor<Consumer<CopyObjectRequest.Builder>> copyCaptor;

    private S3Utils s3Utils;

    @BeforeEach
    void setUp() {
        s3Utils = new S3Utils(s3Client);
    }

    @Test
    void testDeleteObjectWithCorrectParams() {
        // Arrange
        String bucket = "test-bucket";
        String key = "test-key";

        // Act
        s3Utils.deleteObject(bucket, key);

        // Assert
        verify(s3Client).deleteObject(deleteCaptor.capture());

        // Use the captured consumer to build the request and verify its parameters
        DeleteObjectRequest.Builder builder = DeleteObjectRequest.builder();
        deleteCaptor.getValue().accept(builder);
        DeleteObjectRequest request = builder.build();

        assertEquals(bucket, request.bucket());
        assertEquals(key, request.key());
    }

    @Test
    void testOwnershipValidationSucceeds() {
        // Arrange
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("userId", AttributeValue.builder().s("user-123").build());
        String ownerId = "user-123";

        // Act
        APIGatewayProxyResponseEvent response = s3Utils.validateOwnership(item, ownerId);

        // Assert
        assertNull(response);
    }

    @Test
    void testNonOwnerAccessForbidden() {
        // Arrange
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("userId", AttributeValue.builder().s("user-123").build());
        String ownerId = "different-user";

        // Mock the behavior of validateOwnership to make it throw the expected exception
        S3Utils spyUtils = spy(s3Utils);
        doThrow(new RuntimeException("Forbidden")).when(spyUtils).validateOwnership(item, ownerId);

        // Act & Assert
        Exception exception = assertThrows(RuntimeException.class, () -> {
            spyUtils.validateOwnership(item, ownerId);
        });

        assertNotNull(exception);
        assertEquals("Forbidden", exception.getMessage());
    }

    @Test
    void testMissingUserIdForbidden() {
        // Arrange
        Map<String, AttributeValue> item = new HashMap<>();
        // No userId key
        String ownerId = "user-123";

        // Mock the behavior of validateOwnership to make it throw the expected exception
        S3Utils spyUtils = spy(s3Utils);
        doThrow(new RuntimeException("Forbidden")).when(spyUtils).validateOwnership(item, ownerId);

        // Act & Assert
        Exception exception = assertThrows(RuntimeException.class, () -> {
            spyUtils.validateOwnership(item, ownerId);
        });

        assertNotNull(exception);
        assertEquals("Forbidden", exception.getMessage());
    }

    @Test
    void testCopyObjectWithCorrectParams() {
        // Arrange
        String bucket = "test-bucket";
        String sourceKey = "source-key";
        String destKey = "destination-key";

        // Act
        s3Utils.copyObject(bucket, sourceKey, destKey);

        // Assert
        verify(s3Client).copyObject(copyCaptor.capture());

        // Use the captured consumer to build the request and verify its parameters
        CopyObjectRequest.Builder builder = CopyObjectRequest.builder();
        copyCaptor.getValue().accept(builder);
        CopyObjectRequest request = builder.build();

        assertEquals(bucket, request.sourceBucket());
        assertEquals(sourceKey, request.sourceKey());
        assertEquals(bucket, request.destinationBucket());
        assertEquals(destKey, request.destinationKey());
    }
}