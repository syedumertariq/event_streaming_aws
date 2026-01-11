# Outputs for Event Streaming AWS Infrastructure

output "vpc_id" {
  description = "ID of the VPC"
  value       = aws_vpc.main.id
}

output "vpc_cidr_block" {
  description = "CIDR block of the VPC"
  value       = aws_vpc.main.cidr_block
}

output "public_subnet_ids" {
  description = "IDs of the public subnets"
  value       = aws_subnet.public[*].id
}

output "private_subnet_ids" {
  description = "IDs of the private subnets"
  value       = aws_subnet.private[*].id
}

output "load_balancer_dns_name" {
  description = "DNS name of the load balancer"
  value       = aws_lb.main.dns_name
}

output "load_balancer_zone_id" {
  description = "Zone ID of the load balancer"
  value       = aws_lb.main.zone_id
}

output "load_balancer_url" {
  description = "URL of the load balancer"
  value       = "http://${aws_lb.main.dns_name}"
}

output "rds_endpoint" {
  description = "RDS instance endpoint"
  value       = aws_db_instance.mysql.endpoint
}

output "rds_port" {
  description = "RDS instance port"
  value       = aws_db_instance.mysql.port
}

output "database_info" {
  description = "Database connection information"
  value = {
    endpoint     = aws_db_instance.mysql.endpoint
    port         = aws_db_instance.mysql.port
    database_name = aws_db_instance.mysql.db_name
    # Note: password is sensitive and not exposed
  }
  sensitive = false
}

output "security_group_ids" {
  description = "Security group IDs"
  value = {
    alb = aws_security_group.alb.id
    ec2 = aws_security_group.ec2.id
    rds = aws_security_group.rds.id
  }
}

output "autoscaling_group_name" {
  description = "Name of the Auto Scaling Group"
  value       = aws_autoscaling_group.app.name
}

output "launch_template_id" {
  description = "ID of the launch template"
  value       = aws_launch_template.app.id
}

output "target_group_arn" {
  description = "ARN of the target group"
  value       = aws_lb_target_group.app.arn
}

# Application URLs
output "application_urls" {
  description = "Application access URLs"
  value = {
    load_balancer = "http://${aws_lb.main.dns_name}"
    health_check  = "http://${aws_lb.main.dns_name}/actuator/health"
    dashboard     = "http://${aws_lb.main.dns_name}/dashboard"
    api_base      = "http://${aws_lb.main.dns_name}/api"
  }
}

# Deployment Information
output "deployment_info" {
  description = "Deployment information"
  value = {
    region            = var.aws_region
    environment       = var.environment
    instance_type     = var.instance_type
    min_instances     = var.asg_min_size
    max_instances     = var.asg_max_size
    desired_instances = var.asg_desired_capacity
    db_instance_class = var.db_instance_class
  }
}