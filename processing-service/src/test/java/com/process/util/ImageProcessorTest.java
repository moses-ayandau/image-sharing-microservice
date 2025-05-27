package com.process.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ImageProcessorTest {

    private ImageProcessor imageProcessor;

    private BufferedImage testImage;
    private byte[] testImageBytes;

    @BeforeEach
    void setUp() throws IOException {
        imageProcessor = new ImageProcessor();

        testImage = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = testImage.createGraphics();
        g2d.setColor(Color.WHITE);
        g2d.fillRect(0, 0, 100, 100);
        g2d.dispose();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(testImage, "jpg", baos);
        testImageBytes = baos.toByteArray();
    }

    @Test
    void addWatermark_ValidImageAndNames_ReturnsWatermarkedImage() throws IOException {

        String firstName = "John";
        String lastName = "Doe";
        String date = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
        String expectedWatermark = firstName + " " + lastName + " - " + date;


        byte[] result = imageProcessor.addWatermark(testImageBytes, firstName, lastName);


        assertNotNull(result);
        assertTrue(result.length > 0);

        BufferedImage resultImage = ImageIO.read(new ByteArrayInputStream(result));
        assertNotNull(resultImage);
        assertEquals(testImage.getWidth(), resultImage.getWidth());
        assertEquals(testImage.getHeight(), resultImage.getHeight());

        assertFalse(java.util.Arrays.equals(testImageBytes, result));
    }

    @Test
    void addWatermark_NullImageBytes_ThrowsIOException() {

        IOException exception = assertThrows(IOException.class, () -> {
            imageProcessor.addWatermark(null, "John", "Doe");
        });
        assertEquals("Image byte array is null or empty", exception.getMessage());
    }

    @Test
    void addWatermark_EmptyImageBytes_ThrowsIOException() {

        IOException exception = assertThrows(IOException.class, () -> {
            imageProcessor.addWatermark(new byte[0], "John", "Doe");
        });
        assertEquals("Image byte array is null or empty", exception.getMessage());
    }

    @Test
    void addWatermark_InvalidImageFormat_ThrowsIOException() throws IOException {

        byte[] invalidImageBytes = new byte[]{1, 2, 3};

        try (MockedStatic<ImageIO> mockedImageIO = mockStatic(ImageIO.class)) {
            mockedImageIO.when(() -> ImageIO.read(any(ByteArrayInputStream.class)))
                    .thenReturn(null);


            IOException exception = assertThrows(IOException.class, () -> {
                imageProcessor.addWatermark(invalidImageBytes, "John", "Doe");
            });
            assertEquals("Error validating image format: Invalid image format or corrupted image data", exception.getMessage());
        }
    }

    @Test
    void addWatermark_ImageIOReadFails_ThrowsIOException() throws IOException {

        try (MockedStatic<ImageIO> mockedImageIO = mockStatic(ImageIO.class)) {
            mockedImageIO.when(() -> ImageIO.read(any(ByteArrayInputStream.class)))
                    .thenThrow(new IOException("Read error"));


            IOException exception = assertThrows(IOException.class, () -> {
                imageProcessor.addWatermark(testImageBytes, "John", "Doe");
            });
            assertTrue(exception.getMessage().contains("Error validating image format"));
        }
    }

    @Test
    void addWatermark_ValidImageWithNullNames_HandlesGracefully() throws IOException {

        String firstName = null;
        String lastName = null;
        String date = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
        String expectedWatermark = "null null - " + date;


        byte[] result = imageProcessor.addWatermark(testImageBytes, firstName, lastName);


        assertNotNull(result);
        assertTrue(result.length > 0);


        BufferedImage resultImage = ImageIO.read(new ByteArrayInputStream(result));
        assertNotNull(resultImage);
        assertEquals(testImage.getWidth(), resultImage.getWidth());
        assertEquals(testImage.getHeight(), resultImage.getHeight());
    }

    @Test
    void addWatermark_LargeImage_AppliesWatermarkWithCorrectFontSize() throws IOException {

        BufferedImage largeImage = new BufferedImage(1000, 1000, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = largeImage.createGraphics();
        g2d.setColor(Color.WHITE);
        g2d.fillRect(0, 0, 1000, 1000);
        g2d.dispose();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(largeImage, "jpg", baos);
        byte[] largeImageBytes = baos.toByteArray();

        String firstName = "Jane";
        String lastName = "Smith";


        byte[] result = imageProcessor.addWatermark(largeImageBytes, firstName, lastName);


        assertNotNull(result);
        assertTrue(result.length > 0);

        BufferedImage resultImage = ImageIO.read(new ByteArrayInputStream(result));
        assertNotNull(resultImage);
        assertEquals(1000, resultImage.getWidth());
        assertEquals(1000, resultImage.getHeight());
    }
}