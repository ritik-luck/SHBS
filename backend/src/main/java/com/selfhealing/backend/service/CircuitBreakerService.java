package com.selfhealing.backend.service;

import com.selfhealing.backend.model.AuditLog;
import com.selfhealing.backend.model.CircuitBreakerState;
import com.selfhealing.backend.repository.FailureMetricRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class CircuitBreakerService {

    @Autowired
    AuditService auditService;
    @Autowired
    ConfigService configService;
    @Autowired
    FailureMetricRepository repository;
    private CircuitBreakerState state = CircuitBreakerState.CLOSED;
    private long lastFailureTime = 0L;
    private long RECOVERY_TIMEOUT = 10000; // default 10s, can be overridden from config


    public boolean allowRequest() {

        int threshold = configService.getInt("circuit_breaker_threshold", 5);
        long recoveryTimeout = configService.getInt("recovery_timeout_ms", 10000);

        if (repository.count() >= threshold) {
            state = CircuitBreakerState.OPEN;
        }

        if (state == CircuitBreakerState.OPEN) {
            long now = System.currentTimeMillis();

            if (now - lastFailureTime > recoveryTimeout) {
                state = CircuitBreakerState.HALF_OPEN;
                return true;
            }
            return false;
        }

        return true;
    }

    public void recordFailure(long failureCount) {
        int threshold = configService.getInt("circuit_breaker_threshold", 5);

        if (failureCount >= threshold) {
            state = CircuitBreakerState.OPEN;
            auditService.log(
                            "CIRCUIT_OPENED",
                            "Failure threshold exceeded: " + failureCount,
                            "SYSTEM",
                            "SUCCESS"
                    );
            lastFailureTime = System.currentTimeMillis();
        }
    }

    public void recordSuccess() {
        if (state == CircuitBreakerState.HALF_OPEN) {
            state = CircuitBreakerState.CLOSED;
            auditService.log(
                "CIRCUIT_CLOSED",
                "System stabilized, closing circuit",
                "SYSTEM",
                "SUCCESS"
            );
        }

    }

    public String fallbackResponse() {
        return "Circuit breaker OPEN - service temporarily unavailable";
    }
    public void tryRecovery(){
        System.out.println("Scheduler triggered");

        if (state == CircuitBreakerState.OPEN) {
            state = CircuitBreakerState.HALF_OPEN;

            auditService.log(
                "RECOVERY_TRIGGERED",
                "Scheduler moved circuit to HALF_OPEN",
                "SYSTEM",
                "SUCCESS"
            );

            System.out.println("Circuit breaker moved to half-open");
        }
    }

    public CircuitBreakerState getState() {
        return state;
    }

    public void resetCircuit(){
        state = CircuitBreakerState.CLOSED;
        auditService.log(
                "CIRCUIT_RESET",
                "System recovered and circuit closed",
                "SYSTEM",
                "SUCCESS"
        );
        System.out.println("Circuit breaker manually reset");

    }
}