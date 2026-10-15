# Rollback Runbook

## Quick Reference

### Rollback via GitHub Actions (Recommended)
```bash
# Deploy the last known-good image tag
gh workflow run deploy.yml \
  -f environment=production \
  -f image_tag=<PREVIOUS_GOOD_TAG>
```

### Rollback via ECS (Direct)
```bash
# List recent task definitions
aws ecs list-task-definitions --family shbs-backend --sort DESC --max-items 5

# Update service to previous task definition
aws ecs update-service \
  --cluster shbs-production \
  --service backend \
  --task-definition shbs-backend:<PREVIOUS_REVISION> \
  --force-new-deployment
```

### Rollback via Docker Compose (Staging/Dev)
```bash
# Pull and run the previous known-good tag
export IMAGE_TAG=<PREVIOUS_GOOD_TAG>
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d
```

## Finding the Previous Good Version
1. Check GitHub Actions: download the `deployment-record-*` artifact from the last successful deployment.
2. Check ECS console: review the previous task definition revision.
3. Check deployment metadata stored in the CI artifacts (retained for 90 days).

## Database Rollback

> [!CAUTION]
> Database rollbacks are destructive operations. Always take a snapshot before proceeding.

### RDS Point-in-Time Recovery
```bash
# Restore to a specific point in time
aws rds restore-db-instance-to-point-in-time \
  --source-db-instance-identifier shbs-production \
  --target-db-instance-identifier shbs-production-restored \
  --restore-time "2026-09-06T10:00:00Z"

# After verification, rename instances to swap
```

### Flyway Repair (if migration partially applied)
```bash
# Connect to the application and run Flyway repair
./mvnw flyway:repair -Dflyway.url=<DB_URL>
```

## Incident Escalation
1. **P0 (Production down)**: Page on-call engineer immediately.
2. **P1 (Degraded)**: Notify team channel, investigate within 30 minutes.
3. **P2 (Non-critical)**: Create ticket, investigate next business day.

## Post-Rollback Checklist
- [ ] Verify `/actuator/health/readiness` returns 200
- [ ] Verify circuit breakers are CLOSED
- [ ] Verify error rate has returned to baseline
- [ ] Verify no data corruption occurred
- [ ] Create incident report
- [ ] Schedule root cause analysis
