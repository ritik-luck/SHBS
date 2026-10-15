package com.selfhealing.backend.controller;

import com.selfhealing.backend.model.Incident;
import com.selfhealing.backend.service.IncidentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * IncidentController — REST API for incident management.
 */
@RestController
@RequestMapping("/admin/incidents")
public class IncidentController {

    private final IncidentService incidentService;

    public IncidentController(IncidentService incidentService) {
        this.incidentService = incidentService;
    }

    @GetMapping
    public ResponseEntity<List<Incident>> getRecentIncidents() {
        return ResponseEntity.ok(incidentService.getRecentIncidents());
    }

    @GetMapping("/active")
    public ResponseEntity<List<Incident>> getActiveIncidents() {
        return ResponseEntity.ok(incidentService.getActiveIncidents());
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getIncident(@PathVariable Long id) {
        Incident incident = incidentService.getIncident(id);
        if (incident == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(incident);
    }

    @GetMapping("/{id}/timeline")
    public ResponseEntity<?> getTimeline(@PathVariable Long id) {
        Incident incident = incidentService.getIncident(id);
        if (incident == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(Map.of(
                "incidentId", id,
                "timeline", incident.getTimeline()
        ));
    }

    @PutMapping("/{id}/acknowledge")
    public ResponseEntity<Incident> acknowledge(@PathVariable Long id,
                                                 @RequestParam String owner) {
        return ResponseEntity.ok(incidentService.acknowledge(id, owner));
    }

    @PutMapping("/{id}/mitigate")
    public ResponseEntity<Incident> mitigate(@PathVariable Long id,
                                              @RequestParam String notes) {
        return ResponseEntity.ok(incidentService.mitigate(id, notes));
    }

    @PutMapping("/{id}/resolve")
    public ResponseEntity<Incident> resolve(@PathVariable Long id,
                                             @RequestParam String notes) {
        return ResponseEntity.ok(incidentService.resolve(id, notes));
    }
}
