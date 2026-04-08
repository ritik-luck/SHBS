package com.selfhealing.backend.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
public class RecoveryMetric {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private LocalDateTime timestamp;

    private String stage; // HEALTH_CHECK, TEST_REQUEST, FULL_RECOVERY
    private String result; // SUCCESS / FAILURE

    private long durationMs;

    private String details;

    public RecoveryMetric() {
    }

    public RecoveryMetric(String stage, String result, long durationMs, String details) {
        this.timestamp = LocalDateTime.now();
        this.stage = stage;
        this.result = result;
        this.durationMs = durationMs;
        this.details = details;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }

    public String getStage() {
        return stage;
    }

    public void setStage(String stage) {
        this.stage = stage;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(long durationMs) {
        this.durationMs = durationMs;
    }

    public String getDetails() {
        return details;
    }

    public void setDetails(String details) {
        this.details = details;
    }
}