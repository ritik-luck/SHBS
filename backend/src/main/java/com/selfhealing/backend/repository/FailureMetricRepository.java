package com.selfhealing.backend.repository;

import com.selfhealing.backend.model.FailureMetric;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FailureMetricRepository extends JpaRepository<FailureMetric, Long> {

}