package com.selfhealing.backend.repository;

import com.selfhealing.backend.model.HealthMetric;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HealthMetricRepository extends JpaRepository<HealthMetric, Long> {
}
