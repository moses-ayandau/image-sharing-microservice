package upload.service;

import upload.model.ImageUploadRequest;
import upload.model.ImageUploadResponse;
import upload.repository.S3Repository;

import java.io.ByteArrayInputStream;
import java.net.URLConnection;
import java.util.Base64;
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
    
    public ImageUploadResponse processImageUpload(ImageUploadRequest request) throws Exception {
        // Extract and validate image data
        byte[] imageData = Base64.getDecoder().decode(request.getImage());
        
        // Validate content type
        String contentType = request.getContentType();
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
        
        // Create a unique filename with user info
        String fileName = String.format("uploads/%s-%s-%s%s", 
                request.getFirstName(), 
                request.getLastName(), 
                UUID.randomUUID().toString(), 
                extension);
        
        // Upload to S3 and get URL
        String fileUrl = s3Repository.uploadFile(fileName, imageData, contentType);
        
        return new ImageUploadResponse(fileUrl, "Image uploaded successfully");
    }
}