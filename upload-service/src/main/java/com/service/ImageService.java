package com.service;

import com.repository.S3Repository;

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
    
    public ImageService(S3Repository s3Repository) {
        this.s3Repository = s3Repository;
    }
    
    /**
     * Processes an image upload request by validating the image data,
     * determining the content type, and storing the image in S3.
     *
     * @param username     The username of the user uploading the image
     * @param imageBase64  The base64-encoded image data
     * @param contentType  The content type of the image (optional, will be detected if null)
     * @return A map containing the URL of the uploaded image and a success message
     * @throws Exception If the image processing or upload fails
     */
    public Map<String, Object> processImageUpload(String username, String imageBase64, String contentType) throws Exception {
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
        
        String extension = contentType.equals("image/png") ? ".png" : ".jpg";
        

        String fileName = String.format("uploads/%s-%s%s",
                username, 
                UUID.randomUUID().toString(), 
                extension);
        
        String fileUrl = s3Repository.uploadFile(fileName, imageData, contentType);
        
        Map<String, Object> response = new HashMap<>();
        response.put("url", fileUrl);
        response.put("message", "Image uploaded Successfully");
        
        return response;
    }
}
