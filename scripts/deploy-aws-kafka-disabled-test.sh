#!/bin/bash
# Deploy AWS with Kafka Disabled - SECURE VERSION for Clean Repository
# Compatible with new AWS repository profile structure

set -e

echo "🚀 AWS KAFKA DISABLED DEPLOYMENT - SECURE VERSION"
echo "================================================="
date
echo

# Check required environment variables
if [ -z "$DB_PASSWORD" ]; then
    echo "❌ ERROR: DB_PASSWORD environment variable not set"
    echo "Please set: export DB_PASSWORD=your-secure-password"
    exit 1
fi

if [ -z "$DB_HOST" ]; then
    echo "❌ ERROR: DB_HOST environment variable not set"
    echo "Please set: export DB_HOST=your-rds-endpoint"
    exit 1
fi

# Default values for other variables
DB_PORT=${DB_PORT:-3306}
DB_NAME=${DB_NAME:-event_streaming_db}
DB_USERNAME=${DB_USERNAME:-admin}

# Get instance IP
PRIVATE_IP=$(curl -s http://169.254.169.254/latest/meta-data/local-ipv4 2>/dev/null || echo "localhost")
echo "✅ Private IP: $PRIVATE_IP"

echo
echo "🔒 Security Check"
echo "================"
echo "✅ Using environment variables for sensitive data"
echo "✅ DB_HOST: $DB_HOST"
echo "✅ DB_USERNAME: $DB_USERNAME"
echo "✅ DB_PASSWORD: [HIDDEN]"
echo "✅ No hardcoded credentials"

echo
echo "🛑 Step 1: Clean Stop"
echo "===================="
echo "Stopping any existing Java processes..."
pkill -f java || true
sleep 5
echo "✅ Clean environment ready"

echo
echo "📦 Step 2: Verify JAR with KafkaConditionalDiagnostics"
echo "===================================================="
if [ -f "event-streaming-app-1.0.0-SNAPSHOT.jar" ]; then
    JAR_SIZE=$(ls -lh event-streaming-app-1.0.0-SNAPSHOT.jar | awk '{print $5}')
    echo "✅ JAR found: $JAR_SIZE"
    
    # Check if JAR contains the diagnostic class
    if jar tf event-streaming-app-1.0.0-SNAPSHOT.jar | grep -q "KafkaConditionalDiagnostics"; then
        echo "✅ KafkaConditionalDiagnostics class found in JAR"
    else
        echo "❌ KafkaConditionalDiagnostics class NOT found in JAR"
        echo "Please upload the latest JAR with the diagnostic class"
        exit 1
    fi
else
    echo "❌ JAR file not found!"
    echo "Please upload: event-streaming-app-1.0.0-SNAPSHOT.jar"
    exit 1
fi

echo
echo "🔧 Step 3: Test AWS RDS Connectivity (SECURE)"
echo "============================================="
echo "Testing MySQL connection to AWS RDS..."
# Use environment variables instead of hardcoded credentials
mysql -h "$DB_HOST" -u "$DB_USERNAME" -p"$DB_PASSWORD" "$DB_NAME" \
  -e "SELECT 'AWS RDS Connection OK' as status;" 2>/dev/null || {
    echo "❌ Cannot connect to AWS RDS"
    echo "Check your DB_HOST, DB_USERNAME, and DB_PASSWORD environment variables"
    exit 1
}
echo "✅ AWS RDS connection successful"

echo
echo "🚀 Step 4: Start with SECURE CONFIG (Compatible with New AWS Repo)"
echo "=================================================================="
echo
echo "🔧 Configuration:"
echo "  Profile: cluster-mysql (✅ Available in new AWS repo)"
echo "  Kafka: DISABLED via -Dapp.kafka.enabled=false"
echo "  Database: AWS RDS MySQL (via environment variables)"
echo "  Security: No hardcoded passwords"
echo "  Debug: KafkaConditionalDiagnostics logging enabled"
echo "  Expected: Same ❌ diagnostic messages as local test"
echo

nohup java -Xmx768m \
  -Dspring.profiles.active=cluster-mysql \
  -Dapp.kafka.enabled=false \
  -Dspring.main.allow-bean-definition-overriding=true \
  -Dserver.port=8080 \
  -Dpekko.remote.artery.canonical.hostname=$PRIVATE_IP \
  -Dpekko.remote.artery.canonical.port=2551 \
  -Dpekko.cluster.seed-nodes.0="pekko://EventStreamingSystem@$PRIVATE_IP:2551" \
  -Dpekko.cluster.min-nr-of-members=1 \
  -Dlogging.level.com.eventstreaming.config.KafkaConditionalDiagnostics=DEBUG \
  -DMYSQL_HOST="$DB_HOST" \
  -DMYSQL_PORT="$DB_PORT" \
  -DMYSQL_DATABASE="$DB_NAME" \
  -DMYSQL_USERNAME="$DB_USERNAME" \
  -DMYSQL_PASSWORD="$DB_PASSWORD" \
  -DMYSQL_USE_SSL=false \
  -jar event-streaming-app-1.0.0-SNAPSHOT.jar > aws-kafka-disabled-test.log 2>&1 &

APP_PID=$!
echo "✅ Application started with PID: $APP_PID"
echo "📁 Log file: aws-kafka-disabled-test.log"

echo
echo "✅ AWS KAFKA DISABLED TEST COMPLETE (SECURE VERSION)"
echo "=================================================="
echo
echo "🔒 SECURITY IMPROVEMENTS:"
echo "========================"
echo "✅ No hardcoded passwords"
echo "✅ Environment variables for all sensitive data"
echo "✅ Compatible with new AWS repository"
echo "✅ Uses cluster-mysql profile from new repo"
echo
echo "🚀 USAGE:"
echo "========"
echo "export DB_HOST=your-rds-endpoint"
echo "export DB_PASSWORD=your-secure-password"
echo "./deploy-aws-kafka-disabled-test.sh"