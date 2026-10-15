-- Create ShedLock table
CREATE TABLE IF NOT EXISTS shedlock (
  name VARCHAR(64) NOT NULL,
  lock_until TIMESTAMP(3) NOT NULL,
  locked_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  locked_by VARCHAR(255) NOT NULL,
  PRIMARY KEY (name)
);

-- Add timestamp indexes to tables that are pruned by DataArchivalScheduler
-- This prevents full table scans and long locks during the DELETE ... LIMIT batch process
CREATE INDEX idx_audit_log_timestamp ON audit_log(timestamp);
CREATE INDEX idx_failure_metric_timestamp ON failure_metric(timestamp);
CREATE INDEX idx_recovery_metric_timestamp ON recovery_metric(timestamp);
CREATE INDEX idx_alert_timestamp ON alert(timestamp);
CREATE INDEX idx_health_metric_timestamp ON health_metric(timestamp);
