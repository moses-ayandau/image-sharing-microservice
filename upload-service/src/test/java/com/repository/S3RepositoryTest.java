package upload.repository;

import org.junit.Before;
import org.junit.Test;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class S3RepositoryTest {

    private TestS3Client testS3Client;
    private S3Repository s3Repository;
    private final String bucketName = "test-bucket";
    
    // Test implementation of S3Client
    private static class TestS3Client implements S3Client {
        private PutObjectRequest lastRequest;
        private RequestBody lastRequestBody;
        
        @Override
        public PutObjectResponse putObject(PutObjectRequest request, RequestBody requestBody) {
            this.lastRequest = request;
            this.lastRequestBody = requestBody;
            return PutObjectResponse.builder().build();
        }
        
        @Override
        public String serviceName() {
            return "s3";
        }
        
        @Override
        public void close() {
            // No-op for test
        }
        
        public PutObjectRequest getLastRequest() {
            return lastRequest;
        }
        
        public RequestBody getLastRequestBody() {
            return lastRequestBody;
        }
    }
    
    @Before
    public void setUp() {
        testS3Client = new TestS3Client();
        s3Repository = new S3Repository(testS3Client, bucketName);
    }
    
    @Test
    public void testUploadFile() {
        // Setup
        String fileName = "test-file.jpg";
        byte[] fileData = "test data".getBytes();
        String contentType = "image/jpeg";
        
        // Test
        String result = s3Repository.uploadFile(fileName, fileData, contentType);
        
        // Verify
        String expectedUrl = "https://test-bucket.s3.amazonaws.com/test-file.jpg";
        assertEquals(expectedUrl, result);
        
        // Verify correct request was made
        PutObjectRequest capturedRequest = testS3Client.getLastRequest();
        assertNotNull(capturedRequest);
        assertEquals(bucketName, capturedRequest.bucket());
        assertEquals(fileName, capturedRequest.key());
        assertEquals(contentType, capturedRequest.contentType());
    }
}


