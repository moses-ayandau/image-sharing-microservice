package com.process.util;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class ImageProcessor {

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
        // Read the image
        BufferedImage sourceImage = ImageIO.read(new ByteArrayInputStream(imageBytes));

        // Create a new image with watermark
        BufferedImage watermarkedImage = new BufferedImage(
                sourceImage.getWidth(),
                sourceImage.getHeight(),
                BufferedImage.TYPE_INT_RGB);

        // Draw the original image
        Graphics2D g2d = watermarkedImage.createGraphics();
        g2d.drawImage(sourceImage, 0, 0, null);

        // Set watermark properties
        g2d.setFont(new Font("Arial", Font.BOLD, 30));
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