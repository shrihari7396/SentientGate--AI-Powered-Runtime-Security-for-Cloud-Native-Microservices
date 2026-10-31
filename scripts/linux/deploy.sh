#!/bin/bash
# scripts/<os>/deploy.sh
set -e

trap 'echo -e "\n❌ Deployment failed at line $LINENO. Exiting."; exit 1' ERR

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"

echo "============================================="
echo "   🚀 SentientGate Kubernetes Deployment"
echo "============================================="
echo

echo "[+] Moving to project root..."
cd "$ROOT_DIR"

echo "[+] Current directory:"
pwd
echo

echo "[+] Checking Kubernetes cluster..."

if ! kubectl cluster-info > /dev/null 2>&1; then
    echo "❌ Kubernetes cluster is not reachable."
    exit 1
fi

echo "✅ Kubernetes cluster is available."
echo

if ! command -v helm >/dev/null 2>&1; then
    echo "❌ Helm is required to deploy the chart."
    exit 1
fi

echo "[+] Checking for KEDA (required for autoscaling)..."
if ! kubectl get crd scaledobjects.keda.sh >/dev/null 2>&1; then
    echo "📦 KEDA not found. Installing KEDA..."
    helm repo add kedacore https://kedacore.github.io/charts --force-update
    helm repo update
    helm upgrade --install keda kedacore/keda \
        --namespace keda --create-namespace --take-ownership --wait --timeout 10m
else
    echo "✅ KEDA is already installed."
fi
echo

echo "[+] SentientGate Helm chart:"
find k8s/ -type f \( -name "Chart.yaml" -o -path "k8s/templates/*.yaml" \) | sort
echo

read -r -p "Deploy the SentientGate Helm chart? [y/N]: " CONFIRM

if [[ ! "$CONFIRM" =~ ^[Yy]$ ]]; then
    echo "🚫 Deployment cancelled."
    exit 0
fi

echo
echo "============================================="
echo "📦 Deploying SentientGate Helm chart..."
echo "============================================="
echo

for setting in \
    API_GATEWAY_SENTINEL_SECRET_KEY \
    API_GATEWAY_JWT_SECRET_KEY \
    POSTGRES_USER \
    POSTGRES_PASSWORD \
    OLLAMA_BASE_URL; do
    if [[ -z "${!setting:-}" ]]; then
        echo "❌ Required environment variable is missing: ${setting}" >&2
        exit 1
    fi
done

values_dir="$(mktemp -d)"
trap 'rm -f "$values_dir/api-gateway-sentinel-key" "$values_dir/api-gateway-jwt-key" "$values_dir/postgres-user" "$values_dir/postgres-password" "$values_dir/ollama-base-url"; rmdir "$values_dir"' EXIT
printf '%s' "$API_GATEWAY_SENTINEL_SECRET_KEY" > "$values_dir/api-gateway-sentinel-key"
printf '%s' "$API_GATEWAY_JWT_SECRET_KEY" > "$values_dir/api-gateway-jwt-key"
printf '%s' "$POSTGRES_USER" > "$values_dir/postgres-user"
printf '%s' "$POSTGRES_PASSWORD" > "$values_dir/postgres-password"
printf '%s' "$OLLAMA_BASE_URL" > "$values_dir/ollama-base-url"

helm upgrade --install sentientgate ./k8s \
    --namespace sentientgate \
    --create-namespace \
    --take-ownership \
    --values ./k8s/values.yaml \
    --set-file "secrets.apiGatewaySentinelSecretKey=$values_dir/api-gateway-sentinel-key" \
    --set-file "secrets.apiGatewayJwtSecretKey=$values_dir/api-gateway-jwt-key" \
    --set-file "secrets.postgresUser=$values_dir/postgres-user" \
    --set-file "secrets.postgresPassword=$values_dir/postgres-password" \
    --set-file "ollama.baseUrl=$values_dir/ollama-base-url" \
    --set-string "rolloutId=$(date -u +%Y%m%d%H%M%S)" \
    --wait \
    --timeout 10m

echo
echo "============================================="
echo "✅ Helm deployment completed successfully!"
echo "============================================="
echo

echo "[+] Current Pods:"
kubectl get pods -n sentientgate

echo
echo "[+] Current Services:"
kubectl get services -n sentientgate

echo
echo "[+] Current Deployments:"
kubectl get deployments -n sentientgate

echo
echo "============================================="
echo "🎉 SentientGate is deployed!"
echo "============================================="
