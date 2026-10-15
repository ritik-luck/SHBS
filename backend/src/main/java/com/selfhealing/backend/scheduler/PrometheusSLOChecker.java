package com.selfhealing.backend.scheduler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.selfhealing.backend.service.AlertService;
import com.selfhealing.backend.service.AuditService;
import com.selfhealing.backend.service.SelfHealingActionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * PrometheusSLOChecker — Replaces JVM-local memory burn rate checks with
 * global Prometheus querying. Ensures all replicas make healing decisions
 * based on the global cluster state, preventing split-brain healing.
 */
@Component
public class PrometheusSLOChecker {

    private static final Logger log = LoggerFactory.getLogger(PrometheusSLOChecker.class);
    
    private static final double FAST_BURN_THRESHOLD = 14.4;
    private static final double SLOW_BURN_THRESHOLD = 6.0;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final AlertService alertService;
    private final AuditService auditService;
    private final SelfHealingActionService healingActionService;
    private final StringRedisTemplate redisTemplate;

    @org.springframework.beans.factory.annotation.Value("${prometheus.url:http://prometheus:9090}")
    private String prometheusUrl;

    public PrometheusSLOChecker(AlertService alertService,
                                AuditService auditService,
                                SelfHealingActionService healingActionService,
                                StringRedisTemplate redisTemplate) {
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
        this.alertService = alertService;
        this.auditService = auditService;
        this.healingActionService = healingActionService;
        this.redisTemplate = redisTemplate;
    }

    @Scheduled(fixedRate = 30000) // Check every 30s
    public void checkGlobalBurnRate() {
        // Distributed lock: only one replica should execute this check
        String lockKey = "lock:slo_checker";
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(lockKey, "LOCKED", Duration.ofSeconds(20));
        
        if (Boolean.FALSE.equals(acquired)) {
            return; // Another replica is handling the SLO check
        }

        try {
            // Check fast burn
            double fastBurn = queryPrometheus("slo:burn_rate_5m");
            if (fastBurn >= FAST_BURN_THRESHOLD) {
                log.warn("GLOBAL FAST BURN: {}x over 5min", String.format("%.1f", fastBurn));
                alertService.sendAlert("CRITICAL", 
                        String.format("Global Fast burn rate %.1fx (5min window)", fastBurn),
                        "SLO_ENGINE");
                healingActionService.clearCaches();
                healingActionService.restartConnectionPool();
            }

            // Check slow burn
            double slowBurn = queryPrometheus("slo:burn_rate_1h");
            if (slowBurn >= SLOW_BURN_THRESHOLD) {
                log.warn("GLOBAL SLOW BURN: {}x over 1hr", String.format("%.1f", slowBurn));
                alertService.sendAlert("WARNING",
                        String.format("Global Slow burn rate %.1fx (1hr window)", slowBurn),
                        "SLO_ENGINE");
                auditService.log("SLO_SLOW_BURN",
                        "Burn rate: " + String.format("%.2f", slowBurn) + "x",
                        "SYSTEM", "TRIGGERED");
            }
        } catch (Exception e) {
            log.warn("Failed to query Prometheus for global burn rates: {}", e.getMessage());
        }
    }

    private double queryPrometheus(String query) throws Exception {
        String url = prometheusUrl + "/api/v1/query?query=" + query;
        String response = restTemplate.getForObject(url, String.class);
        if (response != null) {
            JsonNode root = objectMapper.readTree(response);
            JsonNode result = root.path("data").path("result");
            if (result.isArray() && result.size() > 0) {
                JsonNode valueNode = result.get(0).path("value");
                if (valueNode.isArray() && valueNode.size() > 1) {
                    return valueNode.get(1).asDouble();
                }
            }
        }
        return 0.0;
    }
}
