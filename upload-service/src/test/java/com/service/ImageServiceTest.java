package com.service;

import org.junit.Before;
import org.junit.Test;
import upload.model.ImageUploadRequest;
import upload.model.ImageUploadResponse;
import upload.repository.S3Repository;
import upload.repository.SqsRepository;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ImageServiceTest {
    
    private ImageService imageService;
    private S3Repository mockS3Repository;
    private SqsRepository mockSqsRepository;
    
    @Before
    public void setUp() {
        mockS3Repository = mock(S3Repository.class);
        mockSqsRepository = mock(SqsRepository.class);
        imageService = new ImageService(mockS3Repository, mockSqsRepository);
    }
    
    @Test
    public void testProcessImageUpload_Success() throws Exception {
        // Sample base64 encoded small PNG image
        String base64Image = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==";
        String name = "John Doe";
        String email = "john.doe@example.com";
        String contentType = "image/png";
        String imageTitle = "Test Image";
        
        // Mock S3 repository
        when(mockS3Repository.uploadFile(anyString(), any(byte[].class), anyString(), any(Map.class)))
            .thenReturn("https://test-bucket.s3.amazonaws.com/uploads/test-file.png");
        
        // Test
        Map<String, Object> result = imageService.processImageUpload(name, email, base64Image, contentType, imageTitle);
        
        // Verify
        assertNotNull(result);
        assertEquals("https://test-bucket.s3.amazonaws.com/uploads/test-file.png", result.get("url"));
        assertEquals("Image uploaded successfully", result.get("message"));
        assertEquals("John Doe", result.get("name"));
        assertEquals("John", result.get("firstName"));
        assertEquals("Doe", result.get("lastName"));
        assertEquals("john.doe@example.com", result.get("email"));
        assertEquals("Test Image", result.get("imageTitle"));
        
        // Verify SQS message attributes
        verify(mockSqsRepository).sendMessage(any(Map.class));
    }
    
    @Test
    public void testProcessImageUpload_SingleName() throws Exception {
        // Sample base64 encoded small PNG image
        String base64Image = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==";
        String name = "John";
        String email = "john@example.com";
        String contentType = "image/png";
        
        // Mock S3 repository
        when(mockS3Repository.uploadFile(anyString(), any(byte[].class), anyString(), any(Map.class)))
            .thenReturn("https://test-bucket.s3.amazonaws.com/uploads/test-file.png");
        
        // Test
        Map<String, Object> result = imageService.processImageUpload(name, email, base64Image, contentType);
        
        // Verify
        assertNotNull(result);
        assertEquals("John", result.get("firstName"));
        assertEquals("", result.get("lastName"));
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void testProcessImageUpload_UnsupportedFileType() throws Exception {
        // Create request with unsupported content type
        String base64Image = "SGVsbG8gV29ybGQ="; // "Hello World" in base64
        String name = "John Doe";
        String email = "john.doe@example.com";
        String contentType = "text/plain";
        
        // This should throw IllegalArgumentException
        imageService.processImageUpload(name, email, base64Image, contentType);
    }
    
    @Test
    public void testProcessImageUpload_NullName() throws Exception {
        // Sample base64 encoded small PNG image
        String base64Image = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==";
        String email = "anonymous@example.com";
        String contentType = "image/png";
        
        // Mock S3 repository
        when(mockS3Repository.uploadFile(anyString(), any(byte[].class), anyString(), any(Map.class)))
            .thenReturn("https://test-bucket.s3.amazonaws.com/uploads/test-file.png");
        
        // Test
        Map<String, Object> result = imageService.processImageUpload(null, email, base64Image, contentType);
        
        // Verify default name is used
        assertEquals("unknown-user", result.get("name"));
        assertEquals("unknown-user", result.get("firstName"));
        assertEquals("", result.get("lastName"));
    }
}