package factories;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

public class DynamodbFactory {
    private static final String regionName = System.getenv("AWS_REGION");
    private static final Region region = (regionName != null && !regionName.isEmpty())
            ? Region.of(regionName)
            : Region.US_EAST_1;
    private static final DynamoDbClient client = DynamoDbClient.builder()
            .region(region)
            .build();

    public static DynamoDbClient createClient() {
        return client;
    }

}
