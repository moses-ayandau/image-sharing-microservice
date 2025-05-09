package upload.util;

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
}