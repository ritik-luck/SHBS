package com.selfhealing.backend.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
public class FailureMetric {

    @Enumerated(EnumType.STRING)
    private FailureCategory category;

    @Enumerated(EnumType.STRING)
    private FailureSeverity severity;

    private boolean resolved;

    private String endpoint;

    private String stackTrace;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private int failureCount;

    // name of the service/component where failure happened
    private String serviceName;

    // type of failure (timeout, database_error, network_error, etc.)
    private String failureType;

    // timestamp when the failure occurred
    private java.time.LocalDateTime timestamp;

    public FailureMetric() {}

    public FailureMetric(String serviceName, String type,
                         FailureCategory category,
                         FailureSeverity severity) {

        this.serviceName = serviceName;
        this.failureType = type;
        this.category = category;
        this.severity = severity;
        this.timestamp = LocalDateTime.now();
        this.resolved = false;
    }

    public Long getId() {
        return id;
    }

    public int getFailureCount() {
        return failureCount;
    }

    public void setFailureCount(int failureCount) {
        this.failureCount = failureCount;
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public String getFailureType() {
        return failureType;
    }

    public void setFailureType(String failureType) {
        this.failureType = failureType;
    }

    public java.time.LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(java.time.LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }
}