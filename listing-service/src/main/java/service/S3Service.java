package service;

import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import utils.DynamoDbUtils;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class S3Service {
    private static final String BUCKET_NAME = System.getenv("PROCESSED_IMAGES_BUCKET");
    private static final S3Presigner presigner = S3Presigner.create();

    public static List<Map<String, Object>> attachPresignedUrls(List<Map<String, AttributeValue>> items) {
        return items.stream()
                .map(item -> {
                    Map<String, Object> map = DynamoDbUtils.convertDynamoItemToMap(item);

                    String s3Key = (String) map.get("imageKey");
                    if (s3Key != null) {
                        String url = generatePresignedUrl(s3Key);
                        map.put("url", url);
                    }

                    return map;
                })
                .collect(Collectors.toList());
    }

    public static String generatePresignedUrl(String imageKey) {
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(BUCKET_NAME)
                .key(imageKey)
                .build();

        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(15))
                .getObjectRequest(getObjectRequest)
                .build();

        return presigner.presignGetObject(presignRequest).url().toString();
    }
}
