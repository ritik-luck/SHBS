package com.selfhealing.backend.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Incident — Represents a structured incident lifecycle.
 *
 * States: OPEN → ACKNOWLEDGED → MITIGATING → RESOLVED → POSTMORTEM_REQUIRED
 * Auto-created when circuit breaker opens or SLO burns.
 */
@Entity
@Table(name = "incident")
public class Incident {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;

    @Column(nullable = false)
    private String state; // OPEN, ACKNOWLEDGED, MITIGATING, RESOLVED, POSTMORTEM_REQUIRED

    private String severity; // SEV1 (Critical), SEV2 (High), SEV3 (Medium), SEV4 (Low)
    private String affectedService;
    private String owner;
    private String escalationPolicy; // e.g., "SRE_ONCALL", "DBA_TEAM"
    private Integer ackTargetMinutes; // e.g., 5, 15, 60
    private String suggestedRunbook;

    @Column(columnDefinition = "TEXT")
    private String timeline; // JSON array of timeline events

    @Column(columnDefinition = "TEXT")
    private String resolutionNotes;

    private LocalDateTime createdAt;
    private LocalDateTime acknowledgedAt;
    private LocalDateTime resolvedAt;
    private LocalDateTime updatedAt;

    public Incident() {}

    public Incident(String title, String severity, String affectedService) {
        this.title = title;
        this.severity = severity;
        this.affectedService = affectedService;
        this.state = "OPEN";
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        this.timeline = "[{\"event\":\"INCIDENT_CREATED\",\"timestamp\":\"" + LocalDateTime.now() + "\"}]";
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getState() { return state; }
    public void setState(String state) { this.state = state; this.updatedAt = LocalDateTime.now(); }

    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }

    public String getAffectedService() { return affectedService; }
    public void setAffectedService(String affectedService) { this.affectedService = affectedService; }

    public String getOwner() { return owner; }
    public void setOwner(String owner) { this.owner = owner; }

    public String getEscalationPolicy() { return escalationPolicy; }
    public void setEscalationPolicy(String escalationPolicy) { this.escalationPolicy = escalationPolicy; }

    public Integer getAckTargetMinutes() { return ackTargetMinutes; }
    public void setAckTargetMinutes(Integer ackTargetMinutes) { this.ackTargetMinutes = ackTargetMinutes; }

    public String getSuggestedRunbook() { return suggestedRunbook; }
    public void setSuggestedRunbook(String suggestedRunbook) { this.suggestedRunbook = suggestedRunbook; }

    public String getTimeline() { return timeline; }
    public void setTimeline(String timeline) { this.timeline = timeline; }

    public String getResolutionNotes() { return resolutionNotes; }
    public void setResolutionNotes(String resolutionNotes) { this.resolutionNotes = resolutionNotes; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getAcknowledgedAt() { return acknowledgedAt; }
    public void setAcknowledgedAt(LocalDateTime acknowledgedAt) { this.acknowledgedAt = acknowledgedAt; }

    public LocalDateTime getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(LocalDateTime resolvedAt) { this.resolvedAt = resolvedAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
