package factories;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

public class DynamodbFactory {
    public static DynamoDbClient createClient(){
        return DynamoDbClient.builder()
                .region(Region.EU_CENTRAL_1)
                .build();
    }

}
