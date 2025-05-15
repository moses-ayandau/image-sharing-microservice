package com.repository;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

public class S3Repository {
    private final S3Client s3Client;
    private final String bucketName;

    public S3Repository() {
        this.s3Client = S3Client.builder()
                .region(Region.US_EAST_1)
                .build();
        this.bucketName = System.getenv("BUCKET_NAME");
    }

    public S3Repository(S3Client s3Client, String bucketName) {
        this.s3Client = s3Client;
        this.bucketName = bucketName;
    }

    /**
     * Uploads a file to the S3 bucket.
     *
     * @param fileName    The name/path to use for the file in S3
     * @param fileData    The binary content of the file
     * @param contentType The MIME type of the file
     * @return The URL to the uploaded file
     */
    public String uploadFile(String fileName, byte[] fileData, String contentType) {
        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(fileName)
                .contentType(contentType)
                .build();
        
        PutObjectResponse response = s3Client.putObject(putObjectRequest,
                RequestBody.fromBytes(fileData));
        
        return String.format("https://%s.s3.amazonaws.com/%s", bucketName, fileName);
    }
}
