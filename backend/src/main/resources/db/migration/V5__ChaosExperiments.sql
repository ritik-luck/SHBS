-- V5: Chaos Experiments table for AI Red Team Agent
CREATE TABLE IF NOT EXISTS chaos_experiments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    experiment_type VARCHAR(50) NOT NULL,
    target_service VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'RUNNING',
    injected_at DATETIME NULL,
    detected_at DATETIME NULL,
    healed_at DATETIME NULL,
    circuit_opened_within_ms BIGINT NULL,
    circuit_closed_within_ms BIGINT NULL,
    total_duration_ms BIGINT NULL,
    healing_succeeded BOOLEAN NULL,
    resilience_score INT NULL,
    ai_analysis TEXT NULL,
    experiment_log TEXT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_chaos_status (status),
    INDEX idx_chaos_created (created_at)
);
