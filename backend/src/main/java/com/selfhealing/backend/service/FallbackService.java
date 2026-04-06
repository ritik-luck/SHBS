package com.selfhealing.backend.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class FallbackService {

    @Autowired
    ConfigService configService;

    private final Map<String, Object> fallbackResponses = new HashMap<>();

    public FallbackService() {
        // Default fallback configs (can later move to DB)
        fallbackResponses.put("/hello", "Service temporarily unavailable. Please try again later.");
    }

    public Object getFallback(String endpoint) {
        String key = "fallback_" + endpoint;
        String fallbackMessage = configService.get(key, "Default fallback response");


        return Map.of(
                "data", fallbackMessage,
                "fallback", true,
                "reason", "Service unavailable",
                "endpoint" , endpoint,
                "timestamp", System.currentTimeMillis()
        );
    }

    public void updateFallback(String endpoint, Object response) {
        fallbackResponses.put(endpoint, response);
    }
}