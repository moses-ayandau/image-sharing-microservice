package com.repository;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

public class S3RepositoryTest {

    @Mock
    private S3Client s3Client;

    private S3Repository s3Repository;
    private final String bucketName = "test-bucket";

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        s3Repository = new S3Repository(s3Client, bucketName);
    }

    @Test
    public void testConstructor() {
       try {
            S3Repository defaultRepo = new S3Repository();
            assertNotNull(defaultRepo);
        } catch (Exception e) {
            // Expected if environment variables are not set
        }

        // Test constructor with dependencies
        S3Repository repo = new S3Repository(s3Client, bucketName);
        assertNotNull(repo);
    }

    @Test
    public void testUploadFileWithMetadata() {
        // Setup
        String fileName = "test-image.jpg";
        byte[] fileData = "test image data".getBytes();
        String contentType = "image/jpeg";

        Map<String, String> metadata = new HashMap<>();
        metadata.put("name", "John Doe");
        metadata.put("email", "john@example.com");

        PutObjectResponse mockResponse = PutObjectResponse.builder().build();

        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(mockResponse);

        // Execute
        String result = s3Repository.uploadFile(fileName, fileData, contentType, metadata);

        // Verify
        assertNotNull(result);
        assertTrue(result.contains(bucketName));
        assertTrue(result.contains(fileName));
    }

    @Test
    public void testUploadFileWithoutMetadata() {
        // Setup
        String fileName = "test-image.jpg";
        byte[] fileData = "test image data".getBytes();
        String contentType = "image/jpeg";

        PutObjectResponse mockResponse = PutObjectResponse.builder().build();

        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(mockResponse);

        // Execute
        String result = s3Repository.uploadFile(fileName, fileData, contentType);

        // Verify
        assertNotNull(result);
        assertTrue(result.contains(bucketName));
        assertTrue(result.contains(fileName));
    }
}