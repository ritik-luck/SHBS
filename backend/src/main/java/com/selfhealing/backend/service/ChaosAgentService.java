package com.selfhealing.backend.service;

import com.selfhealing.backend.model.ChaosExperiment;
import com.selfhealing.backend.model.CircuitBreakerState;
import com.selfhealing.backend.repository.ChaosExperimentRepository;
import dev.langchain4j.model.chat.ChatLanguageModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * ChaosAgentService — Autonomous AI Red Team Agent.
 *
 * This agent autonomously attacks its own system to continuously verify
 * that the self-healing loop (Circuit Breaker → Recovery → Heal) works correctly.
 *
 * Workflow:
 * 1. Randomly select a failure type
 * 2. Inject the failure (set inventory fail rate to 100%)
 * 3. Trigger traffic to force the circuit breaker to trip
 * 4. Wait and observe the circuit opening
 * 5. Remove the failure (set fail rate to 0%)
 * 6. Wait and observe the circuit healing back to CLOSED
 * 7. Score the result and generate an AI analysis
 */
@Service
public class ChaosAgentService {

    private static final Logger log = LoggerFactory.getLogger(ChaosAgentService.class);

    private static final String[] FAILURE_TYPES = {"timeout", "network", "database"};
    private static final String[] TARGET_SERVICES = {
        "inventory-service", 
        "payment-service", 
        "notification-service"
    };

    // Max time to wait for circuit to open after injection (ms)
    private static final long MAX_DETECT_WAIT_MS = 30_000;
    // Max time to wait for circuit to heal after removing failure (ms)
    private static final long MAX_HEAL_WAIT_MS = 60_000;
    // Polling interval when watching state transitions
    private static final long POLL_INTERVAL_MS = 500;

    private final ChaosExperimentRepository experimentRepo;
    private final CircuitBreakerService circuitBreakerService;
    private final InventoryService inventoryService;
    private final PaymentService paymentService;
    private final NotificationService notificationService;
    private final OrderService orderService;
    private final AuditService auditService;
    private final ChatLanguageModel chatLanguageModel;

    public ChaosAgentService(ChaosExperimentRepository experimentRepo,
                             CircuitBreakerService circuitBreakerService,
                             InventoryService inventoryService,
                             PaymentService paymentService,
                             NotificationService notificationService,
                             OrderService orderService,
                             AuditService auditService,
                             ChatLanguageModel chatLanguageModel) {
        this.experimentRepo = experimentRepo;
        this.circuitBreakerService = circuitBreakerService;
        this.inventoryService = inventoryService;
        this.paymentService = paymentService;
        this.notificationService = notificationService;
        this.orderService = orderService;
        this.auditService = auditService;
        this.chatLanguageModel = chatLanguageModel;
    }

    /**
     * Run a full chaos experiment. This is the main entry point.
     * Thread-safe: each invocation creates its own ChaosExperiment record.
     */
    public ChaosExperiment runExperiment() {
        String failureType = FAILURE_TYPES[ThreadLocalRandom.current().nextInt(FAILURE_TYPES.length)];
        String targetService = TARGET_SERVICES[ThreadLocalRandom.current().nextInt(TARGET_SERVICES.length)];
        return runExperiment(failureType, targetService);
    }

    /**
     * Run a chaos experiment with a specific failure type and target.
     */
    public ChaosExperiment runExperiment(String failureType, String targetService) {
        ChaosExperiment experiment = new ChaosExperiment(failureType, targetService);
        experiment = experimentRepo.save(experiment);

        log.info("🔴 CHAOS AGENT: Starting experiment #{} — injecting '{}' into {}",
                experiment.getId(), failureType, targetService);
        auditService.log("CHAOS_EXPERIMENT_STARTED",
                "Chaos Agent starting experiment #" + experiment.getId() + " type=" + failureType,
                "CHAOS_AGENT", "STARTED");

        try {
            // ── Phase 1: Ensure clean baseline ──
            experiment.appendLog("Phase 1: Ensuring clean baseline state");
            CircuitBreakerState baseline = circuitBreakerService.getState(targetService);
            experiment.appendLog("Baseline circuit state: " + baseline);

            if (baseline != CircuitBreakerState.CLOSED) {
                experiment.appendLog("WARNING: Circuit not CLOSED at baseline. Resetting...");
                circuitBreakerService.resetCircuit(targetService);
                removeFailure(targetService);
                Thread.sleep(2000);
            }
            experimentRepo.save(experiment);

            // ── Phase 2: Inject failure ──
            experiment.appendLog("Phase 2: Injecting failure type '" + failureType + "'");
            experiment.setInjectedAt(LocalDateTime.now());
            long injectTimestamp = System.currentTimeMillis();

            applyFailure(targetService, failureType);
            experiment.appendLog("Failure injected. Sending traffic to trigger circuit breaker...");
            experimentRepo.save(experiment);

            // Send traffic to force detection
            for (int i = 0; i < 10; i++) {
                try {
                    orderService.createOrder("CHAOS-PROBE-" + i, 1,
                            "chaos-" + experiment.getId() + "-" + i);
                } catch (Exception ignored) {
                    // Expected — we're injecting failures
                }
                Thread.sleep(200);
            }

            // ── Phase 3: Wait for circuit to OPEN ──
            experiment.appendLog("Phase 3: Waiting for circuit breaker to transition to OPEN...");
            experimentRepo.save(experiment);

            boolean detected = waitForState(targetService, CircuitBreakerState.OPEN, MAX_DETECT_WAIT_MS);
            long detectDuration = System.currentTimeMillis() - injectTimestamp;

            if (detected) {
                experiment.setDetectedAt(LocalDateTime.now());
                experiment.setCircuitOpenedWithinMs(detectDuration);
                experiment.appendLog("✅ Circuit OPENED in " + detectDuration + " ms");
            } else {
                experiment.appendLog("❌ Circuit did NOT open within " + MAX_DETECT_WAIT_MS + " ms — FAILED");
                experiment.setHealingSucceeded(false);
                experiment.setStatus("FAILED");
                experiment.setResilienceScore(0);
                removeFailure(targetService);
                finalizeExperiment(experiment, injectTimestamp);
                return experimentRepo.save(experiment);
            }
            experimentRepo.save(experiment);

            // ── Phase 4: Remove failure and wait for healing ──
            experiment.appendLog("Phase 4: Removing injected failure. Waiting for autonomous healing...");
            removeFailure(targetService);
            long healStartTimestamp = System.currentTimeMillis();
            experimentRepo.save(experiment);

            boolean healed = waitForState(targetService, CircuitBreakerState.CLOSED, MAX_HEAL_WAIT_MS);
            long healDuration = System.currentTimeMillis() - healStartTimestamp;

            if (healed) {
                experiment.setHealedAt(LocalDateTime.now());
                experiment.setCircuitClosedWithinMs(healDuration);
                experiment.setHealingSucceeded(true);
                experiment.setStatus("PASSED");
                experiment.appendLog("✅ Circuit CLOSED (healed) in " + healDuration + " ms");
            } else {
                experiment.setHealingSucceeded(false);
                experiment.setStatus("FAILED");
                experiment.appendLog("❌ Circuit did NOT close within " + MAX_HEAL_WAIT_MS + " ms — healing FAILED");
                // Force reset to leave system in clean state
                circuitBreakerService.resetCircuit(targetService);
                experiment.appendLog("Force-reset circuit to CLOSED to restore system");
            }

            // ── Phase 5: Score and Analyze ──
            finalizeExperiment(experiment, injectTimestamp);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            experiment.setStatus("ABORTED");
            experiment.appendLog("Experiment interrupted: " + e.getMessage());
            removeFailure(targetService);
        } catch (Exception e) {
            experiment.setStatus("FAILED");
            experiment.appendLog("Unexpected error: " + e.getMessage());
            log.error("Chaos experiment #{} failed unexpectedly", experiment.getId(), e);
            removeFailure(targetService);
        }

        experiment = experimentRepo.save(experiment);

        log.info("🏁 CHAOS AGENT: Experiment #{} finished — Status: {}, Score: {}",
                experiment.getId(), experiment.getStatus(), experiment.getResilienceScore());
        auditService.log("CHAOS_EXPERIMENT_COMPLETED",
                "Experiment #" + experiment.getId() + " status=" + experiment.getStatus()
                        + " score=" + experiment.getResilienceScore(),
                "CHAOS_AGENT", experiment.getStatus());

        return experiment;
    }

    /**
     * Calculate resilience score and generate AI analysis.
     */
    private void finalizeExperiment(ChaosExperiment experiment, long startTimestamp) {
        long totalMs = System.currentTimeMillis() - startTimestamp;
        experiment.setTotalDurationMs(totalMs);
        experiment.appendLog("Total experiment duration: " + totalMs + " ms");

        // ── Calculate Resilience Score (0-100) ──
        int score = calculateResilienceScore(experiment);
        experiment.setResilienceScore(score);
        experiment.appendLog("Resilience Score: " + score + "/100");

        // ── Generate AI Analysis ──
        try {
            String analysis = generateAiAnalysis(experiment);
            experiment.setAiAnalysis(analysis);
            experiment.appendLog("AI analysis generated successfully");
        } catch (Exception e) {
            experiment.appendLog("AI analysis failed: " + e.getMessage());
            log.warn("Failed to generate AI analysis for experiment #{}: {}", experiment.getId(), e.getMessage());
        }
    }

    /**
     * Score the experiment based on detection speed and healing speed.
     * - Detection under 5s = 40 points
     * - Healing under 30s = 40 points
     * - Successful healing = 20 points
     */
    private int calculateResilienceScore(ChaosExperiment experiment) {
        int score = 0;

        // Detection speed (max 40 points)
        if (experiment.getCircuitOpenedWithinMs() != null) {
            long detectMs = experiment.getCircuitOpenedWithinMs();
            if (detectMs <= 2000) score += 40;
            else if (detectMs <= 5000) score += 30;
            else if (detectMs <= 10000) score += 20;
            else if (detectMs <= 20000) score += 10;
        }

        // Healing speed (max 40 points)
        if (experiment.getCircuitClosedWithinMs() != null) {
            long healMs = experiment.getCircuitClosedWithinMs();
            if (healMs <= 10000) score += 40;
            else if (healMs <= 20000) score += 30;
            else if (healMs <= 30000) score += 20;
            else if (healMs <= 45000) score += 10;
        }

        // Healing success (20 points)
        if (Boolean.TRUE.equals(experiment.getHealingSucceeded())) {
            score += 20;
        }

        return Math.min(score, 100);
    }

    private String generateAiAnalysis(ChaosExperiment experiment) {
        String prompt = String.format("""
            You are an expert Site Reliability Engineer analyzing a chaos engineering experiment.
            
            EXPERIMENT DETAILS:
            - Type: %s
            - Target Service: %s
            - Status: %s
            - Detection Time (circuit opened): %s ms
            - Healing Time (circuit closed): %s ms
            - Total Duration: %s ms
            - Resilience Score: %d/100
            - Healing Succeeded: %s
            
            EXPERIMENT LOG:
            %s
            
            Please provide a brief analysis in Markdown with these sections:
            #### Experiment Summary
            #### Detection Analysis
            #### Healing Analysis
            #### Recommendations
            
            Keep it concise (under 300 words). Focus on actionable improvements.
            """,
                experiment.getExperimentType(),
                experiment.getTargetService(),
                experiment.getStatus(),
                experiment.getCircuitOpenedWithinMs(),
                experiment.getCircuitClosedWithinMs(),
                experiment.getTotalDurationMs(),
                experiment.getResilienceScore() != null ? experiment.getResilienceScore() : 0,
                experiment.getHealingSucceeded(),
                experiment.getExperimentLog());

        return chatLanguageModel.generate(prompt);
    }

    // ── Failure injection/removal helpers ──

    private void applyFailure(String targetService, String type) {
        long latencyMs = type.equalsIgnoreCase("timeout") ? 5000 : 0;
        double failRate = type.equalsIgnoreCase("timeout") ? 0.0 : 1.0;

        switch (targetService) {
            case "inventory-service" -> {
                inventoryService.setLatencyMs(latencyMs);
                inventoryService.setFailRate(failRate);
            }
            case "payment-service" -> {
                paymentService.setLatencyMs(latencyMs);
                paymentService.setFailRate(failRate);
            }
            case "notification-service" -> {
                notificationService.setLatencyMs(latencyMs);
                notificationService.setFailRate(failRate);
            }
        }
    }

    private void removeFailure(String targetService) {
        switch (targetService) {
            case "inventory-service" -> {
                inventoryService.setFailRate(0.0);
                inventoryService.setLatencyMs(0);
            }
            case "payment-service" -> {
                paymentService.setFailRate(0.0);
                paymentService.setLatencyMs(0);
            }
            case "notification-service" -> {
                notificationService.setFailRate(0.0);
                notificationService.setLatencyMs(0);
            }
        }
    }

    /**
     * Poll circuit breaker state until it matches the target or timeout.
     */
    private boolean waitForState(String targetService, CircuitBreakerState target, long maxWaitMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + maxWaitMs;
        while (System.currentTimeMillis() < deadline) {
            CircuitBreakerState current = circuitBreakerService.getState(targetService);
            if (current == target) return true;
            Thread.sleep(POLL_INTERVAL_MS);
        }
        return false;
    }

    // ── Query methods ──

    public List<ChaosExperiment> getRecentExperiments() {
        return experimentRepo.findTop20ByOrderByCreatedAtDesc();
    }

    public ChaosExperiment getExperiment(Long id) {
        return experimentRepo.findById(id).orElse(null);
    }
}
