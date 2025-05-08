package com.utils;

import com.factories.AwsFactory;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.s3.S3Client;

import java.util.Map;

public class S3Utils {

    private static final Log log = LogFactory.getLog(S3Utils.class);
    private final S3Client s3Client;

    public S3Utils() {
        this.s3Client = AwsFactory.s3Client();
    }

    public void deleteObject(String primaryBucket, String key) {
        s3Client.deleteObject(builder -> builder
                .bucket(primaryBucket)
                .key(key));
    }

    public void validateOwnership(Map<String, AttributeValue> item, String ownerId) {
        if (!item.containsKey("userId") || !item.get("userId").s().equals(ownerId)) {
            ResponseUtils.errorResponse(403, "User not authorized to delete this image");
        }
    }

    public void copyObject(String sourceBucket, String destinationBucket, String sourceKey, String destKey) {
        s3Client.copyObject(builder -> builder
                .sourceBucket(sourceBucket)
                .sourceKey(sourceKey)
                .destinationBucket(destinationBucket)
                .destinationKey(destKey));
    }
}
