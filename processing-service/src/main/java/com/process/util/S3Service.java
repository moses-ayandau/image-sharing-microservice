package com.process.util;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.io.InputStream;

public class S3Service {

    private final S3Client s3Client;
    private final String processedBucket;

    public S3Service(String regionName, String processedBucket) {
        Region region = Region.of(regionName);
        this.s3Client = S3Client.builder().region(region).build();
        this.processedBucket = processedBucket;
    }

    /**
     * Retrieves an image from S3 bucket
     *
     * @param bucket The source bucket name
     * @param key The object key
     * @return The image as byte array
     * @throws IOException If the image cannot be read
     */
    public byte[] getImageFromS3(String bucket, String key) throws IOException {
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build();

        try (InputStream s3InputStream = s3Client.getObject(getObjectRequest)) {
            return s3InputStream.readAllBytes();
        }
    }

    /**
     * Uploads a processed image to the destination bucket
     *
     * @param imageData The processed image data
     * @param key The destination object key
     */
    public void uploadToProcessedBucket(byte[] imageData, String key) {
        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(processedBucket)
                .key(key)
                .contentType("image/jpeg")
                .build();

        s3Client.putObject(putObjectRequest, RequestBody.fromBytes(imageData));
    }

    /**
     * Deletes an object from the staging bucket after processing
     *
     * @param bucket The source bucket
     * @param key The object key to delete
     */
    public void deleteFromStagingBucket(String bucket, String key) {
        DeleteObjectRequest deleteObjectRequest = DeleteObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build();

        s3Client.deleteObject(deleteObjectRequest);
    }

    /**
     * Checks if an object exists in S3
     *
     * @param bucket The bucket name to check
     * @param key The object key to check
     * @return true if the object exists, false otherwise
     */
    public boolean objectExists(String bucket, String key) {
        try {
            HeadObjectRequest headObjectRequest = HeadObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build();

            s3Client.headObject(headObjectRequest);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}