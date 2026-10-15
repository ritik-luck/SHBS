package com.selfhealing.backend;

import com.selfhealing.backend.model.CircuitBreakerState;
import com.selfhealing.backend.service.CircuitBreakerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.*;

class RedisIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private CircuitBreakerService circuitBreakerService;

    private static final String TEST_SERVICE = "inventory-service-test";

    @BeforeEach
    void setUp() {
        // Reset state before each test
        circuitBreakerService.resetCircuit(TEST_SERVICE);
    }

    @Test
    void testInitialStateIsClosed() {
        assertEquals(CircuitBreakerState.CLOSED, circuitBreakerService.getState(TEST_SERVICE));
        assertTrue(circuitBreakerService.allowRequest(TEST_SERVICE));
    }

    @Test
    void testThresholdOpensCircuitAndFailsFast() {
        // Assume default threshold is 5
        for (int i = 0; i < 4; i++) {
            assertFalse(circuitBreakerService.recordFailure(TEST_SERVICE), "Circuit should not open before threshold");
        }
        
        // 5th failure should trigger transition
        assertTrue(circuitBreakerService.recordFailure(TEST_SERVICE), "Circuit should OPEN on threshold failure");
        assertEquals(CircuitBreakerState.OPEN, circuitBreakerService.getState(TEST_SERVICE));

        // Subsequent requests should fail fast
        assertFalse(circuitBreakerService.allowRequest(TEST_SERVICE), "Requests should be rejected while OPEN");
    }

    @Test
    void testRecoveryFlowToHalfOpenAndClosed() throws InterruptedException {
        // 1. Force OPEN
        for (int i = 0; i < 5; i++) {
            circuitBreakerService.recordFailure(TEST_SERVICE);
        }
        assertEquals(CircuitBreakerState.OPEN, circuitBreakerService.getState(TEST_SERVICE));

        // 2. Wait for recovery timeout (mocking time is hard with Redis, so we wait or manually transition)
        // Since DEFAULT_RECOVERY_MS is 10s, we will use tryRecovery() manually
        circuitBreakerService.tryRecovery(TEST_SERVICE);
        assertEquals(CircuitBreakerState.HALF_OPEN, circuitBreakerService.getState(TEST_SERVICE));
        
        // 3. Probes are allowed in HALF_OPEN
        assertTrue(circuitBreakerService.allowRequest(TEST_SERVICE));

        // 4. Successes should transition to CLOSED
        // Assume DEFAULT_PROBE_SUCCESS_REQUIRED is 3
        circuitBreakerService.recordSuccess(TEST_SERVICE);
        circuitBreakerService.recordSuccess(TEST_SERVICE);
        circuitBreakerService.recordSuccess(TEST_SERVICE);

        assertEquals(CircuitBreakerState.CLOSED, circuitBreakerService.getState(TEST_SERVICE));
    }
}
