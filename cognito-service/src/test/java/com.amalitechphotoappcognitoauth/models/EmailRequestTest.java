package com.amalitechphotoappcognitoauth.models;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The type Email request test.
 */
public class EmailRequestTest {

    /**
     * Test email request.
     */
    @Test
    public void testEmailRequest() {
        EmailRequest request = new EmailRequest("test@example.com", "Subject", "<p>Body</p>");
        assertEquals("test@example.com", request.getRecipient());
        assertEquals("Subject", request.getSubject());
        assertEquals("<p>Body</p>", request.getHtmlBody());
    }

    /**
     * Test email request null values.
     */
    @Test
    public void testEmailRequest_NullValues() {
        EmailRequest request = new EmailRequest(null, null, null);
        assertNull(request.getRecipient());
        assertNull(request.getSubject());
        assertNull(request.getHtmlBody());
    }
}