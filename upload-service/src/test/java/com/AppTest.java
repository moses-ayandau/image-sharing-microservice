package com;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.service.ImageService;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class AppTest {

    private App app;

    @Mock
    private Context context;

    private ObjectMapper objectMapper = new ObjectMapper();

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        
        // Create mock ImageService
        ImageService mockImageService = mock(ImageService.class);
        Map<String, Object> mockResponse = new HashMap<>();
        mockResponse.put("url", "https://test-bucket.s3.amazonaws.com/test-file.png");
        mockResponse.put("message", "Image uploaded Successfully");
        
        try {
            when(mockImageService.processImageUpload(eq("testuser"), anyString(), anyString()))
                .thenReturn(mockResponse);
        } catch (Exception e) {
            fail("Mock setup failed");
        }
        
        // Create App instance
        app = new App();
        
        // Use reflection to replace the imageService field
        Field imageServiceField = App.class.getDeclaredField("imageService");
        imageServiceField.setAccessible(true);
        imageServiceField.set(app, mockImageService);
    }

    @Test
    public void testHandleRequest_Success() throws Exception {
        // Create a sample JWT token with username
        String jwtPayload = "{\"username\":\"testuser\"}";
        String encodedPayload = Base64.getUrlEncoder().encodeToString(jwtPayload.getBytes());
        String jwtToken = "header." + encodedPayload + ".signature";
        
        // Create request with Authorization header
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent();
        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Bearer " + jwtToken);
        request.setHeaders(headers);
        request.setHttpMethod("POST");
        
        // Create request body
        Map<String, String> requestBody = new HashMap<>();
        requestBody.put("image", "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");
        requestBody.put("contentType", "image/png");
        request.setBody(objectMapper.writeValueAsString(requestBody));
        
        // Execute
        APIGatewayProxyResponseEvent response = app.handleRequest(request, context);
        
        // Verify
        assertEquals(200, response.getStatusCode().intValue());
        assertTrue(response.getBody().contains("url"));
    }

    @Test
    public void testHandleOptions() {
        // Create OPTIONS request
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent();
        request.setHttpMethod("OPTIONS");
        
        // Execute
        APIGatewayProxyResponseEvent response = app.handleRequest(request, context);
        
        // Verify
        assertEquals(200, response.getStatusCode().intValue());
        assertNotNull(response.getHeaders());
        assertTrue(response.getHeaders().containsKey("Access-Control-Allow-Origin"));
    }
}
