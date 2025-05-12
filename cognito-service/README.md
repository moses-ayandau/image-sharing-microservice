# Photo Blog Authentication Service

This project implements a serverless authentication service for the Photo Blog application using AWS Cognito, Lambda, and SES.

## Features

- User signup and signin using Amazon Cognito
- Email verification for new accounts
- Welcome emails sent to new users upon account confirmation
- Login alert emails sent to users when they sign in
- Daily backups of the Cognito user pool data
- Direct integration with frontend applications using AWS Amplify

## Project Structure

```
photo-blog-auth/
├── src/
│   └── main/
│       ├── java/
│       │   └── com/
│       │       └── amalitechphotoappcognitoauth/
│       │           ├── EmailService.java         # Utility for sending emails via SES
│       │           ├── SigninAlertHandler.java   # Lambda for sending login alerts
│       │           └── WelcomeEmailHandler.java  # Lambda for sending welcome emails
│       └── resources/
│           └── log4j2.xml                        # Logging configuration
├── backup_script/
│   ├── index.py                                  # Python script for Cognito backups
│   └── requirements.txt                          # Python dependencies
├── template.yaml                                 # SAM template for AWS resources
├── pom.xml                                       # Maven project configuration
└── README.md                                     # Project documentation
```

## Deployment

### Prerequisites

- AWS CLI installed and configured
- AWS SAM CLI installed
- Java 21 JDK
- Maven

### Deployment Steps

1. Build the project:
   ```
   mvn clean package
   ```

2. Deploy using SAM:
   ```
   sam deploy --guided
   ```

3. During the guided deployment, you'll be prompted for:
   - Stack name (e.g., photo-blog-auth)
   - AWS Region
   - Environment parameter (dev, test, or prod)
   - Email domain for sending notifications

## Frontend Integration with AWS Amplify

To integrate the authentication service with your frontend application using AWS Amplify:

1. Install the Amplify libraries:
   ```
   npm install aws-amplify
   ```

2. Configure Amplify in your application:

```javascript
import { Amplify } from 'aws-amplify';

Amplify.configure({
  Auth: {
    region: 'YOUR_AWS_REGION',
    userPoolId: 'YOUR_USER_POOL_ID',
    userPoolWebClientId: 'YOUR_USER_POOL_CLIENT_ID',
    oauth: {
      domain: 'YOUR_COGNITO_DOMAIN',
      scope: ['email', 'profile', 'openid'],
      redirectSignIn: 'https://your-app-url.com/auth/callback',
      redirectSignOut: 'https://your-app-url.com/auth/logout',
      responseType: 'code'
    }
  }
});
```

3. Use Amplify Auth in your components:

```javascript
import { Auth } from 'aws-amplify';

// Sign up
async function signUp(username, password, email) {
  try {
    const { user } = await Auth.signUp({
      username,
      password,
      attributes: {
        email
      }
    });
    console.log('Sign up successful:', user);
  } catch (error) {
    console.error('Error signing up:', error);
  }
}

// Confirm sign up
async function confirmSignUp(username, code) {
  try {
    await Auth.confirmSignUp(username, code);
    console.log('Confirmation successful');
  } catch (error) {
    console.error('Error confirming sign up:', error);
  }
}

// Sign in
async function signIn(username, password) {
  try {
    const user = await Auth.signIn(username, password);
    console.log('Sign in successful:', user);
  } catch (error) {
    console.error('Error signing in:', error);
  }
}

// Sign out
async function signOut() {
  try {
    await Auth.signOut();
    console.log('Sign out successful');
  } catch (error) {
    console.error('Error signing out:', error);
  }
}

// Get current authenticated user
async function getCurrentUser() {
  try {
    const user = await Auth.currentAuthenticatedUser();
    console.log('Current user:', user);
    return user;
  } catch (error) {
    console.error('Error getting current user:', error);
    return null;
  }
}
```

## Testing

To test the authentication service:

1. Run unit tests:
   ```
   mvn test
   ```

2. Test the Cognito user pool:
   - Create a test user using the AWS Console or AWS CLI
   - Verify that the welcome email is sent upon confirmation
   - Sign in with the test user and verify that the login alert email is sent

3. Test the backup script:
   - Manually invoke the backup Lambda function
   - Verify that the backup file is created in the S3 bucket

## Troubleshooting

- Check CloudWatch Logs for Lambda function errors
- Verify that the SES email identity is verified in your AWS account
- Ensure that the Cognito triggers are properly configured
- Check IAM permissions if you encounter access denied errors
