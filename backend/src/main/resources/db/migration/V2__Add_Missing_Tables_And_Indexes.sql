-- ============================================================
-- V2: Add missing tables (orders, incidents, users) and indexes
-- for timestamp-filtered queries on metrics/audit tables.
-- ============================================================

-- Orders table
CREATE TABLE IF NOT EXISTS orders (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    product VARCHAR(255),
    quantity INT NOT NULL,
    total_price DOUBLE NOT NULL,
    status VARCHAR(50) NOT NULL,
    failure_reason VARCHAR(500),
    idempotency_key VARCHAR(64),
    payload_hash VARCHAR(64),
    created_at DATETIME(6),
    updated_at DATETIME(6),
    UNIQUE KEY uk_orders_idempotency (idempotency_key)
) ENGINE=InnoDB;

-- Incidents table
CREATE TABLE IF NOT EXISTS incident (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(255),
    state VARCHAR(50) NOT NULL,
    severity VARCHAR(50),
    affected_service VARCHAR(255),
    owner VARCHAR(255),
    suggested_runbook VARCHAR(500),
    timeline TEXT,
    resolution_notes TEXT,
    created_at DATETIME(6),
    acknowledged_at DATETIME(6),
    resolved_at DATETIME(6),
    updated_at DATETIME(6)
) ENGINE=InnoDB;

-- Users table (for JWT auth)
CREATE TABLE IF NOT EXISTS users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(100) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(50) NOT NULL,
    created_at DATETIME(6),
    UNIQUE KEY uk_users_username (username)
) ENGINE=InnoDB;

-- ============================================================
-- Indexes for timestamp-filtered queries
-- ============================================================

-- Failure metrics: filter/sort by timestamp, service_name
CREATE INDEX idx_failure_metric_timestamp ON failure_metric (timestamp);
CREATE INDEX idx_failure_metric_service ON failure_metric (service_name, timestamp);

-- Audit logs: filter/sort by timestamp, action_type
CREATE INDEX idx_audit_log_timestamp ON audit_log (timestamp);
CREATE INDEX idx_audit_log_action ON audit_log (action_type, timestamp);

-- Alerts: filter/sort by timestamp, level
CREATE INDEX idx_alert_timestamp ON alert (timestamp);
CREATE INDEX idx_alert_level ON alert (level, timestamp);

-- Recovery metrics: filter/sort by timestamp
CREATE INDEX idx_recovery_metric_timestamp ON recovery_metric (timestamp);

-- Health metrics: filter/sort by timestamp
CREATE INDEX idx_health_metric_timestamp ON health_metric (timestamp);

-- Orders: filter by status, created_at
CREATE INDEX idx_orders_status ON orders (status);
CREATE INDEX idx_orders_created_at ON orders (created_at);

-- Incidents: filter by state, service, created_at
CREATE INDEX idx_incident_state ON incident (state);
CREATE INDEX idx_incident_service ON incident (affected_service, state);
CREATE INDEX idx_incident_created ON incident (created_at);
