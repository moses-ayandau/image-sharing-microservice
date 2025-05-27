package com.amalitechphotoappcognitoauth.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The type Email templates test.
 */
public class EmailTemplatesTest {

    /**
     * Test get welcome email template.
     */
    @Test
    public void testGetWelcomeEmailTemplate() {
        String html = EmailTemplates.getWelcomeEmailTemplate("Test User");
        assertTrue(html.contains("Welcome to Photo Blog App, Test User!"));
        assertTrue(html.contains("href=\"https://mscv2group2.link/auth\""));
    }

    /**
     * Test get sign in alert template.
     */
    @Test
    public void testGetSignInAlertTemplate() {
        String html = EmailTemplates.getSignInAlertTemplate("Test User", "Test Device", "May 16, 2025 at 9:41 AM UTC");
        assertTrue(html.contains("Hello Test User,"));
        assertFalse(html.contains("Device: Unknown Device"));
    }
}