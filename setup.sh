#!/bin/bash
# Setup script for Event Streaming AWS Repository
# This script prepares the repository for deployment

set -e

echo "🚀 Setting up Event Streaming AWS Repository"
echo "============================================="

# Make scripts executable
echo "📋 Making scripts executable..."
chmod +x scripts/*.sh
echo "✅ Scripts are now executable"

# Create terraform.tfvars from example
if [ ! -f "terraform/terraform.tfvars" ]; then
    echo "📋 Creating terraform.tfvars from example..."
    cp config/terraform.tfvars.example terraform/terraform.tfvars
    echo "✅ terraform.tfvars created"
    echo "⚠️  Please edit terraform/terraform.tfvars with your actual values"
else
    echo "✅ terraform.tfvars already exists"
fi

# Create application-aws.properties from template
if [ ! -f "config/application-aws.properties" ]; then
    echo "📋 Creating application-aws.properties from template..."
    cp config/application-aws.properties.template config/application-aws.properties
    echo "✅ application-aws.properties created"
    echo "⚠️  Please edit config/application-aws.properties with your actual values"
else
    echo "✅ application-aws.properties already exists"
fi

# Check prerequisites
echo "📋 Checking prerequisites..."

# Check AWS CLI
if command -v aws &> /dev/null; then
    echo "✅ AWS CLI found"
    if aws sts get-caller-identity &> /dev/null; then
        echo "✅ AWS credentials configured"
    else
        echo "❌ AWS credentials not configured. Please run 'aws configure'"
    fi
else
    echo "❌ AWS CLI not found. Please install AWS CLI"
fi

# Check Terraform
if command -v terraform &> /dev/null; then
    echo "✅ Terraform found"
else
    echo "❌ Terraform not found. Please install Terraform"
fi

# Check Java
if command -v java &> /dev/null; then
    JAVA_VERSION=$(java -version 2>&1 | head -n 1 | cut -d'"' -f2)
    echo "✅ Java found: $JAVA_VERSION"
else
    echo "❌ Java not found. Please install Java 21"
fi

# Check Maven
if command -v mvn &> /dev/null; then
    echo "✅ Maven found"
else
    echo "❌ Maven not found. Please install Maven"
fi

echo ""
echo "🎯 Next Steps:"
echo "1. Edit terraform/terraform.tfvars with your AWS configuration"
echo "2. Edit config/application-aws.properties with your database settings"
echo "3. Run: cd terraform && terraform init"
echo "4. Run: ./scripts/deploy-to-aws.sh"
echo ""
echo "📚 Documentation:"
echo "- AWS Deployment Guide: docs/AWS-DEPLOYMENT-GUIDE.md"
echo "- Architecture Overview: docs/ARCHITECTURE.md"
echo "- Testing Guide: docs/TESTING.md"
echo ""
echo "✅ Setup complete!"