package com.util;

import org.junit.Test;
import static org.junit.Assert.*;

public class ImageUtilsTest {

    @Test
    public void testDecodeBase64Image() {
        String base64 = "SGVsbG8gV29ybGQ="; // "Hello World"
        byte[] decoded = ImageUtils.decodeBase64Image(base64);
        assertEquals("Hello World", new String(decoded));
    }

    @Test
    public void testIsValidImageType() {
        assertTrue(ImageUtils.isValidImageType("image/jpeg"));
        assertTrue(ImageUtils.isValidImageType("image/png"));
        assertFalse(ImageUtils.isValidImageType("text/plain"));
    }

    @Test
    public void testGetExtensionFromContentType() {
        assertEquals(".png", ImageUtils.getExtensionFromContentType("image/png"));
        assertEquals(".jpg", ImageUtils.getExtensionFromContentType("image/jpeg"));
    }
}
