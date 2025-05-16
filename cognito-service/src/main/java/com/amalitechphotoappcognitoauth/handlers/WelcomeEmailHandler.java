package com.amalitechphotoappcognitoauth.handlers;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amalitechphotoappcognitoauth.models.CognitoEvent;
import com.amalitechphotoappcognitoauth.models.EmailRequest;
import com.amalitechphotoappcognitoauth.services.EmailService;
import com.amalitechphotoappcognitoauth.utils.EmailTemplates;

/**
 * Lambda function handler for sending welcome emails to new users
 * This function is triggered after a user confirms their sign-up
 */
public class WelcomeEmailHandler implements RequestHandler<Object, Object> {
    private static final Logger logger = LoggerFactory.getLogger(WelcomeEmailHandler.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private final EmailService emailService;

    public WelcomeEmailHandler(EmailService emailService) {
        this.emailService = emailService;
    }

    @Override
    public Object handleRequest(Object input, Context context) {
        logger.info("Processing welcome email request");

        try {
            CognitoEvent event = objectMapper.convertValue(input, CognitoEvent.class);
            logger.info("Trigger source: {}", event.getTriggerSource());

            // Check if this is a post confirmation event for user sign-up
            if (event.getTriggerSource() != null &&
                    event.getTriggerSource().equals("PostConfirmation_ConfirmSignUp")) {

                String email = event.getUserEmail();
                String name = event.getUserDisplayName();

                if (email == null) {
                    logger.error("Email not found in the event");
                    return input;
                }

                logger.info("Sending welcome email to: {}", email);

                String subject = "Welcome to Photo Blog App!";
                String htmlBody = EmailTemplates.getWelcomeEmailTemplate(name);

                EmailRequest emailRequest = new EmailRequest(email, subject, htmlBody);
                boolean result = emailService.sendEmail(emailRequest);

                if (result) {
                    logger.info("Welcome email sent successfully to: {}", email);
                } else {
                    logger.error("Failed to send welcome email to: {}", email);
                }
            } else {
                logger.info("Not a sign-up confirmation event. Skipping welcome email.");
            }
        } catch (Exception e) {
            logger.error("Error processing welcome email", e);
        }
        return input;
    }
}