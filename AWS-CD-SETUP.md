# AWS release deployment setup

The `Deploy release to AWS EKS` workflow runs when a GitHub Release is
published. It checks for the `sentientgate-eks` cluster in the configured AWS
region (default `us-east-1`), creates the Terraform-managed VPC and EKS cluster
only when that cluster is absent, then installs the Helm chart in `k8s/` using
its `values.yaml`.

The current CI publishes the application images to Docker Hub with the `latest`
tag. The release workflow upgrades the Helm release with a unique run ID so
each release rolls the deployments and pulls the newly published images.
Publish the intended images before publishing the GitHub Release.

## GitHub Actions secrets

Add these under **Settings > Secrets and variables > Actions > Secrets**:

| Name | Value |
| --- | --- |
| `AWS_ROLE_ARN` | ARN of the AWS IAM role trusted for this repository's GitHub Actions OIDC identity. |
| `API_GATEWAY_SENTINEL_SECRET_KEY` | New, randomly generated key used for the API gateway's Sentinel authentication. |
| `API_GATEWAY_JWT_SECRET_KEY` | New, randomly generated JWT signing key. |
| `POSTGRES_USER` | PostgreSQL username used by PostgreSQL and the logging service. |
| `POSTGRES_PASSWORD` | Strong PostgreSQL password used by PostgreSQL and the logging service. |

The workflow writes the secret values to a temporary runner file and passes that
file to Helm as an additional values override. Do not put secret values in
`k8s/values.yaml` or commit them to the repository.

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

The AI service chart value `ollama.baseUrl` is populated from
`OLLAMA_BASE_URL`. Provide an endpoint reachable from EKS before releasing.

## What the workflow provisions

When the named cluster is absent from the selected AWS account and region,
Terraform creates a VPC across two availability zones, public and private
subnets, a single NAT gateway, an EKS control plane, two `t3.xlarge` managed
worker nodes (scaling from one to four), and the EBS CSI add-on. EKS access is
granted to the OIDC role in `AWS_ROLE_ARN`.

Only when the EKS cluster is absent, the workflow creates or reuses a private,
encrypted, versioned S3 bucket for Terraform state and provisions the cluster.
Its name is based on the AWS account and repository; Terraform uses an S3 lock
file to prevent concurrent state updates. If the cluster exists, the workflow
checks it before creating the bucket or initializing Terraform, skips all
Terraform provisioning, and deploys the Helm chart. For an existing cluster,
the OIDC role must already have Kubernetes access. The workflow does not import
or modify existing infrastructure.

The EKS API endpoint is public so GitHub-hosted runners can reach it; access
still requires AWS authentication. Restrict
`cluster_endpoint_public_access_cidrs` in `terraform/variables.tf` if you use
self-hosted runners with stable egress addresses.

The workflow installs ingress-nginx, KEDA, and metrics-server because the chart
uses Ingress, KEDA `ScaledObject`, and CPU-based HPAs. The Helm chart creates
the `sentientgate` namespace resources, application resources, ConfigMaps, and
Kubernetes Secrets in one release. `--take-ownership` allows Helm to adopt
resources previously applied from the Kubernetes manifests.

## Secret rotation and image notes

The Helm chart creates Kubernetes Secrets using the values supplied by the
workflow. Create fresh values for the GitHub secrets and rotate credentials
that were previously committed. Kubernetes Secret values are not encrypted by
base64 encoding. Helm also stores release values in its release records, so
restrict access to the namespace and cluster.

The Docker Hub images referenced by the chart are public. If they become
private, configure Kubernetes image-pull credentials in the chart before
releasing.

EKS, EC2 worker nodes, and NAT gateways incur AWS charges. Review the Terraform
plan and AWS pricing before publishing a release that provisions the cluster.
