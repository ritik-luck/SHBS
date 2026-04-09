package com.selfhealing.backend.repository;

import com.selfhealing.backend.model.FailureMetric;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

public interface FailureMetricRepository extends JpaRepository<FailureMetric, Long> {

    @Query("SELECT f.serviceName, COUNT(f) FROM FailureMetric f GROUP BY f.serviceName")
    List<Object[]> countFailuresByService();

    @Query("SELECT f.failureType, COUNT(f) FROM FailureMetric f GROUP BY f.failureType")
    List<Object[]> countFailuresByType();

    @Query("SELECT f.severity, COUNT(f) FROM FailureMetric f GROUP BY f.severity")
    List<Object[]> countFailuresBySeverity();

    @Query("SELECT f.timestamp, COUNT(f) FROM FailureMetric f WHERE f.timestamp >= :start GROUP BY f.timestamp ORDER BY f.timestamp")
    List<Object[]> countFailuresTimeline(LocalDateTime start);
}

