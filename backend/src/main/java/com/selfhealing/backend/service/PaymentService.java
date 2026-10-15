package com.selfhealing.backend.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * PaymentService — Simulated downstream payment gateway.
 *
 * Represents a real payment processor (like Stripe or Razorpay)
 * that SHBS monitors and protects with an independent circuit breaker.
 * Failure rate is controllable via Redis for chaos injection.
 */
@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private static final String REDIS_KEY_FAIL_RATE = "chaos:payment:fail_rate";
    private static final String REDIS_KEY_LATENCY_MS = "chaos:payment:latency_ms";

    private final StringRedisTemplate redisTemplate;
    private final Counter paymentAttempts;
    private final Counter paymentFailures;
    private final Timer paymentLatency;

    public PaymentService(StringRedisTemplate redisTemplate, MeterRegistry meterRegistry) {
        this.redisTemplate = redisTemplate;
        this.paymentAttempts = Counter.builder("payment_attempts_total")
                .description("Total payment processing attempts")
                .register(meterRegistry);
        this.paymentFailures = Counter.builder("payment_failures_total")
                .description("Failed payment processing attempts")
                .register(meterRegistry);
        this.paymentLatency = Timer.builder("payment_duration")
                .description("Payment processing latency")
                .register(meterRegistry);
    }

    /**
     * Process a payment for the given order.
     *
     * @param orderId the order identifier
     * @param amount  the payment amount
     * @return PaymentResult with transaction details
     * @throws PaymentFailedException when the downstream payment gateway fails
     */
    public PaymentResult processPayment(Long orderId, double amount) {
        paymentAttempts.increment();

        return paymentLatency.record(() -> {
            // Simulate chaos latency
            long latency = getConfiguredLatency();
            if (latency > 0) {
                try {
                    Thread.sleep(latency);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }

            // Simulate chaos failures
            double failRate = getConfiguredFailRate();
            if (failRate > 0 && ThreadLocalRandom.current().nextDouble() < failRate) {
                paymentFailures.increment();
                log.warn("Payment FAILED (chaos) for orderId={}, amount={}", orderId, amount);
                throw new PaymentFailedException(
                        "Payment gateway timeout — downstream service unresponsive",
                        "GATEWAY_TIMEOUT");
            }

            // Simulate successful payment
            String transactionId = "TXN-" + System.currentTimeMillis() + "-" + orderId;
            log.info("Payment SUCCESS for orderId={}, amount={}, txn={}", orderId, amount, transactionId);
            return new PaymentResult(transactionId, orderId, amount, "COMPLETED");
        });
    }

    /**
     * Health check for the payment gateway.
     */
    public boolean isHealthy() {
        double failRate = getConfiguredFailRate();
        return failRate < 0.5; // Consider unhealthy if fail rate >= 50%
    }

    // ── Chaos Configuration ──

    public void setFailRate(double rate) {
        redisTemplate.opsForValue().set(REDIS_KEY_FAIL_RATE, String.valueOf(Math.max(0, Math.min(1, rate))));
        log.info("Payment chaos fail rate set to: {}%", rate * 100);
    }

    public void setLatencyMs(long latencyMs) {
        redisTemplate.opsForValue().set(REDIS_KEY_LATENCY_MS, String.valueOf(Math.max(0, latencyMs)));
        log.info("Payment chaos latency set to: {}ms", latencyMs);
    }

    public Map<String, Object> getChaosConfig() {
        return Map.of(
                "service", "payment-service",
                "failRate", getConfiguredFailRate(),
                "latencyMs", getConfiguredLatency()
        );
    }

    private double getConfiguredFailRate() {
        try {
            String val = redisTemplate.opsForValue().get(REDIS_KEY_FAIL_RATE);
            return val == null ? 0.0 : Double.parseDouble(val);
        } catch (Exception e) {
            return 0.0;
        }
    }

    private long getConfiguredLatency() {
        try {
            String val = redisTemplate.opsForValue().get(REDIS_KEY_LATENCY_MS);
            return val == null ? 0L : Long.parseLong(val);
        } catch (Exception e) {
            return 0L;
        }
    }

    // ── Inner classes ──

    public record PaymentResult(String transactionId, Long orderId, double amount, String status) {}

    public static class PaymentFailedException extends RuntimeException {
        private final String failureType;

        public PaymentFailedException(String message, String failureType) {
            super(message);
            this.failureType = failureType;
        }

        public String getFailureType() { return failureType; }
    }
}
