output "gateway_public_ip" {
  description = "The public IPv4 address assigned to the API Gateway / Ingress."
  value       = aws_eip.gateway.public_ip
}

output "gateway_allocation_id" {
  description = "The allocation ID of the Elastic IP (for NLB/ALB annotations)."
  value       = aws_eip.gateway.allocation_id
}

output "gateway_eip_id" {
  description = "The ID of the Elastic IP."
  value       = aws_eip.gateway.id
}
