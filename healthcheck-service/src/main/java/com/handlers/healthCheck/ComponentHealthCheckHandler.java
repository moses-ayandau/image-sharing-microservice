package com.handlers.healthCheck;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.utils.CheckUtils;
import com.utils.Helper;
import com.utils.ResponseUtil;
import software.amazon.awssdk.regions.Region;
import java.util.HashMap;
import java.util.Map;

public class ComponentHealthCheckHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static final int CONNECTION_TIMEOUT_SECONDS = 5;
    private static final Region REGION = Region.US_EAST_1;
    private static final String DEFAULT_TABLE_NAME = "photo";

    private final Region region;

    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;
    private final EnvironmentProvider environmentProvider;

    public interface EnvironmentProvider {
        String getEnv(String name);
    }

    public static class DefaultEnvironmentProvider implements EnvironmentProvider {
        @Override
        public String getEnv(String name) {
            return System.getenv(name);
        }
    }

    public ComponentHealthCheckHandler() {
        this(new DefaultEnvironmentProvider());
    }

    public ComponentHealthCheckHandler(EnvironmentProvider environmentProvider) {
        this.environmentProvider = environmentProvider;
        this.region = REGION;
        this.objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent input, Context context) {
        context.getLogger().log("Processing component health check request");

        Map<String, String> headers = Helper.createResponseHeaders();
        String component = Helper.getComponentFromPath(input);
        Map<String, Object> healthResults = new HashMap<>();
        boolean allHealthy = true;

        try {
            CheckUtils checkHealth = new CheckUtils(environmentProvider);
            switch (component.toLowerCase()) {
                case "cognito":
                    allHealthy = checkHealth.checkCognitoHealth(context, healthResults);
                    break;
                case "s3":
                    allHealthy = checkHealth.checkS3Health(context, healthResults);
                    break;
                case "dynamodb":
                    allHealthy = checkHealth.checkDynamoDBHealth(context, healthResults);
                    break;
                case "all":
                    allHealthy = checkHealth.checkAllComponents(context, healthResults);
                    break;
                default:
                    return ResponseUtil.createErrorResponse(headers, "Invalid component specified: " + component);
            }

            return  ResponseUtil.createSuccessResponse(headers, healthResults, allHealthy, objectMapper);

        } catch (Exception e) {
            context.getLogger().log("Error during health checks: " + e.getMessage());
            return ResponseUtil.createErrorResponse(headers, "Health check failed: " + e.getMessage());
        }
    }

}