package com.selfhealing.backend.service;

import com.selfhealing.backend.model.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class HealingStrategyEngine {

    @Autowired
    private SystemHealthService healthService;

    @Autowired
    private CircuitBreakerService circuitBreakerService;

    @Autowired
    private AuditService auditService;

    public HealingStrategy decideStrategy(long failureCount) {

        int healthScore = healthService.calculateHealthScore();
        CircuitBreakerState state = circuitBreakerService.getState();

        HealingStrategy strategy;

        if (healthScore >= 80) {
            strategy = HealingStrategy.DO_NOTHING;

        } else if (healthScore >= 50) {
            strategy = HealingStrategy.SERVE_DEGRADED;

        } else if (healthScore >= 30) {
            strategy = HealingStrategy.SERVE_FALLBACK;

        } else if (failureCount >= 5 && state == CircuitBreakerState.CLOSED) {
            strategy = HealingStrategy.APPLY_CIRCUIT_BREAKER;

        } else if (state == CircuitBreakerState.OPEN) {
            strategy = HealingStrategy.TRIGGER_RECOVERY;

        } else {
            strategy = HealingStrategy.ALERT_ADMIN;
        }

        auditService.log(
                "HEALING_DECISION",
                "Strategy: " + strategy + ", Health: " + healthScore,
                "SYSTEM",
                "SUCCESS"
        );

        return strategy;
    }
}