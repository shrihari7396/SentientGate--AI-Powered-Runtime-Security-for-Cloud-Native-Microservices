variable "aws_region" {
  description = "AWS region for the EKS cluster and networking resources."
  type        = string
  default     = "us-east-1"
}

variable "cluster_name" {
  description = "Name used for the EKS cluster."
  type        = string
  default     = "sentientgate-eks"
}

variable "cluster_version" {
  description = "Kubernetes version for the EKS control plane."
  type        = string
  default     = "1.35"
}

variable "github_actions_role_arn" {
  description = "IAM role assumed by the GitHub Actions OIDC workflow."
  type        = string
}

variable "vpc_cidr" {
  description = "CIDR block for the EKS VPC."
  type        = string
  default     = "10.40.0.0/16"
}

variable "cluster_endpoint_public_access_cidrs" {
  description = "CIDRs allowed to reach the public EKS API endpoint. GitHub-hosted runners require a public endpoint."
  type        = list(string)
  default     = ["0.0.0.0/0"]
}
