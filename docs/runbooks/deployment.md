# Deployment Runbook

## Prerequisites
- Access to GitHub Actions
- AWS CLI configured (for ECS deployments)
- Docker and Docker Compose (for Compose deployments)

## Deployment Flow

### Standard Rolling Deployment (Default)
1. Merge PR to `main` → CI pipeline runs automatically.
2. CI builds images, tags with `YYYYMMDD-<sha>`, pushes to registry.
3. Deploy workflow triggers automatically for staging.
4. Smoke test polls `/actuator/health/readiness` with 120s timeout.
5. On success, deployment record is stored as GitHub artifact.

### Manual Deployment
```bash
# Trigger via GitHub Actions UI or CLI:
gh workflow run deploy.yml -f environment=staging -f image_tag=20260906-abc1234
```

### Production Deployment
1. Deploy to staging first.
2. Run staging acceptance checklist (see `release-checklist.md`).
3. Trigger production deployment via GitHub Actions with manual approval.

## High-Risk Change Protocol
Use blue-green deployment for:
- Flyway database migrations
- Circuit breaker logic changes
- Redis coordination changes
- Authentication/JWT changes
- Recovery logic changes

### Blue-Green Steps
1. Deploy new version to "green" target group (separate ECS service or task set).
2. Run integration tests against green.
3. Gradually shift ALB traffic (10% → 50% → 100%).
4. Monitor for 15 minutes for regressions.
5. If clean, deregister blue target group.

## Automatic Rollback Criteria
The system should automatically rollback if any of these occur within 15 minutes of deployment:
- Readiness probe failure (`/actuator/health/readiness` returns non-200)
- 5xx error rate exceeds 5% (measured by ALB CloudWatch metrics)
- p95 latency exceeds 2x the pre-deployment baseline
- SLO fast-burn alert fires (burn rate > 14.4x)
- Circuit breaker opens unexpectedly for any downstream service

## Flyway Migration Strategy
Use **expand → migrate → contract**:
1. **Expand**: Add new columns/tables (backward-compatible with old code).
2. **Migrate**: Deploy new code that uses new schema.
3. **Contract**: Remove deprecated columns/tables in a subsequent release.

> [!WARNING]
> Never deploy a destructive migration (DROP COLUMN, rename) in the same release as the code change that depends on it.
