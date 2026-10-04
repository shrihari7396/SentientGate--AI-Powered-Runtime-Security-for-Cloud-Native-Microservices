# AWS release deployment setup

The `Deploy release to AWS EKS` workflow runs when a GitHub Release is
published. It checks for the `sentientgate-eks` cluster in the configured AWS
region (default `us-east-1`), creates the Terraform-managed VPC and EKS cluster
only when that cluster is absent, and then applies the manifests in `k8s/`.

The current CI publishes the application images to Docker Hub with the `latest`
tag. The release workflow therefore reapplies the existing manifests and
restarts the application deployments so Kubernetes pulls those images again.
Publish the intended images before publishing the GitHub Release.

## GitHub Actions secrets

Add these under **Settings > Secrets and variables > Actions > Secrets**:

| Name | Value |
| --- | --- |
| `AWS_ROLE_ARN` | ARN of the AWS IAM role trusted for this repository's GitHub Actions OIDC identity. |
| `API_GATEWAY_SENTINEL_SECRET_KEY` | New, randomly generated secret key for the API gateway's Sentinel signing/authentication. |
| `API_GATEWAY_JWT_SECRET_KEY` | New, randomly generated JWT signing key. |
| `POSTGRES_USER` | PostgreSQL username used by both PostgreSQL and the logging service. |
| `POSTGRES_PASSWORD` | Strong PostgreSQL password used by both PostgreSQL and the logging service. |

Do not store AWS access keys. The workflow uses GitHub OIDC to assume
`AWS_ROLE_ARN`. The IAM role must already exist, trust
`token.actions.githubusercontent.com` with audience `sts.amazonaws.com`, and
limit its subject to this repository's release tags
(`repo:shrihari7396/SentientGate--AI-Powered-Runtime-Security-for-Cloud-Native-Microservices:ref:refs/tags/*`).

The role needs permissions to inspect/create EKS clusters, VPC and related EC2
network resources, IAM roles and policies used by EKS, and to pass those roles.
It also needs access to create and secure the workflow's Terraform state bucket
and read/write the Terraform state and lock file. Use a dedicated role and
scope permissions to this cluster, state bucket, and required resources rather
than using long-lived access keys.
Set the IAM role's maximum session duration to at least two hours for first-time
cluster provisioning.

## GitHub Actions variables

Add these under **Settings > Secrets and variables > Actions > Variables**:

| Name | Value |
| --- | --- |
| `AWS_REGION` | AWS region. Defaults to `us-east-1` when unset. |
| `OLLAMA_BASE_URL` | Reachable URL for the Ollama service from EKS, for example `http://ollama.example.internal:11434`. |

The current AI service manifest uses `host.minikube.internal`, which is only
available in Minikube. The workflow replaces that ConfigMap value with
`OLLAMA_BASE_URL`; provide a network-reachable endpoint before releasing.

## What the workflow provisions

When the named cluster is absent from the selected AWS account and region,
Terraform creates a VPC across two availability zones, public and private
subnets, a single NAT gateway, an EKS control plane, two `t3.xlarge` managed
worker nodes (scaling from one to four), and the EBS CSI add-on. EKS access is
granted to the OIDC role in `AWS_ROLE_ARN`.

The workflow creates a private, encrypted, versioned S3 bucket for Terraform
state on its first run. Its name is based on the AWS account and repository;
Terraform uses an S3 lock file to prevent concurrent state updates. A cluster
that already exists but has no state in this bucket is treated as externally
managed: the workflow skips Terraform creation and deploys to it. In that case,
the OIDC role must already have Kubernetes access to that cluster. The workflow
does not import or modify externally managed infrastructure.

The EKS API endpoint is public so GitHub-hosted runners can reach it; access
still requires AWS authentication. Restrict
`cluster_endpoint_public_access_cidrs` in `terraform/variables.tf` if you use
self-hosted runners with stable egress addresses.

The workflow installs ingress-nginx, KEDA, and metrics-server because the
manifests use Ingress, KEDA `ScaledObject`, and CPU-based HPAs. It creates the
`sentientgate` namespace and Kubernetes Secrets from the GitHub secrets above
before applying the remaining manifests.

## Secret rotation and image notes

The Kubernetes manifests previously contained API signing keys and
base64-encoded database credentials. Those values have been removed from the
manifests; create fresh values for the GitHub secrets and rotate any values
that were previously committed. Base64 encoding is not encryption.

The Docker Hub images referenced by the manifests are public. If they become
private, configure Kubernetes image-pull credentials before the release
workflow applies the manifests.

EKS, EC2 worker nodes, and NAT gateways incur AWS charges. Review the Terraform
plan and AWS pricing before publishing a release that provisions the cluster.
