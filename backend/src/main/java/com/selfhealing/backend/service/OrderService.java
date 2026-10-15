package com.selfhealing.backend.service;

import com.selfhealing.backend.model.Order;
import com.selfhealing.backend.repository.OrderRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * OrderService — Core business logic for order processing.
 *
 * Implements a 3-step pipeline with independent circuit breakers:
 *   1. Inventory Check   (circuit: "inventory-service")  — REQUIRED: blocks order if down
 *   2. Payment Processing (circuit: "payment-service")   — DEFERRABLE: order goes to PAYMENT_PENDING if down
 *   3. Notification       (circuit: "notification-service") — OPTIONAL: fire-and-forget, order still succeeds
 *
 * This demonstrates cascading failure prevention:
 * If Payment goes down, Inventory and Notification still work.
 * If Notification goes down, orders still process normally.
 */
@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private static final String INVENTORY_SERVICE = "inventory-service";
    private static final String PAYMENT_SERVICE = "payment-service";
    private static final String NOTIFICATION_SERVICE = "notification-service";

    private static final String IDEMPOTENCY_PREFIX = "idempotency:order:";
    private static final Duration IDEMPOTENCY_TTL = Duration.ofHours(24);

    private final OrderRepository orderRepository;
    private final InventoryService inventoryService;
    private final PaymentService paymentService;
    private final NotificationService notificationService;
    private final CircuitBreakerService circuitBreakerService;
    private final AuditService auditService;
    private final FeatureFlagService featureFlagService;
    private final DegradedResponseService degradedResponseService;
    private final StringRedisTemplate redis;
    private final Counter ordersCreated;
    private final Counter ordersFailed;

    public OrderService(OrderRepository orderRepository,
                        InventoryService inventoryService,
                        PaymentService paymentService,
                        NotificationService notificationService,
                        CircuitBreakerService circuitBreakerService,
                        AuditService auditService,
                        FeatureFlagService featureFlagService,
                        DegradedResponseService degradedResponseService,
                        StringRedisTemplate redis,
                        MeterRegistry meterRegistry) {
        this.orderRepository = orderRepository;
        this.inventoryService = inventoryService;
        this.paymentService = paymentService;
        this.notificationService = notificationService;
        this.circuitBreakerService = circuitBreakerService;
        this.auditService = auditService;
        this.featureFlagService = featureFlagService;
        this.degradedResponseService = degradedResponseService;
        this.redis = redis;

        this.ordersCreated = Counter.builder("orders_created_total")
                .description("Total orders created successfully")
                .register(meterRegistry);
        this.ordersFailed = Counter.builder("orders_failed_total")
                .description("Total orders that failed")
                .register(meterRegistry);
    }

    /**
     * Create a new order with idempotency enforcement and 3-step circuit-broken pipeline.
     */
    public Order createOrder(String product, int quantity, String idempotencyKey) {
        log.info("Creating order: product={}, quantity={}, idempotencyKey={}", product, quantity, idempotencyKey);

        // ── Step 0: Idempotency check ──
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            Order existing = checkIdempotency(idempotencyKey, product, quantity);
            if (existing != null) return existing;
        }

        // ══════════════════════════════════════════════════
        // STEP 1: INVENTORY CHECK (circuit: inventory-service)
        // Critical — if inventory is down, order FAILS immediately
        // ══════════════════════════════════════════════════
        if (!circuitBreakerService.allowRequest(INVENTORY_SERVICE)) {
            log.warn("⚡ Circuit OPEN for {} — order rejected", INVENTORY_SERVICE);
            ordersFailed.increment();
            return saveFailedOrder(product, quantity, idempotencyKey,
                    "Circuit breaker OPEN — inventory service unavailable");
        }

        double totalPrice;
        try {
            InventoryService.InventoryResult result;

            if (!featureFlagService.isEnabled("failing-feature")) {
                log.info("Feature 'failing-feature' disabled. Skipping inventory check.");
                if (degradedResponseService.hasCache("inventory:" + product)) {
                    Map<String, Object> fallback = degradedResponseService.getDegradedResponse("inventory:" + product, "Feature disabled");
                    @SuppressWarnings("unchecked")
                    Map<String, Object> data = (Map<String, Object>) fallback.get("data");
                    result = new InventoryService.InventoryResult(true, product, quantity,
                            Double.parseDouble(data.get("price").toString()));
                } else {
                    result = new InventoryService.InventoryResult(true, product, quantity, quantity * 100.0);
                }
            } else {
                result = inventoryService.checkAvailability(product, quantity);
                degradedResponseService.cacheResponse("inventory:" + product, Map.of("price", result.totalPrice()));
            }

            circuitBreakerService.recordSuccess(INVENTORY_SERVICE);
            totalPrice = result.totalPrice();

        } catch (InventoryService.InventoryUnavailableException e) {
            log.error("⚡ Inventory check FAILED: {}", e.getFailureType());
            circuitBreakerService.recordFailure(INVENTORY_SERVICE);
            ordersFailed.increment();
            return saveFailedOrder(product, quantity, idempotencyKey, e.getMessage());
        }

        // ══════════════════════════════════════════════════
        // STEP 2: PAYMENT PROCESSING (circuit: payment-service)
        // Deferrable — if payment is down, order goes to PAYMENT_PENDING
        // ══════════════════════════════════════════════════
        String orderStatus = "CONFIRMED";
        String transactionId = null;

        if (!circuitBreakerService.allowRequest(PAYMENT_SERVICE)) {
            log.warn("⚡ Circuit OPEN for {} — deferring payment", PAYMENT_SERVICE);
            orderStatus = "PAYMENT_PENDING";
        } else {
            try {
                // We create a temporary order ID for payment reference
                PaymentService.PaymentResult paymentResult = paymentService.processPayment(
                        System.currentTimeMillis(), totalPrice);
                circuitBreakerService.recordSuccess(PAYMENT_SERVICE);
                transactionId = paymentResult.transactionId();
                log.info("✅ Payment processed: txn={}", transactionId);

            } catch (PaymentService.PaymentFailedException e) {
                log.warn("⚡ Payment FAILED: {} — deferring order", e.getFailureType());
                circuitBreakerService.recordFailure(PAYMENT_SERVICE);
                orderStatus = "PAYMENT_PENDING";
            }
        }

        // ── Create and save the order ──
        Order order = new Order(product, quantity, totalPrice, orderStatus);
        order.setIdempotencyKey(idempotencyKey);
        order.setPayloadHash(computePayloadHash(product, quantity));
        order = orderRepository.save(order);
        ordersCreated.increment();
        cacheIdempotencyKey(idempotencyKey, order.getId());

        // ══════════════════════════════════════════════════
        // STEP 3: NOTIFICATION (circuit: notification-service)
        // Optional / Fire-and-forget — order succeeds regardless
        // ══════════════════════════════════════════════════
        if (!circuitBreakerService.allowRequest(NOTIFICATION_SERVICE)) {
            log.warn("⚡ Circuit OPEN for {} — skipping notification (non-critical)", NOTIFICATION_SERVICE);
        } else {
            try {
                notificationService.sendOrderConfirmation(order.getId(), product, "customer@example.com");
                circuitBreakerService.recordSuccess(NOTIFICATION_SERVICE);
            } catch (NotificationService.NotificationFailedException e) {
                log.warn("⚡ Notification FAILED: {} — order still valid", e.getFailureType());
                circuitBreakerService.recordFailure(NOTIFICATION_SERVICE);
                // Notification failure does NOT affect the order status
            }
        }

        log.info("Order #{} created: status={}, price=${}, payment={}",
                order.getId(), orderStatus, totalPrice,
                transactionId != null ? transactionId : "DEFERRED");
        return order;
    }

    /** Backward-compatible overload for callers without an idempotency key. */
    public Order createOrder(String product, int quantity) {
        return createOrder(product, quantity, null);
    }

    public Order getOrder(Long id) {
        return orderRepository.findById(id).orElse(null);
    }

    public List<Order> getRecentOrders() {
        return orderRepository.findTop20ByOrderByCreatedAtDesc();
    }

    public List<Order> getOrdersByStatus(String status) {
        return orderRepository.findByStatus(status);
    }

    // ── Helpers ──

    private Order checkIdempotency(String idempotencyKey, String product, int quantity) {
        String payloadHash = computePayloadHash(product, quantity);

        // Check Redis first (fast path)
        String cachedOrderId = null;
        try {
            cachedOrderId = redis.opsForValue().get(IDEMPOTENCY_PREFIX + idempotencyKey);
        } catch (Exception e) {
            log.warn("Redis unavailable for idempotency check: {}", e.getMessage());
        }

        if (cachedOrderId != null) {
            Optional<Order> existing = orderRepository.findById(Long.parseLong(cachedOrderId));
            if (existing.isPresent()) {
                Order cached = existing.get();
                if (!payloadHash.equals(cached.getPayloadHash())) {
                    throw new IdempotencyConflictException(
                            "Idempotency-Key '" + idempotencyKey + "' was already used with a different payload");
                }
                log.info("Returning cached order {} for idempotency key '{}'", cached.getId(), idempotencyKey);
                return cached;
            }
        }

        // Check DB (slow path)
        Optional<Order> dbExisting = orderRepository.findByIdempotencyKey(idempotencyKey);
        if (dbExisting.isPresent()) {
            Order cached = dbExisting.get();
            if (!payloadHash.equals(cached.getPayloadHash())) {
                throw new IdempotencyConflictException(
                        "Idempotency-Key '" + idempotencyKey + "' was already used with a different payload");
            }
            log.info("Returning DB-cached order {} for idempotency key '{}'", cached.getId(), idempotencyKey);
            return cached;
        }

        return null;
    }

    private Order saveFailedOrder(String product, int quantity, String idempotencyKey, String reason) {
        Order failedOrder = new Order(product, quantity, 0, "FAILED");
        failedOrder.setFailureReason(reason);
        failedOrder.setIdempotencyKey(idempotencyKey);
        failedOrder.setPayloadHash(computePayloadHash(product, quantity));
        Order saved = orderRepository.save(failedOrder);
        cacheIdempotencyKey(idempotencyKey, saved.getId());
        return saved;
    }

    private String computePayloadHash(String product, int quantity) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest((product + ":" + quantity).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash).substring(0, 16);
        } catch (Exception e) {
            return product + ":" + quantity;
        }
    }

    private void cacheIdempotencyKey(String key, Long orderId) {
        if (key == null || key.isBlank()) return;
        try {
            redis.opsForValue().set(IDEMPOTENCY_PREFIX + key, String.valueOf(orderId), IDEMPOTENCY_TTL);
        } catch (Exception e) {
            log.warn("Failed to cache idempotency key '{}': {}", key, e.getMessage());
        }
    }

    // ── Exception ──

    public static class IdempotencyConflictException extends RuntimeException {
        public IdempotencyConflictException(String message) {
            super(message);
        }
    }
}
