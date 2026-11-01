variable "cluster_name" {
  description = "Cluster or application prefix for naming RDS resources."
  type        = string
}

variable "vpc_id" {
  description = "VPC ID where the RDS security group will be created."
  type        = string
}

variable "database_subnet_group_name" {
  description = "Name of the DB subnet group (placed in private database subnets)."
  type        = string
}

variable "eks_node_security_group_id" {
  description = "Security group ID of the EKS worker nodes allowed to connect to RDS."
  type        = string
  default     = null
}

variable "allowed_cidr_blocks" {
  description = "Additional CIDR blocks allowed to connect to RDS on port 3306."
  type        = list(string)
  default     = []
}

variable "db_name" {
  description = "Name of the initial MySQL database."
  type        = string
  default     = "sentientgate"
}

variable "db_username" {
  description = "Master username for MySQL RDS."
  type        = string
  default     = "sg_admin"
}

variable "db_password" {
  description = "Master password for MySQL RDS. If left empty, a secure random password is generated."
  type        = string
  default     = ""
  sensitive   = true
}

variable "db_instance_class" {
  description = "Compute and memory capacity of the RDS instance."
  type        = string
  default     = "db.t3.micro"
}

variable "db_allocated_storage" {
  description = "Allocated storage size in GB."
  type        = number
  default     = 20
}

variable "db_max_allocated_storage" {
  description = "Upper limit in GB for RDS storage autoscaling."
  type        = number
  default     = 100
}

variable "db_engine_version" {
  description = "MySQL database engine version."
  type        = string
  default     = "8.0"
}

variable "db_skip_final_snapshot" {
  description = "Whether to skip a final snapshot before deleting the DB instance."
  type        = bool
  default     = true
}

variable "db_deletion_protection" {
  description = "Protect database from accidental deletion."
  type        = bool
  default     = false
}

variable "db_backup_retention_period" {
  description = "Backup retention period in days."
  type        = number
  default     = 7
}

variable "tags" {
  description = "Tags applied to all RDS resources."
  type        = map(string)
  default     = {}
}
