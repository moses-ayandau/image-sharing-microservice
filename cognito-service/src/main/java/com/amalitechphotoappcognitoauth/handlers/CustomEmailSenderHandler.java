//package com.amalitechphotoappcognitoauth.handlers;
//
//import com.amazonaws.encryptionsdk.AwsCrypto;
//import com.amazonaws.encryptionsdk.CommitmentPolicy;
//import com.amazonaws.encryptionsdk.CryptoResult;
//import com.amazonaws.encryptionsdk.kms.KmsMasterKeyProvider;
//import com.amazonaws.services.lambda.runtime.Context;
//import com.amazonaws.services.lambda.runtime.RequestHandler;
//import com.fasterxml.jackson.databind.ObjectMapper;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import software.amazon.awssdk.regions.Region;
//import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
//import software.amazon.awssdk.services.cognitoidentityprovider.model.ListUserPoolClientsRequest;
//import software.amazon.awssdk.services.cognitoidentityprovider.model.ListUserPoolClientsResponse;
//import software.amazon.awssdk.services.cognitoidentityprovider.model.UserPoolClientDescription;
//
//import com.amalitechphotoappcognitoauth.models.CognitoEvent;
//import com.amalitechphotoappcognitoauth.models.EmailRequest;
//import com.amalitechphotoappcognitoauth.services.EmailService;
//
//import java.nio.charset.StandardCharsets;
//import java.util.Base64;
//
//public class CustomEmailSenderHandler implements RequestHandler<Object, Object> {
//    private static final Logger logger = LoggerFactory.getLogger(CustomEmailSenderHandler.class);
//    private static final ObjectMapper objectMapper = new ObjectMapper();
//    private final EmailService emailService;
//    private final String kmsKeyId;
//    private final String environment;
//
//    public CustomEmailSenderHandler() {
//        this.emailService = new EmailService();
//        this.kmsKeyId = System.getenv("KMS_KEY_ID");
//        this.environment = System.getenv("ENVIRONMENT");
//    }
//
//    @Override
//    public Object handleRequest(Object input, Context context) {
//        logger.info("Processing custom email sender request with input: {}", input);
//
//        try {
//            CognitoEvent event = objectMapper.readValue(objectMapper.writeValueAsString(input), CognitoEvent.class);
//
//            // Extract userPoolId and awsRegion from the event
//            String userPoolId = event.getUserPoolId();
//            String awsRegion = event.getRegion();
//
//            // Extract awsAccountId from the Lambda context
//            String awsAccountId = context.getInvokedFunctionArn().split(":")[4];
//
//            // Validate extracted values and environment variables
//            if (userPoolId == null || awsRegion == null || awsAccountId == null || kmsKeyId == null || environment == null) {
//                logger.error("Missing required data: userPoolId={}, awsRegion={}, awsAccountId={}, kmsKeyId={}, environment={}",
//                        userPoolId, awsRegion, awsAccountId, kmsKeyId, environment);
//                throw new IllegalStateException("Missing required data");
//            }
//
//            String triggerSource = event.getTriggerSource();
//
//            if ("CustomEmailSender_SignUp".equals(triggerSource) || "CustomEmailSender_ResendCode".equals(triggerSource)) {
//                String email = event.getUserEmail();
//                String userName = event.getUserName();
//                String encryptedCode = event.getRequest().getCode();
//
//                if (email == null || userName == null || encryptedCode == null) {
//                    logger.error("Missing required event data: email={}, userName={}, encryptedCode={}", email, userName, encryptedCode);
//                    throw new IllegalArgumentException("Missing required event data");
//                }
//
//                String code = decryptCode(encryptedCode, awsRegion, awsAccountId);
//
//                // Construct Cognito domain dynamically
//                String cognitoDomain = String.format("https://photo-blog-auth-%s-%s.auth.%s.amazoncognito.com",
//                        environment, awsAccountId, awsRegion);
//
//                // Fetch ClientId dynamically
//                String clientId = getClientId(userPoolId, awsRegion);
//
//                String confirmationUrl = cognitoDomain + "/confirmUser?client_id=" + clientId + "&user_name=" + userName + "&confirmation_code=" + code;
//
//                String subject = triggerSource.equals("CustomEmailSender_SignUp") ? "Confirm your account" : "Resend Confirmation Code";
//                String htmlBody = "Click this link to confirm your account: <a href=\"" + confirmationUrl + "\">Confirm your account</a>";
//                EmailRequest emailRequest = new EmailRequest(email, subject, htmlBody);
//                boolean result = emailService.sendEmail(emailRequest);
//
//                if (result) {
//                    logger.info("Verification email sent successfully to: {}", email);
//                } else {
//                    logger.error("Failed to send verification email to: {}", email);
//                }
//            } else {
//                logger.info("Not a supported event. Skipping.");
//            }
//        } catch (Exception e) {
//            logger.error("Error processing custom email sender", e);
//        } finally {
//            emailService.close();
//        }
//        return input;
//    }
//
//    private String decryptCode(String encryptedCode, String awsRegion, String awsAccountId) {
//        try {
//            // Create AwsCrypto with CommitmentPolicy set to ForbidEncryptAllowDecrypt
//            AwsCrypto crypto = AwsCrypto.builder()
//                    .withCommitmentPolicy(CommitmentPolicy.ForbidEncryptAllowDecrypt)
//                    .build();
//
//            // Construct the full KMS key ARN
//            String kmsKeyArn = String.format("arn:aws:kms:%s:%s:key/%s", awsRegion, awsAccountId, kmsKeyId);
//            logger.info("Using KMS key ARN: {}", kmsKeyArn);
//
//            KmsMasterKeyProvider keyProvider = KmsMasterKeyProvider.builder()
//                    .buildStrict(kmsKeyArn);
//
//            byte[] encryptedBytes = Base64.getDecoder().decode(encryptedCode);
//            CryptoResult<byte[], ?> result = crypto.decryptData(keyProvider, encryptedBytes);
//            return new String(result.getResult(), StandardCharsets.UTF_8);
//        } catch (Exception e) {
//            logger.error("Failed to decrypt code", e);
//            throw new RuntimeException("Failed to decrypt code", e);
//        }
//    }
//
//    private String getClientId(String userPoolId, String awsRegion) {
//        try (CognitoIdentityProviderClient cognitoClient = CognitoIdentityProviderClient.builder()
//                .region(Region.of(awsRegion))
//                .build()) {
//            ListUserPoolClientsRequest request = ListUserPoolClientsRequest.builder()
//                    .userPoolId(userPoolId)
//                    .maxResults(10)
//                    .build();
//            ListUserPoolClientsResponse response = cognitoClient.listUserPoolClients(request);
//            for (UserPoolClientDescription client : response.userPoolClients()) {
//                if (client.clientName().equals("PhotoBlogAppClient-" + environment)) {
//                    return client.clientId();
//                }
//            }
//            logger.error("No matching user pool client found for clientName: PhotoBlogAppClient-{}", environment);
//            throw new RuntimeException("No matching user pool client found");
//        } catch (Exception e) {
//            logger.error("Failed to fetch ClientId", e);
//            throw new RuntimeException("Failed to fetch ClientId", e);
//        }
//    }
//}