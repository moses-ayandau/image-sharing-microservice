package upload.repository;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.ObjectMetadata;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

/**
 * Repository for S3 operations.
 */
public class S3Repository {
    private final AmazonS3 s3Client;
    private final String bucketName;

    public S3Repository() {
        this.s3Client = AmazonS3ClientBuilder.standard().build();
        this.bucketName = System.getenv("STAGING_BUCKET");
    }

    
    public S3Repository(AmazonS3 s3Client, String bucketName) {
        this.s3Client = s3Client;
        this.bucketName = bucketName;
    }

    /**
     * Uploads file to S3 bucket and returns public URL.
     */
    public String uploadFile(String fileName, byte[] fileData, String contentType) {
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(fileData.length);
        metadata.setContentType(contentType);
        
        InputStream inputStream = new ByteArrayInputStream(fileData);
        
        s3Client.putObject(bucketName, fileName, inputStream, metadata);
        
        return s3Client.getUrl(bucketName, fileName).toString();
    }
}
