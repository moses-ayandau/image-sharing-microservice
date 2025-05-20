package com.amalitechphotoappcognitoauth.utils;

public class EmailTemplates {

    private static final String APP_URL = System.getenv("APP_URL") != null ?
            System.getenv("APP_URL") : "https://mscv2group2.link";

    private static final String EMAIL_HEADER = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Photo Blog App</title>
                <style>
                    body { 
                        font-family: Arial, sans-serif; 
                        line-height: 1.6; 
                        color: #333; 
                        max-width: 600px; 
                        margin: 0 auto;
                        padding: 20px;
                    }
                    .header { 
                        background-color: #c41061; 
                        color: white; 
                        padding: 20px; 
                        text-align: center; 
                        border-radius: 5px 5px 0 0;
                    }
                    .content { 
                        padding: 20px; 
                        border: 1px solid #ddd; 
                        border-top: none;
                        border-radius: 0 0 5px 5px;
                    }
                    .button {
                        display: inline-block;
                        background-color: #c41061;
                        color: white;
                        padding: 10px 20px;
                        text-decoration: none;
                        border-radius: 5px;
                        margin-top: 15px;
                    }
                    .footer {
                        margin-top: 20px;
                        font-size: 12px;
                        color: #777;
                        text-align: center;
                    }
                </style>
            </head>
            <body>
                <div class="header">
                    <h1>📸 Photo Blog App</h1>
                </div>
                <div class="content">
            """;

    private static final String EMAIL_FOOTER = """
                </div>
                <div class="footer">
                    <p>© 2025 Photo Blog App. All rights reserved.</p>
                    <p>If you did not request this email, please ignore it or contact support.</p>
                </div>
            </body>
            </html>
            """;

    /**
     * Creates a welcome email template for new users
     *
     * @param userName User's display name
     * @return HTML email body
     */
    public static String getWelcomeEmailTemplate(String userName) {
        String content = String.format("""
                <h2>Welcome to Photo Blog App, %s!</h2>
                <p>Thank you for signing up. We're excited to have you join our community!</p>
                <p>With Photo Blog App, you can:</p>
                <ul>
                    <li>Share your photos with friends and family</li>
                    <li>Create beautiful photo collections</li>
                    <li>Discover amazing photos from other users</li>
                </ul>
                <p>Click the button below to start your journey:</p>
                <p><a href="%s/auth" class="button">Login to Your Account</a></p>
                <p>If you have any questions or need assistance, please don't hesitate to contact our support team.</p>
                <p>Happy sharing!</p>
                <p>The Photo Blog App Team</p>
                """, userName, APP_URL);

        return EMAIL_HEADER + content + EMAIL_FOOTER;
    }

    /**
     * Creates a sign-in alert email template
     *
     * @param userName      User's display name
     * @param deviceDetails Device details from sign-in event
     * @param timestamp     Sign-in timestamp
     * @return HTML email body
     */
    public static String getSignInAlertTemplate(String userName, String deviceDetails, String timestamp) {
        String content = String.format("""
                <h2>New Sign-In Detected</h2>
                <p>Hello %s,</p>
                <p>We detected a new sign-in to your Photo Blog App account.</p>
                <p><strong>Time:</strong> %s</p>
                <p><strong>Device:</strong> %s</p>
                <p>If this was you, no action is needed. If you didn't sign in recently, please reset your password immediately.</p>
                <p><a href="%s/reset-password" class="button">Reset Password</a></p>
                <p>Security is our top priority. If you have any concerns, please contact our support team.</p>
                """, userName, timestamp, deviceDetails, APP_URL);

        return EMAIL_HEADER + content + EMAIL_FOOTER;
    }

}