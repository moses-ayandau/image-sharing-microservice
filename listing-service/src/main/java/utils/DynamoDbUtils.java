package utils;

import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import java.util.HashMap;
import java.util.Map;

public class DynamoDbUtils {
    
    /**
     * Converts a DynamoDB item to a regular Java Map
     * @param item The DynamoDB item to convert
     * @return A Map with String keys and Object values
     */
    public static Map<String, Object> convertDynamoItemToMap(Map<String, AttributeValue> item) {
        Map<String, Object> map = new HashMap<>();
        for (Map.Entry<String, AttributeValue> entry : item.entrySet()) {
            AttributeValue attributeValue = entry.getValue();
            if (attributeValue.s() != null) {
                map.put(entry.getKey(), attributeValue.s());
            } else if (attributeValue.n() != null) {
                map.put(entry.getKey(), attributeValue.n());
            } else if (attributeValue.bool() != null) {
                map.put(entry.getKey(), attributeValue.bool());
            } else if (attributeValue.m() != null) {
                map.put(entry.getKey(), convertDynamoItemToMap(attributeValue.m()));
            } else if (attributeValue.l() != null) {
                map.put(entry.getKey(), attributeValue.l());
            }
            // Add other types as needed
        }
        return map;
    }
}