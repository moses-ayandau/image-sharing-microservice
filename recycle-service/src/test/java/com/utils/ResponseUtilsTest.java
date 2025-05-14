package com.utils;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;


@ExtendWith(MockitoExtension.class)
public class ResponseUtilsTest {

    @Test
    public void testCreateResponse() {
        APIGatewayProxyResponseEvent response = ResponseUtils.createResponse();

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
        int statusCode = 200;
        Map<String, String> data = new HashMap<>();
        data.put("key", "value");

        APIGatewayProxyResponseEvent response = ResponseUtils.successResponse(statusCode, data);

        assertNotNull(response);
        assertEquals(statusCode, response.getStatusCode());
        assertEquals("{\"key\":\"value\"}", response.getBody());
        assertNotNull(response.getHeaders());
    }

    @Test
    public void testSuccessResponseWithNullData() throws JsonProcessingException {
        int statusCode = 204;

        APIGatewayProxyResponseEvent response = ResponseUtils.successResponse(statusCode, null);

        assertNotNull(response);
        assertEquals(statusCode, response.getStatusCode());
        assertEquals("null", response.getBody());
        assertNotNull(response.getHeaders());
    }

    @Test
    public void testSuccessResponseWithComplexObject() throws JsonProcessingException {
        int statusCode = 200;
        TestObject testObject = new TestObject("test", 123);

        APIGatewayProxyResponseEvent response = ResponseUtils.successResponse(statusCode, testObject);

        assertNotNull(response);
        assertEquals(statusCode, response.getStatusCode());
        assertEquals("{\"name\":\"test\",\"value\":123}", response.getBody());
        assertNotNull(response.getHeaders());
    }

    @Test
    public void testErrorResponse() {
        int statusCode = 400;
        String errorMessage = "Bad Request";

        APIGatewayProxyResponseEvent response = ResponseUtils.errorResponse(statusCode, errorMessage);

        assertNotNull(response);
        assertEquals(statusCode, response.getStatusCode());
        assertEquals("{\"message\":\"Bad Request\"}", response.getBody());
        assertNotNull(response.getHeaders());
    }

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