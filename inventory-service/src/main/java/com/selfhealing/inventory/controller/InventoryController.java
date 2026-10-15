package com.selfhealing.inventory.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class InventoryController {

    private final StringRedisTemplate redis;

    public InventoryController(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @GetMapping("/inventory/check")
    public ResponseEntity<?> checkInventory(@RequestParam String product, @RequestParam int quantity) {
        
        // Read chaos config from Redis
        String failRateStr = redis.opsForValue().get("chaos:inventory:fail_rate");
        double failRate = failRateStr != null ? Double.parseDouble(failRateStr) : 0.0;
        
        String latencyStr = redis.opsForValue().get("chaos:inventory:latency_ms");
        long latencyMs = latencyStr != null ? Long.parseLong(latencyStr) : 0L;

        // 1. Inject Latency
        if (latencyMs > 0) {
            try {
                Thread.sleep(latencyMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        // 2. Inject Failure
        if (Math.random() < failRate) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", "Simulated downstream failure", "type", "DATABASE_TIMEOUT"));
        }

        // 3. Normal Response
        double price = quantity * 100.0;
        return ResponseEntity.ok(Map.of(
                "available", true,
                "product", product,
                "quantity", quantity,
                "totalPrice", price
        ));
    }
}
