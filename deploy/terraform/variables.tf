# -----------------------------------------------------------------------------
# General & AWS Configuration
# -----------------------------------------------------------------------------
variable "aws_region" {
  description = "AWS region for all infrastructure resources."
  type        = string
  default     = "us-east-1"
}

variable "cluster_name" {
  description = "Name used for the EKS cluster, VPC, and associated resources."
  type        = string
  default     = "sentientgate-eks"
}

variable "github_actions_role_arn" {
  description = "IAM role assumed by the GitHub Actions OIDC workflow."
  type        = string
  default     = null
}

# -----------------------------------------------------------------------------
# Networking / VPC
# -----------------------------------------------------------------------------
variable "vpc_cidr" {
  description = "CIDR block for the EKS VPC."
  type        = string
  default     = "10.40.0.0/16"
}

variable "public_subnet_cidrs" {
  description = "CIDR blocks for public subnets (Ingress / NAT Gateway)."
  type        = list(string)
  default     = ["10.40.0.0/20", "10.40.16.0/20"]
}

variable "private_subnet_cidrs" {
  description = "CIDR blocks for private subnets (EKS nodes and elastic services)."
  type        = list(string)
  default     = ["10.40.32.0/20", "10.40.48.0/20"]
}

variable "database_subnet_cidrs" {
  description = "CIDR blocks for private isolated database subnets (RDS MySQL)."
  type        = list(string)
  default     = ["10.40.64.0/22", "10.40.68.0/22"]
}

variable "single_nat_gateway" {
  description = "Set to true to use a single NAT gateway to optimize cloud cost."
  type        = bool
  default     = true
}

# -----------------------------------------------------------------------------
# EKS Cluster
# -----------------------------------------------------------------------------
variable "cluster_version" {
  description = "Kubernetes version for the EKS control plane."
  type        = string
  default     = "1.31"
}

variable "cluster_endpoint_public_access_cidrs" {
  description = "CIDRs allowed to reach the public EKS API endpoint."
  type        = list(string)
  default     = ["0.0.0.0/0"]
}

variable "node_instance_types" {
  description = "Instance types for the EKS worker nodes."
  type        = list(string)
  default     = ["t3.xlarge"]
}

variable "node_min_size" {
  description = "Minimum number of worker nodes."
  type        = number
  default     = 1
}

variable "node_max_size" {
  description = "Maximum number of worker nodes."
  type        = number
  default     = 4
}

variable "node_desired_size" {
  description = "Desired number of worker nodes."
  type        = number
  default     = 2
}

variable "node_disk_size" {
  description = "Disk size in GB for worker node instances."
  type        = number
  default     = 40
}

# -----------------------------------------------------------------------------
# RDS MySQL Database
# -----------------------------------------------------------------------------
variable "db_name" {
  description = "Database name for the MySQL instance."
  type        = string
  default     = "sentientgate"
}

variable "db_username" {
  description = "Master administrative username for MySQL."
  type        = string
  default     = "sg_admin"
}

variable "db_password" {
  description = "Master password for MySQL. If empty, a secure password is auto-generated."
  type        = string
  default     = ""
  sensitive   = true
}

variable "db_instance_class" {
  description = "Compute and memory instance type for MySQL RDS."
  type        = string
  default     = "db.t3.micro"
}

variable "db_allocated_storage" {
  description = "Initial allocated storage in GB."
  type        = number
  default     = 20
}

variable "db_max_allocated_storage" {
  description = "Maximum autoscale storage limit in GB."
  type        = number
  default     = 100
}

variable "db_engine_version" {
  description = "MySQL database engine version."
  type        = string
  default     = "8.0"
}

variable "db_skip_final_snapshot" {
  description = "Skip final snapshot upon RDS destruction."
  type        = bool
  default     = true
}

variable "db_deletion_protection" {
  description = "Enable deletion protection on the database instance."
  type        = bool
  default     = false
}

# -----------------------------------------------------------------------------
# Kubernetes & IRSA
# -----------------------------------------------------------------------------
variable "kubernetes_namespace" {
  description = "Target Kubernetes namespace for SentientGate microservices."
  type        = string
  default     = "sentientgate"
}

variable "mcp_service_account_name" {
  description = "ServiceAccount name for MCP service."
  type        = string
  default     = "mcp-server"
}

variable "logging_service_account_name" {
  description = "ServiceAccount name for Logging service."
  type        = string
  default     = "logging-service"
}
