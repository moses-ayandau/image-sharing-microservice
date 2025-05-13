package com.utils;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.factories.AwsFactory;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.s3.S3Client;

import java.util.Map;

public class S3Utils {

    private final S3Client s3Client;

    public S3Utils() {
        this.s3Client = AwsFactory.s3Client();
    }

    public S3Utils(S3Client s3Client) {
        this.s3Client = s3Client;
    }

    public void deleteObject(String primaryBucket, String key) {
        s3Client.deleteObject(builder -> builder
                .bucket(primaryBucket)
                .key(key));
    }

    public APIGatewayProxyResponseEvent validateOwnership(Map<String, AttributeValue> item, String ownerId) {
        if (!item.containsKey("userId") || !item.get("userId").s().equals(ownerId)) {
            ResponseUtils.errorResponse(403, "User not authorized to delete this image");
        }
        return null;
    }

    public void copyObject(String bucketName, String sourceKey, String destKey) {
        s3Client.copyObject(builder -> builder
                .sourceBucket(bucketName)
                .sourceKey(sourceKey)
                .destinationBucket(bucketName)
                .destinationKey(destKey));
    }
}
