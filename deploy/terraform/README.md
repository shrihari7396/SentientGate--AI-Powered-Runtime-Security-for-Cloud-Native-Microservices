# SentientGate Infrastructure (Terraform)

This repository contains modular Terraform infrastructure code for **SentientGate - AI-Powered Runtime Security for Cloud-Native Microservices**.

---

## Directory Structure

```text
terraform/
├── main.tf                    # Root composition module connecting all infrastructure components
├── variables.tf               # Top-level input variables with sensible defaults
├── outputs.tf                 # Top-level outputs (cluster endpoints, VPC, RDS, IAM roles, EIP)
├── versions.tf                # Terraform required providers and S3 backend configuration
├── terraform.tfvars.example   # Example input variable file
└── modules/
    ├── vpc/                   # VPC networking with 3 tiers of subnets (Public, Private, Database)
    │   ├── main.tf
    │   ├── variables.tf
    │   └── outputs.tf
    ├── eks/                   # AWS EKS cluster, managed worker nodes, addons, and OIDC provider
    │   ├── main.tf
    │   ├── variables.tf
    │   └── outputs.tf
    ├── rds/                   # Private RDS MySQL instance, subnet group, security group, and Secrets Manager
    │   ├── main.tf
    │   ├── variables.tf
    │   └── outputs.tf
    ├── iam/                   # IAM Roles for Service Accounts (IRSA) for MCP and Logging Service pods
    │   ├── main.tf
    │   ├── variables.tf
    │   └── outputs.tf
    └── gateway_ip/            # Static Elastic IP (EIP) allocated for API Gateway Ingress
        ├── main.tf
        ├── variables.tf
        └── outputs.tf
```

---

## Architecture & Design Principles

### 1. Multi-Tier Subnet Isolation in VPC
- **Public Subnets**: Host Internet Gateway, NAT Gateway, and ingress load balancers. Tagged with `kubernetes.io/role/elb = 1`.
- **Private Subnets**: Host EKS worker nodes and elastic backend microservices (AI Service, Kafka, Redis, Eureka). Outbound internet access is routed via NAT Gateway. Inbound direct internet access is blocked. Tagged with `kubernetes.io/role/internal-elb = 1`.
- **Database Subnets**: Dedicated, isolated subnets for **RDS MySQL**. These subnets have **NO route to the internet** (no IGW, no NAT). They are accessible exclusively within the VPC from private compute subnets.

### 2. RDS MySQL Database
- Deployed inside an `aws_db_subnet_group` spanning the private database subnets across multiple AZs.
- `publicly_accessible = false` prevents any public internet exposure.
- Security Group allows inbound MySQL traffic (`3306/tcp`) strictly from the EKS worker nodes security group (`node_security_group_id`).
- `iam_database_authentication_enabled = true` enables AWS IAM authentication for Kubernetes pods.
- Master credentials and JDBC URL are generated and stored in AWS Secrets Manager.

### 3. IRSA (IAM Roles for Service Accounts) for MCP and Logging Pods
- Connects Kubernetes ServiceAccounts (`mcp-server` and `logging-service`) in the `sentientgate` namespace to AWS IAM roles via the cluster OIDC provider.
- **MCP Server Role**:
  - `rds-db:connect` allows logging into MySQL via IAM auth.
  - `secretsmanager:GetSecretValue` allows reading DB credentials from Secrets Manager.
- **Logging Service Role**:
  - `rds-db:connect` allows logging into MySQL via IAM auth.
  - `secretsmanager:GetSecretValue` allows reading DB credentials from Secrets Manager.
  - `logs:*` allows streaming runtime telemetry and security audit logs to AWS CloudWatch.

### 4. API Gateway Public IP
- Allocates an AWS Elastic IP (`aws_eip`) with tags for EKS Ingress.
- Can be bound directly to Ingress NGINX or AWS Load Balancer Controller using the allocation ID (`gateway_allocation_id`).

---

## Deployment Instructions

### Prerequisites
- Terraform >= 1.10.0
- AWS CLI configured with appropriate credentials or assumed role

### Initializing and Applying
```bash
cd terraform

# 1. Initialize Terraform
terraform init

# 2. Review Plan
terraform plan

# 3. Apply Infrastructure
terraform apply
```

### Kubernetes ServiceAccount Setup
Once provisioned, configure the Kubernetes ServiceAccounts with the output IAM role ARNs:

```yaml
apiVersion: v1
kind: ServiceAccount
metadata:
  name: mcp-server
  namespace: sentientgate
  annotations:
    eks.amazonaws.com/role-arn: "<output: mcp_role_arn>"
---
apiVersion: v1
kind: ServiceAccount
metadata:
  name: logging-service
  namespace: sentientgate
  annotations:
    eks.amazonaws.com/role-arn: "<output: logging_role_arn>"
```
