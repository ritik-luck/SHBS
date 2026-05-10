package com.selfhealing.backend.service;

import com.selfhealing.backend.model.Alert;
import com.selfhealing.backend.repository.AlertRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;

@Service
public class AlertService {

    @Autowired
    AlertRepository alertRepository;
    @Autowired
    SimpMessagingTemplate messagingTemplate;
    @Autowired
    EmailNotificationService emailService;

    LocalDateTime lastAlertTime;

    private final long COOLDOWN_SECONDS = 30;

    public void sendAlert(String level, String message, String source) {

        // 🔥 Cooldown check
        if (lastAlertTime != null &&
                lastAlertTime.plusSeconds(COOLDOWN_SECONDS).isAfter(LocalDateTime.now())) {
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

        messagingTemplate.convertAndSend("/topic/alerts", payload);

        System.out.println("🚨 ALERT: " + message);

        if ("CRITICAL".equalsIgnoreCase(level)) {
            emailService.sendEmail(
                    "🚨 CRITICAL ALERT - Self Healing System",
                    message + "\nSource: " + source
            );
        }

        lastAlertTime = LocalDateTime.now();
    }
}