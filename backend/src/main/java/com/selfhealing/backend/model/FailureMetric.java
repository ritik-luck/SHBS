package com.selfhealing.backend.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity
public class FailureMetric {

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

    public FailureMetric(int failureCount, String serviceName, String failureType) {
        this.failureCount = failureCount;
        this.serviceName = serviceName;
        this.failureType = failureType;
        this.timestamp = java.time.LocalDateTime.now();
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