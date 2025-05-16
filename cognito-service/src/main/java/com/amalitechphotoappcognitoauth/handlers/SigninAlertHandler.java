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

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * Lambda function handler for sending sign-in alert emails
 * This function is triggered after a user successfully signs in
 */
public class SigninAlertHandler implements RequestHandler<Object, Object> {
    private static final Logger logger = LoggerFactory.getLogger(SigninAlertHandler.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private final EmailService emailService;

    public SigninAlertHandler(EmailService emailService) {
        this.emailService = emailService;
    }

    @Override
    public Object handleRequest(Object input, Context context) {
        logger.info("Processing sign-in alert request");

        try {
            CognitoEvent event = objectMapper.convertValue(input, CognitoEvent.class);
            logger.info("Trigger source: {}", event.getTriggerSource());

            // Send sign-in alert only for PostAuthentication trigger
            if (isSignInTrigger(event.getTriggerSource())) {
                String email = event.getUserEmail();
                String name = event.getUserDisplayName();

                if (email == null) {
                    logger.error("Email not found in the event");
                    return input;
                }

                logger.info("Sending sign-in alert to: {}", email);

                String deviceDetails = extractDeviceDetails(event);

                String timestamp = formatTimestamp(Instant.now());

                String subject = "New Sign-in Detected - Photo Blog App";
                String htmlBody = EmailTemplates.getSignInAlertTemplate(name, deviceDetails, timestamp);

                EmailRequest emailRequest = new EmailRequest(email, subject, htmlBody);
                boolean result = emailService.sendEmail(emailRequest);

                if (result) {
                    logger.info("Sign-in alert email sent successfully to: {}", email);
                } else {
                    logger.error("Failed to send sign-in alert email to: {}", email);
                }
            } else {
                logger.info("Not a sign-in event. Skipping sign-in alert.");
            }
        } catch (Exception e) {
            logger.error("Error processing sign-in alert", e);
        }
        return input;
    }

    /**
     * Check if the trigger source indicates a sign-in event
     *
     * @param triggerSource The trigger source from the Cognito event
     * @return true if it's a sign-in trigger, false otherwise
     */
    private boolean isSignInTrigger(String triggerSource) {
        return "PostAuthentication_Authentication".equals(triggerSource);
    }

    /**
     * Extracts device and location details from the Cognito event
     *
     * @param event Cognito event
     * @return String with device details
     */
    private String extractDeviceDetails(CognitoEvent event) {
        try {
            if (event.getCallerContext() != null) {
                StringBuilder details = new StringBuilder();

                // Extract data from caller context
                Map<String, Object> callerContext = (Map<String, Object>) event.getCallerContext().get("callerContext");
                if (callerContext != null) {
                    if (callerContext.containsKey("deviceName")) {
                        details.append(callerContext.get("deviceName"));
                    }

                    if (callerContext.containsKey("deviceVersion")) {
                        if (!details.isEmpty()) details.append(", ");
                        details.append(callerContext.get("deviceVersion"));
                    }
                }

                if (details.isEmpty()) {
                    return "Unknown device";
                }

                return details.toString();
            }
        } catch (Exception e) {
            logger.error("Error extracting device details", e);
        }
        return "Unknown device";
    }

    /**
     * Formats a timestamp for display in emails
     *
     * @param instant The timestamp to format
     * @return Formatted timestamp string
     */
    private String formatTimestamp(Instant instant) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMMM d, yyyy 'at' h:mm a z")
                .withZone(ZoneId.systemDefault());
        return formatter.format(instant);
    }
}