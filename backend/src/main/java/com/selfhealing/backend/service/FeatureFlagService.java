package com.selfhealing.backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * FeatureFlagService v2 — Redis-backed feature flags with SCAN (not KEYS).
 *
 * Changes from v1:
 * 1. Replaced Redis KEYS command with SCAN (production-safe, non-blocking).
 * 2. Added try-catch for Redis unavailability (fail-open: features default to enabled).
 */
@Service
public class FeatureFlagService {

    private static final Logger log = LoggerFactory.getLogger(FeatureFlagService.class);
    private static final String KEY_PREFIX = "feature:";

    private final StringRedisTemplate redis;
    private final AuditService auditService;

    public FeatureFlagService(StringRedisTemplate redis, AuditService auditService) {
        this.redis = redis;
        this.auditService = auditService;
    }

    /**
     * Check if a feature is enabled. Defaults to true if not explicitly set or Redis is down.
     */
    public boolean isEnabled(String featureName) {
        try {
            String val = redis.opsForValue().get(KEY_PREFIX + featureName);
            return val == null || "true".equalsIgnoreCase(val);
        } catch (Exception e) {
            log.warn("Redis unavailable for feature flag '{}', defaulting to enabled: {}", featureName, e.getMessage());
            return true; // Fail-open
        }
    }

    /**
     * Enable or disable a feature.
     */
    public void setEnabled(String featureName, boolean enabled) {
        try {
            redis.opsForValue().set(KEY_PREFIX + featureName, String.valueOf(enabled));
            auditService.log(
                    enabled ? "FEATURE_ENABLED" : "FEATURE_DISABLED",
                    "Feature '" + featureName + "' set to " + enabled,
                    "SYSTEM",
                    "SUCCESS"
            );
            log.info("Feature flag '{}' set to: {}", featureName, enabled);
        } catch (Exception e) {
            log.error("Failed to set feature flag '{}': {}", featureName, e.getMessage());
        }
    }

    /**
     * Get all feature flags and their current states.
     * Uses SCAN instead of KEYS to avoid blocking Redis in production.
     */
    public Map<String, Boolean> getAllFlags() {
        Map<String, Boolean> flags = new HashMap<>();
        try {
            ScanOptions options = ScanOptions.scanOptions().match(KEY_PREFIX + "*").count(100).build();
            try (Cursor<String> cursor = redis.scan(options)) {
                while (cursor.hasNext()) {
                    String key = cursor.next();
                    String val = redis.opsForValue().get(key);
                    String flagName = key.substring(KEY_PREFIX.length());
                    flags.put(flagName, val == null || "true".equalsIgnoreCase(val));
                }
            }
        } catch (Exception e) {
            log.warn("Redis unavailable when scanning feature flags: {}", e.getMessage());
        }
        return flags;
    }
}
