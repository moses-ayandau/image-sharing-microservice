package com.repository;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

import java.util.Map;

public class S3Repository {
    private final S3Client s3Client;
    private final String bucketName;

    public S3Repository() {
        this.s3Client = S3Client.builder()
                .region(Region.US_EAST_1)
                .build();
        this.bucketName = System.getenv("STAGING_BUCKET");
    }

    public S3Repository(S3Client s3Client, String bucketName) {
        this.s3Client = s3Client;
        this.bucketName = bucketName;
    }

    /**
     * Uploads a file to the S3 bucket with metadata.
     * @param fileName    The name/path to use for the file in S3
     * @param fileData    The binary content of the file
     * @param contentType The MIME type of the file
     * @param metadata    Additional metadata to attach to the S3 object
     * @return The URL to the uploaded file
     */
    public String uploadFile(String fileName, byte[] fileData, String contentType, Map<String, String> metadata) {
        PutObjectRequest.Builder requestBuilder = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(fileName)
                .contentLength((long) fileData.length)
                .contentType(contentType);
        
        if (metadata != null) {
            requestBuilder.metadata(metadata);
        }

        return String.format("https://%s.s3.%s.amazonaws.com/%s",
                bucketName, 
                Region.EU_CENTRAL_1.toString(), 
                fileName);
    }

    /**
     * Uploads a file to the S3 bucket without additional metadata.
     *
     * @param fileName    The name/path to use for the file in S3
     * @param fileData    The binary content of the file
     * @param contentType The MIME type of the file
     * @return The URL to the uploaded file
     */
    public String uploadFile(String fileName, byte[] fileData, String contentType) {
        return uploadFile(fileName, fileData, contentType, null);
    }
}
