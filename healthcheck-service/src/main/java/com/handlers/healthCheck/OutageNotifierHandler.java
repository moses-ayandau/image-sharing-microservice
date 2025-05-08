package com.photo.handlers;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import software.amazon.awssdk.services.sns.model.PublishResponse;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

public class OutageNotifierHandler implements RequestHandler<Object, String> {

    private final SnsClient snsClient;
    private final String SNS_TOPIC_ARN;
    private final ObjectMapper objectMapper;

    public OutageNotifierHandler() {
        // Get the region from environment variable or use a default
        String regionName = System.getenv("AWS_REGION");
        Region region = regionName != null ? Region.of(regionName) : Region.EU_CENTRAL_1;

        // Initialize SNS client
        this.snsClient = SnsClient.builder().region(region).build();

        // Get SNS topic ARN from environment variable
        this.SNS_TOPIC_ARN = System.getenv("SNS_TOPIC_ARN");

        // Initialize JSON object mapper
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public String handleRequest(Object input, Context context) {
        try {
            context.getLogger().log("Processing outage notification event: " + input);

            // Get current time in ISO 8601 format
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
            dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
            String timestamp = dateFormat.format(new Date());

            // Process different event types
            String subject;
            String message;

            // Convert input to JSON to handle various event types
            JsonNode eventJson = objectMapper.valueToTree(input);

            // Check if it's a CloudWatch Alarm event
            if (eventJson.has("detail") && eventJson.get("detail").has("alarmName")) {
                // EventBridge event format for CloudWatch Alarms
                subject = "[ALERT] Service Outage Detected: " + eventJson.get("detail").get("alarmName").asText();
                message = buildCloudWatchEventMessage(eventJson, timestamp);
            }
            else if (eventJson.has("AlarmName")) {
                // Direct CloudWatch Alarm event
                subject = "[ALERT] Service Outage Detected: " + eventJson.get("AlarmName").asText();
                message = buildDirectCloudWatchAlarmMessage(eventJson, timestamp);
            }
            else if (eventJson.has("healthCheckId")) {
                // Route 53 Health Check event
                subject = "[ALERT] Route 53 Health Check Failure";
                message = buildRoute53Message(eventJson, timestamp);
            }
            else {
                // Generic event handling
                subject = "[ALERT] Photo API Service Issue Detected";
                message = buildGenericMessage(eventJson, timestamp);
            }

            // Send notification
            PublishResponse publishResponse = snsClient.publish(PublishRequest.builder()
                    .topicArn(SNS_TOPIC_ARN)
                    .subject(subject)
                    .message(message)
                    .build());

            context.getLogger().log("Successfully published notification: " + publishResponse.messageId());
            return "Successfully processed outage notification";
        } catch (Exception e) {
            context.getLogger().log("Error processing outage notification: " + e.getMessage());
            e.printStackTrace();
            return "Error processing outage notification: " + e.getMessage();
        }
    }

    private String buildCloudWatchEventMessage(JsonNode eventJson, String timestamp) {
        String alarmName = eventJson.get("detail").get("alarmName").asText();
        String state = eventJson.get("detail").get("state").get("value").asText();
        String reason = eventJson.get("detail").get("state").get("reason").asText();

        return String.format(
                "ALERT: Service Outage Detected\n\n" +
                        "Timestamp: %s\n" +
                        "Environment: %s\n" +
                        "Alarm: %s\n" +
                        "State: %s\n" +
                        "Reason: %s\n\n" +
                        "Please investigate immediately.\n" +
                        "View in CloudWatch: https://%s.console.aws.amazon.com/cloudwatch/home?region=%s#alarmsV2:alarm/%s",
                timestamp,
                System.getenv("ENVIRONMENT"),
                alarmName,
                state,
                reason,
                System.getenv("AWS_REGION"),
                System.getenv("AWS_REGION"),
                alarmName.replace(" ", "%20")
        );
    }

    private String buildDirectCloudWatchAlarmMessage(JsonNode event, String timestamp) {
        String alarmName = event.get("AlarmName").asText();
        String description = event.has("AlarmDescription") ? event.get("AlarmDescription").asText() : "";
        String newState = event.has("NewStateValue") ? event.get("NewStateValue").asText() : "ALARM";
        String oldState = event.has("OldStateValue") ? event.get("OldStateValue").asText() : "";
        String reason = event.has("NewStateReason") ? event.get("NewStateReason").asText() : "";

        return String.format(
                "ALERT: Service Outage Detected\n\n" +
                        "Timestamp: %s\n" +
                        "Environment: %s\n" +
                        "Alarm: %s\n" +
                        "Description: %s\n" +
                        "State: %s (Old State: %s)\n" +
                        "Reason: %s\n\n" +
                        "Please investigate immediately.\n" +
                        "View in CloudWatch: https://%s.console.aws.amazon.com/cloudwatch/home?region=%s#alarmsV2:alarm/%s",
                timestamp,
                System.getenv("ENVIRONMENT"),
                alarmName,
                description,
                newState,
                oldState,
                reason,
                System.getenv("AWS_REGION"),
                System.getenv("AWS_REGION"),
                alarmName.replace(" ", "%20")
        );
    }

    private String buildRoute53Message(JsonNode eventJson, String timestamp) {
        String healthCheckId = eventJson.get("healthCheckId").asText();
        String status = eventJson.has("status") ? eventJson.get("status").asText() : "FAILED";

        return String.format(
                "ALERT: Route 53 Health Check Failure\n\n" +
                        "Timestamp: %s\n" +
                        "Environment: %s\n" +
                        "Health Check ID: %s\n" +
                        "Status: %s\n\n" +
                        "The API health endpoint is not responding correctly. Please investigate immediately.\n" +
                        "View in Route 53: https://%s.console.aws.amazon.com/route53/healthchecks/home?region=%s#/",
                timestamp,
                System.getenv("ENVIRONMENT"),
                healthCheckId,
                status,
                System.getenv("AWS_REGION"),
                System.getenv("AWS_REGION")
        );
    }

    private String buildGenericMessage(JsonNode eventJson, String timestamp) {
        return String.format(
                "ALERT: Photo API Service Issue Detected\n\n" +
                        "Timestamp: %s\n" +
                        "Environment: %s\n" +
                        "Event Details: %s\n\n" +
                        "Please investigate immediately.",
                timestamp,
                System.getenv("ENVIRONMENT"),
                eventJson.toString()
        );
    }
}