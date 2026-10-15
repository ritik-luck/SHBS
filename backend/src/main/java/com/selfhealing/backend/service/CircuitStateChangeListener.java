package com.selfhealing.backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * CircuitStateChangeListener — Listens for circuit breaker state transitions
 * and automatically manages the incident lifecycle.
 *
 * OPEN → auto-creates an incident
 * CLOSED → auto-resolves all open incidents for the service
 */
@Component
public class CircuitStateChangeListener {

    private static final Logger log = LoggerFactory.getLogger(CircuitStateChangeListener.class);

    private final IncidentService incidentService;

    public CircuitStateChangeListener(IncidentService incidentService) {
        this.incidentService = incidentService;
    }

    @EventListener
    @Async
    public void onCircuitStateChange(CircuitStateChangeEvent event) {
        switch (event.toState()) {
            case "OPEN" -> {
                log.info("Circuit OPENED for '{}' — creating incident", event.serviceName());
                incidentService.createIncident(
                        "Circuit breaker opened for '" + event.serviceName() + "' (" + event.failureCount() + " failures)",
                        "CRITICAL",
                        event.serviceName(),
                        "runbook:circuit_breaker_open");
            }
            case "CLOSED" -> {
                log.info("Circuit CLOSED for '{}' — auto-resolving incidents", event.serviceName());
                incidentService.autoResolveForService(event.serviceName());
            }
            default -> {
                // HALF_OPEN — no incident action needed, but could append to timeline
                log.debug("Circuit state changed to {} for '{}'", event.toState(), event.serviceName());
            }
        }
    }
}
