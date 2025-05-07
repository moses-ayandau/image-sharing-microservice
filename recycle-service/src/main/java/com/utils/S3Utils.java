package com.utils;

import com.factories.AwsFactory;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.s3.S3Client;

import java.util.Map;

public class S3Utils {
    private static final String BUCKET_NAME = System.getenv("BUCKET_NAME");


    private final S3Client s3Client;

    public S3Utils() {
        this.s3Client = AwsFactory.s3Client();
    }

    public void deleteObject(String key) {
        s3Client.deleteObject(builder -> builder
                .bucket(BUCKET_NAME)
                .key(key));
    }

    public void validateOwnership(Map<String, AttributeValue> item, String ownerId) {
        if (!item.containsKey("OwnerId") || !item.get("OwnerId").s().equals(ownerId)) {
            ResponseUtils.errorResponse(403, "User not authorized to delete this image");
        }
    }

    public void copyObject(String sourceKey, String destKey) {
        s3Client.copyObject(builder -> builder
                .sourceBucket(BUCKET_NAME)
                .sourceKey(sourceKey)
                .destinationBucket(BUCKET_NAME)
                .destinationKey(destKey));
    }
}
