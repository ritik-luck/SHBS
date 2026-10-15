package com.selfhealing.backend.repository;

import com.selfhealing.backend.model.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {
    List<Order> findByStatus(String status);
    List<Order> findTop20ByOrderByCreatedAtDesc();
    Optional<Order> findByIdempotencyKey(String idempotencyKey);
    Page<Order> findByStatus(String status, Pageable pageable);
}
