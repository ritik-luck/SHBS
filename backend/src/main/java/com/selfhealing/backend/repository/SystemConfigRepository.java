package com.selfhealing.backend.repository;

import com.selfhealing.backend.model.SystemConfig;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SystemConfigRepository extends JpaRepository<SystemConfig, String> {
}