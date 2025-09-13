
package com.service;

import com.repository.S3Repository;
import com.repository.SqsRepository;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

public class ImageServiceTest {

    @Mock
    private S3Repository s3Repository;

    @Mock
    private SqsRepository sqsRepository;

    private ImageService imageService;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        imageService = new ImageService(s3Repository, sqsRepository);
    }

    @Test
    public void testConstructor() {
        // Test default constructor
        ImageService defaultService = new ImageService();
        assertNotNull(defaultService);
        
        // Test constructor with dependencies
        ImageService service = new ImageService(s3Repository, sqsRepository);
        assertNotNull(service);
    }
    
    @Test
    public void testProcessImageUploadWithValidData() throws Exception {
        // Setup
        String name = "John Doe";
        String email = "john@example.com";
        String imageBase64 = "SGVsbG8gV29ybGQ="; // "Hello World" in base64
        String contentType = "image/jpeg";
        String imageTitle = "Test Image";
        String userId = "user123";
        
        // Mock S3 upload
        when(s3Repository.uploadFile(anyString(), any(byte[].class), anyString(), any(Map.class)))
                .thenReturn("https://example.com/test-image.jpg");
        
        // Mock SQS send
        Map<String, Object> sqsResponse = new HashMap<>();
        sqsResponse.put("success", true);
        sqsResponse.put("messageId", "msg123");
        when(sqsRepository.sendMessage(any(Map.class), any())).thenReturn(sqsResponse);
        
        // Execute
        Map<String, Object> result = imageService.processImageUpload(name, email, imageBase64, contentType, imageTitle, userId, null);
        
        // Verify
        assertNotNull(result);
        assertTrue(result.containsKey("url"));
        assertTrue(result.containsKey("message"));
        assertEquals("John", result.get("firstName"));
        assertEquals("Doe", result.get("lastName"));
    }
}
