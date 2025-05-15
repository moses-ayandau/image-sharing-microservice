package com.process.util;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.logging.Logger;

public class ImageProcessor {
    private static final Logger logger = Logger.getLogger(ImageProcessor.class.getName());

    public byte[] addWatermark(byte[] imageBytes, String firstName, String lastName) throws IOException {
        if (imageBytes == null || imageBytes.length == 0) {
            throw new IOException("Image byte array is null or empty");
        }

        logger.info("Image byte array size: " + imageBytes.length + " bytes");

        ByteArrayInputStream inputStream = new ByteArrayInputStream(imageBytes);

        try {
            if (ImageIO.read(new ByteArrayInputStream(imageBytes.clone())) == null) {
                throw new IOException("Invalid image format or corrupted image data");
            }
        } catch (Exception e) {
            throw new IOException("Error validating image format: " + e.getMessage(), e);
        }

        inputStream.reset();

        BufferedImage sourceImage;
        try {
            sourceImage = ImageIO.read(inputStream);
            if (sourceImage == null) {
                throw new IOException("Failed to read image data - ImageIO returned null");
            }
        } catch (Exception e) {
            throw new IOException("Error reading image data: " + e.getMessage(), e);
        }

        logger.info("Read image successfully. Dimensions: " + sourceImage.getWidth() + "x" + sourceImage.getHeight());

        // Create a new buffered image with the same dimensions
        BufferedImage watermarkedImage = new BufferedImage(
                sourceImage.getWidth(),
                sourceImage.getHeight(),
                BufferedImage.TYPE_INT_RGB);

        // Draw the original image onto the new image
        Graphics2D g2d = watermarkedImage.createGraphics();
        g2d.drawImage(sourceImage, 0, 0, null);

        // Set up the font for the watermark
        int fontSize = Math.max(sourceImage.getWidth(), sourceImage.getHeight()) / 20;
        g2d.setFont(new Font("Arial", Font.BOLD, fontSize));

        // Create the watermark text
        String date = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
        String watermarkText = firstName + " " + lastName + " - " + date;

        // Calculate text dimensions to center it
        FontMetrics fontMetrics = g2d.getFontMetrics();
        int textWidth = fontMetrics.stringWidth(watermarkText);
        int textHeight = fontMetrics.getHeight();

        // Calculate center position
        int x = (sourceImage.getWidth() - textWidth) / 2;
        int y = (sourceImage.getHeight() + textHeight / 2) / 2;

        // Create a semi-transparent outline effect for better visibility
        // First draw the outline/shadow
        g2d.setStroke(new BasicStroke(3.0f));
        g2d.setColor(new Color(0, 0, 0, 180)); // Dark shadow

        // Draw the text outline for better visibility against any background
        for (int i = -2; i <= 2; i++) {
            for (int j = -2; j <= 2; j++) {
                if (i != 0 || j != 0) { // Skip the center position
                    g2d.drawString(watermarkText, x + i, y + j);
                }
            }
        }

        // Now draw the main text in semi-transparent white
        g2d.setColor(new Color(255, 255, 255, 180));
        g2d.drawString(watermarkText, x, y);

        g2d.dispose();

        // Write the watermarked image to a byte array
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ImageIO.write(watermarkedImage, "jpg", outputStream);

        return outputStream.toByteArray();
    }
}