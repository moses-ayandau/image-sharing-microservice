package com.amalitechphotoappcognitoauth.handlers;

import com.amalitechphotoappcognitoauth.services.BackupService;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.events.ScheduledEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BackupHandlerTest {

    @Mock
    private Context context;
    
    @Mock
    private LambdaLogger logger;
    
    @Mock
    private CognitoIdentityProviderClient cognitoClient;
    
    @Mock
    private DynamoDbClient dynamoDbClient;
    
    private BackupHandler handler;
    private ScheduledEvent event;

    @BeforeEach
    void setUp() throws Exception {
        // Mock logger
        when(context.getLogger()).thenReturn(logger);
        
        // Create handler with mocked AWS clients
        handler = new BackupHandler();
        
        // Use reflection to inject mocked clients
        Field cognitoClientField = BackupHandler.class.getDeclaredField("cognitoClient");
        cognitoClientField.setAccessible(true);
        cognitoClientField.set(handler, cognitoClient);
        
        Field dynamoDbClientField = BackupHandler.class.getDeclaredField("dynamoDbClient");
        dynamoDbClientField.setAccessible(true);
        dynamoDbClientField.set(handler, dynamoDbClient);
        
        // Set environment variables via reflection
        Field userPoolIdField = BackupHandler.class.getDeclaredField("userPoolId");
        userPoolIdField.setAccessible(true);
        userPoolIdField.set(handler, "test-user-pool-id");
        
        Field backupTableField = BackupHandler.class.getDeclaredField("backupTable");
        backupTableField.setAccessible(true);
        backupTableField.set(handler, "test-backup-table");
        
        // Create a sample scheduled event
        event = new ScheduledEvent();
    }

    @Test
    void handleRequest_Success() {
        try (MockedStatic<BackupService> mockedBackupService = mockStatic(BackupService.class)) {
            // Mock the static methods in BackupService
            mockedBackupService.when(() -> BackupService.backupUserPoolConfiguration(
                    any(), any(), anyString(), anyString(), any(), any()))
                    .thenReturn(null);
            
            mockedBackupService.when(() -> BackupService.backupUserData(
                    any(), any(), anyString(), anyString(), any(), any()))
                    .thenReturn(null);
            
            // Execute the handler
            String result = handler.handleRequest(event, context);
            
            // Verify the result
            assertEquals("Cognito user pool backup completed successfully", result);
            
            // Verify that both backup methods were called
            mockedBackupService.verify(() -> 
                    BackupService.backupUserPoolConfiguration(
                            eq(cognitoClient), 
                            eq(dynamoDbClient), 
                            eq("test-user-pool-id"), 
                            eq("test-backup-table"), 
                            any(), 
                            eq(context)));
            
            mockedBackupService.verify(() -> 
                    BackupService.backupUserData(
                            eq(cognitoClient), 
                            eq(dynamoDbClient), 
                            eq("test-user-pool-id"), 
                            eq("test-backup-table"), 
                            any(), 
                            eq(context)));
            
            // Verify logger was called
            verify(logger).log("Starting Cognito user pool backup for pool: test-user-pool-id");
            verify(logger).log("Cognito user pool backup completed successfully");
        }
    }

    @Test
    void handleRequest_BackupUserPoolConfigurationFails() {
        try (MockedStatic<BackupService> mockedBackupService = mockStatic(BackupService.class)) {
            // Mock the static methods in BackupService - first one throws exception
            mockedBackupService.when(() -> BackupService.backupUserPoolConfiguration(
                    any(), any(), anyString(), anyString(), any(), any()))
                    .thenThrow(new RuntimeException("Test exception"));
            
            // Execute the handler and verify it throws exception
            RuntimeException exception = assertThrows(RuntimeException.class, () -> 
                    handler.handleRequest(event, context));
            
            // Verify the exception message
            assertTrue(exception.getMessage().contains("Error during Cognito backup"));
            assertTrue(exception.getMessage().contains("Test exception"));
            
            // Verify that only the first backup method was called
            mockedBackupService.verify(() -> 
                    BackupService.backupUserPoolConfiguration(
                            eq(cognitoClient), 
                            eq(dynamoDbClient), 
                            eq("test-user-pool-id"), 
                            eq("test-backup-table"), 
                            any(), 
                            eq(context)));
            
            mockedBackupService.verify(() -> 
                    BackupService.backupUserData(
                            eq(cognitoClient), 
                            eq(dynamoDbClient), 
                            eq("test-user-pool-id"), 
                            eq("test-backup-table"), 
                            any(), 
                            eq(context)), 
                    never());
            
            // Verify logger was called with error message
            verify(logger).log("Starting Cognito user pool backup for pool: test-user-pool-id");
            verify(logger).log(contains("Error during Cognito backup"));
        }
    }

    @Test
    void handleRequest_BackupUserDataFails() {
        try (MockedStatic<BackupService> mockedBackupService = mockStatic(BackupService.class)) {
            // Mock the static methods in BackupService - second one throws exception
            mockedBackupService.when(() -> BackupService.backupUserPoolConfiguration(
                    any(), any(), anyString(), anyString(), any(), any()))
                    .thenReturn(null);
            
            mockedBackupService.when(() -> BackupService.backupUserData(
                    any(), any(), anyString(), anyString(), any(), any()))
                    .thenThrow(new RuntimeException("Test exception"));
            
            // Execute the handler and verify it throws exception
            RuntimeException exception = assertThrows(RuntimeException.class, () -> 
                    handler.handleRequest(event, context));
            
            // Verify the exception message
            assertTrue(exception.getMessage().contains("Error during Cognito backup"));
            assertTrue(exception.getMessage().contains("Test exception"));
            
            // Verify that both backup methods were called
            mockedBackupService.verify(() -> 
                    BackupService.backupUserPoolConfiguration(
                            eq(cognitoClient), 
                            eq(dynamoDbClient), 
                            eq("test-user-pool-id"), 
                            eq("test-backup-table"), 
                            any(), 
                            eq(context)));
            
            mockedBackupService.verify(() -> 
                    BackupService.backupUserData(
                            eq(cognitoClient), 
                            eq(dynamoDbClient), 
                            eq("test-user-pool-id"), 
                            eq("test-backup-table"), 
                            any(), 
                            eq(context)));
            
            // Verify logger was called with error message
            verify(logger).log("Starting Cognito user pool backup for pool: test-user-pool-id");
            verify(logger).log(contains("Error during Cognito backup"));
        }
    }
}