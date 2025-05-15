package com.repository;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class S3RepositoryTest {

    @Mock
    private S3Client s3Client;

    private S3Repository s3Repository;
    private final String BUCKET_NAME = "test-bucket";

    @Before
    public void setUp() {
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());
        s3Repository = new S3Repository(s3Client, BUCKET_NAME);
    }

    @Test
    public void testUploadFile() {
        String fileName = "test-file.jpg";
        byte[] fileData = "test data".getBytes();
        String contentType = "image/jpeg";

        String result = s3Repository.uploadFile(fileName, fileData, contentType);

        String expectedUrl = "https://test-bucket.s3.amazonaws.com/test-file.jpg";
        assertEquals(expectedUrl, result);
        
        // Verify S3 client was called
        verify(s3Client).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }
}