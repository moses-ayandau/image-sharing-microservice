package upload.repository;

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

    // For testing with dependency injection
    public S3Repository(S3Client s3Client, String bucketName) {
        this.s3Client = s3Client;
        this.bucketName = bucketName;
    }

    public String uploadFile(String fileName, byte[] fileData, String contentType) {
        // Create the PutObjectRequest
        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(fileName)
                .contentType(contentType)
                .build();
        
        // Upload the file
        PutObjectResponse response = s3Client.putObject(putObjectRequest, 
                RequestBody.fromBytes(fileData));
        
        // Return the URL to the uploaded file
        return String.format("https://%s.s3.amazonaws.com/%s", bucketName, fileName);
    }
}
