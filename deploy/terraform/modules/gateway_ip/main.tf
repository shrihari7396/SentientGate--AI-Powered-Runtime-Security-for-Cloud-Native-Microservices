resource "aws_eip" "gateway" {
  domain = "vpc"

  tags = merge(
    var.tags,
    {
      Name                                        = "${var.cluster_name}-api-gateway-eip"
      "kubernetes.io/cluster/${var.cluster_name}" = "owned"
      Component                                   = "api-gateway"
      Role                                        = "public-ingress"
    }
  )
}
