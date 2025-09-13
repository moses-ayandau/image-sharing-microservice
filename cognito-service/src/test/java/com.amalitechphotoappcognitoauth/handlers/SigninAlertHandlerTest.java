package com.amalitechphotoappcognitoauth.handlers;

import com.amalitechphotoappcognitoauth.models.EmailRequest;
import com.amalitechphotoappcognitoauth.services.EmailService;
import com.amazonaws.services.lambda.runtime.Context;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * The type Signin alert handler test.
 */
@ExtendWith(MockitoExtension.class)
public class SigninAlertHandlerTest {

    @Mock
    private EmailService emailService;

    @Mock
    private Context context;

    private SigninAlertHandler handler;

    /**
     * Sets up.
     */
    @BeforeEach
    public void setUp() {
        handler = new SigninAlertHandler(emailService);
    }

    /**
     * Test handle request sign in trigger sends email.
     */
    @Test
    public void testHandleRequest_SignInTrigger_SendsEmail() {
        // Arrange
        Map<String, Object> event = new HashMap<>();
        event.put("triggerSource", "PostAuthentication_Authentication");
        Map<String, Object> userAttributes = new HashMap<>();
        userAttributes.put("email", "test@example.com");
        userAttributes.put("name", "Test User");
        Map<String, Object> request = new HashMap<>();
        request.put("userAttributes", userAttributes);
        event.put("request", request);
        Map<String, Object> callerContext = new HashMap<>();
        callerContext.put("deviceName", "Test Device");
        callerContext.put("deviceVersion", "1.0");
        event.put("callerContext", callerContext);
        when(emailService.sendEmail(any(EmailRequest.class))).thenReturn(true);

        // Act
        Object result = handler.handleRequest(event, context);

        // Assert
        verify(emailService, times(1)).sendEmail(any(EmailRequest.class));
        ArgumentCaptor<EmailRequest> captor = ArgumentCaptor.forClass(EmailRequest.class);
        verify(emailService).sendEmail(captor.capture());
        EmailRequest captured = captor.getValue();
        assertEquals("test@example.com", captured.getRecipient());
        assertEquals("New Sign-in Detected - Photo Blog App", captured.getSubject());
        String htmlBody = captured.getHtmlBody();
        assertTrue(htmlBody.contains("Hello Test User,"));
        assertFalse(htmlBody.contains("Device: Unknown Device,"));
        assertTrue(htmlBody.contains("<p><strong>Time:</strong>"));
        assertSame(event, result);
    }

    /**
     * Test handle request not sign in trigger does not send email.
     */
    @Test
    public void testHandleRequest_NotSignInTrigger_DoesNotSendEmail() {
        // Arrange
        Map<String, Object> event = new HashMap<>();
        event.put("triggerSource", "OtherTrigger");

        // Act
        Object result = handler.handleRequest(event, context);

        // Assert
        verify(emailService, never()).sendEmail(any());
        assertSame(event, result);
    }

    /**
     * Test handle request missing email does not send email.
     */
    @Test
    public void testHandleRequest_MissingEmail_DoesNotSendEmail() {
        // Arrange
        Map<String, Object> event = new HashMap<>();
        event.put("triggerSource", "PostAuthentication_Authentication");
        Map<String, Object> userAttributes = new HashMap<>();
        userAttributes.put("name", "Test User");
        Map<String, Object> request = new HashMap<>();
        request.put("userAttributes", userAttributes);
        event.put("request", request);

        // Act
        Object result = handler.handleRequest(event, context);

        // Assert
        verify(emailService, never()).sendEmail(any());
        assertSame(event, result);
    }
}