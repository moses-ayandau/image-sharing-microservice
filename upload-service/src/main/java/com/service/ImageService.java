package upload.service;

import upload.model.ImageUploadRequest;
import upload.model.ImageUploadResponse;
import upload.repository.S3Repository;

import java.io.ByteArrayInputStream;
import java.net.URLConnection;
import java.util.Base64;
import java.util.UUID;

/**
 * Service for processing image uploads.
 */
public class ImageService {
    private final S3Repository s3Repository;
    
    public ImageService() {
        this.s3Repository = new S3Repository();
    }
    
    
    public ImageService(S3Repository s3Repository) {
        this.s3Repository = s3Repository;
    }
    
    /**
     * Processes image upload request and stores in S3.
     * @throws IllegalArgumentException If image type not supported
     */
    public ImageUploadResponse processImageUpload(ImageUploadRequest request) throws Exception {
        byte[] imageData = Base64.getDecoder().decode(request.getImage());
        
        String contentType = request.getContentType();
        if (contentType == null) {
            contentType = URLConnection.guessContentTypeFromStream(new ByteArrayInputStream(imageData));
        }
        
        if (contentType == null || (!contentType.equals("image/jpeg") && 
                !contentType.equals("image/jpg") && 
                !contentType.equals("image/png"))) {
            throw new IllegalArgumentException("Unsupported file type. Only PNG and JPG/JPEG allowed.");
        }
        
        
        String extension = contentType.equals("image/png") ? ".png" : ".jpg";
        
        
        String fileName = String.format("uploads/%s-%s-%s%s", 
                request.getFirstName(), 
                request.getLastName(), 
                UUID.randomUUID().toString(), 
                extension);
        
        
        String fileUrl = s3Repository.uploadFile(fileName, imageData, contentType);
        
        return new ImageUploadResponse(fileUrl, "Image uploaded successfully");
    }
}
