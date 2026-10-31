# Helm Charts in SentientGate

While the core microservices of SentientGate are deployed using static Kubernetes manifests (located in the `k8s/` directory), we rely on **Helm** (the package manager for Kubernetes) to install and manage complex third-party infrastructure components.

## Third-Party Helm Installations

The following infrastructure components should be installed via Helm charts before applying the core application manifests.

### 1. KEDA (Kubernetes Event-driven Autoscaling)
KEDA is required for scaling the `mcp-server` based on Kafka consumer lag.

**Installation Commands:**
```bash
# Add the KEDA Helm repository
helm repo add kedacore https://kedacore.github.io/charts
helm repo update

# Install KEDA into the 'keda' namespace
helm install keda kedacore/keda --namespace keda --create-namespace
```

### 2. NGINX Ingress Controller (Optional but Recommended)
If your cluster does not come with an Ingress controller pre-installed (like Minikube with the ingress addon enabled), you must install NGINX to route external traffic to the API Gateway and Sentinel UI.

**Installation Commands:**
```bash
helm repo add ingress-nginx https://kubernetes.github.io/ingress-nginx
helm repo update

helm install ingress-nginx ingress-nginx/ingress-nginx \
  --namespace ingress-nginx --create-namespace
```

### 3. Prometheus & Grafana (Observability Stack)
To utilize the `/actuator/prometheus` endpoints exposed by the SentientGate microservices, deploy the kube-prometheus-stack.

**Installation Commands:**
```bash
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
helm repo update

helm install monitoring prometheus-community/kube-prometheus-stack \
  --namespace monitoring --create-namespace
```

---

## SentientGate Application Chart

The SentientGate application chart is in `k8s/`. It deploys the application
services, ConfigMaps, Kubernetes Secrets, ingress, autoscaling resources, and
supporting Kafka, PostgreSQL, and Redis components.

The release pipeline deploys it with `helm upgrade --install sentientgate ./k8s`
and `k8s/values.yaml`. Sensitive values are provided by GitHub Actions secrets
through a temporary values override; do not commit credentials to the chart.
