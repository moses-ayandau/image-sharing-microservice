//package com.amalitechphotoappcognitoauth.handlers;
//
//import com.amalitechphotoappcognitoauth.models.CognitoEvent;
//import com.amazonaws.services.lambda.runtime.Context;
//import com.amazonaws.services.lambda.runtime.RequestHandler;
//import com.fasterxml.jackson.databind.ObjectMapper;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//
///**
// * Lambda function handler for PostConfirmation trigger in Cognito
// */
//public class PostConfirmationHandler implements RequestHandler<Object, Object> {
//    private static final Logger logger = LoggerFactory.getLogger(PostConfirmationHandler.class);
//    private static final ObjectMapper objectMapper = new ObjectMapper();
//
//    @Override
//    public Object handleRequest(Object input, Context context) {
//        logger.info("Processing PostConfirmation request with input: {}", input);
//
//        try {
//            // Parse the input into a Cognito event structure
//            String inputJson = objectMapper.writeValueAsString(input);
//            CognitoEvent event = objectMapper.readValue(inputJson, CognitoEvent.class);
//            logger.info("Trigger source: {}", event.getTriggerSource());
//
//            // Check if this is a PostConfirmation event after sign-up
//            if ("PostConfirmation_ConfirmSignUp".equals(event.getTriggerSource())) {
//                String email = (String) event.getRequest().getUserAttributes().get("email");
//                if (email != null) {
//                    logger.info("User confirmed with email: {}", email);
//                    // Add your business logic here, e.g., SES email verification or welcome email
//                } else {
//                    logger.warn("No email found in user attributes");
//                }
//            } else {
//                logger.info("Event is not a PostConfirmation_ConfirmSignUp trigger");
//            }
//        } catch (Exception e) {
//            logger.error("Error processing PostConfirmation event", e);
//            throw new RuntimeException("Failed to process PostConfirmation event", e);
//        }
//
//        // Return the input as per Cognito trigger requirements
//        return input;
//    }
//}