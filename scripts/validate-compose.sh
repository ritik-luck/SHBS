#!/usr/bin/env bash
# validate-compose.sh — Validate that the Compose stack starts correctly with 2 backend replicas
set -euo pipefail

echo "🔍 Validating Compose configuration..."
docker compose -f docker-compose.yml config --quiet
echo "✅ Compose config is valid."

echo ""
echo "🚀 Starting stack with --scale backend=2..."
docker compose up -d --build --scale backend=2

echo ""
echo "⏳ Waiting for services to be healthy (60s timeout)..."
TIMEOUT=60
ELAPSED=0
INTERVAL=5

while [ $ELAPSED -lt $TIMEOUT ]; do
  HEALTHY=$(docker compose ps --format json | grep -c '"Health":"healthy"' || true)
  TOTAL=$(docker compose ps --format json | wc -l | tr -d ' ')
  echo "  Healthy: ${HEALTHY}/${TOTAL} (${ELAPSED}s)"

  if [ "$HEALTHY" -ge "$TOTAL" ] && [ "$TOTAL" -gt 0 ]; then
    echo "✅ All services healthy!"
    break
  fi

  sleep $INTERVAL
  ELAPSED=$((ELAPSED + INTERVAL))
done

echo ""
echo "🔍 Verifying backend replicas..."
BACKEND_COUNT=$(docker compose ps backend --format json | wc -l | tr -d ' ')
echo "  Backend containers: ${BACKEND_COUNT}"

if [ "$BACKEND_COUNT" -ge 2 ]; then
  echo "✅ Two backend replicas confirmed."
else
  echo "❌ Expected 2 backend replicas, found ${BACKEND_COUNT}"
  docker compose logs backend --tail=20
  docker compose down
  exit 1
fi

echo ""
echo "🧹 Tearing down..."
docker compose down
echo "✅ Validation complete."
