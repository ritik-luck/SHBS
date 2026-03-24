package com.selfhealing.backend.service;

import com.selfhealing.backend.model.CircuitBreakerState;
import org.springframework.stereotype.Service;

@Service
public class CircuitBreakerService {

    private CircuitBreakerState state = CircuitBreakerState.CLOSED;

    private static final int FAILURE_THRESHOLD = 5;

    private long lastFailureTime = 0;

    private static final long RECOVERY_TIMEOUT = 10000; // 10 seconds

    public boolean allowRequest() {
        if (state == CircuitBreakerState.OPEN) {
            long now = System.currentTimeMillis();
            if (now - lastFailureTime > RECOVERY_TIMEOUT) {
                state = CircuitBreakerState.HALF_OPEN;
                return true;
            }
            return false;
        }
        return true;
    }

    public void recordFailure(long failureCount) {

        if (failureCount > FAILURE_THRESHOLD) {
            state = CircuitBreakerState.OPEN;
            lastFailureTime = System.currentTimeMillis();
        }
    }

    public void recordSuccess() {
        if (state == CircuitBreakerState.HALF_OPEN) {
            state = CircuitBreakerState.CLOSED;
        }

    }

    public String fallbackResponse() {
        return "Circuit breaker OPEN - service temporarily unavailable";
    }
    public void tryRecovery(){
        System.out.println("Scheduler triggered");
        if(state == CircuitBreakerState.OPEN){
            state = CircuitBreakerState.HALF_OPEN;
            System.out.println("Circuit breaker moved to half-open");
        }
    }

    public CircuitBreakerState getState() {
        return state;
    }

    public void resetCircuit(){
        state = CircuitBreakerState.CLOSED;
        System.out.println("Circuit breaker manually reset");

    }
}