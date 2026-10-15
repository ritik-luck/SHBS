package com.selfhealing.backend.service;

import com.selfhealing.backend.model.CircuitBreakerState;
import com.selfhealing.backend.model.RecoveryMetric;
import com.selfhealing.backend.repository.RecoveryMetricRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

/**
 * RecoveryService v2 — Non-blocking, distributed, jittered recovery.
 *
 * Key improvements:
 * 1. No Thread.sleep() — backoff is checked via Redis timestamp, not blocking.
 * 2. Recovery state (retryAttempts, successCount, backoffTime) stored in Redis for replica agreement.
 * 3. Distributed lock ensures only one replica runs recovery at a time.
 * 4. Jitter added to backoff to prevent thundering herd.
 * 5. Canary now hits a downstream dependency check, not just local /actuator/health.
 * 6. Explicit connect/read timeouts on all outbound calls.
 */
@Service
public class RecoveryService {

    private static final Logger log = LoggerFactory.getLogger(RecoveryService.class);

    private static final long INITIAL_BACKOFF_MS = 2000;
    private static final long MAX_BACKOFF_MS = 30000;
    private static final Duration LOCK_TTL = Duration.ofSeconds(30);

    // Lua script: Delete key only if the value matches the given token
    private static final String RELEASE_LOCK_SCRIPT =
            "if redis.call('GET', KEYS[1]) == ARGV[1] then " +
            "return redis.call('DEL', KEYS[1]) " +
            "else return 0 end";

    private final CircuitBreakerService circuitBreakerService;
    private final ConfigService configService;
    private final AuditService auditService;
    private final RecoveryMetricRepository recoveryRepo;
    private final AlertService alertService;
    private final DataSource dataSource;
    private final SelfHealingActionService healingActionService;
    private final InventoryService inventoryService;
    private final StringRedisTemplate redis;
    private final RestTemplate restTemplate;

    public RecoveryService(CircuitBreakerService circuitBreakerService,
                           ConfigService configService,
                           AuditService auditService,
                           RecoveryMetricRepository recoveryRepo,
                           AlertService alertService,
                           DataSource dataSource,
                           SelfHealingActionService healingActionService,
                           InventoryService inventoryService,
                           StringRedisTemplate redis) {
        this.circuitBreakerService = circuitBreakerService;
        this.configService = configService;
        this.auditService = auditService;
        this.recoveryRepo = recoveryRepo;
        this.alertService = alertService;
        this.dataSource = dataSource;
        this.healingActionService = healingActionService;
        this.inventoryService = inventoryService;
        this.redis = redis;

        // RestTemplate with explicit timeouts
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(5000);
        this.restTemplate = new RestTemplate(factory);
    }

    /**
     * Non-blocking recovery attempt for a specific service.
     * Called by RecoveryScheduler on a fixed interval.
     */
    public void attemptRecovery(String serviceName) {
        // Only attempt if circuit is OPEN
        if (circuitBreakerService.getState(serviceName) != CircuitBreakerState.OPEN) {
            return;
        }

        // Service-specific keys
        String lockKey = "recovery:" + serviceName + ":lock";
        String attemptsKey = "recovery:" + serviceName + ":attempts";
        String successesKey = "recovery:" + serviceName + ":successes";
        String backoffKey = "recovery:" + serviceName + ":backoff_ms";
        String lastAttemptKey = "recovery:" + serviceName + ":last_attempt_ms";

        // Check non-blocking backoff: has enough time elapsed since last attempt?
        if (!isBackoffElapsed(lastAttemptKey, backoffKey)) {
            return;
        }

        // Distributed lock: token-safe
        String lockToken = java.util.UUID.randomUUID().toString();
        Boolean lockAcquired = false;
        try {
            lockAcquired = redis.opsForValue().setIfAbsent(lockKey, lockToken, LOCK_TTL);
        } catch (Exception e) {
            log.warn("Redis unavailable for recovery lock on {}, skipping: {}", serviceName, e.getMessage());
            return;
        }

        if (Boolean.FALSE.equals(lockAcquired)) {
            log.debug("Another replica is running recovery for {} — skipping", serviceName);
            return;
        }

        long start = System.currentTimeMillis();
        int retryAttempts = getRedisInt(attemptsKey, 0);

        try {
            log.info("Recovery attempt started for {} (attempt #{})", serviceName, retryAttempts);
            auditService.log("RECOVERY_ATTEMPT",
                    "Trying recovery attempt #" + retryAttempts + " for " + serviceName,
                    "SYSTEM", "STARTED");

            // Record last attempt time
            setRedisValue(lastAttemptKey, String.valueOf(System.currentTimeMillis()));

            // Step 1: Health check
            boolean healthy = performHealthCheck();
            recoveryRepo.save(new RecoveryMetric("HEALTH_CHECK",
                    healthy ? "SUCCESS" : "FAILURE",
                    System.currentTimeMillis() - start,
                    "Health check stage"));

            if (!healthy) {
                incrementRedis(attemptsKey);
                auditService.log("RECOVERY_FAILED", "Health check failed for " + serviceName + ", applying remediation", "SYSTEM", "FAILURE");
                alertService.sendAlert("WARNING", "Recovery health check failed for " + serviceName + ", executing remediation strategies", "RECOVERY");
                executeRemediation();
                increaseBackoff(backoffKey);
                return;
            }

            // Step 2: Canary test (downstream dependency, not just local health)
            boolean canarySuccess = performCanaryTest();
            recoveryRepo.save(new RecoveryMetric("CANARY_TEST",
                    canarySuccess ? "SUCCESS" : "FAILURE",
                    System.currentTimeMillis() - start,
                    "Downstream canary"));

            if (!canarySuccess) {
                incrementRedis(attemptsKey);
                auditService.log("RECOVERY_FAILED", "Canary test failed for " + serviceName, "SYSTEM", "FAILURE");
                increaseBackoff(backoffKey);
                return;
            }

            // Step 3: Transition to HALF_OPEN
            circuitBreakerService.tryRecovery(serviceName);

            int successCount = (int) incrementRedis(successesKey);

            int maxSuccessThreshold = configService.getInt("recovery_success_threshold", 3);
            if (successCount >= maxSuccessThreshold) {
                circuitBreakerService.resetCircuit(serviceName);

                recoveryRepo.save(new RecoveryMetric("FULL_RECOVERY",
                        "SUCCESS",
                        System.currentTimeMillis() - start,
                        "System restored: " + serviceName));

                auditService.log("RECOVERY_SUCCESS",
                        "Service " + serviceName + " fully recovered after " + retryAttempts + " attempts",
                        "SYSTEM", "SUCCESS");

                log.info("Service {} fully recovered — circuit breaker reset", serviceName);
                resetRecoveryState(attemptsKey, successesKey, backoffKey, lastAttemptKey);
            }

        } finally {
            // Token-safe release lock
            try {
                org.springframework.data.redis.core.script.DefaultRedisScript<Long> redisScript =
                        new org.springframework.data.redis.core.script.DefaultRedisScript<>(RELEASE_LOCK_SCRIPT, Long.class);
                Long result = redis.execute(redisScript, java.util.Collections.singletonList(lockKey), lockToken);
                if (result == null || result == 0) {
                    log.warn("Recovery lock fencing warning: failed to release lock for {}, token may have expired and been stolen", serviceName);
                }
            } catch (Exception e) {
                log.warn("Failed to release recovery lock for {}: {}", serviceName, e.getMessage());
            }
        }
    }

    /**
     * Real health check: DB connectivity + memory pressure.
     */
    private boolean performHealthCheck() {
        log.debug("Performing health check");
        try {
            // DB connectivity
            try (Connection conn = dataSource.getConnection()) {
                if (!conn.isValid(2)) {
                    log.error("Health Check Failed: Database connection invalid");
                    return false;
                }
            }

            // Memory check (require at least 5% free)
            long maxMemory = Runtime.getRuntime().maxMemory();
            long allocatedMemory = Runtime.getRuntime().totalMemory();
            long freeMemory = Runtime.getRuntime().freeMemory();
            long actualFreeMemory = freeMemory + (maxMemory - allocatedMemory);
            double freePercentage = (double) actualFreeMemory / maxMemory;

            if (freePercentage < 0.05) {
                log.error("Health Check Failed: Memory critically low ({} %)",
                        String.format("%.2f", freePercentage * 100));
                return false;
            }

            return true;
        } catch (Exception e) {
            log.error("Health check exception: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Canary test: exercises a safe downstream dependency check rather than
     * just the app's local /actuator/health.
     */
    private boolean performCanaryTest() {
        log.debug("Performing downstream canary test");
        try {
            // Check if the inventory service is reporting healthy
            return inventoryService.isHealthy();
        } catch (Exception e) {
            log.warn("Canary test failed: {}", e.getMessage());
            return false;
        }
    }

    private void executeRemediation() {
        log.info("Executing automatic remediation steps based on failure context...");
        healingActionService.restartConnectionPool();
        healingActionService.clearCaches();
    }

    // ── Non-blocking backoff with jitter ──

    private boolean isBackoffElapsed(String lastAttemptKey, String backoffKey) {
        try {
            String lastAttemptStr = redis.opsForValue().get(lastAttemptKey);
            if (lastAttemptStr == null) return true;

            long lastAttempt = Long.parseLong(lastAttemptStr);
            long backoffMs = getRedisLong(backoffKey, INITIAL_BACKOFF_MS);

            // Add jitter: ±25% of backoff
            long jitter = (long) (backoffMs * 0.25 * (ThreadLocalRandom.current().nextDouble() * 2 - 1));
            long effectiveBackoff = backoffMs + jitter;

            return System.currentTimeMillis() - lastAttempt >= effectiveBackoff;
        } catch (Exception e) {
            log.warn("Redis unavailable for backoff check, proceeding: {}", e.getMessage());
            return true;
        }
    }

    private void increaseBackoff(String backoffKey) {
        try {
            long currentBackoff = getRedisLong(backoffKey, INITIAL_BACKOFF_MS);
            long newBackoff = Math.min(currentBackoff * 2, MAX_BACKOFF_MS);
            redis.opsForValue().set(backoffKey, String.valueOf(newBackoff));
            log.info("Recovery backoff increased to {} ms", newBackoff);
        } catch (Exception e) {
            log.warn("Failed to update backoff in Redis: {}", e.getMessage());
        }
    }

    private void resetRecoveryState(String attemptsKey, String successesKey, String backoffKey, String lastAttemptKey) {
        try {
            redis.delete(attemptsKey);
            redis.delete(successesKey);
            redis.delete(backoffKey);
            redis.delete(lastAttemptKey);
        } catch (Exception e) {
            log.warn("Failed to reset recovery state in Redis: {}", e.getMessage());
        }
    }

    // ── Redis helpers ──

    private int getRedisInt(String key, int defaultVal) {
        try {
            String val = redis.opsForValue().get(key);
            return val != null ? Integer.parseInt(val) : defaultVal;
        } catch (Exception e) {
            return defaultVal;
        }
    }

    private long getRedisLong(String key, long defaultVal) {
        try {
            String val = redis.opsForValue().get(key);
            return val != null ? Long.parseLong(val) : defaultVal;
        } catch (Exception e) {
            return defaultVal;
        }
    }

    private void setRedisValue(String key, String value) {
        try {
            redis.opsForValue().set(key, value);
        } catch (Exception e) {
            log.warn("Redis unavailable for set '{}': {}", key, e.getMessage());
        }
    }

    private long incrementRedis(String key) {
        try {
            Long result = redis.opsForValue().increment(key);
            return result != null ? result : 0;
        } catch (Exception e) {
            log.warn("Redis unavailable for increment '{}': {}", key, e.getMessage());
            return 0;
        }
    }
}