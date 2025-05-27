package com.process.util;

import com.amazonaws.services.lambda.runtime.Context;
import com.process.service.DynamoDbService;
import com.process.service.EmailService;
import com.process.service.S3Service;
import com.process.service.SqsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ProcessImageTest {

    @Mock
    private S3Service s3Service;

    @Mock
    private DynamoDbService dynamoDbService;

    @Mock
    private EmailService emailService;

    @Mock
    private ImageProcessor imageProcessor;

    @Mock
    private SqsService sqsService;

    @Mock
    private Context context;

    @InjectMocks
    private ProcessImage processImage;

    private static final String BUCKET = "test-bucket";
    private static final String KEY = "test-image.jpg";
    private static final String USER_ID = "user123";
    private static final String EMAIL = "test@example.com";
    private static final String FIRST_NAME = "John";
    private static final String LAST_NAME = "Doe";
    private static final String IMAGE_TITLE = "Test Image";
    private static final String PROCESSED_BUCKET = "processed-bucket";
    private static final String AWS_REGION = "us-east-1";

    @BeforeEach
    void setUp() {
        System.setProperty("PROCESSED_BUCKET", PROCESSED_BUCKET);
        System.setProperty("AWS_REGION", AWS_REGION);
    }

    @Test
    void testProcessImage_SuccessfulProcessing_FirstAttempt() throws IOException {

        byte[] imageData = new byte[]{1, 2, 3};
        byte[] watermarkedImage = new byte[]{4, 5, 6};

        when(s3Service.getImageFromS3(BUCKET, KEY)).thenReturn(imageData);
        when(imageProcessor.addWatermark(imageData, FIRST_NAME, LAST_NAME)).thenReturn(watermarkedImage);


        processImage.processImage(context, BUCKET, KEY, USER_ID, EMAIL, FIRST_NAME, LAST_NAME, IMAGE_TITLE);


        verify(emailService).sendProcessingStartEmail(eq(EMAIL), eq(FIRST_NAME));
        verify(s3Service).getImageFromS3(eq(BUCKET), eq(KEY));
        verify(imageProcessor).addWatermark(eq(imageData), eq(FIRST_NAME), eq(LAST_NAME));
        verify(s3Service).uploadToProcessedBucket(eq(watermarkedImage), anyString());
        verify(dynamoDbService).storeImageMetadata(eq(USER_ID), anyString(), eq(IMAGE_TITLE), anyString());
        verify(s3Service).deleteFromStagingBucket(eq(BUCKET), eq(KEY));
        verify(emailService).sendProcessingCompleteEmail(eq(EMAIL), eq(FIRST_NAME), anyString());
        verifyNoInteractions(sqsService);
    }



    @Test
    void testProcessImage_WatermarkingFails() throws IOException {

        byte[] imageData = new byte[]{1, 2, 3};
        when(s3Service.getImageFromS3(BUCKET, KEY)).thenReturn(imageData);
        when(imageProcessor.addWatermark(imageData, FIRST_NAME, LAST_NAME)).thenReturn(null);


        processImage.processImage(context, BUCKET, KEY, USER_ID, EMAIL, FIRST_NAME, LAST_NAME, IMAGE_TITLE);


        verify(emailService).sendProcessingStartEmail(eq(EMAIL), eq(FIRST_NAME));
        verify(s3Service).getImageFromS3(eq(BUCKET), eq(KEY));
        verify(imageProcessor).addWatermark(eq(imageData), eq(FIRST_NAME), eq(LAST_NAME));
        verifyNoInteractions(dynamoDbService, sqsService);
        verify(emailService, never()).sendProcessingCompleteEmail(anyString(), anyString(), anyString());
    }

    @Test
    void testProcessImage_FinalRetryAttempt() throws IOException {

        byte[] imageData = new byte[]{1, 2, 3};
        when(s3Service.getImageFromS3(BUCKET, KEY)).thenThrow(new RuntimeException("S3 error"));


        assertThrows(RuntimeException.class, () -> processImage.processImage(context, BUCKET, KEY, USER_ID, EMAIL, FIRST_NAME, LAST_NAME, IMAGE_TITLE, 5));


        verify(emailService, never()).sendProcessingStartEmail(anyString(), anyString());
        verify(emailService).sendProcessingFailureEmail(eq(EMAIL), eq(FIRST_NAME));
        verify(s3Service).getImageFromS3(eq(BUCKET), eq(KEY));
        verifyNoInteractions(imageProcessor, dynamoDbService);
    }

    @Test
    void testProcessImage_ExceedMaxRetries() throws IOException {

        when(s3Service.getImageFromS3(BUCKET, KEY)).thenThrow(new RuntimeException("S3 error"));


        assertThrows(RuntimeException.class, () -> processImage.processImage(context, BUCKET, KEY, USER_ID, EMAIL, FIRST_NAME, LAST_NAME, IMAGE_TITLE, 6));


        verify(emailService, never()).sendProcessingStartEmail(anyString(), anyString());
        verify(emailService).sendProcessingFailureEmail(eq(EMAIL), eq(FIRST_NAME));
        verify(sqsService).queueForRetry(eq(BUCKET), eq(KEY), eq(USER_ID), eq(EMAIL), eq(FIRST_NAME), eq(LAST_NAME), eq(IMAGE_TITLE), eq(7));
        verify(s3Service).getImageFromS3(eq(BUCKET), eq(KEY));
        verifyNoInteractions(imageProcessor, dynamoDbService);
    }


    @Test
    void testProcessImage_CompletionEmailException() throws IOException {

        byte[] imageData = new byte[]{1, 2, 3};
        byte[] watermarkedImage = new byte[]{4, 5, 6};
        when(s3Service.getImageFromS3(BUCKET, KEY)).thenReturn(imageData);
        when(imageProcessor.addWatermark(imageData, FIRST_NAME, LAST_NAME)).thenReturn(watermarkedImage);
        doThrow(new RuntimeException("Email error")).when(emailService).sendProcessingCompleteEmail(eq(EMAIL), eq(FIRST_NAME), anyString());


        processImage.processImage(context, BUCKET, KEY, USER_ID, EMAIL, FIRST_NAME, LAST_NAME, IMAGE_TITLE);


        verify(emailService).sendProcessingStartEmail(eq(EMAIL), eq(FIRST_NAME));
        verify(s3Service).getImageFromS3(eq(BUCKET), eq(KEY));
        verify(imageProcessor).addWatermark(eq(imageData), eq(FIRST_NAME), eq(LAST_NAME));
        verify(s3Service).uploadToProcessedBucket(eq(watermarkedImage), anyString());
        verify(dynamoDbService).storeImageMetadata(eq(USER_ID), anyString(), eq(IMAGE_TITLE), anyString());
        verify(s3Service).deleteFromStagingBucket(eq(BUCKET), eq(KEY));
        verify(emailService).sendProcessingCompleteEmail(eq(EMAIL), eq(FIRST_NAME), anyString());
        verifyNoInteractions(sqsService);
    }
}