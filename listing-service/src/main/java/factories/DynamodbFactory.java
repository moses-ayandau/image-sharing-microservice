package factories;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

public class DynamodbFactory {
    public static DynamoDbClient createClient() {
        // Get region from environment variable or use EU_CENTRAL_1 as default
        String regionName = System.getenv("AWS_REGION");
        Region region = (regionName != null && !regionName.isEmpty()) 
            ? Region.of(regionName) 
            : Region.US_EAST_1;
        
        return DynamoDbClient.builder()
                .region(region)
                .build();
    }

}
