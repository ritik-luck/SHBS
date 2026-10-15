package com.selfhealing.backend.service;

import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class FallbackService {

    private final ConfigService configService;

    public FallbackService(ConfigService configService) {
        this.configService = configService;
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
}