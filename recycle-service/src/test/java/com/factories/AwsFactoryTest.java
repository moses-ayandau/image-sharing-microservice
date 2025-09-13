package com.factories;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClientBuilder;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AwsFactoryTest {

    @Mock
    private S3ClientBuilder mockS3Builder;

    @Mock
    private S3Client mockS3Client;

    @Mock
    private DynamoDbClientBuilder mockDynamoBuilder;

    @Mock
    private DynamoDbClient mockDynamoClient;

    @BeforeEach
    void setup() {
        mockS3Builder = mock(S3ClientBuilder.class);
        mockS3Client = mock(S3Client.class);

        mockDynamoBuilder = mock(DynamoDbClientBuilder.class);
        mockDynamoClient = mock(DynamoDbClient.class);
    }

    @Test
    void testS3Client_NotNull_AndConfiguredCorrectly() {
        try (MockedStatic<S3Client> staticS3 = Mockito.mockStatic(S3Client.class)) {
            staticS3.when(S3Client::builder).thenReturn(mockS3Builder);

            when(mockS3Builder.credentialsProvider(any(DefaultCredentialsProvider.class))).thenReturn(mockS3Builder);
            when(mockS3Builder.region(Region.US_EAST_1)).thenReturn(mockS3Builder);
            when(mockS3Builder.build()).thenReturn(mockS3Client);

            S3Client result = AwsFactory.s3Client();

            assertNotNull(result);
            assertEquals(mockS3Client, result);

            verify(mockS3Builder).credentialsProvider(any(DefaultCredentialsProvider.class));
            verify(mockS3Builder).region(Region.US_EAST_1);
            verify(mockS3Builder).build();
        }
    }

    @Test
    void testDynamoDbClient_NotNull_AndConfiguredCorrectly() {
        try (MockedStatic<DynamoDbClient> staticDynamo = Mockito.mockStatic(DynamoDbClient.class)) {
            staticDynamo.when(DynamoDbClient::builder).thenReturn(mockDynamoBuilder);

            when(mockDynamoBuilder.credentialsProvider(any(DefaultCredentialsProvider.class))).thenReturn(mockDynamoBuilder);
            when(mockDynamoBuilder.region(Region.US_EAST_1)).thenReturn(mockDynamoBuilder);
            when(mockDynamoBuilder.build()).thenReturn(mockDynamoClient);

            DynamoDbClient result = AwsFactory.dynamoDbClient();

            assertNotNull(result);
            assertEquals(mockDynamoClient, result);

            verify(mockDynamoBuilder).credentialsProvider(any(DefaultCredentialsProvider.class));
            verify(mockDynamoBuilder).region(Region.US_EAST_1);
            verify(mockDynamoBuilder).build();
        }
    }

}
