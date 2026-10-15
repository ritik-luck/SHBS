package com.selfhealing.backend;

import com.selfhealing.backend.model.CircuitBreakerState;
import com.selfhealing.backend.service.CircuitBreakerService;
import com.selfhealing.backend.service.InventoryService;
import com.selfhealing.backend.service.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Duration;
import java.util.UUID;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
class EndToEndSelfHealingTest extends BaseIntegrationTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private CircuitBreakerService circuitBreakerService;

    @Test
    void testFullSelfHealingLoop() throws Exception {
        String serviceName = "inventory-service";

        // 1. Initial State: Circuit is CLOSED
        assertEquals(CircuitBreakerState.CLOSED, circuitBreakerService.getState(serviceName));

        // 2. Inject Failure (100% fail rate for inventory)
        inventoryService.setFailRate(1.0);

        // 3. Trigger Failures to Open Circuit
        for (int i = 0; i < 5; i++) {
            orderService.createOrder("PROD-" + i, 1, UUID.randomUUID().toString());
        }

        // 4. Circuit should now be OPEN
        assertEquals(CircuitBreakerState.OPEN, circuitBreakerService.getState(serviceName));

        // 5. Remove Failure (simulate downstream recovered)
        inventoryService.setFailRate(0.0);

        // 6. Wait for RecoveryService to transition it to HALF_OPEN (runs every 5s)
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> 
            assertEquals(CircuitBreakerState.HALF_OPEN, circuitBreakerService.getState(serviceName))
        );

        // 7. Send successful requests to Close the circuit
        for (int i = 0; i < 3; i++) {
            orderService.createOrder("PROD-TEST-" + i, 1, UUID.randomUUID().toString());
        }

        // 8. Circuit should now be CLOSED again
        assertEquals(CircuitBreakerState.CLOSED, circuitBreakerService.getState(serviceName));
    }
}
