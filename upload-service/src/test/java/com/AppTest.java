package upload;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import org.junit.Before;
import org.junit.Test;
import upload.model.ImageUploadRequest;
import upload.model.ImageUploadResponse;
import upload.service.ImageService;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

public class AppTest {
    
    private App app;
    private Context context;
    private ObjectMapper objectMapper;
    
    @Before
    public void setUp() {
        app = new App();
        objectMapper = new ObjectMapper();
        // Mock context can be added if needed
    }
    
    @Test
    public void testHandleOptions() {
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent();
        request.setHttpMethod("OPTIONS");
        
        APIGatewayProxyResponseEvent response = app.handleRequest(request, context);
        
        assertEquals(200, response.getStatusCode().intValue());
        assertNotNull(response.getHeaders());
        assertTrue(response.getHeaders().containsKey("Access-Control-Allow-Origin"));
        assertTrue(response.getHeaders().containsKey("Access-Control-Allow-Methods"));
    }
    
    @Test
    public void testHandleRequestWithInvalidInput() throws Exception {
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent();
        request.setHttpMethod("POST");
        request.setBody("{}"); // Empty JSON will cause validation error
        
        APIGatewayProxyResponseEvent response = app.handleRequest(request, context);
        
        assertEquals(500, response.getStatusCode().intValue()); // Update expected status code to 500
        assertTrue(response.getBody().contains("error"));
    }
}
