variable "cluster_name" {
  description = "Cluster or application name used for naming and tagging the Elastic IP."
  type        = string
}

variable "tags" {
  description = "Resource tags applied to the Elastic IP."
  type        = map(string)
  default     = {}
}
