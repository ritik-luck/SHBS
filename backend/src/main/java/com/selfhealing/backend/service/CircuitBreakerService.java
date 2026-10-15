package com.selfhealing.backend.service;

import com.selfhealing.backend.model.CircuitBreakerState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import org.springframework.context.ApplicationEventPublisher;

/**
 * CircuitBreakerService v4 — Production-grade, atomic, and concurrency-safe.
 *
 * Improvements over v3:
 * 1. Unique UUID member IDs in the ZSET — prevents millisecond collisions under concurrency.
 * 2. Full atomic Lua script: add failure, prune, count, and conditionally OPEN the circuit in one round-trip.
 * 3. Explicit TTLs on all Redis keys (state, failures, probes, last_failure).
 * 4. HALF_OPEN probe concurrency limit — only N concurrent probes allowed via an atomic counter.
 * 5. Compare-and-set (CAS) for state transitions — prevents duplicate OPEN transitions from racing replicas.
 * 6. True fail-open: every Redis call is wrapped; RedisConnectionException never bubbles up.
 */
@Service
public class CircuitBreakerService {

    private static final Logger log = LoggerFactory.getLogger(CircuitBreakerService.class);

    private static final String KEY_PREFIX = "cb:";
    private static final String STATE_SUFFIX = ":state";
    private static final String FAILURES_SUFFIX = ":failures";
    private static final String LAST_FAILURE_SUFFIX = ":last_failure";
    private static final String PROBES_SUFFIX = ":probes";
    private static final String PROBE_ACTIVE_SUFFIX = ":probe_active"; // concurrent probe counter
    private static final String TRIP_COUNT_SUFFIX = ":trip_count"; // exponential backoff counter

    private static final String DEFAULT_SERVICE = "default";
    private static final int DEFAULT_THRESHOLD = 5;
    private static final long DEFAULT_WINDOW_MS = 60_000;
    private static final long DEFAULT_RECOVERY_MS = 10_000;
    private static final long MAX_BACKOFF_MS = 300_000; // 5 minutes cap
    private static final int DEFAULT_PROBE_SUCCESS_REQUIRED = 3;
    private static final int DEFAULT_MAX_HALF_OPEN_PROBES = 3;
    private static final Duration KEY_TTL = Duration.ofMinutes(10);

    private final AuditService auditService;
    private final ConfigService configService;
    private final AlertService alertService;
    private final StringRedisTemplate redis;
    private final MeterRegistry meterRegistry;
    private final DefaultRedisScript<Long> recordFailureScript;
    private final DefaultRedisScript<Long> casTransitionScript;
    private final Counter circuitOpenedCounter;
    private final Counter circuitClosedCounter;
    private final ApplicationEventPublisher eventPublisher;

    public CircuitBreakerService(AuditService auditService,
                                 ConfigService configService,
                                 AlertService alertService,
                                 StringRedisTemplate redisTemplate,
                                 MeterRegistry meterRegistry,
                                 ApplicationEventPublisher eventPublisher) {
        this.auditService = auditService;
        this.configService = configService;
        this.alertService = alertService;
        this.redis = redisTemplate;
        this.meterRegistry = meterRegistry;
        this.eventPublisher = eventPublisher;

        this.circuitOpenedCounter = Counter.builder("circuit_breaker_opened_total")
                .description("Total times circuit breaker has opened")
                .register(meterRegistry);
        this.circuitClosedCounter = Counter.builder("circuit_breaker_closed_total")
                .description("Total times circuit breaker has closed after recovery")
                .register(meterRegistry);

        // Lua: atomically add unique failure, prune window, count, and conditionally transition CLOSED→OPEN
        // KEYS[1] = failures ZSET, KEYS[2] = last_failure, KEYS[3] = state key
        // ARGV[1] = now (ms), ARGV[2] = window_ms, ARGV[3] = unique_member, ARGV[4] = threshold, ARGV[5] = TTL seconds
        String recordLua =
            "local fail_key = KEYS[1] " +
            "local last_fail_key = KEYS[2] " +
            "local state_key = KEYS[3] " +
            "local now = tonumber(ARGV[1]) " +
            "local window_ms = tonumber(ARGV[2]) " +
            "local member = ARGV[3] " +
            "local threshold = tonumber(ARGV[4]) " +
            "local ttl = tonumber(ARGV[5]) " +
            // Add unique failure, set last_failure, set TTLs, prune old
            "redis.call('ZADD', fail_key, now, member) " +
            "redis.call('SET', last_fail_key, now) " +
            "redis.call('EXPIRE', fail_key, ttl) " +
            "redis.call('EXPIRE', last_fail_key, ttl) " +
            "redis.call('ZREMRANGEBYSCORE', fail_key, '-inf', now - window_ms) " +
            "local count = redis.call('ZCOUNT', fail_key, '-inf', '+inf') " +
            // CAS: only transition CLOSED→OPEN once atomically
            "local state = redis.call('GET', state_key) " +
            "if (state == false or state == 'CLOSED') and count >= threshold then " +
            "  redis.call('SET', state_key, 'OPEN') " +
            "  redis.call('EXPIRE', state_key, ttl) " +
            "  return count * -1 " + // Negative signals that the transition happened
            "end " +
            "return count";
        this.recordFailureScript = new DefaultRedisScript<>(recordLua, Long.class);

        // Lua: compare-and-set state transition
        // KEYS[1] = state key
        // ARGV[1] = expected old state, ARGV[2] = new state, ARGV[3] = TTL seconds
        String casLua =
            "local state_key = KEYS[1] " +
            "local expected = ARGV[1] " +
            "local new_state = ARGV[2] " +
            "local ttl = tonumber(ARGV[3]) " +
            "local current = redis.call('GET', state_key) " +
            "if current == false then current = 'CLOSED' end " +
            "if current == expected then " +
            "  redis.call('SET', state_key, new_state) " +
            "  redis.call('EXPIRE', state_key, ttl) " +
            "  return 1 " +
            "end " +
            "return 0";
        this.casTransitionScript = new DefaultRedisScript<>(casLua, Long.class);
    }

    // ── Public API ──

    public CircuitBreakerState getState(String serviceName) {
        // Idempotently register a gauge for this specific service
        Gauge.builder("circuit_breaker_state", this, cb -> cb.fetchStateFromRedis(serviceName).ordinal())
             .tag("service", serviceName)
             .description("Circuit Breaker State (0=CLOSED, 1=HALF_OPEN, 2=OPEN)")
             .register(meterRegistry);

        return fetchStateFromRedis(serviceName);
    }

    private CircuitBreakerState fetchStateFromRedis(String serviceName) {
        try {
            String stateStr = redis.opsForValue().get(stateKey(serviceName));
            if (stateStr == null) return CircuitBreakerState.CLOSED;
            return CircuitBreakerState.valueOf(stateStr);
        } catch (Exception e) {
            log.warn("Redis unavailable, assuming CLOSED for '{}': {}", serviceName, e.getMessage());
            return CircuitBreakerState.CLOSED;
        }
    }

    public boolean allowRequest(String serviceName) {
        try {
            CircuitBreakerState state = getState(serviceName);

            switch (state) {
                case CLOSED:
                    return true;

                case OPEN:
                    long baseRecoveryMs = configService.getInt("cb_recovery_ms_" + serviceName, (int) DEFAULT_RECOVERY_MS);
                    
                    // ── Exponential Backoff ──
                    // Each consecutive trip doubles the cooldown: 10s → 20s → 40s → 80s → ... (capped at 5 min)
                    int tripCount = getTripCount(serviceName);
                    long backoffMs = Math.min(baseRecoveryMs * (1L << tripCount), MAX_BACKOFF_MS);
                    
                    String lastFailStr = redis.opsForValue().get(lastFailureKey(serviceName));
                    long lastFail = lastFailStr != null ? Long.parseLong(lastFailStr) : 0;
                    long elapsed = System.currentTimeMillis() - lastFail;

                    if (elapsed > backoffMs) {
                        // CAS transition OPEN → HALF_OPEN
                        Long result = redis.execute(casTransitionScript,
                                List.of(stateKey(serviceName)),
                                "OPEN", "HALF_OPEN", String.valueOf(KEY_TTL.toSeconds()));
                        if (result != null && result == 1L) {
                            auditService.log("CIRCUIT_HALF_OPEN",
                                    "Circuit for '" + serviceName + "': OPEN → HALF_OPEN (backoff=" + backoffMs + "ms, trip #" + tripCount + ")",
                                    "SYSTEM", "SUCCESS");
                            log.info("Circuit breaker HALF_OPEN for '{}' (backoff={}ms, trip #{})", serviceName, backoffMs, tripCount);
                        }
                        // Whether this replica won the CAS or another did, allow the probe
                        return true;
                    }
                    log.debug("Circuit OPEN for '{}' — waiting for backoff: {}ms remaining (trip #{})",
                            serviceName, backoffMs - elapsed, tripCount);
                    return false;

                case HALF_OPEN:
                    // Limit concurrent probes
                    int maxProbes = configService.getInt("cb_max_probes_" + serviceName, DEFAULT_MAX_HALF_OPEN_PROBES);
                    Long activeProbes = redis.opsForValue().increment(probeActiveKey(serviceName));
                    if (activeProbes != null && activeProbes > maxProbes) {
                        redis.opsForValue().decrement(probeActiveKey(serviceName));
                        log.debug("HALF_OPEN probe limit reached for '{}' ({}/{})", serviceName, activeProbes, maxProbes);
                        return false;
                    }
                    redis.expire(probeActiveKey(serviceName), Duration.ofSeconds(30));
                    return true;

                default:
                    return true;
            }
        } catch (Exception e) {
            log.warn("Redis unavailable during allowRequest for '{}', failing open: {}", serviceName, e.getMessage());
            return true;
        }
    }

    /**
     * Record a failure for a named service. Fully atomic via Lua script.
     * Returns true if the circuit transitioned from CLOSED → OPEN.
     */
    public boolean recordFailure(String serviceName) {
        try {
            long now = System.currentTimeMillis();
            long windowMs = configService.getInt("cb_window_ms_" + serviceName, (int) DEFAULT_WINDOW_MS);
            int threshold = getThreshold(serviceName);
            String uniqueMember = now + ":" + UUID.randomUUID().toString().substring(0, 8);

            Long result = redis.execute(
                    recordFailureScript,
                    List.of(failuresKey(serviceName), lastFailureKey(serviceName), stateKey(serviceName)),
                    String.valueOf(now),
                    String.valueOf(windowMs),
                    uniqueMember,
                    String.valueOf(threshold),
                    String.valueOf(KEY_TTL.toSeconds())
            );

            if (result == null) return false;

            boolean transitioned = result < 0;
            long count = Math.abs(result);

            if (transitioned) {
                log.warn("Circuit breaker OPENED for '{}' — {} failures in window (threshold: {})",
                        serviceName, count, threshold);
                alertService.sendAlert("CRITICAL",
                        "Circuit breaker opened for '" + serviceName + "': " + count + " failures",
                        "CIRCUIT_BREAKER");
                auditService.log("CIRCUIT_OPEN",
                        "Circuit for '" + serviceName + "': CLOSED → OPEN (" + count + " failures)",
                        "SYSTEM", "SUCCESS");
                circuitOpenedCounter.increment();
                incrementTripCount(serviceName);
                eventPublisher.publishEvent(new CircuitStateChangeEvent(serviceName, "CLOSED", "OPEN", count));
            }

            // If in HALF_OPEN and a probe fails, CAS back to OPEN
            if (getState(serviceName) == CircuitBreakerState.HALF_OPEN) {
                Long cas = redis.execute(casTransitionScript,
                        List.of(stateKey(serviceName)),
                        "HALF_OPEN", "OPEN", String.valueOf(KEY_TTL.toSeconds()));
                if (cas != null && cas == 1L) {
                    redis.delete(probeKey(serviceName));
                    redis.delete(probeActiveKey(serviceName));
                    auditService.log("CIRCUIT_OPEN",
                            "Circuit for '" + serviceName + "': HALF_OPEN → OPEN (probe failed)",
                            "SYSTEM", "SUCCESS");
                    log.warn("Circuit breaker re-OPENED for '{}' — probe failed", serviceName);
                }
            }

            return transitioned;

        } catch (Exception e) {
            log.error("Redis unavailable during recordFailure for '{}': {}", serviceName, e.getMessage());
            return false;
        }
    }

    public void recordSuccess(String serviceName) {
        try {
            CircuitBreakerState state = getState(serviceName);

            // Decrement active probe counter
            if (state == CircuitBreakerState.HALF_OPEN) {
                redis.opsForValue().decrement(probeActiveKey(serviceName));

                Long probeCount = redis.opsForValue().increment(probeKey(serviceName));
                redis.expire(probeKey(serviceName), KEY_TTL);
                int required = configService.getInt("cb_probe_success_" + serviceName, DEFAULT_PROBE_SUCCESS_REQUIRED);

                if (probeCount != null && probeCount >= required) {
                    // CAS: HALF_OPEN → CLOSED
                    Long cas = redis.execute(casTransitionScript,
                            List.of(stateKey(serviceName)),
                            "HALF_OPEN", "CLOSED", String.valueOf(KEY_TTL.toSeconds()));
                    if (cas != null && cas == 1L) {
                        redis.delete(probeKey(serviceName));
                        redis.delete(failuresKey(serviceName));
                        redis.delete(probeActiveKey(serviceName));
                        auditService.log("CIRCUIT_CLOSED",
                                "Circuit for '" + serviceName + "': HALF_OPEN → CLOSED (" + probeCount + " probes)",
                                "SYSTEM", "SUCCESS");
                        log.info("Circuit breaker CLOSED for '{}' — {} successful probes", serviceName, probeCount);
                        circuitClosedCounter.increment();
                        resetTripCount(serviceName); // ← Reset backoff on successful recovery
                        eventPublisher.publishEvent(new CircuitStateChangeEvent(serviceName, "HALF_OPEN", "CLOSED", 0));
                    }
                }
            }
        } catch (Exception e) {
            log.error("Redis unavailable during recordSuccess for '{}': {}", serviceName, e.getMessage());
        }
    }

    public long countRecentFailures(String serviceName) {
        try {
            long now = System.currentTimeMillis();
            long windowMs = configService.getInt("cb_window_ms_" + serviceName, (int) DEFAULT_WINDOW_MS);
            Long count = redis.opsForZSet().count(failuresKey(serviceName), now - windowMs, now);
            return count != null ? count : 0;
        } catch (Exception e) {
            log.warn("Redis unavailable during countRecentFailures: {}", e.getMessage());
            return 0;
        }
    }

    public Map<String, Object> getServiceStatus(String serviceName) {
        return Map.of(
                "service", serviceName,
                "state", getState(serviceName).name(),
                "recentFailures", countRecentFailures(serviceName),
                "threshold", getThreshold(serviceName)
        );
    }

    private int getThreshold(String serviceName) {
        return configService.getInt("cb_threshold_" + serviceName, DEFAULT_THRESHOLD);
    }

    // ── Legacy backward-compatible methods ──
    public CircuitBreakerState getState() { return getState(DEFAULT_SERVICE); }
    public boolean allowRequest() { return allowRequest(DEFAULT_SERVICE); }
    public void recordFailure(long failureCount) { recordFailure(DEFAULT_SERVICE); }
    public void recordSuccess() { recordSuccess(DEFAULT_SERVICE); }
    public void tryRecovery() { tryRecovery(DEFAULT_SERVICE); }

    public void tryRecovery(String serviceName) {
        try {
            Long cas = redis.execute(casTransitionScript,
                    List.of(stateKey(serviceName)),
                    "OPEN", "HALF_OPEN", String.valueOf(KEY_TTL.toSeconds()));
            if (cas != null && cas == 1L) {
                log.info("Circuit breaker transitioned to HALF_OPEN for '{}'", serviceName);
            }
        } catch (Exception e) {
            log.error("Redis unavailable during tryRecovery: {}", e.getMessage());
        }
    }

    public void resetCircuit() { resetCircuit(DEFAULT_SERVICE); }

    /**
     * Proactively opens the circuit breaker.
     * Called by the PredictiveMLService when a failure is predicted.
     */
    public void tripCircuit(String serviceName) {
        CircuitBreakerState currentState = getState(serviceName);
        if (currentState != CircuitBreakerState.OPEN) {
            log.warn("🛡️ AIOps: Proactively TRIPPING circuit breaker for {} to prevent cascading failure!", serviceName);
            try {
                redis.opsForValue().set(stateKey(serviceName), CircuitBreakerState.OPEN.name(), KEY_TTL);
                // Record current time as last failure so exponential backoff timer starts now
                redis.opsForValue().set(lastFailureKey(serviceName), String.valueOf(System.currentTimeMillis()), KEY_TTL);
                incrementTripCount(serviceName);
            } catch (Exception e) {
                log.warn("Redis unavailable during proactive circuit trip: {}", e.getMessage());
            }
            
            // Trigger failure event so dashboards update
            String reason = "AI predicted imminent service failure based on telemetry trends.";
            auditService.log("CIRCUIT_TRIPPED_PROACTIVELY",
                    "AIOps tripped circuit for " + serviceName + ": " + reason,
                    "PREDICTIVE_ML", "SUCCESS");
        }
    }

    public void resetCircuit(String serviceName) {
        try {
            redis.opsForValue().set(stateKey(serviceName), CircuitBreakerState.CLOSED.name(), KEY_TTL);
            redis.delete(failuresKey(serviceName));
            redis.delete(probeKey(serviceName));
            redis.delete(probeActiveKey(serviceName));
            auditService.log("CIRCUIT_RESET",
                    "Circuit manually reset to CLOSED for '" + serviceName + "'",
                    "SYSTEM", "SUCCESS");
            log.info("Circuit breaker manually reset to CLOSED for '{}'", serviceName);
        } catch (Exception e) {
            log.error("Redis unavailable during resetCircuit: {}", e.getMessage());
        }
    }

    public String fallbackResponse() { return "Circuit breaker OPEN - service temporarily unavailable"; }

    // ── Exponential Backoff Helpers ──

    /**
     * Get the number of consecutive times this circuit has tripped.
     * Used to calculate exponential backoff: baseRecovery * 2^tripCount
     */
    private int getTripCount(String serviceName) {
        try {
            String val = redis.opsForValue().get(tripCountKey(serviceName));
            return val != null ? Integer.parseInt(val) : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * Increment the trip counter when the circuit opens.
     */
    private void incrementTripCount(String serviceName) {
        try {
            redis.opsForValue().increment(tripCountKey(serviceName));
            redis.expire(tripCountKey(serviceName), Duration.ofMinutes(30));
            int newCount = getTripCount(serviceName);
            long backoffMs = Math.min(DEFAULT_RECOVERY_MS * (1L << newCount), MAX_BACKOFF_MS);
            log.info("⏱️ Exponential backoff for '{}': trip #{}, next cooldown = {}ms", serviceName, newCount, backoffMs);
        } catch (Exception e) {
            log.warn("Failed to increment trip count for '{}': {}", serviceName, e.getMessage());
        }
    }

    /**
     * Reset the trip counter when the circuit fully closes (service recovered).
     * This is critical — a successful recovery resets the backoff to zero.
     */
    private void resetTripCount(String serviceName) {
        try {
            redis.delete(tripCountKey(serviceName));
            log.info("✅ Backoff reset for '{}' — service fully recovered, trip count = 0", serviceName);
        } catch (Exception e) {
            log.warn("Failed to reset trip count for '{}': {}", serviceName, e.getMessage());
        }
    }

    // ── Key builders ──
    private String stateKey(String svc) { return KEY_PREFIX + svc + STATE_SUFFIX; }
    private String failuresKey(String svc) { return KEY_PREFIX + svc + FAILURES_SUFFIX; }
    private String lastFailureKey(String svc) { return KEY_PREFIX + svc + LAST_FAILURE_SUFFIX; }
    private String probeKey(String svc) { return KEY_PREFIX + svc + PROBES_SUFFIX; }
    private String probeActiveKey(String svc) { return KEY_PREFIX + svc + PROBE_ACTIVE_SUFFIX; }
    private String tripCountKey(String svc) { return KEY_PREFIX + svc + TRIP_COUNT_SUFFIX; }
}