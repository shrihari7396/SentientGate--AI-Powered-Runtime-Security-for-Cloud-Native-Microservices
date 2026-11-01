variable "cluster_name" {
  description = "Name for the EKS cluster."
  type        = string
  default     = "sentientgate-eks"
}

variable "cluster_version" {
  description = "Kubernetes version for the EKS control plane."
  type        = string
  default     = "1.31"
}

variable "cluster_endpoint_public_access_cidrs" {
  description = "CIDR blocks allowed to reach the public EKS API endpoint."
  type        = list(string)
  default     = ["0.0.0.0/0"]
}

variable "github_actions_role_arn" {
  description = "IAM role assumed by GitHub Actions workflow (if applicable)."
  type        = string
  default     = null
}

variable "vpc_id" {
  description = "VPC ID where the EKS cluster will be deployed."
  type        = string
}

variable "subnet_ids" {
  description = "Subnet IDs for worker nodes (must be private subnets)."
  type        = list(string)
}

variable "control_plane_subnet_ids" {
  description = "Subnet IDs for the EKS control plane ENIs."
  type        = list(string)
}

variable "node_instance_types" {
  description = "Instance types for the EKS managed node group."
  type        = list(string)
  default     = ["t3.xlarge"]
}

variable "min_size" {
  description = "Minimum size of the node group."
  type        = number
  default     = 1
}

variable "max_size" {
  description = "Maximum size of the node group."
  type        = number
  default     = 4
}

variable "desired_size" {
  description = "Desired number of worker nodes."
  type        = number
  default     = 2
}

variable "disk_size" {
  description = "Disk size in GB for worker node instances."
  type        = number
  default     = 40
}

variable "tags" {
  description = "Resource tags applied to the EKS cluster and node group."
  type        = map(string)
  default     = {}
}
