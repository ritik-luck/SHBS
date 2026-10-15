package com.selfhealing.backend.service;

import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * InventoryService — Simulated downstream dependency.
 *
 * Represents a real external service (like a warehouse API or payment gateway)
 * that SHBS monitors and protects. The failure rate is controllable via Redis,
 * allowing chaos injection from the admin API without restarting the server.
 */
@Service
public class InventoryService {

    private static final Logger log = LoggerFactory.getLogger(InventoryService.class);

    private static final String REDIS_KEY_FAIL_RATE = "chaos:inventory:fail_rate";
    private static final String REDIS_KEY_LATENCY_MS = "chaos:inventory:latency_ms";

    private final StringRedisTemplate redisTemplate;
    private final Counter inventoryChecks;
    private final Counter inventoryFailures;
    private final Timer inventoryLatency;
    private final RestTemplate restTemplate;

    private static final String INVENTORY_SERVICE_URL = "http://inventory-service:8081";

    public InventoryService(StringRedisTemplate redisTemplate, MeterRegistry meterRegistry) {
        this.redisTemplate = redisTemplate;
        this.inventoryChecks = Counter.builder("inventory_checks_total")
                .description("Total inventory check calls")
                .register(meterRegistry);
        this.inventoryFailures = Counter.builder("inventory_failures_total")
                .description("Failed inventory check calls")
                .register(meterRegistry);
        this.inventoryLatency = Timer.builder("inventory_check_duration")
                .description("Inventory check latency")
                .register(meterRegistry);

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2000);
        factory.setReadTimeout(5000); // Wait up to 5s (enough for chaos latency)
        this.restTemplate = new RestTemplate(factory);
    }

    /**
     * Check if inventory is available for the given product and quantity.
     * Simulates real downstream behavior: latency, timeouts, and failures.
     *
     * @throws InventoryUnavailableException when the downstream "fails"
     */
    @Bulkhead(name = "inventoryBulkhead", fallbackMethod = "bulkheadFallback")
    public InventoryResult checkAvailability(String product, int quantity) {
        inventoryChecks.increment();

        return inventoryLatency.record(() -> {
            try {
                String url = INVENTORY_SERVICE_URL + "/inventory/check?product=" + product + "&quantity=" + quantity;
                ResponseEntity<InventoryResult> response = restTemplate.getForEntity(url, InventoryResult.class);
                
                log.debug("Inventory check SUCCESS for product={}, qty={}", product, quantity);
                return response.getBody();
            } catch (RestClientResponseException e) {
                inventoryFailures.increment();
                String failureType = "HTTP_" + e.getStatusCode().value();
                log.warn("Inventory check FAILED for product={}, qty={}, type={}", product, quantity, failureType);
                throw new InventoryUnavailableException(
                        "Downstream inventory service failure: " + e.getStatusText(),
                        failureType
                );
            } catch (Exception e) {
                inventoryFailures.increment();
                String failureType = "CONNECTION_ERROR";
                log.warn("Inventory check FAILED for product={}, qty={}, type={}", product, quantity, failureType);
                throw new InventoryUnavailableException(
                        "Downstream inventory network error: " + e.getMessage(),
                        failureType
                );
            }
        });
    }

    /**
     * Bulkhead fallback — called when too many concurrent inventory calls are in-flight.
     */
    public InventoryResult bulkheadFallback(String product, int quantity, Throwable t) {
        inventoryFailures.increment();
        log.warn("Bulkhead full for inventory check: product={}, qty={}", product, quantity);
        throw new InventoryUnavailableException(
                "Inventory service at capacity — bulkhead limit reached",
                "BULKHEAD_FULL");
    }

    /**
     * Health check for the inventory "service" — used by dependency health indicator.
     */
    public boolean isHealthy() {
        try {
            ResponseEntity<String> response = restTemplate.getForEntity(INVENTORY_SERVICE_URL + "/actuator/health", String.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            log.warn("Inventory service health check failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Set the chaos failure rate (0.0 = no failures, 1.0 = 100% failure).
     */
    public void setFailRate(double rate) {
        redisTemplate.opsForValue().set(REDIS_KEY_FAIL_RATE, String.valueOf(Math.max(0, Math.min(1, rate))));
        log.info("Inventory chaos fail rate set to: {}%", rate * 100);
    }

    /**
     * Set the chaos latency injection in milliseconds.
     */
    public void setLatencyMs(long latencyMs) {
        redisTemplate.opsForValue().set(REDIS_KEY_LATENCY_MS, String.valueOf(Math.max(0, latencyMs)));
        log.info("Inventory chaos latency set to: {}ms", latencyMs);
    }

    public Map<String, Object> getChaosConfig() {
        return Map.of(
                "failRate", getConfiguredFailRate(),
                "latencyMs", getConfiguredLatency()
        );
    }

    private double getConfiguredFailRate() {
        String val = redisTemplate.opsForValue().get(REDIS_KEY_FAIL_RATE);
        return val == null ? 0.0 : Double.parseDouble(val);
    }

    private long getConfiguredLatency() {
        String val = redisTemplate.opsForValue().get(REDIS_KEY_LATENCY_MS);
        return val == null ? 0L : Long.parseLong(val);
    }

    // ── Inner classes ──

    public record InventoryResult(boolean available, String product, int quantity, double totalPrice) {}

    public static class InventoryUnavailableException extends RuntimeException {
        private final String failureType;

        public InventoryUnavailableException(String message, String failureType) {
            super(message);
            this.failureType = failureType;
        }

        public String getFailureType() { return failureType; }
    }
}
