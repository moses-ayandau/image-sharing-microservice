package upload.util;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import static org.junit.Assert.*;

public class ImageUtilsTest {

    @Test
    public void testDecodeBase64Image() {
        // "Hello World" in base64
        String base64String = "SGVsbG8gV29ybGQ=";
        byte[] decoded = ImageUtils.decodeBase64Image(base64String);
        
        assertEquals("Hello World", new String(decoded));
    }
    
    @Test
    public void testIsValidImageType() {
        assertTrue(ImageUtils.isValidImageType("image/jpeg"));
        assertTrue(ImageUtils.isValidImageType("image/jpg"));
        assertTrue(ImageUtils.isValidImageType("image/png"));
        assertFalse(ImageUtils.isValidImageType("image/gif"));
        assertFalse(ImageUtils.isValidImageType("text/plain"));
        assertFalse(ImageUtils.isValidImageType(null));
    }
    
    @Test
    public void testGetExtensionFromContentType() {
        assertEquals("jpg", ImageUtils.getExtensionFromContentType("image/jpeg"));
        assertEquals("jpg", ImageUtils.getExtensionFromContentType("image/jpg"));
        assertEquals("png", ImageUtils.getExtensionFromContentType("image/png"));
        assertEquals("", ImageUtils.getExtensionFromContentType("application/octet-stream"));
        assertEquals("", ImageUtils.getExtensionFromContentType(null));
    }
    
    @Test
    public void testDetectContentType() throws Exception {
        // This is a minimal valid PNG image in base64
        String pngBase64 = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==";
        byte[] pngData = ImageUtils.decodeBase64Image(pngBase64);
        
        String contentType = ImageUtils.detectContentType(pngData);
        assertEquals("image/png", contentType);
    }
    
    @Test
    public void testSanitizeFileName() {
        assertEquals("Hello_World", ImageUtils.sanitizeFileName("Hello World"));
        assertEquals("Hello_World", ImageUtils.sanitizeFileName("Hello/World"));
        assertEquals("Hello_World", ImageUtils.sanitizeFileName("Hello\\World"));
        assertEquals("Hello_World_", ImageUtils.sanitizeFileName("Hello World!"));
        assertEquals("Hello_World", ImageUtils.sanitizeFileName("Hello:World"));
        assertEquals("Hello-World", ImageUtils.sanitizeFileName("Hello-World"));
        assertEquals("Hello.World", ImageUtils.sanitizeFileName("Hello.World"));
    }
}
