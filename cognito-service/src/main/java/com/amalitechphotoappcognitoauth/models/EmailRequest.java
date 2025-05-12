package com.amalitechphotoappcognitoauth.models;

public class EmailRequest {
    private String recipient;
    private String subject;
    private String htmlBody;

    public EmailRequest() {
    }

    public EmailRequest(String recipient, String subject, String htmlBody) {
        this.recipient = recipient;
        this.subject = subject;
        this.htmlBody = htmlBody;
    }

    // Getters and setters
    public String getRecipient() {
        return recipient;
    }

    public String getSubject() {
        return subject;
    }

    public String getHtmlBody() {
        return htmlBody;
    }

    @Override
    public String toString() {
        return "EmailRequest{" +
                "recipient='" + recipient + '\'' +
                ", subject='" + subject + '\'' +
                ", htmlBody length=" + (htmlBody != null ? htmlBody.length() : 0) +
                '}';
    }
}