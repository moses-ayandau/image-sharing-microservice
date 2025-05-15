package com.factories;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClientBuilder;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.*;

class AwsFactoryTest {


    @Test
    void testS3Client_NotNull_AndConfiguredCorrectly() {
        S3ClientBuilder mockBuilder = mock(S3ClientBuilder.class);
        S3Client mockClient = mock(S3Client.class);

        try (MockedStatic<S3Client> mockedStatic = mockStatic(S3Client.class)) {
            mockedStatic.when(S3Client::builder).thenReturn(mockBuilder);

            when(mockBuilder.credentialsProvider(any(DefaultCredentialsProvider.class))).thenReturn(mockBuilder);
            when(mockBuilder.region(Region.US_EAST_1)).thenReturn(mockBuilder);
            when(mockBuilder.build()).thenReturn(mockClient);

            S3Client result = AwsFactory.s3Client();

            assertNotNull(result);
            assertEquals(mockClient, result);

            verify(mockBuilder).credentialsProvider(any(DefaultCredentialsProvider.class));
            verify(mockBuilder).region(Region.US_EAST_1);
            verify(mockBuilder).build();
        }
    }

    @Test
    void testDynamoDbClient_NotNull_AndConfiguredCorrectly() {
        DynamoDbClientBuilder mockBuilder = mock(DynamoDbClientBuilder.class);
        DynamoDbClient mockClient = mock(DynamoDbClient.class);

        try (MockedStatic<DynamoDbClient> mockedStatic = mockStatic(DynamoDbClient.class)) {
            mockedStatic.when(DynamoDbClient::builder).thenReturn(mockBuilder);

            when(mockBuilder.credentialsProvider(any(DefaultCredentialsProvider.class))).thenReturn(mockBuilder);
            when(mockBuilder.region(Region.US_EAST_1)).thenReturn(mockBuilder);
            when(mockBuilder.build()).thenReturn(mockClient);

            DynamoDbClient result = AwsFactory.dynamoDbClient();

            assertNotNull(result);
            assertEquals(mockClient, result);

            verify(mockBuilder).credentialsProvider(any(DefaultCredentialsProvider.class));
            verify(mockBuilder).region(Region.US_EAST_1);
            verify(mockBuilder).build();
        }
    }
}
