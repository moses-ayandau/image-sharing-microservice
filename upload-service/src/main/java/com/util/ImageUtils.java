package upload.util;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URLConnection;
import java.util.Base64;

/**
 * Utility methods for image processing.
 */
public class ImageUtils {
    
    /**
     * Decodes Base64 image to binary.
     */
    public static byte[] decodeBase64Image(String base64Image) {
        return Base64.getDecoder().decode(base64Image);
    }
    
    /**
     * Detects image content type.
     */
    public static String detectContentType(byte[] imageData) throws IOException {
        return URLConnection.guessContentTypeFromStream(new ByteArrayInputStream(imageData));
    }
    
    /**
     * Checks if content type is valid image (JPEG/PNG).
     */
    public static boolean isValidImageType(String contentType) {
        return contentType != null && 
               (contentType.equals("image/jpeg") || 
                contentType.equals("image/jpg") || 
                contentType.equals("image/png"));
    }
    
    /**
     * Gets file extension from content type.
     */
    public static String getExtensionFromContentType(String contentType) {
        return contentType.equals("image/png") ? ".png" : ".jpg";
    }
}
