#!/usr/bin/env bash
# generate-sbom.sh — Generate SBOMs for all SHBS Docker images
# Requires: syft (https://github.com/anchore/syft)
set -euo pipefail

REGISTRY="${1:-ghcr.io/ritik-luck/shbs}"
TAG="${2:-latest}"

for SERVICE in backend frontend inventory-service; do
  IMAGE="${REGISTRY}/${SERVICE}:${TAG}"
  OUTPUT="sbom-${SERVICE}.spdx.json"
  echo "📦 Generating SBOM for ${IMAGE} → ${OUTPUT}"
  syft "${IMAGE}" -o spdx-json > "${OUTPUT}"
  echo "✅ Done: ${OUTPUT}"
done

echo ""
echo "All SBOMs generated. Upload these as release artifacts."
