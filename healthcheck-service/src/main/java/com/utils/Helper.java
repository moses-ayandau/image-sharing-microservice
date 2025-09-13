package com.utils;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;

import java.util.HashMap;
import java.util.Map;

public class Helper {

    public static Map<String, String> createResponseHeaders() {
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("Access-Control-Allow-Origin", "*");
        return headers;
    }

    public  static String getComponentFromPath(APIGatewayProxyRequestEvent input) {
        return input.getPathParameters() != null ?
                input.getPathParameters().get("component") : "all";
    }


}
