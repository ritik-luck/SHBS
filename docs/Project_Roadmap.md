# 🛡️ Self-Healing Backend System — Step-by-Step Project Roadmap

> A complete, step-by-step guide to building the **best** Self-Healing Backend System,
> derived from the [UML Use Case Diagram](./uml.png) and aligned with the [SRS](./SRS.md) and [DFD](./dfd_Overview.md).

---

## Current State Summary

| Component | Status | Files |
|---|---|---|
| API Processing | ✅ Basic | `HelloController`, `GlobalExceptionHandler` |
| Failure Monitoring | ✅ Basic | `FailureMetric` model, `FailureMetricService`, `FailureMetricRepository` |
| Circuit Breaker | ✅ Working | `CircuitBreakerService`, `CircuitBreakerState` |
| Recovery Scheduler | ✅ Basic | `RecoveryScheduler` (1s fixed-rate) |
| Admin Dashboard | ✅ Basic | `AdminController`, React `Dashboard.js` |
| WebSocket Alerts | ✅ Basic | STOMP over WebSocket |
| Rate Limiting | ❌ Missing | Not implemented |
| Audit Logging | ❌ Missing | Not implemented |
| Failure Classification | ❌ Missing | Only "timeout" type hardcoded |
| Degraded Responses | ❌ Missing | Not implemented |
| Notification Service | ❌ Missing | Not implemented |
| Configurable Thresholds | ❌ Missing | Hardcoded values |

---

## Step-by-Step Roadmap

---

### Phase 1 — Strengthen the Core Backend Architecture

> **Goal**: Make the API Processing subsystem production-grade.

#### Step 1.1: Add Rate Limiting (from UML: "Apply Rate Limiting")

The UML shows rate limiting as an `<<include>>` of Process Request. Currently missing.

- **What to do**: Create a `RateLimitingFilter` or use Spring's `@RateLimiter` with Resilience4j
- **Where**: New file `filter/RateLimitingFilter.java`
- **How**:
  1. Add `resilience4j-ratelimiter` dependency to `pom.xml`
  2. Create a servlet filter that intercepts all incoming API requests
  3. Configure limits per endpoint (e.g., 100 requests/minute)
  4. Return HTTP 429 (Too Many Requests) when limit is exceeded
  5. Log rate-limited requests to failure metrics

#### Step 1.2: Proper Request Logging (from UML: "Log Request")

The UML shows logging as an `<<include>>` of Process Request. Currently only basic `logger.info`.

- **What to do**: Create an AOP-based request/response logging interceptor
- **Where**: New file `aspect/RequestLoggingAspect.java`
- **How**:
  1. Add `spring-boot-starter-aop` dependency
  2. Create `@Aspect` that logs: method, URI, params, response time, status code
  3. Use SLF4J with structured JSON logging (add `logback-classic` config)
  4. Store request logs in a `RequestLog` table for auditing

#### Step 1.3: Replace Random Failure with Real Detection (from UML: "Detect Failure")

Currently `HelloController` uses `Math.random() > 0.5` to simulate failures. This should be a deliberate simulation mechanism, not random.

- **What to do**: Create a `FailureSimulationService` with configurable failure types
- **Where**: New file `service/FailureSimulationService.java`
- **How**:
  1. Support different failure types: `timeout`, `exception`, `dependency_failure`, `memory_pressure`
  2. Accept failure type and target service as parameters via API
  3. Make `/simulate-failure` accept `POST` with body `{ "type": "timeout", "service": "auth-service" }`
  4. Remove hardcoded `Math.random()` from `HelloController`

---

### Phase 2 — Build the Failure Monitoring Subsystem

> **Goal**: Fully implement all use cases under the Failure Monitoring subsystem (pink section in UML).

#### Step 2.1: Failure Classification (from UML: "Classify Failure")

Currently all failures are stored as generic entries. The UML requires classification.

- **What to do**: Create a `FailureClassifier` that categorises failures by type and severity
- **Where**: New file `service/FailureClassifierService.java`
- **How**:
  1. Define failure categories as an enum: `TIMEOUT`, `EXCEPTION`, `DEPENDENCY`, `NETWORK`, `DATABASE`
  2. Define severity levels: `LOW`, `MEDIUM`, `HIGH`, `CRITICAL`
  3. Add `severity` and `category` columns to `FailureMetric` entity
  4. Auto-classify based on exception type, response time thresholds, and failure frequency
  5. A failure is `CRITICAL` if >= 5 of the same type occur within 1 minute

#### Step 2.2: Enhanced Failure Metrics Storage (from UML: "Store Failure Metrics")

- **What to do**: Enrich the `FailureMetric` model with more data points
- **Where**: Modify `model/FailureMetric.java`
- **How**:
  1. Add fields: `severity`, `category`, `resolved` (boolean), `resolvedAt`, `healingAction`, `stackTrace` (text), `endpoint`
  2. Add a `FailureMetricRepository` query to get failures grouped by timeWindow (last 1min, 5min, 1hr)
  3. Add `@Query` methods: `findByServiceNameAndTimestampAfter()`, `countByCategoryAndSeverity()`

#### Step 2.3: System Health Tracking (from UML: "Update System Health")

Currently `SystemHealthService.getHealthStatus()` just returns "UP". This should reflect actual system state.

- **What to do**: Make health status dynamic based on failure metrics and circuit breaker state
- **Where**: Enhance `service/SystemHealthService.java`
- **How**:
  1. Calculate health score (0-100) based on: failure count in last 5 minutes, circuit breaker state, response times
  2. Health states: `HEALTHY` (80-100), `DEGRADED` (40-79), `UNHEALTHY` (0-39)
  3. Expose via `/system/health` endpoint as structured JSON
  4. Push health updates via WebSocket to dashboard

#### Step 2.4: Audit Trail for Healing Actions (from UML: "Audit Healing Actions")

- **What to do**: Create an `AuditLog` entity to record every healing action the system takes
- **Where**: New files `model/AuditLog.java`, `repository/AuditLogRepository.java`, `service/AuditService.java`
- **How**:
  1. AuditLog fields: `id`, `timestamp`, `actionType` (CIRCUIT_OPENED, FALLBACK_SERVED, RECOVERY_ATTEMPTED, CIRCUIT_RESET), `details`, `triggeredBy` (SYSTEM/ADMIN), `result` (SUCCESS/FAILURE)
  2. Log every circuit breaker state change, every recovery attempt, every admin action
  3. Expose via `GET /admin/audit-logs` with pagination

---

### Phase 3 — Upgrade the Self-Healing Decision Engine

> **Goal**: Make the system truly intelligent in choosing healing strategies (purple section in UML).

#### Step 3.1: Intelligent Healing Strategy (from UML: "Decide Healing Strategy")

Currently the circuit breaker just opens/closes. The UML requires a proper decision engine.

- **What to do**: Create a `HealingStrategyEngine` that evaluates system state and chooses from multiple strategies
- **Where**: New file `service/HealingStrategyEngine.java`
- **How**:
  1. Input: current health score, failure rate, failure categories, circuit breaker state
  2. Output: one of these strategies:
     - `DO_NOTHING` — system is healthy
     - `APPLY_CIRCUIT_BREAKER` — too many failures, block requests
     - `SERVE_DEGRADED` — serve cached/simplified responses
     - `SERVE_FALLBACK` — return predefined fallback responses
     - `TRIGGER_RECOVERY` — initiate recovery process
     - `ALERT_ADMIN` — send notification for manual intervention
  3. Use a rules-based approach with configurable thresholds
  4. Log every decision to audit trail

#### Step 3.2: Configurable Thresholds (from UML: "Configure Thresholds")

Currently thresholds are hardcoded (`FAILURE_THRESHOLD = 5`, `RECOVERY_TIMEOUT = 10000`).

- **What to do**: Make all thresholds configurable at runtime via admin API and stored in database
- **Where**: New files `model/SystemConfig.java`, `repository/SystemConfigRepository.java`, `service/ConfigService.java`
- **How**:
  1. Store thresholds in a `system_config` table: `configKey`, `configValue`, `description`, `updatedAt`
  2. Default configs: `circuit_breaker_threshold`, `recovery_timeout_ms`, `rate_limit_per_minute`, `health_check_interval`, `alert_threshold`
  3. Admin API: `GET /admin/config`, `PUT /admin/config/{key}`
  4. Use `@ConfigurationProperties` + database override pattern
  5. Hot-reload: apply config changes without restart

#### Step 3.3: Degraded Responses (from UML: "Serve Degraded Response")

- **What to do**: Implement a response cache and degraded mode
- **Where**: New file `service/DegradedResponseService.java`
- **How**:
  1. Cache the last successful response for each endpoint
  2. When circuit breaker is OPEN or system is DEGRADED, serve cached data with a `X-Degraded: true` header
  3. Include metadata: `{ "data": {...}, "degraded": true, "cachedAt": "...", "reason": "Circuit breaker active" }`

#### Step 3.4: Proper Fallback Responses (from UML: "Provide Fallback Response")

- **What to do**: Create configurable fallback responses per endpoint
- **Where**: New file `service/FallbackService.java`
- **How**:
  1. Store fallback responses in configuration (database or YAML)
  2. Return meaningful fallback data instead of just a string message
  3. Include appropriate HTTP status codes (503 Service Unavailable with Retry-After header)

---

### Phase 4 — Build the Recovery Management Subsystem

> **Goal**: Make automatic recovery intelligent and reliable (green section in UML).

#### Step 4.1: Enhanced Recovery Process (from UML: "Trigger Recovery Process", "Attempt System Recovery", "Restore Normal Operation")

Currently `RecoveryScheduler` just calls `tryRecovery()` every 1 second. This needs to be a proper recovery pipeline.

- **What to do**: Create a comprehensive `RecoveryService` with staged recovery
- **Where**: New file `service/RecoveryService.java`, modify `scheduler/RecoveryScheduler.java`
- **How**:
  1. **Stage 1 — Health Check**: Verify if dependent services are responsive
  2. **Stage 2 — Test Request**: Send a canary/probe request to check if the service can handle real traffic
  3. **Stage 3 — Gradual Restore**: Move circuit breaker to HALF_OPEN, allow limited traffic
  4. **Stage 4 — Full Restore**: If test requests succeed for N consecutive attempts, move to CLOSED
  5. Use exponential backoff instead of fixed 1-second interval
  6. Log each recovery stage to audit trail
  7. If recovery fails after M attempts, escalate to alert

#### Step 4.2: Recovery Metrics

- **What to do**: Track recovery success/failure rates
- **Where**: New model `RecoveryAttempt.java`
- **How**:
  1. Fields: `id`, `timestamp`, `stage`, `result` (SUCCESS/FAILURE), `durationMs`, `details`
  2. Track: total attempts, success rate, average recovery time
  3. Expose via `/metrics/recovery`

---

### Phase 5 — Complete the Admin Monitoring & Control Subsystem

> **Goal**: Give admins full visibility and control (yellow section in UML).

#### Step 5.1: View System Health Dashboard (from UML: "View System Health")

- **What to do**: Enhance the `/system/health` endpoint and React dashboard
- **Endpoints needed**:
  ```
  GET /system/health          → detailed health with score
  GET /system/health/history  → health score over time
  GET /system/state           → circuit breaker state (exists)
  ```

#### Step 5.2: View Failure Metrics (from UML: "View Failure Metrics")

- **What to do**: Expose rich failure analytics
- **Endpoints needed**:
  ```
  GET /metrics/failures                    → total count (exists)
  GET /metrics/failures/by-service         → grouped by service
  GET /metrics/failures/by-type            → grouped by failure type
  GET /metrics/failures/by-severity        → grouped by severity
  GET /metrics/failures/timeline?period=1h → time-series data
  ```

#### Step 5.3: Simulate Failure (from UML: "Simulate Failure")

- **What to do**: Create a proper failure simulation API
- **Endpoint**:
  ```
  POST /admin/simulate
  Body: { "type": "timeout", "service": "auth-service", "count": 5 }
  ```
- Allows admins to test the self-healing pipeline end-to-end

#### Step 5.4: View Audit Logs (from UML: "View Audit Logs")

- **Endpoints needed**:
  ```
  GET /admin/audit-logs?page=0&size=20         → paginated logs
  GET /admin/audit-logs/by-action/{actionType} → filter by action
  GET /admin/audit-logs/by-date?from=...&to=...→ date range filter
  ```

---

### Phase 6 — Alerting / Notification Service

> **Goal**: Implement the external Notification Service actor from the UML.

#### Step 6.1: Alert Generation (from UML: "Generate Alert")

- **What to do**: Create `AlertService` that triggers on critical conditions
- **Where**: New file `service/AlertService.java`
- **How**:
  1. Trigger conditions: circuit breaker opened, health score < 30, recovery failed, critical failure detected
  2. Alert levels: `INFO`, `WARNING`, `CRITICAL`
  3. Alert channels:
     - **WebSocket** (already exists, enhance it)
     - **Email** (via Spring Mail + SMTP)
     - **Slack webhook** (optional, impressive bonus)
  4. Alert cooldown: don't spam same alert within configurable window
  5. Alert history stored in database

#### Step 6.2: Email Notifications

- **What to do**: Add email notification support
- **Where**: New file `service/EmailNotificationService.java`
- **How**:
  1. Add `spring-boot-starter-mail` dependency
  2. Configure SMTP in `application.properties`
  3. Send formatted HTML emails for critical alerts
  4. Admin can configure recipient list via `/admin/config`

---

### Phase 7 — Upgrade the Frontend Dashboard

> **Goal**: Make the React dashboard a comprehensive admin panel.

#### Step 7.1: Dashboard Redesign

- **Current state**: Basic dark-themed page with one chart and three buttons
- **Target**: Professional monitoring dashboard with multiple panels

- **Panels to add**:
  1. **System Health Card** — Health score gauge (0-100) with color coding (green/yellow/red)
  2. **Circuit Breaker Status** — Visual state indicator (CLOSED=green, HALF_OPEN=yellow, OPEN=red)
  3. **Failure Timeline Chart** — Line chart of failures over time (use existing ChartJS)
  4. **Failure Breakdown** — Pie/donut chart by failure type and severity
  5. **Service Health Table** — Per-service status with failure counts
  6. **Audit Log Feed** — Live-scrolling list of healing actions
  7. **Configuration Panel** — Edit thresholds with forms
  8. **Alert Banner** — Full-width notification for critical alerts (enhance existing)

#### Step 7.2: Real-Time Updates

- Use existing WebSocket/STOMP connection
- Add subscriptions for: `/topic/health`, `/topic/circuit-breaker`, `/topic/audit`
- All panels auto-update without page refresh

---

### Phase 8 — Testing & Quality

> **Goal**: Ensure the system is robust and demonstrable.

#### Step 8.1: Unit Tests

- Test `CircuitBreakerService` state transitions
- Test `FailureClassifierService` classification logic
- Test `HealingStrategyEngine` decision-making
- Test `RecoveryService` staged recovery

#### Step 8.2: Integration Tests

- Test end-to-end flow: simulate failure → detect → classify → heal → recover
- Test rate limiting blocks excess requests
- Test circuit breaker opens after threshold
- Test recovery restores normal operation

#### Step 8.3: Demo Scenario Script

Create a scripted demonstration that shows the complete self-healing lifecycle:

```
1. Start system → All indicators GREEN
2. Trigger 3 failures → System status changes to DEGRADED (yellow)
3. Trigger 2 more failures → Circuit breaker OPENS (red), alerts fire
4. Observe fallback/degraded responses being served
5. Wait for recovery scheduler → circuit breaker moves to HALF_OPEN
6. Observe system auto-recovery → all indicators return to GREEN
7. Admin views audit logs showing entire healing timeline
```

---

### Phase 9 — Documentation & Polish

> **Goal**: Academic-grade documentation for submission/presentation.

#### Step 9.1: Complete Documentation Set

| Document | Status | What to do |
|---|---|---|
| SRS | ✅ Done | Update with new features from this roadmap |
| UML Use Case Diagram | ✅ Done | Already complete |
| UML Overview | ✅ Done | Already complete |
| DFD Level 0 | ✅ Done | Already complete |
| DFD Level 1 | ✅ Done | Already complete |
| **Class Diagram** | ❌ Missing | Create showing all entities, services, and relationships |
| **Sequence Diagrams** | ❌ Missing | Create for: failure detection flow, healing decision flow, recovery flow |
| **Activity Diagram** | ❌ Missing | Create for the full self-healing lifecycle |
| **API Documentation** | ❌ Missing | Create OpenAPI/Swagger docs |
| **Database Schema** | ❌ Missing | Document all tables and relationships |
| **Deployment Guide** | ❌ Missing | Step-by-step setup instructions |

#### Step 9.2: README

- Project overview with architecture diagram
- Tech stack
- Setup instructions
- API reference
- Screenshots of dashboard
- Demo video link

---

## Suggested Dependency Additions for `pom.xml`

```xml
<!-- Rate Limiting & Circuit Breaker (production-grade) -->
<dependency>
    <groupId>io.github.resilience4j</groupId>
    <artifactId>resilience4j-spring-boot3</artifactId>
</dependency>

<!-- AOP for Request Logging -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-aop</artifactId>
</dependency>

<!-- Email Notifications -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-mail</artifactId>
</dependency>

<!-- API Documentation -->
<dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
    <version>2.8.6</version>
</dependency>

<!-- Actuator (built-in health checks) -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
```

---

## Priority Order (What to Build First)

| Priority | Phase | Impact | Effort |
|---|---|---|---|
| 🔴 P0 | Phase 2 (Failure Monitoring) | Core requirement from UML | Medium |
| 🔴 P0 | Phase 3 (Healing Engine) | Core intelligence — makes project standout | High |
| 🟡 P1 | Phase 4 (Recovery) | Completes the self-healing loop | Medium |
| 🟡 P1 | Phase 1 (Rate Limiting + Logging) | Fills UML gaps | Low |
| 🟢 P2 | Phase 5 (Admin APIs) | Needed for dashboard | Medium |
| 🟢 P2 | Phase 7 (Frontend Upgrade) | Visual impact for demo | Medium |
| 🟣 P3 | Phase 6 (Notifications) | Bonus feature | Low |
| 🟣 P3 | Phase 8 (Testing) | Quality assurance | Medium |
| ⚪ P4 | Phase 9 (Docs) | Final polish | Low |

---

## Final Architecture (After All Phases)

```
┌─────────────────────────────────────────────────────────────────┐
│                   SELF-HEALING BACKEND SYSTEM                   │
│                                                                 │
│  ┌───────────┐    ┌────────────────┐    ┌──────────────────┐   │
│  │   API     │───▶│   Failure      │───▶│  Self-Healing    │   │
│  │ Processing│    │   Monitoring   │    │  Decision Engine │   │
│  │           │    │                │    │                  │   │
│  │ • Routes  │    │ • Classify     │    │ • Strategy       │   │
│  │ • Rate    │    │ • Store        │    │ • Circuit Breaker│   │
│  │   Limit   │    │ • Health Score │    │ • Fallback       │   │
│  │ • Logging │    │ • Audit        │    │ • Degraded Mode  │   │
│  └───────────┘    └────────────────┘    └──────┬───────────┘   │
│                                                │               │
│  ┌───────────┐    ┌────────────────┐           │               │
│  │  Admin    │    │   Recovery     │◀──────────┘               │
│  │ Dashboard │    │   Management   │                           │
│  │           │    │                │                           │
│  │ • Health  │    │ • Staged       │     ┌──────────────┐     │
│  │ • Metrics │    │   Recovery     │────▶│ Notification │     │
│  │ • Config  │    │ • Exponential  │     │ Service      │     │
│  │ • Audit   │    │   Backoff      │     │ (Email/WS)   │     │
│  │ • Simulate│    │ • Auto-restore │     └──────────────┘     │
│  └───────────┘    └────────────────┘                           │
│                                                                 │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │                    MySQL Database                        │  │
│  │  failure_metrics │ audit_logs │ system_config │ recovery │  │
│  └──────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
```

---

> **💡 Tip**: Implementing Phases 2 + 3 + 4 alone will put your project far above average. The self-healing loop (Detect → Classify → Decide → Heal → Recover) is what makes this project unique and impressive.
