package com.process.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.IOException;
import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class S3ServiceTest {

    @Mock
    private S3Client s3Client;

    private S3Service s3Service;

    @BeforeEach
    void setUp() throws Exception {

        s3Service = new S3Service("us-east-1", "processed-bucket");


        Field clientField = S3Service.class.getDeclaredField("s3Client");
        clientField.setAccessible(true);
        clientField.set(s3Service, s3Client);
    }

    @Test
    void testObjectExists_WhenObjectExists() {

        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenReturn(HeadObjectResponse.builder().build());


        boolean exists = s3Service.objectExists("test-bucket", "test-key");


        assertTrue(exists);
        verify(s3Client).headObject(any(HeadObjectRequest.class));
    }

    @Test
    void testObjectExists_WhenObjectDoesNotExist() {

        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenThrow(S3Exception.builder().message("Not found").build());


        boolean exists = s3Service.objectExists("test-bucket", "test-key");


        assertFalse(exists);
    }


    @Test
    void testGetImageFromS3_ThrowsException() {

        when(s3Client.getObject(any(GetObjectRequest.class), any(ResponseTransformer.class)))
                .thenThrow(S3Exception.builder().message("Access denied").build());


        assertThrows(IOException.class, () ->
                s3Service.getImageFromS3("test-bucket", "test-key"));
    }

    @Test
    void testUploadToProcessedBucket_Success() throws IOException {

        byte[] imageData = "processed-image-data".getBytes();
        when(s3Client.putObject(any(PutObjectRequest.class), any(software.amazon.awssdk.core.sync.RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());


        assertDoesNotThrow(() -> s3Service.uploadToProcessedBucket(imageData, "test-key"));


        verify(s3Client).putObject(any(PutObjectRequest.class), any(software.amazon.awssdk.core.sync.RequestBody.class));
    }

    @Test
    void testDeleteFromStagingBucket_Success() throws IOException {

        when(s3Client.deleteObject(any(DeleteObjectRequest.class)))
                .thenReturn(DeleteObjectResponse.builder().build());


        assertDoesNotThrow(() -> s3Service.deleteFromStagingBucket("staging-bucket", "test-key"));


        verify(s3Client).deleteObject(any(DeleteObjectRequest.class));
    }
}