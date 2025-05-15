package upload.repository;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectResult;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.InputStream;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class S3RepositoryTest {

    @Mock
    private AmazonS3 s3Client;
    
    private S3Repository s3Repository;
    private final String bucketName = "test-bucket";
    
    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        s3Repository = new S3Repository(s3Client, bucketName);
    }
    
    @Test
    public void testUploadFile() throws Exception {
        // Setup
        String fileName = "uploads/test-file.jpg";
        byte[] fileData = "test data".getBytes();
        String contentType = "image/jpeg";
        
        // Mock S3 client behavior
        when(s3Client.putObject(anyString(), anyString(), any(InputStream.class), any(ObjectMetadata.class)))
                .thenReturn(new PutObjectResult());
        
        URL mockUrl = new URL("https://test-bucket.s3.amazonaws.com/uploads/test-file.jpg");
        when(s3Client.getUrl(bucketName, fileName)).thenReturn(mockUrl);
        
        // Test
        String result = s3Repository.uploadFile(fileName, fileData, contentType);
        
        // Verify
        assertEquals(mockUrl.toString(), result);
        
        // Verify correct metadata was set
        ArgumentCaptor<ObjectMetadata> metadataCaptor = ArgumentCaptor.forClass(ObjectMetadata.class);
        verify(s3Client).putObject(eq(bucketName), eq(fileName), any(InputStream.class), metadataCaptor.capture());
        
        ObjectMetadata capturedMetadata = metadataCaptor.getValue();
        assertEquals(fileData.length, capturedMetadata.getContentLength());
        assertEquals(contentType, capturedMetadata.getContentType());
    }
    
    @Test
    public void testUploadFileWithMetadata() throws Exception {
        // Setup
        String fileName = "uploads/test-file.jpg";
        byte[] fileData = "test data".getBytes();
        String contentType = "image/jpeg";
        Map<String, String> metadata = new HashMap<>();
        metadata.put("firstName", "John");
        metadata.put("lastName", "Doe");
        metadata.put("email", "john.doe@example.com");
        
        // Mock S3 client behavior
        when(s3Client.putObject(anyString(), anyString(), any(InputStream.class), any(ObjectMetadata.class)))
                .thenReturn(new PutObjectResult());
        
        URL mockUrl = new URL("https://test-bucket.s3.amazonaws.com/uploads/test-file.jpg");
        when(s3Client.getUrl(bucketName, fileName)).thenReturn(mockUrl);
        
        // Test
        String result = s3Repository.uploadFile(fileName, fileData, contentType, metadata);
        
        // Verify
        assertEquals(mockUrl.toString(), result);
        
        // Verify correct metadata was set
        ArgumentCaptor<ObjectMetadata> metadataCaptor = ArgumentCaptor.forClass(ObjectMetadata.class);
        verify(s3Client).putObject(eq(bucketName), eq(fileName), any(InputStream.class), metadataCaptor.capture());
        
        ObjectMetadata capturedMetadata = metadataCaptor.getValue();
        assertEquals(fileData.length, capturedMetadata.getContentLength());
        assertEquals(contentType, capturedMetadata.getContentType());
        assertEquals("John", capturedMetadata.getUserMetadata().get("firstname"));
        assertEquals("Doe", capturedMetadata.getUserMetadata().get("lastname"));
        assertEquals("john.doe@example.com", capturedMetadata.getUserMetadata().get("email"));
    }
}