package com.selfhealing.backend.controller;

import com.selfhealing.backend.model.Alert;
import com.selfhealing.backend.model.AuditLog;
import com.selfhealing.backend.model.SystemConfig;
import com.selfhealing.backend.repository.AlertRepository;
import com.selfhealing.backend.repository.AuditLogRepository;
import com.selfhealing.backend.repository.FailureMetricRepository;
import com.selfhealing.backend.repository.SystemConfigRepository;
import com.selfhealing.backend.service.CircuitBreakerService;
import com.selfhealing.backend.service.ConfigService;
import com.selfhealing.backend.service.FailureMetricService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@CrossOrigin(origins = "http://localhost:3000")
@RestController
public class AdminController {
    @Autowired
    CircuitBreakerService circuitBreakerService;
    @Autowired
    FailureMetricRepository failureMetricRepository;
    @Autowired
    FailureMetricService failureMetricService;
    @Autowired
    AuditLogRepository auditLogRepository;
    @Autowired
    SimpMessagingTemplate messagingTemplate;
    @Autowired
    SystemConfigRepository configRepo;
    @Autowired
    ConfigService configService;
    @Autowired
    AlertRepository alertRepository;


    @PostMapping("/admin/simulate")
    public String simulateFailure(@RequestBody Map<String, Object> payload) {

        String type = (String) payload.getOrDefault("type", "unknown");
        String service = (String) payload.getOrDefault("service", "default-service");

        int count = Integer.parseInt(payload.getOrDefault("count", 1).toString());

        for (int i = 0; i < count; i++) {
            failureMetricService.recordFailure(type, service);
        }

        return "✅ Simulated " + count + " failures of type '" + type +
                "' for service '" + service + "'";
    }


    @GetMapping("/admin/alert-status")
    public String getAlertStatus(){
        long totalFailures = failureMetricRepository.count();
        if(totalFailures >= 5){
            messagingTemplate.convertAndSend("/topic/alerts", "CRITICAL");
        } else if(totalFailures >= 3){
            messagingTemplate.convertAndSend("/topic/alerts", "CRITICAL");
        }
        return "NORMAL";
    }

    @PutMapping("/admin/fallback")
    public String updateFallback(@RequestParam String endpoint,
                                 @RequestParam String message) {

        String key = "fallback_" + endpoint;
        configService.set(key, message, "Updated fallback for " + endpoint);

        return "Fallback updated for " + endpoint;
    }

    @GetMapping("/admin/config")
    public List<SystemConfig> getAllConfig() {
        return configRepo.findAll();
    }

    @PutMapping("/admin/config/{key}")
    public String updateConfig(@PathVariable String key,
                               @RequestParam String value) {

        SystemConfig config = new SystemConfig(key, value, "Updated via API");
        configRepo.save(config);

        return "Updated " + key;
    }

    @GetMapping("/admin/audit-logs")
    public Page<AuditLog> auditlogs(Pageable pageable){
        return auditLogRepository.findAll(pageable);
    }

    @GetMapping("/admin/audit-logs/action/{actionType}")
    public Page<AuditLog> getLogsByAction(@PathVariable String actionType , Pageable pageable) {
        return auditLogRepository.findByActionType(actionType , pageable);
    }

    @GetMapping("/admin/audit-logs/date")
    public List<AuditLog> getLogsByDate(
            @RequestParam String from,
            @RequestParam String to) {

        return auditLogRepository.findByTimestampBetween(
                java.time.LocalDateTime.parse(from),
                java.time.LocalDateTime.parse(to)
        );
    }

    @PostMapping("/admin/reset-circuit")
    public String resetCircuit(){
        circuitBreakerService.resetCircuit();
        messagingTemplate.convertAndSend("/topic/metrics", "update");
        return "Circuit breaker reset to CLOSED state";
    }

    @PostMapping("/admin/clear-metrics")
    public String clearMetrics(){
        failureMetricRepository.deleteAll();
        messagingTemplate.convertAndSend("/topic/metrics", "update");
        return "Failure metrics cleared";
    }

    @GetMapping("/admin/alerts")
    public List<Alert> getAlerts() {
        return alertRepository.findAll();
    }

}
