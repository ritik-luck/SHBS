package com.selfhealing.backend.service;

import com.selfhealing.backend.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * HealingStrategyEngine v2 — Decision router with remediation playbook integration.
 *
 * Maps (health score, circuit state, failure category) → HealingStrategy + RemediationPlaybook.
 * New strategies: DISABLE_FEATURE, REDUCE_TRAFFIC.
 */
@Service
public class HealingStrategyEngine {

    private static final Logger log = LoggerFactory.getLogger(HealingStrategyEngine.class);

    private final SystemHealthService healthService;
    private final CircuitBreakerService circuitBreakerService;
    private final AuditService auditService;
    private final SelfHealingActionService healingActionService;

    public HealingStrategyEngine(SystemHealthService healthService,
                                 CircuitBreakerService circuitBreakerService,
                                 AuditService auditService,
                                 SelfHealingActionService healingActionService) {
        this.healthService = healthService;
        this.circuitBreakerService = circuitBreakerService;
        this.auditService = auditService;
        this.healingActionService = healingActionService;
    }

    /**
     * Decide AND execute the appropriate healing strategy.
     */
    public HealingStrategy decideAndExecute(long failureCount) {
        HealingStrategy strategy = decideStrategy(failureCount);

        // Execute the corresponding remediation playbook
        switch (strategy) {
            case TRIGGER_RECOVERY -> {
                healingActionService.executePlaybook(RemediationPlaybook.EVICT_CONNECTIONS);
                healingActionService.executePlaybook(RemediationPlaybook.FLUSH_CACHE);
            }
            case DISABLE_FEATURE -> {
                healingActionService.executePlaybook(RemediationPlaybook.DISABLE_FEATURE);
            }
            case REDUCE_TRAFFIC -> {
                healingActionService.executePlaybook(RemediationPlaybook.REDUCE_TRAFFIC);
            }
            case APPLY_CIRCUIT_BREAKER, SERVE_DEGRADED, SERVE_FALLBACK, ALERT_ADMIN, DO_NOTHING -> {
                // These are handled by the calling code (controller/circuit breaker)
            }
        }

        return strategy;
    }

    public HealingStrategy decideStrategy(long failureCount) {

        int healthScore = healthService.calculateHealthScore();
        CircuitBreakerState state = circuitBreakerService.getState();

        HealingStrategy strategy;

        if (healthScore >= 80) {
            strategy = HealingStrategy.DO_NOTHING;

        } else if (healthScore >= 60) {
            // Mild degradation — reduce traffic to prevent further damage
            strategy = HealingStrategy.REDUCE_TRAFFIC;

        } else if (healthScore >= 50) {
            strategy = HealingStrategy.SERVE_DEGRADED;

        } else if (healthScore >= 30) {
            // Significant degradation — disable the failing feature
            strategy = HealingStrategy.DISABLE_FEATURE;

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