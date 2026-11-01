locals {
  oidc_issuer_clean = replace(var.oidc_issuer_url, "https://", "")
}

# -----------------------------------------------------------------------------
# Common RDS Access Policy (IAM DB Auth + Secrets Manager)
# -----------------------------------------------------------------------------
resource "aws_iam_policy" "rds_access" {
  name_prefix = "${var.cluster_name}-rds-access-"
  description = "Allows EKS pods to connect to RDS MySQL via IAM DB Auth and access credentials"

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "rds-db:connect"
        ]
        Resource = [
          "arn:aws:rds-db:${var.aws_region}:${var.aws_account_id}:dbuser:${var.rds_resource_id}/*"
        ]
      },
      {
        Effect = "Allow"
        Action = [
          "secretsmanager:GetSecretValue",
          "secretsmanager:DescribeSecret"
        ]
        Resource = [
          var.rds_credentials_secret_arn
        ]
      },
      {
        Effect = "Allow"
        Action = [
          "rds:DescribeDBInstances"
        ]
        Resource = "*"
      }
    ]
  })

  tags = var.tags
}

# -----------------------------------------------------------------------------
# Logging Service CloudWatch Policy
# -----------------------------------------------------------------------------
resource "aws_iam_policy" "logging_cloudwatch" {
  name_prefix = "${var.cluster_name}-logging-cw-"
  description = "Allows logging service pod to ship security and telemetry logs to CloudWatch"

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "logs:CreateLogGroup",
          "logs:CreateLogStream",
          "logs:PutLogEvents",
          "logs:DescribeLogStreams"
        ]
        Resource = "arn:aws:logs:${var.aws_region}:${var.aws_account_id}:log-group:/aws/sentientgate/*"
      }
    ]
  })

  tags = var.tags
}

# -----------------------------------------------------------------------------
# MCP Service IRSA Role
# -----------------------------------------------------------------------------
data "aws_iam_policy_document" "mcp_assume_role" {
  statement {
    actions = ["sts:AssumeRoleWithWebIdentity"]
    effect  = "Allow"

    principals {
      type        = "Federated"
      identifiers = [var.oidc_provider_arn]
    }

    condition {
      test     = "StringEquals"
      variable = "${local.oidc_issuer_clean}:sub"
      values   = ["system:serviceaccount:${var.namespace}:${var.mcp_service_account_name}"]
    }

    condition {
      test     = "StringEquals"
      variable = "${local.oidc_issuer_clean}:aud"
      values   = ["sts.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "mcp_server" {
  name               = "${var.cluster_name}-mcp-server-role"
  description        = "IAM role for SentientGate MCP Server pod to access RDS MySQL"
  assume_role_policy = data.aws_iam_policy_document.mcp_assume_role.json

  tags = merge(
    var.tags,
    {
      Service = "mcp-server"
    }
  )
}

resource "aws_iam_role_policy_attachment" "mcp_rds" {
  role       = aws_iam_role.mcp_server.name
  policy_arn = aws_iam_policy.rds_access.arn
}

# -----------------------------------------------------------------------------
# Logging Service IRSA Role
# -----------------------------------------------------------------------------
data "aws_iam_policy_document" "logging_assume_role" {
  statement {
    actions = ["sts:AssumeRoleWithWebIdentity"]
    effect  = "Allow"

    principals {
      type        = "Federated"
      identifiers = [var.oidc_provider_arn]
    }

    condition {
      test     = "StringEquals"
      variable = "${local.oidc_issuer_clean}:sub"
      values   = ["system:serviceaccount:${var.namespace}:${var.logging_service_account_name}"]
    }

    condition {
      test     = "StringEquals"
      variable = "${local.oidc_issuer_clean}:aud"
      values   = ["sts.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "logging_service" {
  name               = "${var.cluster_name}-logging-service-role"
  description        = "IAM role for SentientGate Logging Service pod to access RDS MySQL and CloudWatch"
  assume_role_policy = data.aws_iam_policy_document.logging_assume_role.json

  tags = merge(
    var.tags,
    {
      Service = "logging-service"
    }
  )
}

resource "aws_iam_role_policy_attachment" "logging_rds" {
  role       = aws_iam_role.logging_service.name
  policy_arn = aws_iam_policy.rds_access.arn
}

resource "aws_iam_role_policy_attachment" "logging_cw" {
  role       = aws_iam_role.logging_service.name
  policy_arn = aws_iam_policy.logging_cloudwatch.arn
}
