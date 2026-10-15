package com.selfhealing.backend.service;

import com.selfhealing.backend.model.Incident;
import com.selfhealing.backend.repository.IncidentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * IncidentService — Manages the full incident lifecycle.
 *
 * Auto-creates incidents when:
 * - Circuit breaker opens for a service
 * - SLO burn rate exceeds threshold
 *
 * Auto-resolves incidents when:
 * - Circuit breaker closes (service recovered)
 */
@Service
public class IncidentService {

    private static final Logger log = LoggerFactory.getLogger(IncidentService.class);

    private final IncidentRepository incidentRepository;
    private final AuditService auditService;
    private final PostmortemAiService aiService;
    private final RunbookGeneratorService runbookGeneratorService;

    public IncidentService(IncidentRepository incidentRepository,
                           AuditService auditService,
                           @org.springframework.context.annotation.Lazy PostmortemAiService aiService,
                           @org.springframework.context.annotation.Lazy RunbookGeneratorService runbookGeneratorService) {
        this.incidentRepository = incidentRepository;
        this.auditService = auditService;
        this.aiService = aiService;
        this.runbookGeneratorService = runbookGeneratorService;
    }

    /**
     * Auto-create an incident when a critical failure is detected.
     */
    public Incident createIncident(String title, String severity, String affectedService, String suggestedRunbook) {
        // Check if there's already an open incident for this service
        List<Incident> existing = incidentRepository.findByAffectedService(affectedService).stream()
                .filter(i -> !"RESOLVED".equals(i.getState()))
                .toList();

        if (!existing.isEmpty()) {
            log.info("Incident already open for service '{}' — appending to timeline", affectedService);
            Incident incident = existing.get(0);
            appendTimeline(incident, "ADDITIONAL_FAILURE: " + title);
            return incidentRepository.save(incident);
        }

        Incident incident = new Incident(title, severity, affectedService);
        incident.setSuggestedRunbook(suggestedRunbook);
        
        // Define default escalation policy based on severity
        if ("SEV1".equalsIgnoreCase(severity) || "CRITICAL".equalsIgnoreCase(severity)) {
            incident.setEscalationPolicy("SRE_ONCALL");
            incident.setAckTargetMinutes(5);
        } else {
            incident.setEscalationPolicy("DEV_TEAM");
            incident.setAckTargetMinutes(30);
        }
        
        incident = incidentRepository.save(incident);

        auditService.log("INCIDENT_CREATED",
                "Incident #" + incident.getId() + ": " + title,
                "SYSTEM", "SUCCESS");

        log.warn("🚨 Incident #{} created: {} [{}]", incident.getId(), title, severity);
        return incident;
    }

    /**
     * Acknowledge an incident — assigns an owner.
     */
    public Incident acknowledge(Long id, String owner) {
        Incident incident = incidentRepository.findById(id).orElseThrow();
        incident.setState("ACKNOWLEDGED");
        incident.setOwner(owner);
        incident.setAcknowledgedAt(LocalDateTime.now());
        appendTimeline(incident, "ACKNOWLEDGED by " + owner);

        auditService.log("INCIDENT_ACKNOWLEDGED",
                "Incident #" + id + " acknowledged by " + owner,
                owner, "SUCCESS");

        return incidentRepository.save(incident);
    }

    /**
     * Mark an incident as being actively mitigated.
     */
    public Incident mitigate(Long id, String notes) {
        Incident incident = incidentRepository.findById(id).orElseThrow();
        incident.setState("MITIGATING");
        appendTimeline(incident, "MITIGATING: " + notes);
        return incidentRepository.save(incident);
    }

    /**
     * Resolve an incident — requires human postmortem later.
     */
    public Incident resolve(Long id, String resolutionNotes) {
        Incident incident = incidentRepository.findById(id).orElseThrow();
        incident.setState("POSTMORTEM_REQUIRED");
        incident.setResolvedAt(LocalDateTime.now());
        incident.setResolutionNotes(resolutionNotes);
        appendTimeline(incident, "RESOLVED (PENDING POSTMORTEM): " + resolutionNotes);

        auditService.log("INCIDENT_RESOLVED",
                "Incident #" + id + " resolved, pending postmortem",
                incident.getOwner() != null ? incident.getOwner() : "SYSTEM", "SUCCESS");

        log.info("✅ Incident #{} resolved: {}", id, resolutionNotes);
        
        Incident savedIncident = incidentRepository.save(incident);
        
        // Trigger AI Postmortem generation asynchronously via RAG
        aiService.generatePostmortem(savedIncident.getId());
        
        // Trigger Dynamic Runbook Generation if this is a novel failure
        runbookGeneratorService.generateRunbookIfNeeded(savedIncident);
        
        return savedIncident;
    }

    /**
     * Complete human-approved postmortem.
     */
    public Incident submitPostmortem(Long id, String postmortemNotes, String reviewer) {
        Incident incident = incidentRepository.findById(id).orElseThrow();
        incident.setState("RESOLVED");
        
        String currentNotes = incident.getResolutionNotes() != null ? incident.getResolutionNotes() : "";
        incident.setResolutionNotes(currentNotes + "\n\n=== POSTMORTEM (" + reviewer + ") ===\n" + postmortemNotes);
        
        appendTimeline(incident, "POSTMORTEM_APPROVED by " + reviewer);

        auditService.log("INCIDENT_POSTMORTEM_APPROVED",
                "Incident #" + id + " postmortem approved",
                reviewer, "SUCCESS");

        return incidentRepository.save(incident);
    }

    /**
     * Auto-resolve all open incidents for a service (called when circuit closes).
     */
    public void autoResolveForService(String serviceName) {
        List<Incident> openIncidents = incidentRepository.findByAffectedService(serviceName).stream()
                .filter(i -> !"RESOLVED".equals(i.getState()))
                .toList();

        for (Incident incident : openIncidents) {
            resolve(incident.getId(), "Auto-resolved: service '" + serviceName + "' recovered");
        }
    }

    public List<Incident> getActiveIncidents() {
        return incidentRepository.findByStateNot("RESOLVED");
    }

    public List<Incident> getRecentIncidents() {
        return incidentRepository.findTop20ByOrderByCreatedAtDesc();
    }

    public Incident getIncident(Long id) {
        return incidentRepository.findById(id).orElse(null);
    }

    private void appendTimeline(Incident incident, String event) {
        String existing = incident.getTimeline();
        String newEntry = "{\"event\":\"" + event + "\",\"timestamp\":\"" + LocalDateTime.now() + "\"}";
        if (existing != null && existing.endsWith("]")) {
            incident.setTimeline(existing.substring(0, existing.length() - 1) + "," + newEntry + "]");
        } else {
            incident.setTimeline("[" + newEntry + "]");
        }
    }
}
