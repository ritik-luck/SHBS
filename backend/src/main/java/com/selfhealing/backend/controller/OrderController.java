package com.selfhealing.backend.controller;

import com.selfhealing.backend.dto.OrderRequest;
import com.selfhealing.backend.model.Order;
import com.selfhealing.backend.service.OrderService;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * OrderController — REST API for the order business service.
 *
 * Enforces:
 * - Idempotency-Key header on POST
 * - Resilience4j rate limiting
 * - Consistent 429 responses with Retry-After
 */
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    @RateLimiter(name = "apiLimiter", fallbackMethod = "rateLimitFallback")
    public ResponseEntity<?> createOrder(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody OrderRequest request) {

        // Enforce Idempotency-Key for production safety
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Missing Idempotency-Key header",
                    "message", "POST /api/orders requires an Idempotency-Key header to prevent duplicate order creation",
                    "timestamp", System.currentTimeMillis()
            ));
        }

        try {
            Order order = orderService.createOrder(request.product(), request.quantity(), idempotencyKey);

            if ("FAILED".equals(order.getStatus())) {
                return ResponseEntity.status(503).body(order);
            }

            return ResponseEntity.ok(order);

        } catch (OrderService.IdempotencyConflictException e) {
            return ResponseEntity.status(409).body(Map.of(
                    "error", "Idempotency conflict",
                    "message", e.getMessage(),
                    "timestamp", System.currentTimeMillis()
            ));
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getOrder(@PathVariable Long id) {
        Order order = orderService.getOrder(id);
        if (order == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(order);
    }

    @GetMapping
    public ResponseEntity<List<Order>> getRecentOrders() {
        return ResponseEntity.ok(orderService.getRecentOrders());
    }

    @GetMapping("/status/{status}")
    public ResponseEntity<List<Order>> getByStatus(@PathVariable String status) {
        return ResponseEntity.ok(orderService.getOrdersByStatus(status));
    }

    public ResponseEntity<?> rateLimitFallback(String idempotencyKey, OrderRequest request, Throwable t) {
        return ResponseEntity
                .status(429)
                .header("Retry-After", "10")
                .body(Map.of(
                        "error", "Too many requests",
                        "message", "Order creation is currently rate limited to protect downstream services",
                        "retryAfterSeconds", 10,
                        "timestamp", System.currentTimeMillis()
                ));
    }
}
