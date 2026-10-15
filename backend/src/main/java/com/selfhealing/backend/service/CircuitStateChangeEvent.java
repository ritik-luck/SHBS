package com.selfhealing.backend.service;

/**
 * CircuitStateChangeEvent — Published when a circuit breaker transitions state.
 * Used to decouple CircuitBreakerService from IncidentService (avoids circular deps).
 */
public record CircuitStateChangeEvent(
    String serviceName,
    String fromState,
    String toState,
    long failureCount
) {}
