#!/bin/bash
# User data script for Event Streaming EC2 instances
# This script sets up the application environment and starts the service

set -e

# Variables passed from Terraform
DB_ENDPOINT="${db_endpoint}"
DB_NAME="${db_name}"
DB_USERNAME="${db_username}"
DB_PASSWORD="${db_password}"

# Get instance metadata
INSTANCE_ID=$(curl -s http://169.254.169.254/latest/meta-data/instance-id)
PRIVATE_IP=$(curl -s http://169.254.169.254/latest/meta-data/local-ipv4)
AZ=$(curl -s http://169.254.169.254/latest/meta-data/placement/availability-zone)

# Determine node number based on last digit of private IP
NODE_NUMBER=$(echo $PRIVATE_IP | cut -d'.' -f4 | tail -c 2)

# Update system
yum update -y

# Install Java 21
yum install -y java-21-amazon-corretto-headless

# Install CloudWatch agent
yum install -y amazon-cloudwatch-agent

# Create application user
useradd -r -s /bin/false pekko

# Create application directory
mkdir -p /opt/pekko-cluster
chown pekko:pekko /opt/pekko-cluster

# Create logs directory
mkdir -p /var/log/pekko-cluster
chown pekko:pekko /var/log/pekko-cluster

# Download application JAR (replace with your S3 bucket or artifact repository)
# For demo purposes, this would typically download from S3
# aws s3 cp s3://your-bucket/event-streaming-app.jar /opt/pekko-cluster/app.jar

# Create application configuration
cat > /opt/pekko-cluster/application-aws.properties << EOF
# AWS Environment Configuration
spring.datasource.url=jdbc:mysql://${DB_ENDPOINT}:3306/${DB_NAME}?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC
spring.datasource.username=${DB_USERNAME}
spring.datasource.password=${DB_PASSWORD}
spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver

# Connection pool settings
spring.datasource.hikari.maximum-pool-size=10
spring.datasource.hikari.minimum-idle=2
spring.datasource.hikari.connection-timeout=30000

# Disable Kafka for AWS deployment
app.kafka.enabled=false

# Server configuration
server.port=8080
management.server.port=8558

# Logging
logging.level.com.eventstreaming=INFO
logging.level.org.apache.pekko=INFO
logging.file.name=/var/log/pekko-cluster/application.log
EOF

# Set ownership
chown pekko:pekko /opt/pekko-cluster/application-aws.properties

# Create systemd service
cat > /etc/systemd/system/pekko-cluster.service << EOF
[Unit]
Description=Pekko Cluster Event Streaming Application
After=network.target

[Service]
Type=simple
User=pekko
WorkingDirectory=/opt/pekko-cluster
ExecStart=/usr/bin/java -Xms256m -Xmx512m -Dspring.profiles.active=cluster-mysql -Dapp.kafka.enabled=false -Dserver.port=8080 -Dpekko.remote.artery.canonical.hostname=${PRIVATE_IP} -Dpekko.remote.artery.canonical.port=2551 -Dpekko.cluster.seed-nodes.0="pekko://EventStreamingSystem@${PRIVATE_IP}:2551" -Dpekko.cluster.min-nr-of-members=1 -Dpekko.cluster.roles.0=worker -DMYSQL_HOST=${DB_ENDPOINT} -DMYSQL_PORT=3306 -DMYSQL_DATABASE=${DB_NAME} -DMYSQL_USERNAME=${DB_USERNAME} -DMYSQL_PASSWORD=${DB_PASSWORD} -DMYSQL_USE_SSL=false -jar /opt/pekko-cluster/app.jar
Restart=always
RestartSec=10
Environment=JAVA_HOME=/usr/lib/jvm/java-21-amazon-corretto
Environment=PRIVATE_IP=${PRIVATE_IP}
Environment=NODE_NUMBER=${NODE_NUMBER}

[Install]
WantedBy=multi-user.target
EOF

# Create CloudWatch configuration
cat > /opt/aws/amazon-cloudwatch-agent/etc/amazon-cloudwatch-agent.json << EOF
{
  "logs": {
    "logs_collected": {
      "files": {
        "collect_list": [
          {
            "file_path": "/var/log/pekko-cluster/application.log",
            "log_group_name": "/aws/ec2/pekko-cluster",
            "log_stream_name": "{instance_id}",
            "timezone": "UTC"
          }
        ]
      }
    }
  },
  "metrics": {
    "namespace": "PekkoCluster",
    "metrics_collected": {
      "cpu": {
        "measurement": [
          "cpu_usage_idle",
          "cpu_usage_iowait",
          "cpu_usage_user",
          "cpu_usage_system"
        ],
        "metrics_collection_interval": 60
      },
      "disk": {
        "measurement": [
          "used_percent"
        ],
        "metrics_collection_interval": 60,
        "resources": [
          "*"
        ]
      },
      "diskio": {
        "measurement": [
          "io_time"
        ],
        "metrics_collection_interval": 60,
        "resources": [
          "*"
        ]
      },
      "mem": {
        "measurement": [
          "mem_used_percent"
        ],
        "metrics_collection_interval": 60
      }
    }
  }
}
EOF

# Start CloudWatch agent
/opt/aws/amazon-cloudwatch-agent/bin/amazon-cloudwatch-agent-ctl \
  -a fetch-config \
  -m ec2 \
  -c file:/opt/aws/amazon-cloudwatch-agent/etc/amazon-cloudwatch-agent.json \
  -s

# Enable and start the service
systemctl daemon-reload
systemctl enable pekko-cluster.service

# Note: The service will start once the JAR file is deployed
# systemctl start pekko-cluster.service

# Create health check script
cat > /opt/pekko-cluster/health-check.sh << 'EOF'
#!/bin/bash
# Health check script for the application

HEALTH_URL="http://localhost:8080/actuator/health"
RESPONSE=$(curl -s -o /dev/null -w "%{http_code}" $HEALTH_URL)

if [ "$RESPONSE" = "200" ]; then
    echo "Application is healthy"
    exit 0
else
    echo "Application is unhealthy (HTTP $RESPONSE)"
    exit 1
fi
EOF

chmod +x /opt/pekko-cluster/health-check.sh
chown pekko:pekko /opt/pekko-cluster/health-check.sh

# Log completion
echo "User data script completed successfully" >> /var/log/user-data.log
echo "Instance ID: $INSTANCE_ID" >> /var/log/user-data.log
echo "Private IP: $PRIVATE_IP" >> /var/log/user-data.log
echo "Node Number: $NODE_NUMBER" >> /var/log/user-data.log
echo "Database Endpoint: $DB_ENDPOINT" >> /var/log/user-data.log