package com.selfhealing.backend.repository;

import com.selfhealing.backend.model.ChaosExperiment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChaosExperimentRepository extends JpaRepository<ChaosExperiment, Long> {

    List<ChaosExperiment> findTop20ByOrderByCreatedAtDesc();

    List<ChaosExperiment> findByStatus(String status);
}
