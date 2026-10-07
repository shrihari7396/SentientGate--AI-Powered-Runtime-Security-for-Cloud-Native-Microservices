#!/bin/bash
# scripts/<os>/build_and_push_images.sh
#
# Usage:
#   build_and_push_images.sh [build|push|all] [image-tag]
#
#   build  - build every image locally (no push)
#   push   - tag + push every previously-built image
#   all    - build every image, then push them (default)
#   image-tag defaults to latest
#
# The build phase aborts on the FIRST failure (set -e), so a service that fails
# to compile/package can never result in a partial set of pushed images. In CI,
# run `build` and `push` as two separate steps so a build failure fails the job
# before anything is pushed to the registry.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR/../.."

PHASE="${1:-all}"

# Configuration
REGISTRY_USER="shrihari7396"
TAG="${2:-latest}"

# Colors for output
GREEN='\033[0;32m'
RED='\033[0;31m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Define services with their folder name and final image name
# Format: "FOLDER_NAME:IMAGE_NAME"
SERVICES=(
    "EurekaServer:eureka-server"
    "ApiGateway:api-gateway"
    "LoggingService:logging-service"
    "MCPService:mcp-server"
    "AIService:ai-service"
    "services:dummy-service"
    "UI/sentinel-gateway-ui:sentinel-ui"
)

build_all() {
    echo -e "\n${BLUE}=== Building all images ===${NC}"
    for service in "${SERVICES[@]}"; do
        local folder="${service%%:*}"
        local image="${service##*:}"
        local local_tag="sentientgate_${image}"

        if [ ! -d "$folder" ]; then
            echo -e "${RED}❌ Folder not found: ${folder}${NC}"
            exit 1
        fi

        echo -e "\n${BLUE}🔨 Building ${folder} -> ${local_tag}...${NC}"
        docker build -t "$local_tag" "$folder"
        echo -e "${GREEN}✅ Built: ${local_tag}${NC}"
    done
    echo -e "\n${GREEN}✅ All images built successfully.${NC}"
}

push_all() {
    echo -e "\n${BLUE}=== Pushing all images ===${NC}"
    for service in "${SERVICES[@]}"; do
        local image="${service##*:}"
        local local_tag="sentientgate_${image}"
        local remote_tag="${REGISTRY_USER}/${image}:${TAG}"

        echo -e "\n${BLUE}🏷️  Tagging ${local_tag} -> ${remote_tag}${NC}"
        docker rmi "$remote_tag" 2>/dev/null || true
        docker tag "$local_tag" "$remote_tag"

        echo -e "${BLUE}📤 Pushing ${remote_tag}...${NC}"
        docker push "$remote_tag"
        echo -e "${GREEN}✅ Pushed: ${remote_tag}${NC}"
    done
    echo -e "\n${GREEN}🎉 All images pushed successfully!${NC}"
}

echo -e "${BLUE}🚀 SentientGate images — phase: ${PHASE}${NC}"
case "$PHASE" in
    build) build_all ;;
    push)  push_all ;;
    all)   build_all; push_all ;;
    *)
        echo -e "${RED}Unknown phase: ${PHASE} (use: build | push | all)${NC}"
        exit 2
        ;;
esac
