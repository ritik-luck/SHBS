package com.selfhealing.backend.repository;

import com.selfhealing.backend.model.RecoveryMetric;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecoveryMetricRepository extends JpaRepository<RecoveryMetric, Long> {
}