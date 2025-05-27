package com.process.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.SendEmailRequest;
import software.amazon.awssdk.services.ses.model.SendEmailResponse;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock
    private SesClient sesClient;

    private EmailService emailService;

    @BeforeEach
    void setUp() throws Exception {

        emailService = new EmailService("us-east-1");


        Field clientField = EmailService.class.getDeclaredField("sesClient");
        clientField.setAccessible(true);
        clientField.set(emailService, sesClient);
    }

    @Test
    void testSendProcessingStartEmail() {

        when(sesClient.sendEmail(any(SendEmailRequest.class)))
                .thenReturn(SendEmailResponse.builder().build());


        assertDoesNotThrow(() ->
                emailService.sendProcessingStartEmail("test@example.com", "John"));


        verify(sesClient).sendEmail(any(SendEmailRequest.class));
    }

    @Test
    void testSendProcessingCompleteEmail() {

        when(sesClient.sendEmail(any(SendEmailRequest.class)))
                .thenReturn(SendEmailResponse.builder().build());


        assertDoesNotThrow(() ->
                emailService.sendProcessingCompleteEmail("test@example.com", "Jane", "image123.jpg"));


        verify(sesClient).sendEmail(any(SendEmailRequest.class));
    }

    @Test
    void testSendProcessingFailureEmail() {

        when(sesClient.sendEmail(any(SendEmailRequest.class)))
                .thenReturn(SendEmailResponse.builder().build());


        assertDoesNotThrow(() ->
                emailService.sendProcessingFailureEmail("test@example.com", "Bob"));


        verify(sesClient).sendEmail(any(SendEmailRequest.class));
    }

    @Test
    void testEmailService_WithNullParameters() {

        when(sesClient.sendEmail(any(SendEmailRequest.class)))
                .thenReturn(SendEmailResponse.builder().build());


        assertDoesNotThrow(() ->
                emailService.sendProcessingStartEmail("test@example.com", null));

        verify(sesClient).sendEmail(any(SendEmailRequest.class));
    }

    @Test
    void testEmailService_WithEmptyParameters() {

        when(sesClient.sendEmail(any(SendEmailRequest.class)))
                .thenReturn(SendEmailResponse.builder().build());


        assertDoesNotThrow(() ->
                emailService.sendProcessingCompleteEmail("", "", ""));

        verify(sesClient).sendEmail(any(SendEmailRequest.class));
    }
}