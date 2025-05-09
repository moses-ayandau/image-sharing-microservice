package com.process.util;

/**
 * Utility class for parsing information from S3 object keys
 * Note: In a real implementation, you would likely replace this with a database lookup
 * or extract this information from S3 object metadata
 */
public class KeyParser {

    /**
     * Extracts user ID from the S3 object key
     *
     * @param key S3 object key
     * @return The user ID
     */
    public String extractUserId(String key) {
        // Assuming key format: uploads/{userId}/{imageName}
        String[] parts = key.split("/");
        return parts.length > 1 ? parts[1] : "unknown";
    }

    /**
     * Extracts user email from the S3 object key
     * Note: In a real implementation, this would typically be a database lookup using userId
     *
     * @param key S3 object key
     * @return The user's email address
     */
    public String extractEmail(String key) {
        // For demonstration purposes - in production this would likely be a DB lookup
        return "user@example.com";
    }

    /**
     * Extracts user's first name from the S3 object key
     * Note: In a real implementation, this would typically be a database lookup using userId
     *
     * @param key S3 object key
     * @return The user's first name
     */
    public String extractFirstName(String key) {
        // For demonstration purposes - in production this would likely be a DB lookup
        return "User";
    }

    /**
     * Extracts user's last name from the S3 object key
     * Note: In a real implementation, this would typically be a database lookup using userId
     *
     * @param key S3 object key
     * @return The user's last name
     */
    public String extractLastName(String key) {
        // For demonstration purposes - in production this would likely be a DB lookup
        return "Name";
    }
}