output "db_instance_id" {
  description = "The RDS instance ID."
  value       = aws_db_instance.mysql.id
}

output "db_instance_endpoint" {
  description = "The connection endpoint in address:port format."
  value       = aws_db_instance.mysql.endpoint
}

output "db_instance_address" {
  description = "The hostname of the RDS MySQL instance."
  value       = aws_db_instance.mysql.address
}

output "db_instance_port" {
  description = "The port the database is listening on."
  value       = aws_db_instance.mysql.port
}

output "db_instance_name" {
  description = "The name of the database."
  value       = aws_db_instance.mysql.db_name
}

output "db_instance_username" {
  description = "Master username for database login."
  value       = aws_db_instance.mysql.username
}

output "db_instance_resource_id" {
  description = "The RDS Resource ID (e.g. db-XXXXX) required for IAM DB Authentication."
  value       = aws_db_instance.mysql.resource_id
}

output "db_security_group_id" {
  description = "Security group ID attached to the RDS MySQL instance."
  value       = aws_security_group.rds.id
}

output "db_credentials_secret_arn" {
  description = "The ARN of the AWS Secrets Manager secret holding DB credentials."
  value       = aws_secretsmanager_secret.db_credentials.arn
}

output "db_credentials_secret_name" {
  description = "The name of the AWS Secrets Manager secret holding DB credentials."
  value       = aws_secretsmanager_secret.db_credentials.name
}
