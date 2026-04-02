package com.selfhealing.backend.repository;

import com.selfhealing.backend.model.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
}

//JPA is a specification, Hibernate is its implementation, and Spring Data JPA is an abstraction layer that simplifies database operations using JPA.