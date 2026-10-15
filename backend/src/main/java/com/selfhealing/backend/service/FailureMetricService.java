package com.selfhealing.backend.service;

import com.selfhealing.backend.model.*;
import com.selfhealing.backend.repository.FailureMetricRepository;
import com.selfhealing.backend.config.RedisWebSocketBroadcaster;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * FailureMetricService v2 — Classifies, records, and routes failures.
 *
 * Changes from v1:
 * 1. Wires HealingStrategyEngine.decideAndExecute() into the failure flow
 *    (previously only decideStrategy was called, playbooks were never executed).
 * 2. Classifies by exception type and HTTP status, not just manual string.
 * 3. Severity based on per-service rolling failure rate, not lifetime DB row count.
 * 4. Does NOT call circuitBreakerService.recordFailure() — that is done by
 *    the caller (OrderService) to avoid duplicate recording.
 */
@Service
public class FailureMetricService {

    private static final Logger log = LoggerFactory.getLogger(FailureMetricService.class);

    private final FailureMetricRepository repository;
    private final CircuitBreakerService circuitBreakerService;
    private final RedisWebSocketBroadcaster broadcaster;
    private final FailureClassifierService classifier;
    private final AuditService auditService;
    private final HealingStrategyEngine healingEngine;
    private final AlertService alertService;
    private final SLOService sloService;
    private final IncidentService incidentService;

    public FailureMetricService(FailureMetricRepository repository,
                                CircuitBreakerService circuitBreakerService,
                                RedisWebSocketBroadcaster broadcaster,
                                FailureClassifierService classifier,
                                AuditService auditService,
                                HealingStrategyEngine healingEngine,
                                AlertService alertService,
                                SLOService sloService,
                                IncidentService incidentService) {
        this.repository = repository;
        this.circuitBreakerService = circuitBreakerService;
        this.broadcaster = broadcaster;
        this.classifier = classifier;
        this.auditService = auditService;
        this.healingEngine = healingEngine;
        this.alertService = alertService;
        this.sloService = sloService;
        this.incidentService = incidentService;
    }

    /**
     * Record a failure from manual simulation or admin API.
     * This path DOES record to the circuit breaker (it's the only path that does for simulated failures).
     */
    public String recordFailure(String type, String serviceName) {
        sloService.recordFailure();

        // Use per-service windowed failure count for severity classification
        long recentFailures = circuitBreakerService.countRecentFailures(serviceName);

        FailureCategory category = classifier.classifyCategory(type);
        FailureSeverity severity = classifier.classifySeverity(recentFailures);

        FailureMetric metric = new FailureMetric(serviceName, type, category, severity);
        repository.save(metric);

        auditService.log("FAILURE_RECORDED",
                "Type: " + type + ", Service: " + serviceName + ", Severity: " + severity,
                "SYSTEM", "SUCCESS");

        if (severity == FailureSeverity.CRITICAL) {
            alertService.sendAlert("CRITICAL",
                    "Critical failure detected: " + type + " on " + serviceName,
                    "FAILURE");

            // Auto-create incident for critical failures
            incidentService.createIncident(
                    "Critical failure: " + type + " on " + serviceName,
                    "CRITICAL",
                    serviceName,
                    "runbook:" + type.toLowerCase());
        }

        // Execute healing strategy with playbook execution (previously only decided, never executed)
        HealingStrategy strategy = healingEngine.decideAndExecute(recentFailures);
        log.info("Healing strategy decided and executed: {} (recent failures: {})", strategy, recentFailures);

        // Record to circuit breaker (simulation path only)
        circuitBreakerService.recordFailure(serviceName);

        broadcaster.broadcast("/topic/metrics", "update");

        return "Failure recorded: " + type + " [" + severity + "] → " + strategy;
    }

    /**
     * Classify a failure from an exception type and HTTP status code.
     * More accurate than string-based classification.
     */
    public String classifyAndRecord(Exception e, int httpStatus, String serviceName) {
        String type;
        if (e instanceof java.net.SocketTimeoutException || e instanceof java.net.ConnectException) {
            type = "timeout";
        } else if (httpStatus == 503 || httpStatus == 502 || httpStatus == 504) {
            type = "network";
        } else if (httpStatus == 429) {
            type = "rate_limit";
        } else if (httpStatus >= 500) {
            type = "internal_error";
        } else {
            type = e.getClass().getSimpleName();
        }
        return recordFailure(type, serviceName);
    }
}