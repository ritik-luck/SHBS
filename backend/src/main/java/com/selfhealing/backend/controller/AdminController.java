package com.selfhealing.backend.controller;

import com.selfhealing.backend.model.AuditLog;
import com.selfhealing.backend.model.SystemConfig;
import com.selfhealing.backend.repository.AuditLogRepository;
import com.selfhealing.backend.repository.FailureMetricRepository;
import com.selfhealing.backend.repository.SystemConfigRepository;
import com.selfhealing.backend.service.CircuitBreakerService;
import com.selfhealing.backend.service.ConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@CrossOrigin(origins = "http://localhost:3000")
@RestController
public class AdminController {
    @Autowired
    CircuitBreakerService circuitBreakerService;
    @Autowired
    FailureMetricRepository failureMetricRepository;
    @Autowired
    AuditLogRepository auditLogRepository;
    @Autowired
    SimpMessagingTemplate messagingTemplate;
    @Autowired
    SystemConfigRepository configRepo;
    @Autowired
    ConfigService configService;


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
}
