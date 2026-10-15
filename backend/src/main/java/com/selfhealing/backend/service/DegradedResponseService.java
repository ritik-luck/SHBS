package com.selfhealing.backend.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
public class DegradedResponseService {
    
    private static final Logger log = LoggerFactory.getLogger(DegradedResponseService.class);
    private static final String CACHE_PREFIX = "fallback_cache:";
    private static final Duration CACHE_TTL = Duration.ofMinutes(15);

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public DegradedResponseService(StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    public void cacheResponse(String endpoint, Object response) {
        try {
            String json = objectMapper.writeValueAsString(response);
            redis.opsForValue().set(CACHE_PREFIX + endpoint, json, CACHE_TTL);
            redis.opsForValue().set(CACHE_PREFIX + endpoint + ":time", LocalDateTime.now().toString(), CACHE_TTL);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize cache response for {}", endpoint, e);
        } catch (Exception e) {
            log.warn("Redis unavailable for caching response on {}: {}", endpoint, e.getMessage());
        }
    }

    public Map<String, Object> getDegradedResponse(String endpoint, String reason) {
        Map<String, Object> result = new HashMap<>();
        result.put("degraded", true);
        result.put("reason", reason);

        try {
            String json = redis.opsForValue().get(CACHE_PREFIX + endpoint);
            String timeStr = redis.opsForValue().get(CACHE_PREFIX + endpoint + ":time");
            
            if (json != null) {
                // Try to parse it back into a generic object/map/list depending on what it was
                Object data = objectMapper.readValue(json, Object.class);
                result.put("data", data);
                result.put("cachedAt", timeStr != null ? LocalDateTime.parse(timeStr) : null);
            } else {
                result.put("data", null);
            }
        } catch (Exception e) {
            log.warn("Failed to retrieve degraded response from Redis for {}: {}", endpoint, e.getMessage());
            result.put("data", null);
            result.put("error", "Cache unavailable");
        }

        return result;
    }

    public boolean hasCache(String endpoint) {
        try {
            return Boolean.TRUE.equals(redis.hasKey(CACHE_PREFIX + endpoint));
        } catch (Exception e) {
            log.warn("Redis unavailable when checking cache for {}: {}", endpoint, e.getMessage());
            return false;
        }
    }

    public void clearCache() {
        try {
            redis.delete(redis.keys(CACHE_PREFIX + "*"));
        } catch (Exception e) {
            log.warn("Redis unavailable when clearing cache: {}", e.getMessage());
        }
    }
}