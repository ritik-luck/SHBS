# Incident Response Runbook

## Severity Levels

| Level | Definition | Response Time | Examples |
|-------|-----------|---------------|----------|
| **P0** | Production down, all users affected | Immediate (< 5 min) | Service unreachable, data corruption |
| **P1** | Degraded, significant user impact | < 30 min | High error rate, circuit breakers open |
| **P2** | Minor impact, workaround exists | < 4 hours | Single endpoint slow, non-critical alert |
| **P3** | No user impact | Next business day | Log anomalies, dependency warnings |

## P0 Playbook: Production Down

### Assess (0-5 min)
1. Check ALB health: `aws elbv2 describe-target-health`
2. Check ECS service: `aws ecs describe-services --cluster shbs-prod`
3. Check recent deployments: review GitHub Actions deploy artifacts
4. Check RDS status: `aws rds describe-db-instances`

### Mitigate (5-15 min)
1. **If caused by recent deployment:** Rollback immediately (see [rollback runbook](./rollback.md))
2. **If caused by database:** Check RDS metrics, initiate PITR if needed (see [DR runbook](./disaster-recovery.md))
3. **If caused by Redis:** Check ElastiCache metrics; application should fail-open
4. **If caused by downstream (inventory-service):** Circuit breaker should activate automatically; verify state

### Communicate (Continuous)
- Post status update in team channel every 15 minutes
- Update status page if applicable
- Notify stakeholders of impact and ETA

### Resolve
1. Identify root cause
2. Implement fix or confirm rollback resolves the issue
3. Monitor for 30 minutes post-resolution

### Post-Incident (Within 48 hours)
1. Write incident report: timeline, root cause, impact, resolution
2. Identify action items to prevent recurrence
3. Update runbooks if gaps were discovered
4. Schedule blameless post-mortem

## P1 Playbook: High Error Rate / Circuit Breaker Open

### Assess
1. Check Grafana dashboard for error rate and circuit state
2. Check `/actuator/health/readiness` on each replica
3. Check Prometheus for `circuit_breaker_state` gauge
4. Check recent deployments

### Mitigate
1. If circuit breaker is OPEN: system should self-heal (HALF_OPEN → CLOSED cycle)
2. If self-healing is not working: check downstream service health
3. If downstream is truly down: nothing to do, circuit breaker is protecting the system
4. If circuit breaker opened due to a bug: rollback the deployment

## Common Failure Scenarios

### Inventory Service Unresponsive
- **Automatic:** Circuit breaker trips after 5 failures, returns degraded/cached response
- **Manual:** Check inventory-service logs, restart if needed
- **Recovery:** Circuit enters HALF_OPEN after recovery timeout, probes inventory-service

### Database Connection Exhaustion
- **Symptoms:** Slow queries, connection timeout errors
- **Check:** `HikariCP` metrics in `/actuator/metrics/hikaricp.connections.active`
- **Mitigate:** Restart affected backend replicas, investigate query patterns

### Redis Unavailable
- **Automatic:** All Redis operations fail-open (circuit breaker assumes CLOSED)
- **Impact:** Temporary loss of distributed state (circuit breaker, fallback cache, recovery locks)
- **Recovery:** Redis restart, AOF replay restores state

### SLO Budget Burn
- **Automatic:** `PrometheusSLOChecker` clears caches and restarts connection pools
- **If persists:** Investigate root cause (usually a downstream dependency or database issue)
