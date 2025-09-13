package com.amalitechphotoappcognitoauth.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import java.util.Map;

@JsonDeserialize
@JsonIgnoreProperties(ignoreUnknown = true)
public class CognitoEvent {
    @JsonProperty("version")
    private String version;

    @JsonProperty("region")
    private String region;

    @JsonProperty("userPoolId")
    private String userPoolId;

    @JsonProperty("userName")
    private String userName;

    @JsonProperty("triggerSource")
    private String triggerSource;

    @JsonProperty("request")
    private Request request;

    @JsonProperty("response")
    private Map<String, String> response;

    @JsonProperty("callerContext")
    private Map<String, Object> callerContext;

    private Map<String, Object> userAttributes;

    public CognitoEvent() {}

    public CognitoEvent(Map<String, Object> userAttributes) {
        this.request = new Request();
        this.request.setUserAttributes(userAttributes);
    }

    public String getTriggerSource() {
        return triggerSource;
    }

    public Map<String, Object> getCallerContext() {
        return callerContext;
    }

    public Request getRequest() {
        return request;
    }

    public void setRequest(Request request) {
        this.request = request;
    }

    public String getUserEmail() {
        if (request != null && request.getUserAttributes() != null && request.getUserAttributes().containsKey("email")) {
            return request.getUserAttributes().get("email").toString();
        }
        return null;
    }

    public String getUserDisplayName() {
        if (request != null && request.getUserAttributes() != null) {
            if (request.getUserAttributes().containsKey("name") && request.getUserAttributes().get("name") != null) {
                return request.getUserAttributes().get("name").toString();
            } else if (request.getUserAttributes().containsKey("email")) {
                return request.getUserAttributes().get("email").toString();
            }
        }
        return "User";
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Request {
        @JsonProperty("userAttributes")
        private Map<String, Object> userAttributes;

        @JsonProperty("newDeviceUsed")
        private Boolean newDeviceUsed;

        @JsonProperty("code")
        private String code;

        @JsonProperty("type")
        private String type;

        @JsonProperty("clientMetadata")
        private Map<String, String> clientMetadata; // Added to handle clientMetadata field

        public Request() {
        }

        public Map<String, Object> getUserAttributes() {
            return userAttributes;
        }

        public void setUserAttributes(Map<String, Object> userAttributes) {
            this.userAttributes = userAttributes;
        }
    }
}