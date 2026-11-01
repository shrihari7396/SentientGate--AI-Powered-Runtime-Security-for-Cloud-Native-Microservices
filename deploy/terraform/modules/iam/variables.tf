variable "cluster_name" {
  description = "Name of the EKS cluster."
  type        = string
}

variable "aws_region" {
  description = "AWS region for building resource ARNs."
  type        = string
}

variable "aws_account_id" {
  description = "AWS account ID for IAM policies."
  type        = string
}

variable "oidc_provider_arn" {
  description = "ARN of the EKS OIDC provider."
  type        = string
}

variable "oidc_issuer_url" {
  description = "Issuer URL of the EKS OIDC provider (with or without https://)."
  type        = string
}

variable "namespace" {
  description = "Kubernetes namespace where service accounts are deployed."
  type        = string
  default     = "sentientgate"
}

variable "mcp_service_account_name" {
  description = "Kubernetes ServiceAccount name for the MCP service."
  type        = string
  default     = "mcp-server"
}

variable "logging_service_account_name" {
  description = "Kubernetes ServiceAccount name for the Logging service."
  type        = string
  default     = "logging-service"
}

variable "rds_resource_id" {
  description = "RDS DB instance Resource ID (e.g. db-XXXXX) for rds-db:connect policy."
  type        = string
}

variable "rds_credentials_secret_arn" {
  description = "ARN of the AWS Secrets Manager secret storing MySQL credentials."
  type        = string
}

variable "tags" {
  description = "Tags applied to all IAM roles and policies."
  type        = map(string)
  default     = {}
}
