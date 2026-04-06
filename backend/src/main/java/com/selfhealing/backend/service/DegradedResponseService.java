package com.selfhealing.backend.service;

import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
public class DegradedResponseService {

    private Map<String, Object> cache = new HashMap<>();
    private Map<String, LocalDateTime> cacheTime = new HashMap<>();

    // ✅ Store successful response
    public void cacheResponse(String endpoint, Object response) {
        cache.put(endpoint, response);
        cacheTime.put(endpoint, LocalDateTime.now());
    }

    // ✅ Get cached response
    public Map<String, Object> getDegradedResponse(String endpoint, String reason) {

        Map<String, Object> result = new HashMap<>();

        result.put("data", cache.get(endpoint));
        result.put("degraded", true);
        result.put("cachedAt", cacheTime.get(endpoint));
        result.put("reason", reason);

        return result;
    }

    public boolean hasCache(String endpoint) {
        return cache.containsKey(endpoint);
    }
}