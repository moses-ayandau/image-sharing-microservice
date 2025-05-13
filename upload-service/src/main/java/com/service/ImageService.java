package upload.service;

import upload.repository.S3Repository;

import java.io.ByteArrayInputStream;
import java.net.URLConnection;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ImageService {
    private final S3Repository s3Repository;
    
    public ImageService() {
        this.s3Repository = new S3Repository();
    }
    
    // For testing with dependency injection
    public ImageService(S3Repository s3Repository) {
        this.s3Repository = s3Repository;
    }
    
    public Map<String, Object> processImageUpload(String username, String imageBase64, String contentType) throws Exception {
        // Extract and validate image data
        byte[] imageData = Base64.getDecoder().decode(imageBase64);
        
        // Validate content type
        if (contentType == null) {
            contentType = URLConnection.guessContentTypeFromStream(new ByteArrayInputStream(imageData));
        }
        
        if (contentType == null || (!contentType.equals("image/jpeg") && 
                !contentType.equals("image/jpg") && 
                !contentType.equals("image/png"))) {
            throw new IllegalArgumentException("Unsupported file type. Only PNG and JPG/JPEG allowed.");
        }
        
        // Generate a file extension based on MIME type
        String extension = contentType.equals("image/png") ? ".png" : ".jpg";
        
        // Create a unique filename with username
        String fileName = String.format("uploads/%s-%s%s", 
                username, 
                UUID.randomUUID().toString(), 
                extension);
        
        // Upload to S3 and get URL
        String fileUrl = s3Repository.uploadFile(fileName, imageData, contentType);
        
        // Create response
        Map<String, Object> response = new HashMap<>();
        response.put("url", fileUrl);
        response.put("message", "Image uploaded successfully");
        
        return response;
    }
}
