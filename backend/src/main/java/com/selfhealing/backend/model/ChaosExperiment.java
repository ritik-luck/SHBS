package com.selfhealing.backend.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * ChaosExperiment — Records the result of an autonomous chaos engineering experiment.
 *
 * The AI Chaos Agent injects a failure, watches the system heal, and scores the result.
 * Each record represents one full inject → detect → heal → verify cycle.
 */
@Entity
@Table(name = "chaos_experiments")
public class ChaosExperiment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String experimentType; // timeout, database, network

    @Column(nullable = false)
    private String targetService;

    @Column(nullable = false)
    private String status; // RUNNING, PASSED, FAILED, ABORTED

    private LocalDateTime injectedAt;
    private LocalDateTime detectedAt;   // When circuit opened
    private LocalDateTime healedAt;     // When circuit closed again

    private Long circuitOpenedWithinMs;
    private Long circuitClosedWithinMs;
    private Long totalDurationMs;

    private Boolean healingSucceeded;

    /** 0-100 score. Higher = faster and more reliable self-healing. */
    private Integer resilienceScore;

    @Column(columnDefinition = "TEXT")
    private String aiAnalysis;

    @Column(columnDefinition = "TEXT")
    private String experimentLog; // Timestamped log of each step

    private LocalDateTime createdAt;

    public ChaosExperiment() {}

    public ChaosExperiment(String experimentType, String targetService) {
        this.experimentType = experimentType;
        this.targetService = targetService;
        this.status = "RUNNING";
        this.createdAt = LocalDateTime.now();
        this.experimentLog = "";
    }

    // ── Log helper ──
    public void appendLog(String entry) {
        this.experimentLog = (this.experimentLog == null ? "" : this.experimentLog)
                + "[" + LocalDateTime.now() + "] " + entry + "\n";
    }

    // ── Getters and Setters ──
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getExperimentType() { return experimentType; }
    public void setExperimentType(String experimentType) { this.experimentType = experimentType; }

    public String getTargetService() { return targetService; }
    public void setTargetService(String targetService) { this.targetService = targetService; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getInjectedAt() { return injectedAt; }
    public void setInjectedAt(LocalDateTime injectedAt) { this.injectedAt = injectedAt; }

    public LocalDateTime getDetectedAt() { return detectedAt; }
    public void setDetectedAt(LocalDateTime detectedAt) { this.detectedAt = detectedAt; }

    public LocalDateTime getHealedAt() { return healedAt; }
    public void setHealedAt(LocalDateTime healedAt) { this.healedAt = healedAt; }

    public Long getCircuitOpenedWithinMs() { return circuitOpenedWithinMs; }
    public void setCircuitOpenedWithinMs(Long circuitOpenedWithinMs) { this.circuitOpenedWithinMs = circuitOpenedWithinMs; }

    public Long getCircuitClosedWithinMs() { return circuitClosedWithinMs; }
    public void setCircuitClosedWithinMs(Long circuitClosedWithinMs) { this.circuitClosedWithinMs = circuitClosedWithinMs; }

    public Long getTotalDurationMs() { return totalDurationMs; }
    public void setTotalDurationMs(Long totalDurationMs) { this.totalDurationMs = totalDurationMs; }

    public Boolean getHealingSucceeded() { return healingSucceeded; }
    public void setHealingSucceeded(Boolean healingSucceeded) { this.healingSucceeded = healingSucceeded; }

    public Integer getResilienceScore() { return resilienceScore; }
    public void setResilienceScore(Integer resilienceScore) { this.resilienceScore = resilienceScore; }

    public String getAiAnalysis() { return aiAnalysis; }
    public void setAiAnalysis(String aiAnalysis) { this.aiAnalysis = aiAnalysis; }

    public String getExperimentLog() { return experimentLog; }
    public void setExperimentLog(String experimentLog) { this.experimentLog = experimentLog; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
