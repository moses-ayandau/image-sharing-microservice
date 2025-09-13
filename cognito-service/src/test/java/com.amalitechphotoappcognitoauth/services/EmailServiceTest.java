package com.amalitechphotoappcognitoauth.services;

import com.amalitechphotoappcognitoauth.models.EmailRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.SendEmailRequest;
import software.amazon.awssdk.services.ses.model.SendEmailResponse;
import software.amazon.awssdk.services.ses.model.SesException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * The type Email service test.
 */
public class EmailServiceTest {

    @Mock
    private SesClient mockSesClient;

    private EmailService emailService;
    private SesClient originalSesClient;

    /**
     * Sets up.
     */
    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        // Save the original SesClient
        originalSesClient = EmailService.sesClient;
        // Replace with mock for testing
        EmailService.setSesClientForTesting(mockSesClient);
        emailService = new EmailService();
        System.setProperty("AWS_REGION", "eu-west-1");
        System.setProperty("EMAIL_SOURCE", "test@example.com");
    }

    /**
     * Tear down.
     */
    @AfterEach
    public void tearDown() {
        EmailService.setSesClientForTesting(originalSesClient);
    }

    /**
     * Test send email success.
     */
    @Test
    public void testSendEmail_Success() {
        // Arrange
        EmailRequest request = new EmailRequest("rose.tetteh@amalitech.com", "Test Subject", "<p>Test Body</p>");
        SendEmailResponse response = SendEmailResponse.builder().messageId("test-message-id").build();
        when(mockSesClient.sendEmail(any(SendEmailRequest.class))).thenReturn(response);

        // Act
        boolean result = emailService.sendEmail(request);

        // Assert
        assertTrue(result);
        verify(mockSesClient, times(1)).sendEmail(any(SendEmailRequest.class));
    }

    /**
     * Test send email failure.
     */
    @Test
    public void testSendEmail_Failure() {
        // Arrange
        EmailRequest request = new EmailRequest("recipient@example.com", "Test Subject", "<p>Test Body</p>");
        when(mockSesClient.sendEmail(any(SendEmailRequest.class))).thenThrow(SesException.class);

        // Act
        boolean result = emailService.sendEmail(request);

        // Assert
        assertFalse(result);
        verify(mockSesClient, times(1)).sendEmail(any(SendEmailRequest.class));
    }

//    /**
//     * Test close ses client closed.
//     */
//    @Test
//    public void testClose_SesClientClosed() {
//        emailService.close();
//
//        verifyNoMoreInteractions(mockSesClient, originalSesClient);
//    }

}