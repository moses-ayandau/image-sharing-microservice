package com;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class AppTest {
    
    private App app;
    
    @Mock
    private Context context;
    
    private ObjectMapper objectMapper = new ObjectMapper();
    
    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        app = new App();
    }
    
    @Test
    public void testHandleRequest_Success() throws Exception {
        // Create a sample JWT token with username
        String jwtPayload = "{\"username\":\"testuser\",\"exp\":1234567890}";
        String encodedPayload = Base64.getUrlEncoder().encodeToString(jwtPayload.getBytes());
        String jwtToken = "header." + encodedPayload + ".signature";
        
        // Create request with Authorization header
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent();
        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Bearer " + jwtToken);
        request.setHeaders(headers);
        
        // Set HTTP method
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
        JsonNode responseBody = objectMapper.readTree(response.getBody());
        assertTrue(responseBody.has("url"));
        assertTrue(responseBody.has("message"));
    }
    
    @Test
    public void testHandleRequest_MissingToken() {
        // Create request without Authorization header
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent();
        request.setHeaders(new HashMap<>());
        request.setHttpMethod("POST");
        
        // Create request body
        Map<String, String> requestBody = new HashMap<>();
        requestBody.put("image", "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");
        requestBody.put("contentType", "image/png");
        try {
            request.setBody(objectMapper.writeValueAsString(requestBody));
        } catch (Exception e) {
            fail("Failed to create request body");
        }
        
        // Execute
        APIGatewayProxyResponseEvent response = app.handleRequest(request, context);
        
        // Verify
        assertEquals(400, response.getStatusCode().intValue());
        assertTrue(response.getBody().contains("Missing or invalid Authorization token"));
    }
    
    @Test
    public void testHandleRequest_InvalidToken() {
        // Create request with invalid JWT token
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent();
        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Bearer invalid-token");
        request.setHeaders(headers);
        request.setHttpMethod("POST");
        
        // Create request body
        Map<String, String> requestBody = new HashMap<>();
        requestBody.put("image", "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");
        requestBody.put("contentType", "image/png");
        try {
            request.setBody(objectMapper.writeValueAsString(requestBody));
        } catch (Exception e) {
            fail("Failed to create request body");
        }
        
        // Execute
        APIGatewayProxyResponseEvent response = app.handleRequest(request, context);
        
        // Verify
        assertEquals(400, response.getStatusCode().intValue());
        assertTrue(response.getBody().contains("Invalid JWT token format"));
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
        Map<String, String> headers = response.getHeaders();
        assertNotNull(headers);
        assertTrue(headers.containsKey("Access-Control-Allow-Origin"));
        assertTrue(headers.containsKey("Access-Control-Allow-Methods"));
        assertTrue(headers.containsKey("Access-Control-Allow-Headers"));
    }
}
