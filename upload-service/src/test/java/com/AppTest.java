package upload;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import upload.service.ImageService;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class AppTest {

    @Mock
    private Context context;
    
    @Mock
    private LambdaLogger logger;
    
    @Mock
    private ImageService imageService;
    
    private App app;
    private ObjectMapper objectMapper = new ObjectMapper();
    
    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        when(context.getLogger()).thenReturn(logger);
        
        app = new App();
        app.setImageService(imageService); // Assuming you have a setter or can inject this
    }
    
    @Test
    public void testHandleOptions() {
        // Setup
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent()
                .withHttpMethod("OPTIONS");
        
        // Test
        APIGatewayProxyResponseEvent response = app.handleRequest(request, context);
        
        // Verify
        assertEquals(200, response.getStatusCode().intValue());
        assertNotNull(response.getHeaders());
        assertTrue(response.getHeaders().containsKey("Access-Control-Allow-Origin"));
        assertTrue(response.getHeaders().containsKey("Access-Control-Allow-Methods"));
        assertTrue(response.getHeaders().containsKey("Access-Control-Allow-Headers"));
    }
    
    @Test
    public void testHandleRequestWithValidInput() throws Exception {
        // Setup
        String validJwtToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiZW1haWwiOiJqb2huLmRvZUBleGFtcGxlLmNvbSIsImlhdCI6MTUxNjIzOTAyMn0.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c";
        
        // Sample base64 encoded small PNG image
        String base64Image = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==";
        
        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Bearer " + validJwtToken);
        
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("image", base64Image);
        requestBody.put("contentType", "image/png");
        requestBody.put("imageTitle", "Test Image");
        
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent()
                .withHttpMethod("POST")
                .withHeaders(headers)
                .withBody(objectMapper.writeValueAsString(requestBody));
        
        // Mock image service response
        Map<String, Object> serviceResponse = new HashMap<>();
        serviceResponse.put("url", "https://test-bucket.s3.amazonaws.com/uploads/test-file.png");
        serviceResponse.put("message", "Image uploaded successfully");
        serviceResponse.put("firstName", "John");
        serviceResponse.put("lastName", "Doe");
        
        when(imageService.processImageUpload(
                anyString(), 
                anyString(), 
                eq(base64Image), 
                eq("image/png"), 
                eq("Test Image")))
            .thenReturn(serviceResponse);
        
        // Test
        APIGatewayProxyResponseEvent response = app.handleRequest(request, context);
        
        // Verify
        assertEquals(200, response.getStatusCode().intValue());
        
        JsonNode responseBody = objectMapper.readTree(response.getBody());
        assertEquals("https://test-bucket.s3.amazonaws.com/uploads/test-file.png", responseBody.get("url").asText());
        assertEquals("Image uploaded successfully", responseBody.get("message").asText());
        assertEquals("John", responseBody.get("firstName").asText());
        assertEquals("Doe", responseBody.get("lastName").asText());
    }
    
    @Test
    public void testHandleRequestWithTokenInBody() throws Exception {
        // Setup
        String validJwtToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiZW1haWwiOiJqb2huLmRvZUBleGFtcGxlLmNvbSIsImlhdCI6MTUxNjIzOTAyMn0.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c";
        
        // Sample base64 encoded small PNG image
        String base64Image = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==";
        
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("token", validJwtToken);
        requestBody.put("image", base64Image);
        requestBody.put("contentType", "image/png");
        
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent()
                .withHttpMethod("POST")
                .withBody(objectMapper.writeValueAsString(requestBody));
        
        // Mock image service response
        Map<String, Object> serviceResponse = new HashMap<>();
        serviceResponse.put("url", "https://test-bucket.s3.amazonaws.com/uploads/test-file.png");
        serviceResponse.put("message", "Image uploaded successfully");
        
        when(imageService.processImageUpload(
                anyString(), 
                anyString(), 
                eq(base64Image), 
                eq("image/png"), 
                any()))
            .thenReturn(serviceResponse);
        
        // Test
        APIGatewayProxyResponseEvent response = app.handleRequest(request, context);
        
        // Verify
        assertEquals(200, response.getStatusCode().intValue());
    }
    
    @Test
    public void testHandleRequestWithMissingToken() throws Exception {
        // Setup
        String base64Image = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==";
        
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("image", base64Image);
        requestBody.put("contentType", "image/png");
        
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent()
                .withHttpMethod("POST")
                .withBody(objectMapper.writeValueAsString(requestBody));
        
        // Test
        APIGatewayProxyResponseEvent response = app.handleRequest(request, context);
        
        // Verify
        assertEquals(400, response.getStatusCode().intValue());
        
        JsonNode responseBody = objectMapper.readTree(response.getBody());
        assertTrue(responseBody.has("error"));
        assertTrue(responseBody.get("error").asText().contains("Authentication token is required"));
    }
    
    @Test
    public void testHandleRequestWithMissingImage() throws Exception {
        // Setup
        String validJwtToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiZW1haWwiOiJqb2huLmRvZUBleGFtcGxlLmNvbSIsImlhdCI6MTUxNjIzOTAyMn0.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c";
        
        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Bearer " + validJwtToken);
        
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("contentType", "image/png");
        
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent()
                .withHttpMethod("POST")
                .withHeaders(headers)
                .withBody(objectMapper.writeValueAsString(requestBody));
        
        // Test
        APIGatewayProxyResponseEvent response = app.handleRequest(request, context);
        
        // Verify
        assertEquals(400, response.getStatusCode().intValue());
        
        JsonNode responseBody = objectMapper.readTree(response.getBody());
        assertTrue(responseBody.has("error"));
        assertTrue(responseBody.get("error").asText().contains("Image data is required"));
    }
}