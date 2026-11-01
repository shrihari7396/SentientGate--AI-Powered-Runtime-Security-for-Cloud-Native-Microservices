variable "cluster_name" {
  description = "EKS cluster name used for tagging subnets and naming resources."
  type        = string
}

variable "vpc_cidr" {
  description = "Base CIDR block for the VPC."
  type        = string
  default     = "10.40.0.0/16"
}

variable "availability_zones" {
  description = "List of availability zone names to deploy subnets into."
  type        = list(string)
}

variable "public_subnet_cidrs" {
  description = "CIDR blocks for public subnets (Internet Gateway / NAT / Public Ingress)."
  type        = list(string)
  default     = ["10.40.0.0/20", "10.40.16.0/20"]
}

variable "private_subnet_cidrs" {
  description = "CIDR blocks for private subnets (EKS nodes, pods, elastic services)."
  type        = list(string)
  default     = ["10.40.32.0/20", "10.40.48.0/20"]
}

variable "database_subnet_cidrs" {
  description = "CIDR blocks for isolated database subnets (RDS MySQL)."
  type        = list(string)
  default     = ["10.40.64.0/22", "10.40.68.0/22"]
}

variable "single_nat_gateway" {
  description = "Whether to use a single shared NAT gateway across AZs to minimize AWS costs."
  type        = bool
  default     = true
}

variable "tags" {
  description = "Common tags applied to all VPC resources."
  type        = map(string)
  default     = {}
}
