package com.selfhealing.backend.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * NotificationService — Simulated downstream notification/email service.
 *
 * Represents a real notification provider (like SendGrid, Twilio, or AWS SES)
 * that SHBS monitors and protects with an independent circuit breaker.
 *
 * Notifications are fire-and-forget — if the service is down, orders still succeed.
 * This demonstrates graceful degradation for non-critical services.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private static final String REDIS_KEY_FAIL_RATE = "chaos:notification:fail_rate";
    private static final String REDIS_KEY_LATENCY_MS = "chaos:notification:latency_ms";

    private final StringRedisTemplate redisTemplate;
    private final Counter notificationsSent;
    private final Counter notificationsFailed;

    public NotificationService(StringRedisTemplate redisTemplate, MeterRegistry meterRegistry) {
        this.redisTemplate = redisTemplate;
        this.notificationsSent = Counter.builder("notifications_sent_total")
                .description("Total notifications sent successfully")
                .register(meterRegistry);
        this.notificationsFailed = Counter.builder("notifications_failed_total")
                .description("Failed notification attempts")
                .register(meterRegistry);
    }

    /**
     * Send an order confirmation notification.
     *
     * @param orderId the order identifier
     * @param product the product name
     * @param customerEmail the customer's email address
     * @return NotificationResult with delivery status
     * @throws NotificationFailedException when the downstream notification service fails
     */
    public NotificationResult sendOrderConfirmation(Long orderId, String product, String customerEmail) {
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
            notificationsFailed.increment();
            log.warn("Notification FAILED (chaos) for orderId={}", orderId);
            throw new NotificationFailedException(
                    "Notification service unreachable — email delivery failed",
                    "SERVICE_UNAVAILABLE");
        }

        // Simulate successful notification
        String messageId = "MSG-" + System.currentTimeMillis() + "-" + orderId;
        notificationsSent.increment();
        log.info("Notification SENT for orderId={}, product={}, msgId={}", orderId, product, messageId);
        return new NotificationResult(messageId, orderId, "DELIVERED", customerEmail);
    }

    /**
     * Health check for the notification service.
     */
    public boolean isHealthy() {
        double failRate = getConfiguredFailRate();
        return failRate < 0.5;
    }

    // ── Chaos Configuration ──

    public void setFailRate(double rate) {
        redisTemplate.opsForValue().set(REDIS_KEY_FAIL_RATE, String.valueOf(Math.max(0, Math.min(1, rate))));
        log.info("Notification chaos fail rate set to: {}%", rate * 100);
    }

    public void setLatencyMs(long latencyMs) {
        redisTemplate.opsForValue().set(REDIS_KEY_LATENCY_MS, String.valueOf(Math.max(0, latencyMs)));
        log.info("Notification chaos latency set to: {}ms", latencyMs);
    }

    public Map<String, Object> getChaosConfig() {
        return Map.of(
                "service", "notification-service",
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

    public record NotificationResult(String messageId, Long orderId, String status, String recipient) {}

    public static class NotificationFailedException extends RuntimeException {
        private final String failureType;

        public NotificationFailedException(String message, String failureType) {
            super(message);
            this.failureType = failureType;
        }

        public String getFailureType() { return failureType; }
    }
}
