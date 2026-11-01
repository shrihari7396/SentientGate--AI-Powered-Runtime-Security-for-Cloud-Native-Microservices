# -----------------------------------------------------------------------------
# EKS Cluster Outputs
# -----------------------------------------------------------------------------
output "cluster_name" {
  description = "EKS cluster name for aws eks update-kubeconfig."
  value       = module.eks.cluster_name
}

output "cluster_endpoint" {
  description = "EKS API server endpoint."
  value       = module.eks.cluster_endpoint
}

output "cluster_arn" {
  description = "EKS cluster ARN."
  value       = module.eks.cluster_arn
}

output "cluster_security_group_id" {
  description = "Security group ID attached to the EKS control plane."
  value       = module.eks.cluster_security_group_id
}

output "node_security_group_id" {
  description = "Security group ID attached to the worker node group."
  value       = module.eks.node_security_group_id
}

output "oidc_provider_arn" {
  description = "ARN of the EKS OIDC Provider for IAM IRSA."
  value       = module.eks.oidc_provider_arn
}

# -----------------------------------------------------------------------------
# VPC & Networking Outputs
# -----------------------------------------------------------------------------
output "vpc_id" {
  description = "ID of the VPC created for SentientGate."
  value       = module.vpc.vpc_id
}

output "public_subnet_ids" {
  description = "Public subnet IDs used for ingress and NAT gateways."
  value       = module.vpc.public_subnet_ids
}

output "private_subnet_ids" {
  description = "Private subnet IDs hosting EKS nodes and elastic microservices."
  value       = module.vpc.private_subnet_ids
}

output "database_subnet_ids" {
  description = "Isolated database subnet IDs hosting RDS MySQL."
  value       = module.vpc.database_subnet_ids
}

# -----------------------------------------------------------------------------
# API Gateway Public IP
# -----------------------------------------------------------------------------
output "gateway_public_ip" {
  description = "Static public IPv4 address allocated for the API Gateway / Ingress."
  value       = module.gateway_ip.gateway_public_ip
}

output "gateway_allocation_id" {
  description = "Elastic IP allocation ID for AWS NLB/ALB annotations."
  value       = module.gateway_ip.gateway_allocation_id
}

# -----------------------------------------------------------------------------
# RDS MySQL Outputs
# -----------------------------------------------------------------------------
output "rds_endpoint" {
  description = "Connection endpoint for the RDS MySQL instance."
  value       = module.rds.db_instance_endpoint
}

output "rds_address" {
  description = "Hostname of the RDS MySQL instance."
  value       = module.rds.db_instance_address
}

output "rds_port" {
  description = "Port the RDS MySQL instance is listening on."
  value       = module.rds.db_instance_port
}

output "rds_database_name" {
  description = "Initial database name in the RDS instance."
  value       = module.rds.db_instance_name
}

output "rds_resource_id" {
  description = "RDS Resource ID used for IAM DB authentication policies."
  value       = module.rds.db_instance_resource_id
}

output "rds_credentials_secret_arn" {
  description = "ARN of the Secrets Manager secret storing MySQL credentials."
  value       = module.rds.db_credentials_secret_arn
}

# -----------------------------------------------------------------------------
# IAM IRSA Outputs (MCP & Logging Pods)
# -----------------------------------------------------------------------------
output "mcp_role_arn" {
  description = "IAM Role ARN for the MCP Server pod."
  value       = module.iam.mcp_role_arn
}

output "logging_role_arn" {
  description = "IAM Role ARN for the Logging Service pod."
  value       = module.iam.logging_role_arn
}

output "mcp_service_account_annotations" {
  description = "Annotations for the MCP Server Kubernetes ServiceAccount."
  value       = module.iam.mcp_service_account_annotations
}

output "logging_service_account_annotations" {
  description = "Annotations for the Logging Service Kubernetes ServiceAccount."
  value       = module.iam.logging_service_account_annotations
}
