resource "random_password" "master_password" {
  length           = 20
  special          = true
  override_special = "!#$%&*()-_=+[]{}<>:?"
}

locals {
  master_password = var.db_password != "" ? var.db_password : random_password.master_password.result
}

# -----------------------------------------------------------------------------
# Security Group for RDS MySQL (Strictly accessible from private EKS nodes)
# -----------------------------------------------------------------------------
resource "aws_security_group" "rds" {
  name_prefix = "${var.cluster_name}-rds-"
  description = "Security group for SentientGate RDS MySQL instance"
  vpc_id      = var.vpc_id

  ingress {
    description     = "Allow MySQL incoming traffic from EKS worker nodes"
    from_port       = 3306
    to_port         = 3306
    protocol        = "tcp"
    security_groups = var.eks_node_security_group_id != null && var.eks_node_security_group_id != "" ? [var.eks_node_security_group_id] : []
    cidr_blocks     = var.allowed_cidr_blocks
  }

  egress {
    description = "Allow all outbound traffic"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = merge(
    var.tags,
    {
      Name = "${var.cluster_name}-rds-sg"
    }
  )

  lifecycle {
    create_before_destroy = true
  }
}

# -----------------------------------------------------------------------------
# MySQL Parameter Group
# -----------------------------------------------------------------------------
resource "aws_db_parameter_group" "mysql" {
  name_prefix = "${var.cluster_name}-mysql8-"
  family      = "mysql8.0"
  description = "Custom parameter group for SentientGate MySQL 8.0"

  parameter {
    name  = "character_set_server"
    value = "utf8mb4"
  }

  parameter {
    name  = "collation_server"
    value = "utf8mb4_unicode_ci"
  }

  tags = var.tags

  lifecycle {
    create_before_destroy = true
  }
}

# -----------------------------------------------------------------------------
# RDS MySQL Database Instance (Private Subnets Only)
# -----------------------------------------------------------------------------
resource "aws_db_instance" "mysql" {
  identifier = "${var.cluster_name}-mysql"

  engine         = "mysql"
  engine_version = var.db_engine_version
  instance_class = var.db_instance_class

  allocated_storage     = var.db_allocated_storage
  max_allocated_storage = var.db_max_allocated_storage
  storage_type          = "gp3"
  storage_encrypted     = true

  db_name  = var.db_name
  username = var.db_username
  password = local.master_password

  db_subnet_group_name   = var.database_subnet_group_name
  vpc_security_group_ids = [aws_security_group.rds.id]
  parameter_group_name   = aws_db_parameter_group.mysql.name

  # CRITICAL: Database is private and not exposed to the public internet
  publicly_accessible = false

  # Enable AWS IAM Database Authentication for Pods / IRSA
  iam_database_authentication_enabled = true

  skip_final_snapshot     = var.db_skip_final_snapshot
  deletion_protection     = var.db_deletion_protection
  backup_retention_period = var.db_backup_retention_period

  apply_immediately = true

  tags = merge(
    var.tags,
    {
      Name = "${var.cluster_name}-mysql"
      Tier = "Database"
    }
  )
}

# -----------------------------------------------------------------------------
# AWS Secrets Manager (Credentials Storage for Application Pods)
# -----------------------------------------------------------------------------
resource "aws_secretsmanager_secret" "db_credentials" {
  name_prefix = "${var.cluster_name}-rds-credentials-"
  description = "Master credentials and connection endpoint for SentientGate MySQL RDS"

  recovery_window_in_days = 0

  tags = var.tags
}

resource "aws_secretsmanager_secret_version" "db_credentials" {
  secret_id = aws_secretsmanager_secret.db_credentials.id
  secret_string = jsonencode({
    engine   = "mysql"
    host     = aws_db_instance.mysql.address
    port     = aws_db_instance.mysql.port
    database = var.db_name
    username = var.db_username
    password = local.master_password
    jdbc_url = "jdbc:mysql://${aws_db_instance.mysql.address}:${aws_db_instance.mysql.port}/${var.db_name}?useSSL=true&requireSSL=true"
  })
}
