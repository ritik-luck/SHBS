package com.selfhealing.backend.repository;

import com.selfhealing.backend.model.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    Page<AuditLog> findByActionType(String actionType, Pageable pageable);

    List<AuditLog> findByTimestampBetween(java.time.LocalDateTime from, java.time.LocalDateTime to);
}
//JPA is a specification, Hibernate is its implementation, and Spring Data JPA is an abstraction layer that simplifies database operations using JPA.