package com.utils;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ResponseUtilsTest {

    @Test
    public void testCreateResponse() {
        // Act
        APIGatewayProxyResponseEvent response = ResponseUtils.createResponse();

        // Assert
        assertNotNull(response);

        Map<String, String> headers = response.getHeaders();
        assertNotNull(headers);
        assertEquals(6, headers.size());
        assertEquals("application/json", headers.get("Content-Type"));
        assertEquals("application/json", headers.get("X-Custom-Header"));
        assertEquals("*", headers.get("Access-Control-Allow-Origin"));
        assertEquals("OPTIONS,POST,GET, PUT, DELETE, PATCH", headers.get("Access-Control-Allow-Methods"));
        assertEquals("Content-Type,Authorization,X-Amz-Date,X-Api-Key,X-Amz-Security-Token",
                headers.get("Access-Control-Allow-Headers"));
        assertEquals("true", headers.get("Access-Control-Allow-Credentials"));
    }

    @Test
    public void testSuccessResponse() throws JsonProcessingException {
        // Arrange
        int statusCode = 200;
        Map<String, String> data = new HashMap<>();
        data.put("key", "value");

        // Act
        APIGatewayProxyResponseEvent response = ResponseUtils.successResponse(statusCode, data);

        // Assert
        assertNotNull(response);
        assertEquals(statusCode, response.getStatusCode());
        assertEquals("{\"key\":\"value\"}", response.getBody());
        assertNotNull(response.getHeaders());
    }

    @Test
    public void testSuccessResponseWithNullData() throws JsonProcessingException {
        // Arrange
        int statusCode = 204;

        // Act
        APIGatewayProxyResponseEvent response = ResponseUtils.successResponse(statusCode, null);

        // Assert
        assertNotNull(response);
        assertEquals(statusCode, response.getStatusCode());
        assertEquals("null", response.getBody());
        assertNotNull(response.getHeaders());
    }

    @Test
    public void testSuccessResponseWithComplexObject() throws JsonProcessingException {
        // Arrange
        int statusCode = 200;
        TestObject testObject = new TestObject("test", 123);

        // Act
        APIGatewayProxyResponseEvent response = ResponseUtils.successResponse(statusCode, testObject);

        // Assert
        assertNotNull(response);
        assertEquals(statusCode, response.getStatusCode());
        assertEquals("{\"name\":\"test\",\"value\":123}", response.getBody());
        assertNotNull(response.getHeaders());
    }

    @Test
    public void testErrorResponse() {
        // Arrange
        int statusCode = 400;
        String errorMessage = "Bad Request";

        // Act
        APIGatewayProxyResponseEvent response = ResponseUtils.errorResponse(statusCode, errorMessage);

        // Assert
        assertNotNull(response);
        assertEquals(statusCode, response.getStatusCode());
        assertEquals("{\"message\":\"Bad Request\"}", response.getBody());
        assertNotNull(response.getHeaders());
    }


    // Helper class for testing complex object serialization
    private static class TestObject {
        private String name;
        private int value;

        public TestObject(String name, int value) {
            this.name = name;
            this.value = value;
        }

        public String getName() {
            return name;
        }

        public int getValue() {
            return value;
        }
    }
}