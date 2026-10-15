# Disaster Recovery Runbook

## Recovery Time Objectives
| Component | RTO | RPO | Strategy |
|-----------|-----|-----|----------|
| Application (ECS) | 5 min | 0 (stateless) | Redeploy from ECR image |
| Database (RDS) | 30 min | 5 min | Automated backup + PITR |
| Redis (ElastiCache) | 10 min | 15 min | AOF persistence + snapshots |
| Monitoring (Prometheus/Grafana) | 15 min | 1 hour | Rebuild from config (IaC) |

## Scenario 1: Application Failure
```bash
# Redeploy from last known-good image
gh workflow run deploy.yml -f environment=production -f image_tag=<LAST_GOOD_TAG>
```

## Scenario 2: Database Corruption / Loss

### RDS Point-in-Time Recovery
```bash
# 1. Identify target restore time
# 2. Restore to new instance
aws rds restore-db-instance-to-point-in-time \
  --source-db-instance-identifier shbs-prod-db \
  --target-db-instance-identifier shbs-prod-db-restored \
  --restore-time "2026-09-06T10:00:00Z" \
  --db-instance-class db.t3.small

# 3. Verify data integrity on restored instance
# 4. Update application DB_URL to point to restored instance
# 5. Rename instances when verified
aws rds modify-db-instance \
  --db-instance-identifier shbs-prod-db \
  --new-db-instance-identifier shbs-prod-db-old

aws rds modify-db-instance \
  --db-instance-identifier shbs-prod-db-restored \
  --new-db-instance-identifier shbs-prod-db
```

### From Automated Backup
```bash
aws rds restore-db-instance-from-db-snapshot \
  --db-instance-identifier shbs-prod-db-restored \
  --db-snapshot-identifier <snapshot-id>
```

## Scenario 3: Redis Failure
ElastiCache with AOF persistence will auto-recover. If manual intervention needed:
```bash
# Create new Redis cluster from latest snapshot
aws elasticache create-replication-group \
  --replication-group-id shbs-prod-redis-restored \
  --snapshot-name <snapshot-name>
```

## Scenario 4: Complete Environment Rebuild
```bash
# 1. Ensure Terraform state is accessible (S3 + DynamoDB lock)
cd infrastructure/terraform

# 2. Re-apply infrastructure
terraform init
terraform plan -var-file=environments/prod.tfvars
terraform apply -var-file=environments/prod.tfvars

# 3. Restore database from latest backup
# 4. Deploy application from latest known-good ECR images
# 5. Verify all health checks pass
```

## Backup Verification Schedule
| Item | Frequency | Owner |
|------|-----------|-------|
| RDS backup restore test | Monthly | DevOps |
| Redis snapshot restore test | Monthly | DevOps |
| Full environment rebuild test | Quarterly | Team |
| Terraform state verification | Weekly (automated) | CI |
