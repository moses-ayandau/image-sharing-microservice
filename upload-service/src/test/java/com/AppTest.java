package com;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.service.ImageService;
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

public class AppTest {

    @Mock
    private ImageService imageService;

    @Mock
    private Context context;
    
    @Mock
    private LambdaLogger lambdaLogger;

    private App app;
    private ObjectMapper objectMapper = new ObjectMapper();

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        // Mock the logger
        when(context.getLogger()).thenReturn(lambdaLogger);
        
        app = new App();
        app.setImageService(imageService);
    }

    @Test
    public void testHandleRequestWithValidInput() throws Exception {
        // Setup
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent();
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("Authorization", "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJ1c2VyMTIzIiwibmFtZSI6IkpvaG4gRG9lIiwiZW1haWwiOiJqb2huQGV4YW1wbGUuY29tIn0.kD3KXUhjYYC-Uw4IiLzZGGvkVJXXT5_UwkrS4IjCz0I");
        request.setHeaders(headers);
        request.setHttpMethod("POST");
        
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("image", "SGVsbG8gV29ybGQ="); // "Hello World" in base64
        requestBody.put("contentType", "image/jpeg");
        requestBody.put("imageTitle", "Test Image");
        
        request.setBody(objectMapper.writeValueAsString(requestBody));
        
        // Mock ImageService response
        Map<String, Object> serviceResponse = new HashMap<>();
        serviceResponse.put("url", "https://example.com/test-image.jpg");
        serviceResponse.put("message", "Image uploaded successfully");
        serviceResponse.put("name", "John Doe");
        serviceResponse.put("firstName", "John");
        serviceResponse.put("lastName", "Doe");
        serviceResponse.put("email", "john@example.com");
        
        when(imageService.processImageUpload(anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), any()))
                .thenReturn(serviceResponse);
        
        // Execute
        APIGatewayProxyResponseEvent response = app.handleRequest(request, context);
        
        // Verify
        assertNotNull(response);
        assertEquals(200, response.getStatusCode().intValue());
        assertTrue(response.getBody().contains("Image uploaded successfully"));
    }

    @Test
    public void testHandleRequestWithInvalidInput() {
        // Setup - empty request
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent();
        request.setHttpMethod("POST");
        
        // Execute
        APIGatewayProxyResponseEvent response = app.handleRequest(request, context);
        
        // Verify
        assertNotNull(response);
        assertEquals(400, response.getStatusCode().intValue());
        assertTrue(response.getBody().contains("error"));
    }
}