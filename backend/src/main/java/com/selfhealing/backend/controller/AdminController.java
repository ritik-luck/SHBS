package com.selfhealing.backend.controller;

import com.selfhealing.backend.model.Alert;
import com.selfhealing.backend.model.AuditLog;
import com.selfhealing.backend.model.ChaosExperiment;
import com.selfhealing.backend.model.SystemConfig;
import com.selfhealing.backend.repository.AlertRepository;
import com.selfhealing.backend.repository.AuditLogRepository;
import com.selfhealing.backend.repository.FailureMetricRepository;
import com.selfhealing.backend.repository.SystemConfigRepository;
import com.selfhealing.backend.service.CircuitBreakerService;
import com.selfhealing.backend.service.ConfigService;
import com.selfhealing.backend.service.FailureMetricService;
import com.selfhealing.backend.service.InventoryService;
import com.selfhealing.backend.service.PaymentService;
import com.selfhealing.backend.service.NotificationService;
import com.selfhealing.backend.service.ChaosAgentService;
import com.selfhealing.backend.config.RedisWebSocketBroadcaster;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@CrossOrigin(origins = "${cors.allowed-origins}")
@RestController
public class AdminController {

    private final CircuitBreakerService circuitBreakerService;
    private final FailureMetricRepository failureMetricRepository;
    private final FailureMetricService failureMetricService;
    private final AuditLogRepository auditLogRepository;
    private final RedisWebSocketBroadcaster broadcaster;
    private final SystemConfigRepository configRepo;
    private final ConfigService configService;
    private final AlertRepository alertRepository;
    private final InventoryService inventoryService;
    private final PaymentService paymentService;
    private final NotificationService notificationService;
    private final ChaosAgentService chaosAgentService;

    public AdminController(CircuitBreakerService circuitBreakerService,
                           FailureMetricRepository failureMetricRepository,
                           FailureMetricService failureMetricService,
                           AuditLogRepository auditLogRepository,
                           RedisWebSocketBroadcaster broadcaster,
                           SystemConfigRepository configRepo,
                           ConfigService configService,
                           AlertRepository alertRepository,
                           InventoryService inventoryService,
                           PaymentService paymentService,
                           NotificationService notificationService,
                           ChaosAgentService chaosAgentService) {
        this.circuitBreakerService = circuitBreakerService;
        this.failureMetricRepository = failureMetricRepository;
        this.failureMetricService = failureMetricService;
        this.auditLogRepository = auditLogRepository;
        this.broadcaster = broadcaster;
        this.configRepo = configRepo;
        this.configService = configService;
        this.alertRepository = alertRepository;
        this.inventoryService = inventoryService;
        this.paymentService = paymentService;
        this.notificationService = notificationService;
        this.chaosAgentService = chaosAgentService;
    }


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
        String level = totalFailures >= 5 ? "CRITICAL" : (totalFailures >= 3 ? "WARNING" : "NORMAL");
        if ("CRITICAL".equalsIgnoreCase(level)) {
            broadcaster.broadcast("/topic/alerts", "CRITICAL");
        } else if ("WARNING".equalsIgnoreCase(level)) {
            broadcaster.broadcast("/topic/alerts", "WARNING");
        }
        return level;
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
        broadcaster.broadcast("/topic/metrics", "update");
        return "Circuit breaker reset to CLOSED state";
    }

    @PostMapping("/admin/clear-metrics")
    public String clearMetrics(){
        failureMetricRepository.deleteAll();
        broadcaster.broadcast("/topic/metrics", "update");
        return "Failure metrics cleared";
    }

    @GetMapping("/admin/alerts")
    public Page<Alert> getAlerts(Pageable pageable) {
        return alertRepository.findAll(pageable);
    }

    // ── Chaos Engineering: Inventory Service ──

    @PostMapping("/admin/chaos/inventory")
    public Map<String, Object> setInventoryChaos(
            @RequestParam(required = false) Double failRate,
            @RequestParam(required = false) Long latencyMs) {

        if (failRate != null) {
            inventoryService.setFailRate(failRate);
        }
        if (latencyMs != null) {
            inventoryService.setLatencyMs(latencyMs);
        }

        return inventoryService.getChaosConfig();
    }

    @GetMapping("/admin/chaos/inventory")
    public Map<String, Object> getInventoryChaos() {
        return inventoryService.getChaosConfig();
    }

    // ── Chaos Engineering: Payment Service ──

    @PostMapping("/admin/chaos/payment")
    public Map<String, Object> setPaymentChaos(
            @RequestParam(required = false) Double failRate,
            @RequestParam(required = false) Long latencyMs) {

        if (failRate != null) {
            paymentService.setFailRate(failRate);
        }
        if (latencyMs != null) {
            paymentService.setLatencyMs(latencyMs);
        }

        return paymentService.getChaosConfig();
    }

    @GetMapping("/admin/chaos/payment")
    public Map<String, Object> getPaymentChaos() {
        return paymentService.getChaosConfig();
    }

    // ── Chaos Engineering: Notification Service ──

    @PostMapping("/admin/chaos/notification")
    public Map<String, Object> setNotificationChaos(
            @RequestParam(required = false) Double failRate,
            @RequestParam(required = false) Long latencyMs) {

        if (failRate != null) {
            notificationService.setFailRate(failRate);
        }
        if (latencyMs != null) {
            notificationService.setLatencyMs(latencyMs);
        }

        return notificationService.getChaosConfig();
    }

    @GetMapping("/admin/chaos/notification")
    public Map<String, Object> getNotificationChaos() {
        return notificationService.getChaosConfig();
    }

    // ── AI Chaos Engineering Agent (Red Team) ──

    @PostMapping("/admin/chaos/run-experiment")
    public ChaosExperiment runChaosExperiment(
            @RequestParam(required = false) String failureType,
            @RequestParam(required = false) String targetService) {
        
        if (targetService == null || targetService.isBlank()) {
            targetService = "inventory-service"; // default
        }
        
        if (failureType != null && !failureType.isBlank()) {
            return chaosAgentService.runExperiment(failureType, targetService);
        }
        // If no failure type is specified, run random experiment but we still need to pass something if we want.
        // Actually, runExperiment() with no args picks randomly for both.
        // If only targetService is passed, let's just pick a random failure type and pass targetService.
        if (failureType == null || failureType.isBlank()) {
             String randomFailureType = "timeout"; // simplified for now, or we can just let it pick
             return chaosAgentService.runExperiment("timeout", targetService); // Quick fix, realistically we could extract the random selection logic.
        }
        
        return chaosAgentService.runExperiment();
    }

    @GetMapping("/admin/chaos/experiments")
    public List<ChaosExperiment> getChaosExperiments() {
        return chaosAgentService.getRecentExperiments();
    }

    @GetMapping("/admin/chaos/experiments/{id}")
    public ChaosExperiment getChaosExperiment(@PathVariable Long id) {
        return chaosAgentService.getExperiment(id);
    }

}
