package com.process.util;

import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.IOException;

public class S3Service {
    private final S3Client s3Client;
    private final String processedBucket;
    private final String stagingBucket;

    public S3Service(String region, String processedBucket) {
        this.s3Client = S3Client.builder().region(Region.of(region)).build();
        this.processedBucket = processedBucket;
        // Use environment variable if available, otherwise use the parameter
        this.stagingBucket = System.getenv("STAGING_BUCKET");

        System.out.println("S3Service initialized with processedBucket: " + processedBucket +
                " and stagingBucket: " + stagingBucket +
                " in region: " + region);
    }

    public boolean objectExists(String bucket, String key) {
        try {
            HeadObjectRequest headObjectRequest = HeadObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build();

            s3Client.headObject(headObjectRequest);
            return true;
        } catch (S3Exception e) {
            System.out.println("Object does not exist: " + bucket + "/" + key);
            return false;
        }
    }


    public byte[] getImageFromS3(String bucket, String key) throws IOException {
        try {
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build();

            return s3Client.getObject(getObjectRequest, ResponseTransformer.toBytes()).asByteArray();
        } catch (S3Exception e) {
            throw new IOException("Failed to retrieve image from S3: " + e.getMessage(), e);
        }
    }

    public void uploadToProcessedBucket(byte[] imageData, String key) throws IOException {
        try {
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(processedBucket)
                    .key(key)
                    .contentType("image/jpeg")
                    .build();

            s3Client.putObject(putObjectRequest, software.amazon.awssdk.core.sync.RequestBody.fromBytes(imageData));
        } catch (S3Exception e) {
            throw new IOException("Failed to upload processed image to S3: " + e.getMessage(), e);
        }
    }


    public void deleteFromStagingBucket(String bucket, String key) throws IOException {
        try {
            DeleteObjectRequest deleteObjectRequest = DeleteObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build();

            s3Client.deleteObject(deleteObjectRequest);
        } catch (S3Exception e) {
            throw new IOException("Failed to delete image from staging bucket: " + e.getMessage(), e);
        }
    }
}