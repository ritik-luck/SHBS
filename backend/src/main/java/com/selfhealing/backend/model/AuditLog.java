package com.selfhealing.backend.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private LocalDateTime timestamp;
    private String actionType;   // CIRCUIT_OPENED, FAILURE_RECORDED etc.
    private String details;
    private String triggeredBy;  // SYSTEM / ADMIN
    private String result;       // SUCCESS / FAILURE

    public AuditLog() {}

    public AuditLog(String actionType, String details, String triggeredBy, String result) {
        this.timestamp = LocalDateTime.now();
        this.actionType = actionType;
        this.details = details;
        this.triggeredBy = triggeredBy;
        this.result = result;
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

    public String getActionType() {
        return actionType;
    }

    public void setActionType(String actionType) {
        this.actionType = actionType;
    }

    public String getDetails() {
        return details;
    }

    public void setDetails(String details) {
        this.details = details;
    }

    public String getTriggeredBy() {
        return triggeredBy;
    }

    public void setTriggeredBy(String triggeredBy) {
        this.triggeredBy = triggeredBy;
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }
}