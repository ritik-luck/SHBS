package com.selfhealing.backend.model;

public enum HealingStrategy {
    DO_NOTHING,
    APPLY_CIRCUIT_BREAKER,
    SERVE_DEGRADED,
    SERVE_FALLBACK,
    TRIGGER_RECOVERY,
    ALERT_ADMIN
}