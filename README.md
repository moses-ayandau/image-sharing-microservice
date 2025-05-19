# Photo Blog App(PixPath)

![License](https://img.shields.io/badge/license-MIT-blue.svg)
![Status](https://img.shields.io/badge/status-production-green)

A serverless, cloud-native image processing application deployed on AWS. This application allows users to upload, process, share, and manage their photos with features like recycling bin, auth protection, and multi-region resilience.

## 🔍 Overview

This application is built using AWS Serverless technology stack, utilizing services such as Lambda, API Gateway, S3, DynamoDB, Cognito, SQS, and CloudWatch. It's designed to be scalable, fault-tolerant, and easy to deploy across multiple environments.

The app enables users to upload, process, and manage their photos in a secure and reliable way, with features like authentication, image processing, sharing capabilities, recycling bin, and comprehensive health monitoring.

## ✨ Features

- **User Authentication** - Secure login/signup via Cognito with email verification
- **Image Upload & Processing** - Upload and automatic processing of images
- **Image Sharing** - Share images with other users
- **Recycle Bin** - Soft delete with recovery options
- **Cross-Region Replication** - Data storage across multiple AWS regions for disaster recovery
- **Health Monitoring** - Comprehensive health checks and alerting
- **Serverless Architecture** - Fully serverless deployment for maximum scalability
- **HTTPS Support** - Secure communication with custom domain and SSL certificate
- **Email Notifications** - Welcome emails and login alerts

## 🏗️ Architecture

The application follows a microservices architecture pattern with the following main components:

1. **Client-facing API** - API Gateway with custom domain
2. **Authentication Layer** - Cognito User Pool with custom triggers
3. **Image Processing Pipeline** - Lambda functions with SQS queues
4. **Storage Layer** - S3 buckets with lifecycle policies and cross-region replication
5. **Database Layer** - DynamoDB Global Tables
6. **Monitoring & Alerting** - CloudWatch, Route53 Health Checks, and SNS

![Architecture Diagram](https://via.placeholder.com/800x500?text=Architecture+Diagram)

## 🧩 Components

### Services

1. **Upload Service**
    - Handles image uploads via API Gateway
    - Stores images in a staging bucket
    - Queues processing requests

2. **Processing Service**
    - Processes images from the staging bucket
    - Creates thumbnails and various sizes
    - Stores processed images in the processed bucket
    - Updates metadata in DynamoDB

3. **Listing Service**
    - Retrieves and lists images for users
    - Handles image sharing functionality

4. **Recycle Service**
    - Manages the recycle bin functionality
    - Soft delete, restore, and permanent delete operations

5. **Cognito Service**
    - Handles authentication-related functionality
    - Welcome emails and sign-in alerts
    - User pool backups

6. **Health Check Service**
    - Monitors the health of all system components
    - Exposes health check endpoints

### Infrastructure Components

- **S3 Buckets**:
    - Staging bucket for initial uploads
    - Processed bucket with versioning and replication
    - Replica bucket in a different region for disaster recovery

- **DynamoDB Tables**:
    - Photo table for image metadata (Global Table)
    - Backup table for Cognito user data (Global Table)

- **API Gateway**:
    - Regional endpoint with custom domain
    - HTTPS support with ACM certificate
    - CORS configuration

- **Cognito**:
    - User Pool with email verification
    - Custom triggers for welcome emails and signin alerts

- **SQS Queues**:
    - Main processing queue
    - Dead letter queue with automatic redrive policy

- **Monitoring**:
    - Route53 health checks
    - CloudWatch alarms
    - SNS topics for alerts

## 🔧 Infrastructure as Code

The application is defined using AWS SAM (Serverless Application Model) and CloudFormation. The main template file (`template.yaml`) defines all resources, permissions, and event sources.

## 🚀 Deployment

### Prerequisites

- AWS CLI installed and configured
- AWS SAM CLI installed
- Java 21 JDK installed
- Maven installed (for building Java Lambda functions)

### Deployment Steps

1. Clone the repository:
   ```bash
   git clone https://github.com/moses-ayandau/image-sharing-microservice.git
   cd photo-app
   ```

2. Build the project:
   ```bash
   mvn clean package
   ```

3. Deploy using SAM:
   ```bash
   sam deploy --guided
   ```

4. Follow the prompts to set parameters:
    - Stack Name: `photo-app-stack`
    - AWS Region: `us-east-1`
    - Environment: `dev`, `test`, or `prod`
    - Other parameters as needed

### Multi-Environment Deployment

The application supports multiple environments (dev, test, prod) through parameters:

```bash
# For development
sam deploy --parameter-overrides Environment=dev

# For testing
sam deploy --parameter-overrides Environment=test

# For production
sam deploy --parameter-overrides Environment=prod
```

## 🔐 Environment Variables

The application uses environment variables for configuration. These are defined in the CloudFormation template and passed to Lambda functions:

| Variable | Description | Default Value |
|----------|-------------|---------------|
| `ENVIRONMENT` | Deployment environment | `test` |
| `STAGING_BUCKET` | Name of the bucket for initial uploads | `image-staging-bucket-{env}-{account}` |
| `PROCESSED_BUCKET` | Name of the bucket for processed images | `image-processed-bucket-{env}-{account}` |
| `IMAGE_TABLE` | DynamoDB table for image metadata | `photo` |
| `QUEUE_URL` | URL of the SQS processing queue | - |
| `EMAIL_SOURCE` | Source email for notifications | `noreply@mscv2group2.link` |
| `APP_URL` | Application frontend URL | `https://mscv2group2.link` |

## 📡 API Endpoints

| Method | Endpoint | Description | Auth Required |
|--------|----------|-------------|--------------|
| POST | `/upload` | Upload a new image | Yes |
| GET | `/users/{userId}/images/active` | Get active images for a user | Yes |
| GET | `/users/{userId}/images/deleted` | Get deleted images for a user | Yes |
| POST | `/images/share` | Share an image with another user | Yes |
| POST | `/images/{imageKey}/delete` | Move an image to recycle bin | Yes |
| POST | `/images/{imageKey}/restore` | Restore an image from recycle bin | Yes |
| DELETE | `/images/{imageKey}/permanent-delete` | Permanently delete an image | Yes |
| GET | `/health` | General system health check | No |
| GET | `/health/component/{component}` | Component-specific health check | No |

## 🔒 Security Features

- **Authentication** - Cognito User Pool with secure password policies
- **Authorization** - API Gateway integrated with Cognito
- **HTTPS** - All endpoints use HTTPS
- **CORS** - Properly configured CORS for web clients
- **S3 Bucket Policies** - Restricted public access
- **IAM Least Privilege** - Specific permissions for each Lambda function
- **Environment Isolation** - Separate resources per environment

## 🌐 High Availability & Disaster Recovery

- **Multi-region deployment** - Resources deployed across primary and DR regions
- **DynamoDB Global Tables** - Active-active replication between regions
- **S3 Cross-Region Replication** - Data replicated to secondary region
- **Automated Backups** - Regular backups of critical data (Cognito users)
- **Health Monitoring** - Proactive detection of issues

## 📊 Monitoring and Alerting

- **Health Checks** - Route53 health checks for API and components
- **CloudWatch Alarms** - Alarms for errors, latency, and health issues
- **SNS Notifications** - Email alerts for system administrators
- **DLQ Monitoring** - Dead Letter Queue monitoring and auto-redrive
- **Error Rate Monitoring** - Detection of API error rates
