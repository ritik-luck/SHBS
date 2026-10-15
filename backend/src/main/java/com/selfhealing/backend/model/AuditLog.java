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
    
    // Rich Compliance Metadata
    private String actor;
    private String requestId;
    private String ipAddress;
    private String targetResource;
    private String reason;
    @Column(columnDefinition = "TEXT")
    private String beforeAfterState; // JSON representing before/after

    public AuditLog() {}

    public AuditLog(String actionType, String details, String triggeredBy, String result) {
        this.timestamp = LocalDateTime.now();
        this.actionType = actionType;
        this.details = details;
        this.triggeredBy = triggeredBy;
        this.result = result;
    }
    
    // Extended Constructor for full compliance logging
    public AuditLog(String actionType, String details, String triggeredBy, String result,
                    String actor, String requestId, String ipAddress, 
                    String targetResource, String reason, String beforeAfterState) {
        this.timestamp = LocalDateTime.now();
        this.actionType = actionType;
        this.details = details;
        this.triggeredBy = triggeredBy;
        this.result = result;
        this.actor = actor;
        this.requestId = requestId;
        this.ipAddress = ipAddress;
        this.targetResource = targetResource;
        this.reason = reason;
        this.beforeAfterState = beforeAfterState;
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

    public String getActor() { return actor; }
    public void setActor(String actor) { this.actor = actor; }

    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }

    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }

    public String getTargetResource() { return targetResource; }
    public void setTargetResource(String targetResource) { this.targetResource = targetResource; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public String getBeforeAfterState() { return beforeAfterState; }
    public void setBeforeAfterState(String beforeAfterState) { this.beforeAfterState = beforeAfterState; }
}