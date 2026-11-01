output "mcp_role_arn" {
  description = "ARN of the IAM role for the MCP Server pod."
  value       = aws_iam_role.mcp_server.arn
}

output "mcp_role_name" {
  description = "Name of the IAM role for the MCP Server pod."
  value       = aws_iam_role.mcp_server.name
}

output "logging_role_arn" {
  description = "ARN of the IAM role for the Logging Service pod."
  value       = aws_iam_role.logging_service.arn
}

output "logging_role_name" {
  description = "Name of the IAM role for the Logging Service pod."
  value       = aws_iam_role.logging_service.name
}

output "mcp_service_account_annotations" {
  description = "Kubernetes annotations for the MCP Server service account."
  value = {
    "eks.amazonaws.com/role-arn" = aws_iam_role.mcp_server.arn
  }
}

output "logging_service_account_annotations" {
  description = "Kubernetes annotations for the Logging Service service account."
  value = {
    "eks.amazonaws.com/role-arn" = aws_iam_role.logging_service.arn
  }
}
