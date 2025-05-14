package com.service;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import com.repository.S3Repository;

import java.util.Map;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

public class ImageServiceTest {
    private ImageService imageService;
    private S3Repository mockRepository;
    private String mockUrl = "https://bucket.s3.amazonaws.com/uploads/testuser-uuid.png";

    @Before
    public void setUp() {
        // Create a mock S3Repository
        mockRepository = Mockito.mock(S3Repository.class);
        
        // Set up the mock to return our test URL
        when(mockRepository.uploadFile(anyString(), any(byte[].class), anyString()))
            .thenReturn(mockUrl);
            
        imageService = new ImageService(mockRepository);
    }

    @Test
    public void testProcessImageUpload() throws Exception {

        String base64Image = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==";
        String username = "testuser";
        String contentType = "image/png";


        Map<String, Object> response = imageService.processImageUpload(username, base64Image, contentType);


        assertNotNull(response);
        assertEquals(mockUrl, response.get("url"));
        assertEquals("Image uploaded Successfully", response.get("message"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testProcessImageUpload_UnsupportedFileType() throws Exception {
        imageService.processImageUpload("testuser", "SGVsbG8=", "text/plain");
    }
}