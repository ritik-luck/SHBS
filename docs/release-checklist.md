# Release Checklist

## Pre-Deployment

### Code Quality
- [ ] All CI checks pass (backend tests, frontend build, security scans)
- [ ] Docker images built and pushed to registry with semver+SHA tag
- [ ] Image vulnerability scan shows no CRITICAL findings
- [ ] PR approved by at least one reviewer
- [ ] No unresolved CRITICAL/HIGH dependency alerts

### Database
- [ ] Flyway migrations are backward-compatible (expand phase only)
- [ ] Migration tested against a copy of production data
- [ ] Rollback migration prepared (if applicable)

### Configuration
- [ ] All required secrets are set in target environment
- [ ] CORS origins updated for target domain
- [ ] WebSocket URL (`wss://`) configured for production
- [ ] No dev-default secrets remain in production config

## Staging Acceptance

- [ ] Deploy to staging with production-like config
- [ ] Health check passes: `/actuator/health/readiness` returns 200
- [ ] Circuit breaker is CLOSED for all downstream services
- [ ] Smoke test: create an order, verify it flows through inventory check
- [ ] WebSocket dashboard receives real-time updates
- [ ] Grafana dashboards load with data
- [ ] Prometheus scrapes all backend replicas (check targets page)
- [ ] Alert rules fire correctly (trigger a test alert)
- [ ] Load test: k6 baseline shows no regression in p95 latency

## Deployment

- [ ] Trigger deployment workflow with correct image tag
- [ ] Monitor deployment progress in ECS/GitHub Actions
- [ ] Smoke test passes in production
- [ ] Verify no 5xx spike in ALB metrics (first 15 minutes)

## Post-Deployment

- [ ] Verify deployment record artifact was created
- [ ] Monitor error rate for 30 minutes
- [ ] Verify SLO burn rate is within budget
- [ ] Update deployment log/changelog
- [ ] Notify team of successful deployment

## Rollback Criteria (Automatic or Manual)

If any of these occur within 30 minutes of deployment, **rollback immediately**:
- [ ] Readiness probe fails persistently
- [ ] 5xx rate exceeds 5% of total requests
- [ ] p95 latency exceeds 2x pre-deployment baseline
- [ ] SLO fast-burn alert fires (>14.4x burn rate)
- [ ] Circuit breaker opens unexpectedly
- [ ] Data integrity issue detected
