# Event Streaming System Architecture

## Overview

The Event Streaming System is a high-performance, scalable event sourcing and processing platform built using modern distributed systems patterns. It leverages the Actor Model through Apache Pekko (Akka successor) for concurrent event processing and maintains complete audit trails through event sourcing.

## System Architecture

### High-Level Architecture

```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   Load Balancer │    │   Auto Scaling  │    │   RDS MySQL     │
│      (ALB)      │────│     Group       │────│   (Primary)     │
│                 │    │   (EC2 Cluster) │    │                 │
└─────────────────┘    └─────────────────┘    └─────────────────┘
         │                       │                       │
         │                       │                       │
         ▼                       ▼                       ▼
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   Internet      │    │   Pekko Cluster │    │   Event Store   │
│   Gateway       │    │   (Actors)      │    │   (Journal)     │
└─────────────────┘    └─────────────────┘    └─────────────────┘
```

### Core Components

#### 1. Actor System (Apache Pekko)
- **Cluster Sharding**: Distributes actors across cluster nodes
- **Event Sourcing**: Persistent actors with event journals
- **Supervision**: Fault-tolerant actor hierarchies
- **Location Transparency**: Actors can move between nodes

#### 2. Event Processing Pipeline
```
Event Input → Actor Router → Sharded Actor → Event Journal → Aggregation
     │              │             │              │             │
     ▼              ▼             ▼              ▼             ▼
  REST API    Load Balancer   User Actor    MySQL Store   Dashboard
```

#### 3. Persistence Layer
- **Event Journal**: Append-only event storage
- **Snapshots**: Periodic actor state snapshots
- **MySQL Backend**: ACID-compliant event storage
- **Connection Pooling**: HikariCP for optimal performance

## Deployment Architecture

### AWS Infrastructure

#### Network Layer
```
┌─────────────────────────────────────────────────────────────┐
│                        VPC (10.0.0.0/16)                   │
│                                                             │
│  ┌─────────────────┐              ┌─────────────────┐      │
│  │  Public Subnet  │              │  Public Subnet  │      │
│  │   (10.0.1.0/24) │              │   (10.0.2.0/24) │      │
│  │                 │              │                 │      │
│  │  ┌───────────┐  │              │  ┌───────────┐  │      │
│  │  │    ALB    │  │              │  │    NAT    │  │      │
│  │  └───────────┘  │              │  │  Gateway  │  │      │
│  └─────────────────┘              └─────────────────┘      │
│           │                                 │              │
│           ▼                                 ▼              │
│  ┌─────────────────┐              ┌─────────────────┐      │
│  │ Private Subnet  │              │ Private Subnet  │      │
│  │  (10.0.10.0/24) │              │  (10.0.20.0/24) │      │
│  │                 │              │                 │      │
│  │  ┌───────────┐  │              │  ┌───────────┐  │      │
│  │  │    RDS    │  │              │  │    RDS    │  │      │
│  │  │  (Primary)│  │              │  │ (Standby) │  │      │
│  │  └───────────┘  │              │  └───────────┘  │      │
│  └─────────────────┘              └─────────────────┘      │
└─────────────────────────────────────────────────────────────┘
```

#### Compute Layer
- **Auto Scaling Group**: 1-10 EC2 instances
- **Launch Template**: Standardized instance configuration
- **Health Checks**: Application and infrastructure monitoring
- **Rolling Updates**: Zero-downtime deployments

#### Data Layer
- **RDS MySQL**: Multi-AZ for high availability
- **Automated Backups**: Point-in-time recovery
- **Read Replicas**: Optional for read scaling
- **Encryption**: Data encryption at rest and in transit

## Event Processing Flow

### 1. Event Ingestion
```
HTTP Request → Load Balancer → EC2 Instance → Spring Boot → REST Controller
```

### 2. Actor Routing
```
REST Controller → Pekko Cluster → Cluster Sharding → User Actor
```

### 3. Event Processing
```
User Actor → Validate Event → Persist to Journal → Update State → Respond
```

### 4. Event Persistence
```
Event Journal → MySQL Transaction → Commit → Acknowledgment
```

## Scalability Patterns

### Horizontal Scaling
- **Cluster Sharding**: Distributes actors across nodes
- **Auto Scaling**: Adds/removes instances based on load
- **Load Balancing**: Distributes requests across instances
- **Database Scaling**: Read replicas for read-heavy workloads

### Vertical Scaling
- **Instance Types**: Scale up to larger instance types
- **Database Scaling**: Increase RDS instance class
- **Memory Allocation**: Adjust JVM heap sizes
- **Connection Pools**: Optimize database connections

## Performance Characteristics

### Throughput
- **Events/Second**: 10,000+ events per second per node
- **Concurrent Users**: 1,000+ concurrent users
- **Batch Processing**: 100+ events per batch
- **Response Time**: Sub-100ms for event processing

### Scalability Limits
- **Cluster Size**: Up to 100 nodes (theoretical)
- **Shards**: 1,000 shards for optimal distribution
- **Database**: Limited by RDS instance class
- **Network**: Limited by VPC and instance networking

## Fault Tolerance

### Actor System Resilience
- **Supervision Strategy**: Restart failed actors
- **Circuit Breakers**: Prevent cascade failures
- **Bulkheads**: Isolate failure domains
- **Timeouts**: Prevent resource exhaustion

### Infrastructure Resilience
- **Multi-AZ Deployment**: Database high availability
- **Auto Scaling**: Replace failed instances
- **Health Checks**: Detect and route around failures
- **Load Balancer**: Distribute load to healthy instances

### Data Consistency
- **Event Sourcing**: Complete audit trail
- **ACID Transactions**: Database consistency
- **Idempotent Operations**: Safe retry mechanisms
- **Eventual Consistency**: Acceptable for aggregations

## Monitoring and Observability

### Application Metrics
- **Actor Metrics**: Message processing rates, mailbox sizes
- **Event Metrics**: Event types, processing times
- **Business Metrics**: User activity, system usage
- **Performance Metrics**: Response times, throughput

### Infrastructure Metrics
- **EC2 Metrics**: CPU, memory, network, disk
- **RDS Metrics**: Connections, queries, performance
- **ALB Metrics**: Request rates, response times, errors
- **Auto Scaling Metrics**: Scaling activities, capacity

### Logging
- **Application Logs**: Structured logging with correlation IDs
- **System Logs**: Infrastructure and system events
- **Audit Logs**: Security and compliance events
- **Performance Logs**: Detailed performance analysis

## Security Architecture

### Network Security
- **VPC Isolation**: Private network environment
- **Security Groups**: Firewall rules at instance level
- **NACLs**: Network-level access control
- **Private Subnets**: Database isolation

### Application Security
- **Authentication**: API key or JWT-based authentication
- **Authorization**: Role-based access control
- **Input Validation**: Prevent injection attacks
- **Rate Limiting**: Prevent abuse and DoS attacks

### Data Security
- **Encryption at Rest**: RDS and EBS encryption
- **Encryption in Transit**: TLS for all communications
- **Key Management**: AWS KMS for key management
- **Backup Encryption**: Encrypted database backups

## Development and Operations

### CI/CD Pipeline
- **Source Control**: Git-based version control
- **Build Process**: Maven-based builds
- **Testing**: Unit, integration, and load testing
- **Deployment**: Terraform-based infrastructure as code

### Environment Management
- **Development**: Local development environment
- **Staging**: Pre-production testing environment
- **Production**: Live production environment
- **Configuration**: Environment-specific configurations

### Monitoring and Alerting
- **CloudWatch**: AWS native monitoring
- **Custom Metrics**: Application-specific metrics
- **Alerting**: Automated alerts for critical issues
- **Dashboards**: Real-time system visibility

## Technology Stack

### Backend Technologies
- **Java 21**: Modern Java with performance improvements
- **Spring Boot 3.x**: Enterprise application framework
- **Apache Pekko**: Actor system for concurrent processing
- **HikariCP**: High-performance connection pooling
- **MySQL 8.0**: Relational database with JSON support

### Infrastructure Technologies
- **AWS EC2**: Scalable compute instances
- **AWS RDS**: Managed database service
- **AWS ALB**: Application load balancing
- **AWS VPC**: Virtual private cloud networking
- **Terraform**: Infrastructure as code

### Monitoring Technologies
- **CloudWatch**: Metrics, logs, and alarms
- **Spring Boot Actuator**: Application health and metrics
- **Micrometer**: Application metrics collection
- **Structured Logging**: JSON-based log format

## Future Enhancements

### Planned Improvements
- **Kafka Integration**: Event streaming with Apache Kafka
- **Redis Caching**: In-memory caching for performance
- **GraphQL API**: Flexible query interface
- **Kubernetes**: Container orchestration platform

### Scalability Enhancements
- **Read Replicas**: Database read scaling
- **CDN Integration**: Static content delivery
- **Microservices**: Service decomposition
- **Event Streaming**: Real-time event processing

### Operational Improvements
- **Blue-Green Deployment**: Zero-downtime deployments
- **Canary Releases**: Gradual feature rollouts
- **Chaos Engineering**: Resilience testing
- **Performance Testing**: Continuous performance validation