# =============================================================================
# SentientGate Infrastructure - Root Orchestration
# =============================================================================

# 1. Networking / VPC Module (Public, Private, and Isolated Database Subnets)
module "vpc" {
  source = "./modules/vpc"

  cluster_name          = var.cluster_name
  vpc_cidr              = var.vpc_cidr
  availability_zones    = local.availability_zones
  public_subnet_cidrs   = var.public_subnet_cidrs
  private_subnet_cidrs  = var.private_subnet_cidrs
  database_subnet_cidrs = var.database_subnet_cidrs
  single_nat_gateway    = var.single_nat_gateway

  tags = local.common_tags
}

# 2. Public IP Allocation for API Gateway / Ingress
module "gateway_ip" {
  source = "./modules/gateway_ip"

  cluster_name = var.cluster_name

  tags = local.common_tags
}

# 3. EKS Cluster Module (Control Plane, Private Worker Nodes, Addons & OIDC)
module "eks" {
  source = "./modules/eks"

  cluster_name                         = var.cluster_name
  cluster_version                      = var.cluster_version
  cluster_endpoint_public_access_cidrs = var.cluster_endpoint_public_access_cidrs
  github_actions_role_arn              = var.github_actions_role_arn

  vpc_id                   = module.vpc.vpc_id
  subnet_ids               = module.vpc.private_subnet_ids
  control_plane_subnet_ids = module.vpc.private_subnet_ids

  node_instance_types = var.node_instance_types
  min_size            = var.node_min_size
  max_size            = var.node_max_size
  desired_size        = var.node_desired_size
  disk_size           = var.node_disk_size

  tags = local.common_tags
}

# 4. RDS MySQL Database Module (Isolated in Private Database Subnets)
module "rds" {
  source = "./modules/rds"

  cluster_name               = var.cluster_name
  vpc_id                     = module.vpc.vpc_id
  database_subnet_group_name = module.vpc.database_subnet_group_name
  eks_node_security_group_id = module.eks.node_security_group_id

  db_name                  = var.db_name
  db_username              = var.db_username
  db_password              = var.db_password
  db_instance_class        = var.db_instance_class
  db_allocated_storage     = var.db_allocated_storage
  db_max_allocated_storage = var.db_max_allocated_storage
  db_engine_version        = var.db_engine_version
  db_skip_final_snapshot   = var.db_skip_final_snapshot
  db_deletion_protection   = var.db_deletion_protection

  tags = local.common_tags
}

# 5. IAM IRSA Module (Role definitions for MCP & Logging pods to access RDS)
module "iam" {
  source = "./modules/iam"

  cluster_name                 = var.cluster_name
  aws_region                   = var.aws_region
  aws_account_id               = data.aws_caller_identity.current.account_id
  oidc_provider_arn            = module.eks.oidc_provider_arn
  oidc_issuer_url              = module.eks.cluster_oidc_issuer_url
  namespace                    = var.kubernetes_namespace
  mcp_service_account_name     = var.mcp_service_account_name
  logging_service_account_name = var.logging_service_account_name
  rds_resource_id              = module.rds.db_instance_resource_id
  rds_credentials_secret_arn   = module.rds.db_credentials_secret_arn

  tags = local.common_tags
}
