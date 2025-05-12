package upload.service;

import org.junit.Before;
import org.junit.Test;
import upload.model.ImageUploadRequest;
import upload.model.ImageUploadResponse;
import upload.repository.S3Repository;

import java.util.Base64;

import static org.junit.Assert.*;

public class ImageServiceTest {

    private ImageService imageService;
    
    // Create a test implementation of S3Repository instead of mocking
    private static class TestS3Repository extends S3Repository {
        private String lastUploadedFileName;
        private byte[] lastUploadedData;
        private String lastContentType;
        private String urlToReturn = "https://bucket.s3.amazonaws.com/uploads/John-Doe-uuid.png";
        
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
    
    private TestS3Repository testRepository;

    @Before
    public void setUp() {
        testRepository = new TestS3Repository();
        imageService = new ImageService(testRepository);
    }

    @Test
    public void testProcessImageUpload_Success() throws Exception {
        // Sample base64 encoded small PNG image
        String base64Image = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==";
        
        ImageUploadRequest request = new ImageUploadRequest();
        request.setFirstName("John");
        request.setLastName("Doe");
        request.setImage(base64Image);
        request.setContentType("image/png");
        
        // Test
        ImageUploadResponse response = imageService.processImageUpload(request);
        
        // Verify
        assertNotNull(response);
        assertEquals(testRepository.urlToReturn, response.getUrl());
        assertTrue(response.getMessage().contains("success"));
        assertEquals("image/png", testRepository.getLastContentType());
        assertTrue(testRepository.getLastUploadedFileName().contains("John-Doe"));
        assertTrue(testRepository.getLastUploadedFileName().contains(".png"));
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void testProcessImageUpload_UnsupportedFileType() throws Exception {
        // Create request with unsupported content type
        ImageUploadRequest request = new ImageUploadRequest();
        request.setFirstName("John");
        request.setLastName("Doe");
        request.setImage("SGVsbG8gV29ybGQ="); // "Hello World" in base64
        request.setContentType("text/plain");
        
        // This should throw IllegalArgumentException
        imageService.processImageUpload(request);
    }
}
