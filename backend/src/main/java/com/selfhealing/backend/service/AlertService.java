package com.selfhealing.backend.service;

import com.selfhealing.backend.model.Alert;
import com.selfhealing.backend.repository.AlertRepository;
import com.selfhealing.backend.config.RedisWebSocketBroadcaster;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;

@Service
public class AlertService {

    private static final Logger log = LoggerFactory.getLogger(AlertService.class);

    private final RedisWebSocketBroadcaster broadcaster;
    private final EmailNotificationService emailService;
    private final StringRedisTemplate redisTemplate;
    private final AlertRepository alertRepository;

    private static final long COOLDOWN_SECONDS = 30;

    public AlertService(AlertRepository alertRepository,
                        RedisWebSocketBroadcaster broadcaster,
                        EmailNotificationService emailService,
                        StringRedisTemplate redisTemplate) {
        this.alertRepository = alertRepository;
        this.broadcaster = broadcaster;
        this.emailService = emailService;
        this.redisTemplate = redisTemplate;
    }

    public void sendAlert(String level, String message, String source) {
        // Distributed deduplication: check if this specific alert type is on cooldown
        String deduplicationKey = "alert_cooldown:" + source + ":" + level;
        
        // setIfAbsent acts as a lock/cooldown. Returns true if it was set (meaning it was not on cooldown)
        Boolean isNewAlert = redisTemplate.opsForValue().setIfAbsent(deduplicationKey, "active", Duration.ofSeconds(COOLDOWN_SECONDS));
        
        if (Boolean.FALSE.equals(isNewAlert)) {
            // Already sent recently from some replica, suppress it
            log.debug("Suppressing duplicate {} alert from {}: still in cooldown", level, source);
            return;
        }

        Alert alert = new Alert(level, message, source);
        alertRepository.save(alert);

        Map<String, Object> payload = Map.of(
                "level", level,
                "message", message,
                "source", source,
                "timestamp", java.time.LocalDateTime.now().toString()
        );

        broadcaster.broadcast("/topic/alerts", payload);

        log.warn("ALERT [{}]: {} (source: {})", level, message, source);

        if ("CRITICAL".equalsIgnoreCase(level)) {
            emailService.sendEmail(
                    "🚨 CRITICAL ALERT - Self Healing System",
                    message + "\nSource: " + source
            );
        }
    }
}