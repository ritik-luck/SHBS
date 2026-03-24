package com.selfhealing.backend.repository;

import com.selfhealing.backend.model.FailureMetric;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Objects;

public interface FailureMetricRepository extends JpaRepository<FailureMetric, Long> {

    @Query("SELECT f.serviceName, COUNT(f) FROM FailureMetric f GROUP BY f.serviceName")
    List<Object[]> countFailuresByService();
}