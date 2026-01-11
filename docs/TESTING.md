# Testing Guide

## Overview

This guide covers testing strategies and procedures for the Event Streaming AWS deployment, including unit tests, integration tests, load tests, and deployment validation.

## Test Categories

### 1. Deployment Tests
Validate that the AWS infrastructure and application are deployed correctly.

#### Running Deployment Tests
```bash
cd scripts
./test-aws-deployment.sh
```

#### Test Coverage
- ✅ Health check endpoints
- ✅ Dashboard accessibility
- ✅ Event processing API
- ✅ User statistics API
- ✅ Recent events API
- ✅ Batch processing
- ✅ Performance under load

### 2. Load Balancer Tests
Verify load balancer functionality and distribution.

#### Running Load Balancer Tests
```bash
cd scripts
./test-load-balancer.sh
```

#### Test Coverage
- ✅ Request distribution across instances
- ✅ Health check distribution
- ✅ Concurrent request handling
- ✅ Response time analysis
- ✅ Batch processing under load
- ✅ Health checks during load

### 3. Infrastructure Tests
Validate AWS infrastructure components.

#### Manual Infrastructure Tests
```bash
# Check Auto Scaling Group
aws autoscaling describe-auto-scaling-groups --auto-scaling-group-names event-streaming-asg

# Check Load Balancer
aws elbv2 describe-load-balancers --names event-streaming-alb

# Check RDS Instance
aws rds describe-db-instances --db-instance-identifier event-streaming-mysql

# Check Target Group Health
aws elbv2 describe-target-health --target-group-arn YOUR_TARGET_GROUP_ARN
```

### 4. Application Tests
Test application-specific functionality.

#### Event Processing Tests
```bash
# Single event test
curl -X POST \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "test-user-001",
    "eventType": "USER_LOGIN",
    "contactId": 10001,
    "eventData": "{\"ip\":\"203.0.113.1\"}",
    "source": "manual-test"
  }' \
  "http://YOUR_LOAD_BALANCER_URL/api/user-events/process"

# Batch event test
curl -X POST \
  -H "Content-Type: application/json" \
  -d '[
    {
      "userId": "batch-user-001",
      "eventType": "USER_REGISTRATION",
      "contactId": 20001,
      "eventData": "{\"email\":\"test@example.com\"}",
      "source": "batch-test"
    }
  ]' \
  "http://YOUR_LOAD_BALANCER_URL/api/user-events/process-batch"
```

#### Health Check Tests
```bash
# Application health
curl "http://YOUR_LOAD_BALANCER_URL/actuator/health"

# Detailed health information
curl "http://YOUR_LOAD_BALANCER_URL/actuator/health" | jq '.'
```

### 5. Performance Tests
Measure system performance under various load conditions.

#### Load Testing with curl
```bash
# Concurrent requests test
for i in {1..100}; do
  curl -X POST \
    -H "Content-Type: application/json" \
    -d "{\"userId\":\"load-test-$i\",\"eventType\":\"LOAD_TEST\",\"contactId\":$i}" \
    "http://YOUR_LOAD_BALANCER_URL/api/user-events/process" &
done
wait
```

#### Response Time Testing
```bash
# Measure response times
for i in {1..10}; do
  curl -w "Response time: %{time_total}s\n" \
    -X POST \
    -H "Content-Type: application/json" \
    -d "{\"userId\":\"timing-test-$i\",\"eventType\":\"TIMING_TEST\",\"contactId\":$i}" \
    -o /dev/null \
    -s \
    "http://YOUR_LOAD_BALANCER_URL/api/user-events/process"
done
```

## Test Automation

### Continuous Integration Tests
```bash
# Run all tests in CI/CD pipeline
./scripts/test-aws-deployment.sh
./scripts/test-load-balancer.sh

# Check exit codes
if [ $? -eq 0 ]; then
  echo "All tests passed"
else
  echo "Tests failed"
  exit 1
fi
```

### Scheduled Health Checks
```bash
# Create a cron job for regular health checks
# Add to crontab: */5 * * * * /path/to/health-check.sh

#!/bin/bash
# health-check.sh
HEALTH_URL="http://YOUR_LOAD_BALANCER_URL/actuator/health"
RESPONSE=$(curl -s -o /dev/null -w "%{http_code}" $HEALTH_URL)

if [ "$RESPONSE" != "200" ]; then
  echo "Health check failed: HTTP $RESPONSE"
  # Send alert (email, Slack, etc.)
fi
```

## Test Data Management

### Test Data Setup
```sql
-- Create test data in database
INSERT INTO user_events (user_id, event_type, contact_id, event_data, source, created_at)
VALUES 
  ('test-user-001', 'USER_LOGIN', 10001, '{"ip":"203.0.113.1"}', 'test-setup', NOW()),
  ('test-user-002', 'EMAIL_DELIVERY', 10002, '{"subject":"Test Email"}', 'test-setup', NOW());
```

### Test Data Cleanup
```sql
-- Clean up test data
DELETE FROM user_events WHERE source LIKE '%test%';
DELETE FROM journal WHERE persistence_id LIKE '%test%';
```

## Monitoring During Tests

### CloudWatch Metrics
Monitor these metrics during testing:
- **EC2 Metrics**: CPU utilization, network I/O
- **RDS Metrics**: Database connections, query performance
- **ALB Metrics**: Request count, response times, error rates
- **Auto Scaling Metrics**: Scaling activities

### Application Metrics
```bash
# Check application metrics
curl "http://YOUR_LOAD_BALANCER_URL/actuator/metrics"

# Specific metrics
curl "http://YOUR_LOAD_BALANCER_URL/actuator/metrics/jvm.memory.used"
curl "http://YOUR_LOAD_BALANCER_URL/actuator/metrics/http.server.requests"
```

### Log Monitoring
```bash
# Monitor application logs
aws logs tail /aws/ec2/pekko-cluster --follow

# Filter for errors
aws logs filter-log-events \
  --log-group-name /aws/ec2/pekko-cluster \
  --filter-pattern "ERROR"
```

## Test Environments

### Development Testing
- **Local Environment**: Test against local instances
- **Mock Services**: Use mocked external dependencies
- **Unit Tests**: Test individual components

### Staging Testing
- **Pre-production Environment**: Mirror production setup
- **Integration Tests**: Test component interactions
- **End-to-End Tests**: Full workflow testing

### Production Testing
- **Smoke Tests**: Basic functionality verification
- **Health Checks**: Continuous monitoring
- **Performance Monitoring**: Real-time performance tracking

## Test Reporting

### Test Results Format
```json
{
  "test_suite": "aws-deployment",
  "timestamp": "2026-01-11T10:00:00Z",
  "total_tests": 7,
  "passed_tests": 7,
  "failed_tests": 0,
  "success_rate": "100%",
  "tests": [
    {
      "name": "health_check",
      "status": "passed",
      "response_time": "0.123s",
      "http_code": 200
    }
  ]
}
```

### Performance Metrics
```json
{
  "performance_test": "load_balancer",
  "timestamp": "2026-01-11T10:00:00Z",
  "metrics": {
    "total_requests": 1000,
    "successful_requests": 995,
    "failed_requests": 5,
    "success_rate": "99.5%",
    "average_response_time": "0.089s",
    "max_response_time": "0.234s",
    "min_response_time": "0.045s"
  }
}
```

## Troubleshooting Test Failures

### Common Test Failures

#### Health Check Failures
```bash
# Check instance status
aws ec2 describe-instances --filters "Name=tag:Name,Values=event-streaming-instance"

# Check application logs
aws logs tail /aws/ec2/pekko-cluster --since 1h
```

#### Load Balancer Issues
```bash
# Check target group health
aws elbv2 describe-target-health --target-group-arn YOUR_TG_ARN

# Check security groups
aws ec2 describe-security-groups --group-ids YOUR_SG_ID
```

#### Database Connection Issues
```bash
# Check RDS status
aws rds describe-db-instances --db-instance-identifier event-streaming-mysql

# Test database connectivity
mysql -h YOUR_RDS_ENDPOINT -u admin -p -e "SELECT 1"
```

### Test Environment Reset
```bash
# Reset test environment
terraform destroy -auto-approve
terraform apply -auto-approve

# Wait for deployment
sleep 300

# Run tests
./test-aws-deployment.sh
```

## Best Practices

### Test Design
- **Idempotent Tests**: Tests should be repeatable
- **Independent Tests**: Tests should not depend on each other
- **Clear Assertions**: Test outcomes should be unambiguous
- **Comprehensive Coverage**: Test all critical paths

### Test Execution
- **Parallel Execution**: Run independent tests in parallel
- **Timeout Handling**: Set appropriate timeouts
- **Retry Logic**: Implement retry for transient failures
- **Clean State**: Ensure clean test environment

### Test Maintenance
- **Regular Updates**: Keep tests updated with application changes
- **Performance Baselines**: Maintain performance benchmarks
- **Test Data Management**: Keep test data current and relevant
- **Documentation**: Document test procedures and expectations

## Security Testing

### Security Test Checklist
- [ ] Network security groups properly configured
- [ ] Database access restricted to application instances
- [ ] No hardcoded credentials in configuration
- [ ] HTTPS/TLS encryption for data in transit
- [ ] Database encryption at rest enabled
- [ ] IAM roles follow least privilege principle

### Security Test Commands
```bash
# Check security groups
aws ec2 describe-security-groups --group-ids YOUR_SG_ID

# Verify encryption
aws rds describe-db-instances --query 'DBInstances[*].StorageEncrypted'

# Check IAM policies
aws iam get-role-policy --role-name event-streaming-ec2-role --policy-name YOUR_POLICY
```