package com.process.util;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.*;

import java.util.logging.Logger;

public class EmailService {

    private final SesClient sesClient;
    private final String sourceEmail = "no-reply@mscv2group2.link";

    public EmailService(String regionName) {
        Region region = Region.of(regionName);
        this.sesClient = SesClient.builder().region(region).build();
    }


    public void sendProcessingStartEmail(String email, String firstName) {
        String subject = "Your Image is Processing";
        String htmlBody = "<html><body>" +
                "<h2>Hello " + firstName + ",</h2>" +
                "<p>We've received your image and it's currently being processed. " +
                "You will receive another email when processing is complete.</p>" +
                "<p>Thank you for using our Photo Blog service!</p>" +
                "</body></html>";

        sendEmail(email, subject, htmlBody);
    }

    public void sendProcessingCompleteEmail(String email, String firstName, String imageKey) {
        String subject = "Your Image Processing is Complete";
        String htmlBody = "<html><body>" +
                "<h2>Good news, " + firstName + "!</h2>" +
                "<p>Your image has been successfully processed and is now available in your photo blog.</p>" +
                "<p>Thank you for using our Photo Blog service!</p>" +
                "</body></html>";

        sendEmail(email, subject, htmlBody);
    }

    public void sendProcessingFailureEmail(String email, String firstName) {
        String subject = "Image Processing Failed";
        String htmlBody = "<html><body>" +
                "<h2>Hello " + firstName + ",</h2>" +
                "<p>We encountered an issue while processing your image. " +
                "Don't worry, we'll automatically retry processing it in 5 minutes.</p>" +
                "<p>You'll receive another email when processing is successful.</p>" +
                "<p>Thank you for your patience!</p>" +
                "</body></html>";

        sendEmail(email, subject, htmlBody);
    }


    private void sendEmail(String recipient, String subject, String htmlBody) {
        Destination destination = Destination.builder()
                .toAddresses(recipient)
                .build();

        Content subjectContent = Content.builder()
                .data(subject)
                .build();

        Content htmlContent = Content.builder()
                .data(htmlBody)
                .build();

        Body body = Body.builder()
                .html(htmlContent)
                .build();

        Message message = Message.builder()
                .subject(subjectContent)
                .body(body)
                .build();

        SendEmailRequest sendEmailRequest = SendEmailRequest.builder()
                .source(sourceEmail)
                .destination(destination)
                .message(message)
                .build();

        Logger.getAnonymousLogger().info(sendEmailRequest.destination().toString());
        Logger.getAnonymousLogger().info(sourceEmail.toString());
        Logger.getAnonymousLogger().info(message.toString());

        sesClient.sendEmail(sendEmailRequest);
    }
}