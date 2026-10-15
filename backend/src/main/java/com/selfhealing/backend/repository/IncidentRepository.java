package com.selfhealing.backend.repository;

import com.selfhealing.backend.model.Incident;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface IncidentRepository extends JpaRepository<Incident, Long> {
    List<Incident> findByState(String state);
    List<Incident> findByAffectedService(String service);
    List<Incident> findByStateNot(String state);
    List<Incident> findTop20ByOrderByCreatedAtDesc();
}
