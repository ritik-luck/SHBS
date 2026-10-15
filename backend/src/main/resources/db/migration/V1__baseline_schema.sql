CREATE TABLE IF NOT EXISTS alert (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    level VARCHAR(255),
    message VARCHAR(255),
    source VARCHAR(255),
    timestamp DATETIME(6)
);

CREATE TABLE IF NOT EXISTS audit_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    action_type VARCHAR(255),
    details VARCHAR(255),
    result VARCHAR(255),
    timestamp DATETIME(6),
    triggered_by VARCHAR(255)
);

CREATE TABLE IF NOT EXISTS failure_metric (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    category VARCHAR(255),
    endpoint VARCHAR(255),
    failure_count INT NOT NULL,
    failure_type VARCHAR(255),
    resolved BIT(1) NOT NULL,
    service_name VARCHAR(255),
    severity VARCHAR(255),
    stack_trace VARCHAR(255),
    timestamp DATETIME(6)
);

CREATE TABLE IF NOT EXISTS health_metric (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    score INT NOT NULL,
    status TINYINT,
    timestamp DATETIME(6)
);

CREATE TABLE IF NOT EXISTS recovery_metric (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    details VARCHAR(255),
    duration_ms BIGINT NOT NULL,
    result VARCHAR(255),
    stage VARCHAR(255),
    timestamp DATETIME(6)
);

CREATE TABLE IF NOT EXISTS system_config (
    config_key VARCHAR(255) PRIMARY KEY,
    config_value VARCHAR(255),
    description VARCHAR(255),
    updated_at DATETIME(6)
);
