# Repository Contents

## 📁 Directory Structure

```
eventstreamaws/
├── README.md                           # Main repository documentation
├── LICENSE                             # MIT License
├── .gitignore                          # Git ignore rules (excludes sensitive files)
├── pom.xml                             # Maven configuration with all dependencies
├── setup.sh                            # Repository setup script
├── REPOSITORY-CONTENTS.md               # This file
│
├── src/                                # Complete Java application source code
│   ├── main/java/com/eventstreaming/   # Main application packages
│   │   ├── actor/                      # Pekko actor implementations
│   │   ├── cluster/                    # Cluster management and sharding
│   │   ├── config/                     # Configuration classes
│   │   ├── controller/                 # REST API controllers
│   │   ├── dto/                        # Data transfer objects
│   │   ├── kafka/                      # Kafka integration services
│   │   ├── model/                      # Domain models and events
│   │   ├── monitoring/                 # Performance monitoring
│   │   ├── persistence/                # Data persistence layer
│   │   ├── service/                    # Business logic services
│   │   ├── streams/                    # Event streaming pipelines
│   │   └── validation/                 # Data validation components
│   ├── main/resources/                 # Application configuration files
│   │   ├── application.yml             # Base application configuration
│   │   ├── application-aws.yml         # AWS-specific configuration
│   │   ├── application-cluster-mysql.yml # Compatible with existing deployment scripts
│   │   ├── logback-spring.xml          # Logging configuration
│   │   ├── db/migration/               # Database migration scripts
│   │   ├── schemas/                    # JSON event schemas
│   │   └── templates/                  # HTML dashboard templates
│   └── test/java/                      # Unit and integration tests
│
├── terraform/                          # Infrastructure as Code
│   ├── main.tf                         # Main Terraform configuration
│   ├── variables.tf                    # Terraform variables
│   ├── outputs.tf                      # Terraform outputs
│   └── user-data.sh                    # EC2 instance initialization script
│
├── scripts/                            # Deployment and testing scripts
│   ├── deploy-to-aws.sh                # Main deployment script
│   ├── test-aws-deployment.sh          # Deployment validation tests
│   └── test-load-balancer.sh           # Load balancer testing
│
├── config/                             # Configuration templates
│   ├── application-aws.properties.template    # AWS app configuration template
│   └── terraform.tfvars.example               # Terraform variables example
│
└── docs/                               # Documentation
    ├── AWS-DEPLOYMENT-GUIDE.md         # Complete deployment guide
    ├── ARCHITECTURE.md                 # System architecture documentation
    └── TESTING.md                      # Testing procedures and guides
```

## 🔧 Complete Application Components

### Java Application (`src/`)
- **174+ Java Files**: Complete event streaming application
- **29,000+ Lines of Code**: Production-ready implementation
- **Maven Configuration**: All dependencies for Pekko, Spring Boot, Kafka
- **Clean Configuration**: Only 3 essential config files for AWS deployment

#### Essential Configuration Files:
- **`application.yml`**: Base application configuration
- **`application-aws.yml`**: AWS-specific settings (RDS, single-node, no Kafka)
- **`application-cluster-mysql.yml`**: Compatible with existing AWS deployment scripts
- **`logback-spring.xml`**: Logging configuration

#### Key Packages:
- **`actor/`**: Pekko actor system with user actors and commands
- **`cluster/`**: Cluster management, sharding, and persistence
- **`config/`**: Configuration classes for all environments
- **`controller/`**: REST API endpoints and health checks
- **`kafka/`**: Kafka integration and event streaming
- **`model/`**: Domain models (UserEvent, CallEvent, EmailEvent, SmsEvent)
- **`monitoring/`**: Performance metrics and pipeline monitoring
- **`service/`**: Business logic and aggregation services
- **`streams/`**: Event processing pipelines
- **`validation/`**: Data validation and schema management

### Infrastructure Components

### Terraform Configuration (`terraform/`)
- **VPC and Networking**: Isolated network environment with public/private subnets
- **EC2 Auto Scaling**: Scalable compute instances with auto-scaling policies
- **Application Load Balancer**: High-availability load balancing
- **RDS MySQL**: Managed database with Multi-AZ deployment
- **Security Groups**: Network security and access control
- **IAM Roles**: Secure instance permissions

### Key Features:
- ✅ **Production-Ready**: Multi-AZ, auto-scaling, load balancing
- ✅ **Secure**: VPC isolation, security groups, encrypted storage
- ✅ **Scalable**: Auto-scaling from 1-10 instances
- ✅ **Monitored**: CloudWatch integration and health checks
- ✅ **Cost-Optimized**: Right-sized instances and storage

## 🚀 Deployment Scripts (`scripts/`)

### `deploy-to-aws.sh`
- Builds the application with Maven
- Uploads artifacts to S3 (configurable)
- Deploys infrastructure with Terraform
- Validates deployment success
- Provides deployment URLs and next steps

### `test-aws-deployment.sh`
- Comprehensive deployment validation
- Tests all API endpoints
- Validates event processing functionality
- Checks health endpoints and dashboard
- Performance and load testing

### `test-load-balancer.sh`
- Load balancer distribution testing
- Concurrent request handling
- Response time analysis
- Health check validation under load
- Performance metrics collection

## 📋 Configuration Templates (`config/`)

### `application-aws.properties.template`
- AWS RDS database configuration
- Connection pool settings
- Application-specific settings
- Logging configuration
- Performance tuning parameters

### `terraform.tfvars.example`
- AWS region and environment settings
- Instance types and scaling configuration
- Database configuration
- Network settings (VPC, subnets)
- Monitoring and tagging options

## 📚 Documentation (`docs/`)

### `AWS-DEPLOYMENT-GUIDE.md`
- Complete step-by-step deployment guide
- Prerequisites and tool requirements
- Configuration instructions
- Troubleshooting common issues
- Security and best practices

### `ARCHITECTURE.md`
- High-level system architecture
- Component interactions and data flow
- Scalability and performance characteristics
- Security architecture
- Technology stack details

### `TESTING.md`
- Comprehensive testing procedures
- Load testing and performance validation
- Security testing checklist
- Monitoring and observability
- Test automation strategies

## 🔒 Security Features

### Network Security
- **VPC Isolation**: Private network environment
- **Security Groups**: Firewall rules at instance level
- **Private Subnets**: Database isolation from internet
- **NAT Gateway**: Secure outbound internet access

### Data Security
- **RDS Encryption**: Database encryption at rest
- **TLS/SSL**: Encrypted data in transit
- **IAM Roles**: Least privilege access
- **No Hardcoded Credentials**: All secrets externalized

### Access Control
- **Security Groups**: Network-level access control
- **IAM Policies**: Resource-level permissions
- **Database Security**: User authentication and authorization

## 🎯 Key Benefits

### For Developers
- **Easy Deployment**: Single-command deployment
- **Comprehensive Testing**: Automated validation scripts
- **Clear Documentation**: Step-by-step guides
- **Best Practices**: Production-ready configuration

### For Operations
- **Infrastructure as Code**: Terraform-managed infrastructure
- **Auto Scaling**: Automatic capacity management
- **Monitoring**: CloudWatch integration
- **High Availability**: Multi-AZ deployment

### For Business
- **Cost Effective**: Pay-as-you-scale model
- **Reliable**: 99.9% availability target
- **Secure**: Enterprise-grade security
- **Scalable**: Handles growth automatically

## 🚀 Quick Start

1. **Setup Repository**:
   ```bash
   chmod +x setup.sh
   ./setup.sh
   ```

2. **Configure Settings**:
   ```bash
   # Edit with your values
   vim terraform/terraform.tfvars
   vim config/application-aws.properties
   ```

3. **Deploy Infrastructure**:
   ```bash
   cd terraform
   terraform init
   terraform apply
   ```

4. **Deploy Application**:
   ```bash
   cd ../scripts
   ./deploy-to-aws.sh
   ```

5. **Test Deployment**:
   ```bash
   ./test-aws-deployment.sh
   ./test-load-balancer.sh
   ```

## 📊 What's Included (Complete Application)

### ✅ Full Application Source Code
- **Complete Java Implementation**: 174+ files, 29,000+ lines
- **Maven Build Configuration**: All dependencies included
- **Multi-Environment Support**: Local, AWS, cluster configurations
- **Production-Ready Code**: Error handling, monitoring, validation
- **Comprehensive Test Suite**: Unit and integration tests

### ✅ AWS Infrastructure
- **Terraform Configuration**: Complete infrastructure as code
- **Auto-Scaling Architecture**: EC2, RDS, Load Balancer
- **Security Best Practices**: VPC, security groups, encryption
- **Monitoring Integration**: CloudWatch metrics and alarms

### ✅ Deployment Automation
- **One-Command Deployment**: Automated build and deploy
- **Testing Scripts**: Comprehensive validation and load testing
- **Configuration Management**: Environment-specific settings

## 🎯 Intended Use Cases

### Portfolio/Resume Projects
- Demonstrate AWS expertise
- Show infrastructure as code skills
- Highlight scalable architecture design
- Prove deployment automation capabilities

### Learning and Education
- Learn Terraform and AWS
- Understand event sourcing patterns
- Practice deployment automation
- Study scalable architecture

### Reference Implementation
- Starting point for similar projects
- Best practices demonstration
- Architecture pattern reference
- Deployment automation template

## 🤝 Contributing

This repository is designed as a demonstration project. Feel free to:
- Fork and customize for your needs
- Use as a reference for your projects
- Adapt the patterns for different technologies
- Share improvements and suggestions

## 📄 License

MIT License - Use freely for personal and commercial projects.

---

**Note**: This repository contains the **complete event streaming application** with full Java source code, AWS infrastructure, and deployment automation. Ready to build, deploy, and run on AWS.