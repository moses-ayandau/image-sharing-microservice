package upload.service;

import org.junit.Before;
import org.junit.Test;
import upload.repository.S3Repository;

import java.util.Map;
import java.util.Base64;

import static org.junit.Assert.*;

public class ImageServiceTest {
    private ImageService imageService;
    private TestS3Repository testRepository;
    
    // Create a test implementation of S3Repository instead of mocking
    private static class TestS3Repository extends S3Repository {
        private String lastUploadedFileName;
        private byte[] lastUploadedData;
        private String lastContentType;
        private String urlToReturn = "https://bucket.s3.amazonaws.com/uploads/testuser-uuid.png";
        
        public TestS3Repository() {
            // Call super with null values since we're overriding the method
            super(null, "test-bucket");
        }
        
        @Override
        public String uploadFile(String fileName, byte[] fileData, String contentType) {
            this.lastUploadedFileName = fileName;
            this.lastUploadedData = fileData;
            this.lastContentType = contentType;
            return urlToReturn;
        }
        
        public String getLastUploadedFileName() {
            return lastUploadedFileName;
        }
        
        public byte[] getLastUploadedData() {
            return lastUploadedData;
        }
        
        public String getLastContentType() {
            return lastContentType;
        }
    }
    
    @Before
    public void setUp() {
        testRepository = new TestS3Repository();
        imageService = new ImageService(testRepository);
    }
    
    @Test
    public void testProcessImageUpload_Success() throws Exception {
        // Sample base64 encoded small PNG image
        String base64Image = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==";
        String username = "testuser";
        String contentType = "image/png";
        
        // Test
        Map<String, Object> response = imageService.processImageUpload(username, base64Image, contentType);
        
        // Verify
        assertNotNull(response);
        assertEquals(testRepository.urlToReturn, response.get("url"));
        assertTrue(response.get("message").toString().contains("success"));
        assertEquals("image/png", testRepository.getLastContentType());
        assertTrue(testRepository.getLastUploadedFileName().contains("testuser"));
        assertTrue(testRepository.getLastUploadedFileName().contains(".png"));
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void testProcessImageUpload_UnsupportedFileType() throws Exception {
        // Create request with unsupported content type
        String username = "testuser";
        String base64Image = "SGVsbG8gV29ybGQ="; // "Hello World" in base64
        String contentType = "text/plain";
        
        // This should throw IllegalArgumentException
        imageService.processImageUpload(username, base64Image, contentType);
    }
}
