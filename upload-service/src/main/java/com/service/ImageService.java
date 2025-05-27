package com.service;

import com.repository.S3Repository;
import com.repository.SqsRepository;

import com.amazonaws.services.lambda.runtime.Context;
import java.io.ByteArrayInputStream;
import java.net.URLConnection;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ImageService {
    private final S3Repository s3Repository;
    private final SqsRepository sqsRepository;

    /**
     * Default constructor that initializes repositories with default settings.
     */
    public ImageService() {
        this.s3Repository = new S3Repository();
        this.sqsRepository = new SqsRepository();
    }

    /**
     * Constructor with dependency injection for repositories.
     * 
     * @param s3Repository The S3 repository for file storage
     * @param sqsRepository The SQS repository for message queuing
     */
    public ImageService(S3Repository s3Repository, SqsRepository sqsRepository) {
        this.s3Repository = s3Repository;
        this.sqsRepository = sqsRepository;
    }

    /**
     * Processes an image upload request by validating the image data,
     * determining the content type, and storing the image in S3.
     * Then sends a message to SQS with metadata including first and last name.
     * The name is split at the first space, with everything before becoming firstName
     * and everything after becoming lastName.
     *
     * @param name         The name of the user uploading the image
     * @param email        The email of the user uploading the image
     * @param imageBase64  The base64-encoded image data
     * @param contentType  The content type of the image (optional, will be detected if null)
     * @param imageTitle   The title of the image (optional)
     * @return A map containing the URL of the uploaded image and a success message
     * @throws Exception If the image processing or upload fails
     */
    public Map<String, Object> processImageUpload(String name, String email, String imageBase64, String contentType, String imageTitle, String userId, Context context) throws Exception {
        if (name == null || name.isEmpty()) {
            name = "unknown-user";
        }

        if (email == null || email.isEmpty()) {
            email = "unknown-email";
        }
        
        String firstName = name;
        String lastName = "_unknown";
        
        if (name.contains(" ")) {
            String[] nameParts = name.split(" ", 2);
            firstName = nameParts[0];
            lastName = nameParts[1];
        }

        String fileExtension = contentType != null ? "." + contentType.split("/")[1] : ".jpg";
        
        String fileName;
        if (imageTitle != null && !imageTitle.isEmpty()) {
            String safeTitle = imageTitle.replaceAll("[^a-zA-Z0-9-_.]", "_");
            fileName = "uploads/" + name + "-" + email + "-" + safeTitle + "-" + UUID.randomUUID().toString() + fileExtension;
        } else {
            fileName = "uploads/" + name + "-" + email + "-" + UUID.randomUUID().toString() + fileExtension;
        }

        byte[] imageData = Base64.getDecoder().decode(imageBase64);

        if (contentType == null) {
            contentType = URLConnection.guessContentTypeFromStream(new ByteArrayInputStream(imageData));
        }

        if (contentType == null || (!contentType.equals("image/jpeg") &&
                !contentType.equals("image/jpg") &&
                !contentType.equals("image/png"))) {
            throw new IllegalArgumentException("Unsupported file type. Only PNG and JPG/JPEG allowed.");
        }

        Map<String, String> metadata = new HashMap<>();
        metadata.put("name", name);
        metadata.put("firstName", firstName);
        metadata.put("lastName", lastName);
        metadata.put("email", email);
        metadata.put("uploadDate", new java.util.Date().toString());
        
        if (imageTitle != null && !imageTitle.isEmpty()) {
            metadata.put("imageTitle", imageTitle);
        }

        String fileUrl = s3Repository.uploadFile(fileName, imageData, contentType, metadata);

        Map<String, Object> response = new HashMap<>();
        response.put("url", fileUrl);
        response.put("message", "Image uploaded successfully");
        response.put("name", name);
        response.put("firstName", firstName);
        response.put("lastName", lastName);
        response.put("email", email);
        response.put("imageTitle", imageTitle);

        Map<String, String> messageAttributes = new HashMap<>();
        messageAttributes.put("name", name);
        messageAttributes.put("userId", userId);
        messageAttributes.put("firstName", firstName);
        messageAttributes.put("lastName", lastName);
        messageAttributes.put("email", email);
        messageAttributes.put("key", fileName);
        messageAttributes.put("uploadDate", new java.util.Date().toString());
        messageAttributes.put("imageTitle", imageTitle != null ? imageTitle : "");

        Map<String, Object> sqsStatus = new HashMap<>();
        sqsStatus.put("attempted", true);
        sqsStatus.put("attributes", messageAttributes);

        try {
            sqsRepository.sendMessage(messageAttributes, context);
            context.getLogger().log("SQS message attributes in SQS send message in processimageupload: " + messageAttributes);
            sqsStatus.put("success", true);
            sqsStatus.put("message", "Message successfully sent to SQS queue");
        } catch (Exception e) {
            sqsStatus.put("success", false);
            sqsStatus.put("error", e.getMessage());
        }

        response.put("sqsStatus", sqsStatus);

        return response;
    }


}