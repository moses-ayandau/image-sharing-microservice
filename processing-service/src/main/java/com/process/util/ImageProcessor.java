package com.process.util;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.logging.Logger;

public class ImageProcessor {
    private static final Logger logger = Logger.getLogger(ImageProcessor.class.getName());

    /**
     * Adds a watermark to an image
     *
     * @param imageBytes The original image as byte array
     * @param firstName User's first name for the watermark
     * @param lastName User's last name for the watermark
     * @return The watermarked image as byte array
     * @throws IOException If image processing fails
     */
    public byte[] addWatermark(byte[] imageBytes, String firstName, String lastName) throws IOException {
        if (imageBytes == null || imageBytes.length == 0) {
            throw new IOException("Image byte array is null or empty");
        }

        // Log image byte array size for debugging
        logger.info("Image byte array size: " + imageBytes.length + " bytes");

        // Try to detect image format before processing
        ByteArrayInputStream inputStream = new ByteArrayInputStream(imageBytes);

        // Check if this is a valid image by attempting to determine its format
        try {
            if (ImageIO.read(new ByteArrayInputStream(imageBytes.clone())) == null) {
                throw new IOException("Invalid image format or corrupted image data");
            }
        } catch (Exception e) {
            throw new IOException("Error validating image format: " + e.getMessage(), e);
        }

        // Reset the input stream
        inputStream.reset();

        // Read the image with explicit error checking
        BufferedImage sourceImage;
        try {
            sourceImage = ImageIO.read(inputStream);
            if (sourceImage == null) {
                throw new IOException("Failed to read image data - ImageIO returned null");
            }
        } catch (Exception e) {
            throw new IOException("Error reading image data: " + e.getMessage(), e);
        }

        // Log image dimensions for debugging
        logger.info("Read image successfully. Dimensions: " + sourceImage.getWidth() + "x" + sourceImage.getHeight());

        // Create a new image with watermark
        BufferedImage watermarkedImage = new BufferedImage(
                sourceImage.getWidth(),
                sourceImage.getHeight(),
                BufferedImage.TYPE_INT_RGB);

        // Draw the original image
        Graphics2D g2d = watermarkedImage.createGraphics();
        g2d.drawImage(sourceImage, 0, 0, null);

        // Set watermark properties
        g2d.setFont(new Font("Arial", Font.BOLD, 50));
        g2d.setColor(new Color(255, 255, 255, 128));

        // Create watermark text
        String date = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
        String watermarkText = firstName + " " + lastName + " - " + date;

        FontMetrics fontMetrics = g2d.getFontMetrics();
        int textWidth = fontMetrics.stringWidth(watermarkText);

        // Position watermark at bottom right
        int x = sourceImage.getWidth() - textWidth - 10;
        int y = sourceImage.getHeight() - 10;

        // Add watermark
        g2d.drawString(watermarkText, x, y);
        g2d.dispose();

        // Convert the watermarked image back to bytes
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ImageIO.write(watermarkedImage, "jpg", outputStream);

        return outputStream.toByteArray();
    }
}