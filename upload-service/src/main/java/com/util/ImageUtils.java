package com.util;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URLConnection;
import java.util.Base64;

public class ImageUtils {
    
    public static byte[] decodeBase64Image(String base64Image) {
        return Base64.getDecoder().decode(base64Image);
    }
    
    public static String detectContentType(byte[] imageData) throws IOException {
        return URLConnection.guessContentTypeFromStream(new ByteArrayInputStream(imageData));
    }
    
    public static boolean isValidImageType(String contentType) {
        return contentType != null && 
               (contentType.equals("image/jpeg") || 
                contentType.equals("image/jpg") || 
                contentType.equals("image/png"));
    }
    
    public static String getExtensionFromContentType(String contentType) {
        return contentType.equals("image/png") ? ".png" : ".jpg";
    }
    
    /**
     * Sanitizes a filename by removing invalid characters and limiting length
     * 
     * @param fileName The filename to sanitize
     * @return A sanitized filename
     */
    public static String sanitizeFileName(String fileName) {
        if (fileName == null) {
            return "unnamed";
        }
        
        // Replace invalid characters with underscores
        String sanitized = fileName.replaceAll("[^a-zA-Z0-9.-]", "_");
        
        // Limit length to 255 characters (common filesystem limit)
        if (sanitized.length() > 255) {
            sanitized = sanitized.substring(0, 255);
        }
        
        return sanitized;
    }
}
