#!/bin/bash
# Deploy Event Streaming Application to AWS
# This script builds the application and deploys it to AWS infrastructure

set -e

# Configuration
APP_NAME="event-streaming"
S3_BUCKET="your-deployment-bucket"  # Replace with your S3 bucket
JAR_NAME="event-streaming-app-1.0.0-SNAPSHOT.jar"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}🚀 Event Streaming AWS Deployment${NC}"
echo "=================================="

# Check prerequisites
echo -e "${YELLOW}📋 Checking prerequisites...${NC}"

if ! command -v aws &> /dev/null; then
    echo -e "${RED}❌ AWS CLI not found. Please install AWS CLI.${NC}"
    exit 1
fi

if ! command -v terraform &> /dev/null; then
    echo -e "${RED}❌ Terraform not found. Please install Terraform.${NC}"
    exit 1
fi

if ! command -v mvn &> /dev/null; then
    echo -e "${RED}❌ Maven not found. Please install Maven.${NC}"
    exit 1
fi

# Check AWS credentials
if ! aws sts get-caller-identity &> /dev/null; then
    echo -e "${RED}❌ AWS credentials not configured. Please run 'aws configure'.${NC}"
    exit 1
fi

echo -e "${GREEN}✅ Prerequisites check passed${NC}"

# Build application
echo -e "${YELLOW}🔨 Building application...${NC}"
if [ -f "../pom.xml" ]; then
    cd ..
    mvn clean package -DskipTests
    cd scripts
    echo -e "${GREEN}✅ Application built successfully${NC}"
else
    echo -e "${RED}❌ pom.xml not found. Please run from the correct directory.${NC}"
    exit 1
fi

# Check if JAR exists
if [ ! -f "../target/$JAR_NAME" ]; then
    echo -e "${RED}❌ JAR file not found: ../target/$JAR_NAME${NC}"
    exit 1
fi

# Upload JAR to S3
echo -e "${YELLOW}📤 Uploading application to S3...${NC}"
aws s3 cp "../target/$JAR_NAME" "s3://$S3_BUCKET/$JAR_NAME"
if [ $? -eq 0 ]; then
    echo -e "${GREEN}✅ Application uploaded to S3${NC}"
else
    echo -e "${RED}❌ Failed to upload to S3. Please check your S3 bucket configuration.${NC}"
    exit 1
fi

# Deploy infrastructure
echo -e "${YELLOW}🏗️ Deploying infrastructure...${NC}"
cd ../terraform

# Initialize Terraform
terraform init

# Plan deployment
echo -e "${YELLOW}📋 Planning infrastructure changes...${NC}"
terraform plan -out=tfplan

# Apply changes
echo -e "${YELLOW}🚀 Applying infrastructure changes...${NC}"
terraform apply tfplan

if [ $? -eq 0 ]; then
    echo -e "${GREEN}✅ Infrastructure deployed successfully${NC}"
else
    echo -e "${RED}❌ Infrastructure deployment failed${NC}"
    exit 1
fi

# Get outputs
LOAD_BALANCER_URL=$(terraform output -raw load_balancer_url)
RDS_ENDPOINT=$(terraform output -raw rds_endpoint)

echo -e "${GREEN}🎉 Deployment completed successfully!${NC}"
echo "=================================="
echo -e "${BLUE}📊 Deployment Information:${NC}"
echo "Load Balancer URL: $LOAD_BALANCER_URL"
echo "Health Check: $LOAD_BALANCER_URL/actuator/health"
echo "Dashboard: $LOAD_BALANCER_URL/dashboard"
echo "API Base: $LOAD_BALANCER_URL/api"
echo ""
echo -e "${YELLOW}⏳ Note: It may take 5-10 minutes for instances to become healthy${NC}"

# Wait for health check
echo -e "${YELLOW}🔍 Waiting for application to become healthy...${NC}"
for i in {1..30}; do
    if curl -s "$LOAD_BALANCER_URL/actuator/health" > /dev/null; then
        echo -e "${GREEN}✅ Application is healthy and ready!${NC}"
        break
    fi
    echo "Attempt $i/30: Waiting for application..."
    sleep 30
done

echo -e "${BLUE}🎯 Next steps:${NC}"
echo "1. Test the deployment: ./test-aws-deployment.sh"
echo "2. Run load balancer tests: ./test-load-balancer.sh"
echo "3. Monitor logs: aws logs tail /aws/ec2/pekko-cluster --follow"

cd ../scripts