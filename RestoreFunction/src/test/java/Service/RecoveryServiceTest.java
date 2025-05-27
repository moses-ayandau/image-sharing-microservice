package Service;

import com.amazonaws.services.lambda.runtime.Context;
import com.google.gson.Gson;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClientBuilder;
import software.amazon.awssdk.services.cognitoidentityprovider.model.*;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class RecoveryServiceTest {

    private DynamoDbClient dynamoDbClient;
    
    private CognitoIdentityProviderClient cognitoClient;
    
    private Context context;
    
    private Gson gson;
    private String backupTable = "backup-table";
    private String userPoolId = "us-east-1_abcdef123";

    @BeforeEach
    void setUp() {
        gson = new Gson();
        dynamoDbClient = Mockito.mock(DynamoDbClient.class, Mockito.RETURNS_DEEP_STUBS);
        cognitoClient = Mockito.mock(CognitoIdentityProviderClient.class, Mockito.RETURNS_DEEP_STUBS);
        context = Mockito.mock(Context.class);
        when(context.getLogger()).thenReturn(new TestLogger());
    }

    @Test
    void testGetLatestBackupId() {
        // Setup mock response
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("BackupId", AttributeValue.builder().s("backup-123").build());
        
        QueryResponse queryResponse = QueryResponse.builder()
                .items(List.of(item))
                .build();
        
        when(dynamoDbClient.query(any(QueryRequest.class))).thenReturn(queryResponse);
        
        // Execute test
        String backupId = RecoveryService.getLatestBackupId(context, backupTable, userPoolId, dynamoDbClient);
        
        // Verify
        assertEquals("backup-123", backupId);
        verify(dynamoDbClient).query(any(QueryRequest.class));
    }
    
    @Test
    void testGetLatestBackupIdNoBackups() {
        // Setup empty response
        QueryResponse queryResponse = QueryResponse.builder()
                .items(List.of())
                .build();
        
        when(dynamoDbClient.query(any(QueryRequest.class))).thenReturn(queryResponse);
        
        // Execute and verify exception
        assertThrows(RuntimeException.class, () -> 
            RecoveryService.getLatestBackupId(context, backupTable, userPoolId, dynamoDbClient)
        );
    }
    
    @Test
    void testRestoreUsersFromBackupNonChunked() {
        // Setup metadata response
        Map<String, AttributeValue> metadata = new HashMap<>();
        metadata.put("BackupId", AttributeValue.builder().s("backup-123").build());
        metadata.put("ChunkId", AttributeValue.builder().s("0").build());
        metadata.put("IsMetadata", AttributeValue.builder().bool(false).build());
        metadata.put("TotalChunks", AttributeValue.builder().n("1").build());
        
        // User data with proper structure - each user is a Map, not an array of attributes
        String userData = "[{\"username\":\"user1\",\"attributes\":{\"email\":\"user1@example.com\",\"name\":\"User One\"}}]";
        metadata.put("Data", AttributeValue.builder().s(userData).build());
        
        GetItemResponse metadataResponse = GetItemResponse.builder()
                .item(metadata)
                .build();
        
        when(dynamoDbClient.getItem(any(GetItemRequest.class))).thenReturn(metadataResponse);
        
        // Mock successful user creation
        when(cognitoClient.adminCreateUser(any(AdminCreateUserRequest.class)))
                .thenReturn(AdminCreateUserResponse.builder().user(UserType.builder().build()).build());
        
        // Mock the static builder pattern
        try (MockedStatic<CognitoIdentityProviderClient> mockedStatic = mockStatic(CognitoIdentityProviderClient.class)) {
            // Mock the builder
            CognitoIdentityProviderClientBuilder mockBuilder = mock(CognitoIdentityProviderClientBuilder.class);
            when(CognitoIdentityProviderClient.builder()).thenReturn(mockBuilder);
            when(mockBuilder.build()).thenReturn(cognitoClient);
            
            // Execute test
            int restored = RecoveryService.restoreUsersFromBackup("backup-123", context, backupTable, 
                    userPoolId, dynamoDbClient, gson);
            
            // Verify
            assertEquals(1, restored);
            verify(dynamoDbClient).getItem(any(GetItemRequest.class));
            verify(cognitoClient).adminCreateUser(any(AdminCreateUserRequest.class));
        }
    }
    
    @Test
    void testRestoreUsersFromBackupChunked() {
        // Setup metadata response
        Map<String, AttributeValue> metadata = new HashMap<>();
        metadata.put("BackupId", AttributeValue.builder().s("backup-123").build());
        metadata.put("ChunkId", AttributeValue.builder().s("0").build());
        metadata.put("IsMetadata", AttributeValue.builder().bool(true).build());
        metadata.put("TotalChunks", AttributeValue.builder().n("2").build());
        
        GetItemResponse metadataResponse = GetItemResponse.builder()
                .item(metadata)
                .build();
        
        // Setup chunk responses with proper JSON structure
        Map<String, AttributeValue> chunk1 = new HashMap<>();
        chunk1.put("BackupId", AttributeValue.builder().s("backup-123").build());
        chunk1.put("ChunkId", AttributeValue.builder().s("1").build());
        chunk1.put("Data", AttributeValue.builder().s("[{\"username\":\"user1\",\"attributes\":{\"email\":").build());
        
        GetItemResponse chunk1Response = GetItemResponse.builder()
                .item(chunk1)
                .build();
        
        Map<String, AttributeValue> chunk2 = new HashMap<>();
        chunk2.put("BackupId", AttributeValue.builder().s("backup-123").build());
        chunk2.put("ChunkId", AttributeValue.builder().s("2").build());
        chunk2.put("Data", AttributeValue.builder().s("\"user1@example.com\",\"name\":\"User One\"}}]").build());
        
        GetItemResponse chunk2Response = GetItemResponse.builder()
                .item(chunk2)
                .build();
        
        // Configure mock to return different responses based on input
        when(dynamoDbClient.getItem(any(GetItemRequest.class)))
                .thenAnswer(invocation -> {
                    GetItemRequest request = invocation.getArgument(0);
                    String chunkId = request.key().get("ChunkId").s();
                    
                    if (chunkId.equals("0")) return metadataResponse;
                    if (chunkId.equals("1")) return chunk1Response;
                    if (chunkId.equals("2")) return chunk2Response;
                    
                    return GetItemResponse.builder().build();
                });
        
        // Mock successful user creation
        when(cognitoClient.adminCreateUser(any(AdminCreateUserRequest.class)))
                .thenReturn(AdminCreateUserResponse.builder().user(UserType.builder().build()).build());
        
        // Mock the static builder pattern
        try (MockedStatic<CognitoIdentityProviderClient> mockedStatic = mockStatic(CognitoIdentityProviderClient.class)) {
            // Mock the builder
            CognitoIdentityProviderClientBuilder mockBuilder = mock(CognitoIdentityProviderClientBuilder.class);
            when(CognitoIdentityProviderClient.builder()).thenReturn(mockBuilder);
            when(mockBuilder.build()).thenReturn(cognitoClient);
            
            // Execute test
            int restored = RecoveryService.restoreUsersFromBackup("backup-123", context, backupTable, 
                    userPoolId, dynamoDbClient, gson);
            
            // Verify
            assertEquals(1, restored);
            verify(dynamoDbClient, times(3)).getItem(any(GetItemRequest.class));
            verify(cognitoClient).adminCreateUser(any(AdminCreateUserRequest.class));
        }
    }
    
    // Helper class for mocking Lambda context logger
    private static class TestLogger implements com.amazonaws.services.lambda.runtime.LambdaLogger {
        @Override
        public void log(String message) {
            System.out.println(message);
        }
        
        @Override
        public void log(byte[] message) {
            System.out.println(new String(message));
        }
    }
}
