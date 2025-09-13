package com.amalitechphotoappcognitoauth.models;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The type Cognito event test.
 */
public class CognitoEventTest {

    /**
     * Test get user email.
     */
    @Test
    public void testGetUserEmail() {
        CognitoEvent event = new CognitoEvent();
        event.setRequest(new CognitoEvent.Request());
        event.getRequest().setUserAttributes(Map.of("email", "test@example.com"));
        assertEquals("test@example.com", event.getUserEmail());
    }

    /**
     * Test get user display name with name.
     */
    @Test
    public void testGetUserDisplayName_WithName() {
        CognitoEvent event = new CognitoEvent();
        event.setRequest(new CognitoEvent.Request());
        event.getRequest().setUserAttributes(Map.of("name", "Test User", "email", "test@example.com"));
        assertEquals("Test User", event.getUserDisplayName());
    }

    /**
     * Test get user display name without name.
     */
    @Test
    public void testGetUserDisplayName_WithoutName() {
        CognitoEvent event = new CognitoEvent();
        event.setRequest(new CognitoEvent.Request());
        event.getRequest().setUserAttributes(Map.of("email", "test@example.com"));
        assertEquals("test@example.com", event.getUserDisplayName());
    }

    /**
     * Test get user display name no attributes.
     */
    @Test
    public void testGetUserDisplayName_NoAttributes() {
        CognitoEvent event = new CognitoEvent();
        assertEquals("User", event.getUserDisplayName());
    }

    /**
     * Test get user email no attributes.
     */
    @Test
    public void testGetUserEmail_NoAttributes() {
        CognitoEvent event = new CognitoEvent();
        assertNull(event.getUserEmail());
    }
}