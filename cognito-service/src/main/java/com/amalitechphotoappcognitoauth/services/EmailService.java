package com.amalitechphotoappcognitoauth.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.Body;
import software.amazon.awssdk.services.ses.model.Content;
import software.amazon.awssdk.services.ses.model.Destination;
import software.amazon.awssdk.services.ses.model.Message;
import software.amazon.awssdk.services.ses.model.SendEmailRequest;
import software.amazon.awssdk.services.ses.model.SendEmailResponse;
import software.amazon.awssdk.services.ses.model.SesException;

import com.amalitechphotoappcognitoauth.models.EmailRequest;

public class EmailService {
    private static final Logger logger = LoggerFactory.getLogger(EmailService.class);
    private final SesClient sesClient;
    private final String sourceEmail;

    public EmailService() {
        // Get the source email from environment variables with a fallback
        this.sourceEmail = System.getenv("EMAIL_SOURCE") != null ?
                System.getenv("EMAIL_SOURCE") : "noreply@mscv2group2.link";

        // Create the SES client with a default region if not specified
        this.sesClient = SesClient.builder()
                .region(Region.of(System.getenv("AWS_REGION") != null ? System.getenv("AWS_REGION") : "eu-west-1"))
                .build();

        logger.info("EmailService initialized with source email: {}", sourceEmail);
    }

    /**
     * Sends an email using Amazon SES
     *
     * @param request Email request details
     * @return True if the email was sent successfully, false otherwise
     */
    public boolean sendEmail(EmailRequest request) {
        try {
            logger.info("Sending email to: {}", request.getRecipient());

            // Create the email content
            Content subject = Content.builder()
                    .data(request.getSubject())
                    .build();

            Content htmlBody = Content.builder()
                    .data(request.getHtmlBody())
                    .build();

            Body body = Body.builder()
                    .html(htmlBody)
                    .build();

            Message message = Message.builder()
                    .subject(subject)
                    .body(body)
                    .build();

            // Create the email request
            SendEmailRequest emailRequest = SendEmailRequest.builder()
                    .source(sourceEmail)
                    .destination(Destination.builder().toAddresses(request.getRecipient()).build())
                    .message(message)
                    .build();

            // Send the email
            SendEmailResponse response = sesClient.sendEmail(emailRequest);
            logger.info("Email sent! Message ID: {}", response.messageId());
            return true;
        } catch (SesException e) {
            logger.error("Failed to send email", e);
            return false;
        }
    }

    /**
     * Closes the SES client
     */
    public void close() {
        if (sesClient != null) {
            sesClient.close();
            logger.info("SES client closed");
        }
    }
}