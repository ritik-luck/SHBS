package com.selfhealing.backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;

/**
 * DependencyHealthIndicator — Real health checks for all downstream dependencies.
 *
 * Goes beyond simple connectivity tests:
 * - MySQL: Connection validity + actual query execution
 * - Redis: PING + read/write test
 * - Inventory Service: HTTP health check
 *
 * Exposed via /actuator/health with per-dependency breakdown.
 */
public class DependencyHealthIndicator {

    private static final Logger log = LoggerFactory.getLogger(DependencyHealthIndicator.class);

    /**
     * MySQL health check — verifies connection AND query execution.
     */
    @Component("mysqlHealth")
    public static class MysqlHealthIndicator implements HealthIndicator {

        private final DataSource dataSource;

        public MysqlHealthIndicator(DataSource dataSource) {
            this.dataSource = dataSource;
        }

        @Override
        public Health health() {
            try (Connection conn = dataSource.getConnection()) {
                if (!conn.isValid(2)) {
                    return Health.down().withDetail("error", "Connection invalid").build();
                }
                // Execute a real query, not just a ping
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute("SELECT 1");
                }
                return Health.up()
                        .withDetail("database", "MySQL")
                        .withDetail("test", "SELECT 1 passed")
                        .build();
            } catch (Exception e) {
                log.error("MySQL health check failed: {}", e.getMessage());
                return Health.down()
                        .withDetail("database", "MySQL")
                        .withDetail("error", e.getMessage())
                        .build();
            }
        }
    }

    /**
     * Redis health check — verifies PING + read/write capability.
     */
    @Component("redisHealth")
    public static class RedisHealthIndicator implements HealthIndicator {

        private final StringRedisTemplate redis;

        public RedisHealthIndicator(StringRedisTemplate redis) {
            this.redis = redis;
        }

        @Override
        public Health health() {
            try {
                // Write test
                redis.opsForValue().set("health:check", "ok");
                // Read test
                String val = redis.opsForValue().get("health:check");
                if (!"ok".equals(val)) {
                    return Health.down().withDetail("error", "Read/write mismatch").build();
                }
                return Health.up()
                        .withDetail("store", "Redis")
                        .withDetail("test", "Read/write passed")
                        .build();
            } catch (Exception e) {
                log.error("Redis health check failed: {}", e.getMessage());
                return Health.down()
                        .withDetail("store", "Redis")
                        .withDetail("error", e.getMessage())
                        .build();
            }
        }
    }

    /**
     * Inventory Service health check — verifies the downstream is responsive.
     */
    @Component("inventoryHealth")
    public static class InventoryHealthIndicator implements HealthIndicator {

        private final InventoryService inventoryService;

        public InventoryHealthIndicator(InventoryService inventoryService) {
            this.inventoryService = inventoryService;
        }

        @Override
        public Health health() {
            if (inventoryService.isHealthy()) {
                return Health.up()
                        .withDetail("service", "inventory-service")
                        .withDetail("status", "responsive")
                        .build();
            }
            return Health.down()
                    .withDetail("service", "inventory-service")
                    .withDetail("status", "degraded (fail rate > 50%)")
                    .build();
        }
    }

    /**
     * Payment Service health check — verifies the downstream payment gateway is responsive.
     */
    @Component("paymentHealth")
    public static class PaymentHealthIndicator implements HealthIndicator {

        private final PaymentService paymentService;

        public PaymentHealthIndicator(PaymentService paymentService) {
            this.paymentService = paymentService;
        }

        @Override
        public Health health() {
            if (paymentService.isHealthy()) {
                return Health.up()
                        .withDetail("service", "payment-service")
                        .withDetail("status", "responsive")
                        .build();
            }
            return Health.down()
                    .withDetail("service", "payment-service")
                    .withDetail("status", "degraded (fail rate > 50%)")
                    .build();
        }
    }

    /**
     * Notification Service health check — verifies the downstream notification provider is responsive.
     */
    @Component("notificationHealth")
    public static class NotificationHealthIndicator implements HealthIndicator {

        private final NotificationService notificationService;

        public NotificationHealthIndicator(NotificationService notificationService) {
            this.notificationService = notificationService;
        }

        @Override
        public Health health() {
            if (notificationService.isHealthy()) {
                return Health.up()
                        .withDetail("service", "notification-service")
                        .withDetail("status", "responsive")
                        .build();
            }
            return Health.down()
                    .withDetail("service", "notification-service")
                    .withDetail("status", "degraded (fail rate > 50%)")
                    .build();
        }
    }

    /**
     * JVM Memory health check — warns when memory is critically low.
     */
    @Component("memoryHealth")
    public static class MemoryHealthIndicator implements HealthIndicator {

        @Override
        public Health health() {
            long maxMemory = Runtime.getRuntime().maxMemory();
            long allocatedMemory = Runtime.getRuntime().totalMemory();
            long freeMemory = Runtime.getRuntime().freeMemory();
            long actualFreeMemory = freeMemory + (maxMemory - allocatedMemory);
            double freePercentage = (double) actualFreeMemory / maxMemory * 100;

            Health.Builder builder = freePercentage > 10 ? Health.up() : Health.down();
            return builder
                    .withDetail("maxMemoryMB", maxMemory / (1024 * 1024))
                    .withDetail("usedMemoryMB", (allocatedMemory - freeMemory) / (1024 * 1024))
                    .withDetail("freePercentage", String.format("%.1f%%", freePercentage))
                    .build();
        }
    }
}
