output "vpc_id" {
  description = "The ID of the created VPC."
  value       = aws_vpc.this.id
}

output "vpc_cidr_block" {
  description = "The CIDR block of the VPC."
  value       = aws_vpc.this.cidr_block
}

output "public_subnet_ids" {
  description = "List of IDs of public subnets."
  value       = aws_subnet.public[*].id
}

output "private_subnet_ids" {
  description = "List of IDs of private subnets for EKS worker nodes and elastic services."
  value       = aws_subnet.private[*].id
}

output "database_subnet_ids" {
  description = "List of IDs of isolated database subnets."
  value       = aws_subnet.database[*].id
}

output "database_subnet_group_name" {
  description = "The name of the RDS DB subnet group."
  value       = aws_db_subnet_group.this.name
}

output "nat_gateway_public_ips" {
  description = "Public IP addresses of the provisioned NAT gateways."
  value       = aws_nat_gateway.this[*].public_ip
}
