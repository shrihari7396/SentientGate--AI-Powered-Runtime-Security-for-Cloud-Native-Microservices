# scripts/windows/deploy.ps1
# Deploy SentientGate to a Kubernetes cluster (installs KEDA if missing).
# Mirrors scripts/linux/deploy.sh.

$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
Set-Location $RepoRoot

Write-Host "============================================="
Write-Host "   SentientGate Kubernetes Deployment"
Write-Host "============================================="
Write-Host ""

Write-Host "[+] Checking Kubernetes cluster..."
kubectl cluster-info *> $null
if ($LASTEXITCODE -ne 0) { Write-Host "Kubernetes cluster is not reachable." -ForegroundColor Red; exit 1 }
Write-Host "Kubernetes cluster is available." -ForegroundColor Green
Write-Host ""

if (-not (Get-Command helm -ErrorAction SilentlyContinue)) {
    Write-Host "Helm is required to deploy the chart." -ForegroundColor Red
    exit 1
}

Write-Host "[+] Checking for KEDA (required for autoscaling)..."
kubectl get crd scaledobjects.keda.sh *> $null
if ($LASTEXITCODE -ne 0) {
    Write-Host "KEDA not found. Installing KEDA..."
    helm repo add kedacore https://kedacore.github.io/charts --force-update
    if ($LASTEXITCODE -ne 0) { Write-Host "KEDA Helm repository setup failed." -ForegroundColor Red; exit 1 }
    helm repo update
    if ($LASTEXITCODE -ne 0) { Write-Host "Helm repository update failed." -ForegroundColor Red; exit 1 }
    helm upgrade --install keda kedacore/keda --namespace keda --create-namespace --take-ownership --wait --timeout 10m
    if ($LASTEXITCODE -ne 0) { Write-Host "KEDA Helm installation failed." -ForegroundColor Red; exit 1 }
}
else { Write-Host "KEDA is already installed." -ForegroundColor Green }
Write-Host ""

Write-Host "[+] SentientGate Helm chart:"
Get-ChildItem -Path 'k8s' -Recurse -Include Chart.yaml, *.yaml | Sort-Object FullName | ForEach-Object { $_.FullName }
Write-Host ""

$confirm = Read-Host "Deploy all Kubernetes manifests? [y/N]"
if ($confirm -notmatch '^[Yy]$') { Write-Host "Deployment cancelled."; exit 0 }

Write-Host ""
Write-Host "============================================="
Write-Host "Deploying SentientGate Helm chart..."
Write-Host "============================================="
Write-Host ""

$RequiredSettings = @(
    'API_GATEWAY_SENTINEL_SECRET_KEY',
    'API_GATEWAY_JWT_SECRET_KEY',
    'POSTGRES_USER',
    'POSTGRES_PASSWORD',
    'OLLAMA_BASE_URL'
)
foreach ($Setting in $RequiredSettings) {
    if ([string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($Setting))) {
        Write-Host "Required environment variable is missing: $Setting" -ForegroundColor Red
        exit 1
    }
}

if (-not (Get-Command helm -ErrorAction SilentlyContinue)) {
    Write-Host "Helm is required to deploy the chart." -ForegroundColor Red
    exit 1
}

$ValuesDir = Join-Path ([System.IO.Path]::GetTempPath()) ([System.Guid]::NewGuid().ToString())
[System.IO.Directory]::CreateDirectory($ValuesDir) | Out-Null
$Utf8NoBom = [System.Text.UTF8Encoding]::new($false)
try {
    $ValueFiles = @{
        'api-gateway-sentinel-key' = 'API_GATEWAY_SENTINEL_SECRET_KEY'
        'api-gateway-jwt-key' = 'API_GATEWAY_JWT_SECRET_KEY'
        'postgres-user' = 'POSTGRES_USER'
        'postgres-password' = 'POSTGRES_PASSWORD'
        'ollama-base-url' = 'OLLAMA_BASE_URL'
    }
    foreach ($Entry in $ValueFiles.GetEnumerator()) {
        $Value = [Environment]::GetEnvironmentVariable($Entry.Value)
        [System.IO.File]::WriteAllText((Join-Path $ValuesDir $Entry.Key), $Value, $Utf8NoBom)
    }

    $RolloutId = (Get-Date).ToUniversalTime().ToString('yyyyMMddHHmmss')
    $HelmArgs = @(
        'upgrade', '--install', 'sentientgate', './k8s',
        '--namespace', 'sentientgate',
        '--create-namespace',
        '--take-ownership',
        '--values', './k8s/values.yaml',
        '--set-file', "secrets.apiGatewaySentinelSecretKey=$(Join-Path $ValuesDir 'api-gateway-sentinel-key')",
        '--set-file', "secrets.apiGatewayJwtSecretKey=$(Join-Path $ValuesDir 'api-gateway-jwt-key')",
        '--set-file', "secrets.postgresUser=$(Join-Path $ValuesDir 'postgres-user')",
        '--set-file', "secrets.postgresPassword=$(Join-Path $ValuesDir 'postgres-password')",
        '--set-file', "ollama.baseUrl=$(Join-Path $ValuesDir 'ollama-base-url')",
        '--set-string', "rolloutId=$RolloutId",
        '--wait', '--timeout', '10m'
    )
    & helm @HelmArgs
    if ($LASTEXITCODE -ne 0) { throw "Helm deployment failed with exit code $LASTEXITCODE." }
}
finally {
    foreach ($FileName in $ValueFiles.Keys) {
        $FilePath = Join-Path $ValuesDir $FileName
        if (Test-Path -LiteralPath $FilePath) {
            Remove-Item -LiteralPath $FilePath -Force
        }
    }
    Remove-Item -LiteralPath $ValuesDir
}

Write-Host ""
Write-Host "Helm deployment completed successfully!" -ForegroundColor Green
Write-Host ""

Write-Host "[+] Current Pods:"
kubectl get pods -n sentientgate
Write-Host ""
Write-Host "[+] Current Services:"
kubectl get services -n sentientgate
Write-Host ""
Write-Host "[+] Current Deployments:"
kubectl get deployments -n sentientgate
Write-Host ""
Write-Host "============================================="
Write-Host "SentientGate is deployed!"
Write-Host "============================================="
