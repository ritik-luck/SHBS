# 🗓️ ResilX — 1 Month Completion Roadmap

> **"Built to break. Engineered to recover."**
>
> Start: **March 25, 2026** → Deadline: **April 24, 2026**
>
> Current state: Basic backend (circuit breaker, failure metrics, recovery scheduler, React dashboard) already built.

---

## Week 1 (Mar 25 – Mar 31): Strengthen Backend Core

> Focus: Fill all UML gaps in API Processing + Failure Monitoring

| Day | What to Do | Hours | Deliverable |
|---|---|---|---|
| **Day 1** (Tue) | Add `spring-boot-starter-aop` + `actuator` to `pom.xml`. Create `RequestLoggingAspect.java` with `@Around` advice to log every API call (method, URI, duration, status). Add **Correlation ID** filter that generates UUID per request. | 3-4h | Every request logged with correlation ID |
| **Day 2** (Wed) | Create `RateLimitingFilter.java` using Token Bucket algorithm (in-memory). Return HTTP 429 when limit exceeded. Configure: 60 requests/min per IP. | 3h | Rate limiting working on all endpoints |
| **Day 3** (Thu) | Upgrade `FailureMetric` model — add `severity`, `category`, `endpoint`, `resolved`, `stackTrace` fields. Create `FailureClassifierService.java` that maps exception types to categories (TIMEOUT, EXCEPTION, DEPENDENCY, DATABASE) and severity (LOW, MEDIUM, HIGH, CRITICAL). | 3-4h | Failures classified and stored with rich metadata |
| **Day 4** (Fri) | Upgrade `SystemHealthService` — calculate dynamic health score (0-100) based on: failure count in last 5 min, circuit breaker state, classification of recent failures. Expose `GET /system/health` returning full JSON. | 3h | Dynamic health score endpoint |
| **Day 5** (Sat) | Create `AuditLog.java` entity + `AuditLogRepository` + `AuditService`. Log every circuit breaker state change, every recovery attempt, every admin action. Add `GET /admin/audit-logs` with pagination. | 3-4h | Audit trail recording all system actions |
| **Day 6** (Sun) | Create `FailureSimulationService.java` — accept POST with `{ "type": "timeout", "service": "auth-service", "count": 3 }`. Replace `Math.random()` in `HelloController`. Make `POST /admin/simulate` the proper chaos testing endpoint. | 2-3h | Proper failure simulation API |
| **Buffer** | Test everything works end-to-end. Fix bugs. | 2h | Week 1 stable |

> **📚 Learn this week**: Spring AOP, Token Bucket algorithm, Observer Pattern

---

## Week 2 (Apr 1 – Apr 7): Self-Healing Intelligence + Recovery

> Focus: Decision engine, strategy pattern, recovery pipeline

| Day | What to Do | Hours | Deliverable |
|---|---|---|---|
| **Day 7** (Mon) | Refactor `CircuitBreakerService` — make transitions explicit (State Machine pattern). Add event publishing: `circuitBreakerStateChanged` event via `ApplicationEventPublisher`. Log all transitions to audit trail. | 3h | Circuit breaker with proper state machine + events |
| **Day 8** (Tue) | Create `HealingStrategy` interface + implementations: `CircuitBreakerStrategy`, `FallbackStrategy`, `DegradedModeStrategy`. Create `HealingStrategyEngine` that picks strategy based on health score + failure rate. | 4h | Strategy Pattern–based healing engine |
| **Day 9** (Wed) | Create `SystemConfig.java` entity + `ConfigService`. Store thresholds in DB: `circuit_breaker_threshold`, `recovery_timeout_ms`, `rate_limit_per_minute`, `alert_threshold`. Admin APIs: `GET /admin/config`, `PUT /admin/config/{key}`. Replace all hardcoded values. | 3-4h | Runtime-configurable thresholds |
| **Day 10** (Thu) | Create `DegradedResponseService` — cache last successful response per endpoint. When circuit is OPEN, serve cached data with `X-Degraded: true` header. Create `FallbackService` — return meaningful 503 responses with `Retry-After` header. | 3h | Graceful degradation + fallback responses |
| **Day 11** (Fri) | Upgrade `RecoveryScheduler` + new `RecoveryService`: Stage 1 (health check) → Stage 2 (canary request) → Stage 3 (gradual restore → HALF_OPEN) → Stage 4 (full restore → CLOSED). Use **exponential backoff with jitter**. | 4h | Staged recovery pipeline |
| **Day 12** (Sat) | Create `RecoveryAttempt.java` model. Track every recovery attempt (stage, result, duration). Expose `GET /metrics/recovery`. Kill the fixed 1-second scheduler, use exponential backoff. | 2-3h | Recovery metrics tracked |
| **Day 13** (Sun) | Create `AlertService.java` — triggers on: circuit opened, health < 30, recovery failed. Alert cooldown (don't repeat same alert within 5 min). Push alerts via WebSocket. Add `AlertHistory` entity for persistence. | 3h | Smart alerting with deduplication |

> **📚 Learn this week**: Strategy Pattern, State Machine, Exponential Backoff, Spring Events

---

## Week 3 (Apr 8 – Apr 14): Frontend Dashboard + Admin Panel

> Focus: Professional monitoring UI + complete admin APIs

| Day | What to Do | Hours | Deliverable |
|---|---|---|---|
| **Day 14** (Mon) | Add missing metrics endpoints: `GET /metrics/failures/by-type`, `GET /metrics/failures/by-severity`, `GET /metrics/failures/timeline?period=1h`. Add WebSocket topics: `/topic/health`, `/topic/circuit-breaker`. | 3h | Complete metrics API |
| **Day 15** (Tue) | Redesign dashboard layout in React. Split `Dashboard.js` into components: `HealthGauge`, `CircuitBreakerCard`, `FailureTimeline`, `ServiceHealthTable`, `AuditLogFeed`, `AlertBanner`. Create a clean grid layout. | 4-5h | Component-based dashboard structure |
| **Day 16** (Wed) | Implement `HealthGauge` (score 0-100 with color), `CircuitBreakerCard` (visual CLOSED/OPEN/HALF_OPEN states with animation), `AlertBanner` (full-width critical notification). Style with dark theme. | 4h | 3 polished dashboard panels |
| **Day 17** (Thu) | Implement `FailureTimeline` (line chart over time), `FailureBreakdown` (pie/donut chart by type), `ServiceHealthTable` (per-service status rows). | 4h | Charts and data tables working |
| **Day 18** (Fri) | Implement `AuditLogFeed` (live-scrolling list of healing actions), `ConfigPanel` (forms to edit thresholds via admin API). Wire up all WebSocket subscriptions for real-time updates. | 4h | Live audit feed + config panel |
| **Day 19** (Sat) | Add controls section: "Simulate Failure" form (type dropdown, service input, count), "Reset Circuit" button, "Clear Metrics" button. Polish responsive layout, add hover effects and transitions. | 3h | Full admin controls |
| **Day 20** (Sun) | End-to-end testing of dashboard. Fix WebSocket reconnection. Add loading/error states. Polish colors, spacing, typography. | 3h | Dashboard complete and polished |

> **📚 Learn this week**: React hooks, Chart.js, WebSocket state management, CSS Grid

---

## Week 4 (Apr 15 – Apr 24): Testing + Documentation + Demo

> Focus: Quality assurance, academic docs, and presentation-ready project

| Day | What to Do | Hours | Deliverable |
|---|---|---|---|
| **Day 21** (Mon) | Write unit tests: `CircuitBreakerServiceTest` (state transitions), `FailureClassifierServiceTest` (classification logic), `HealingStrategyEngineTest` (strategy selection). Target: 10-12 tests. | 4h | Core unit tests |
| **Day 22** (Tue) | Write integration tests: simulate failure → verify circuit opens → verify fallback served → verify recovery. Test rate limiting. Use `@SpringBootTest` + `MockMvc`. | 4h | Integration tests |
| **Day 23** (Wed) | Create **Class Diagram** (UML) — all entities, services, controllers, and relationships. Create **Sequence Diagram** — for the full failure → detection → healing → recovery flow. | 3h | Two new UML diagrams |
| **Day 24** (Thu) | Create **Activity Diagram** — full self-healing lifecycle. Create **State Diagram** — circuit breaker state transitions. Update SRS with all new features. | 3h | Two more UML diagrams + updated SRS |
| **Day 25** (Fri) | Write comprehensive README: project overview, architecture diagram, tech stack, setup instructions, API reference, screenshots. Add Swagger/OpenAPI if possible (`springdoc-openapi`). | 3-4h | Professional README + API docs |
| **Day 26** (Sat) | Create **Demo Script**: step-by-step scenario that shows the complete self-healing lifecycle. Practice the demo flow. Take dashboard screenshots. | 2-3h | Demo-ready |
| **Day 27-28** (Sun-Mon) | **Buffer days**: Fix bugs, polish UI, improve documentation, handle edge cases. Practice presentation. | 4-6h | Final polish |

> **📚 Learn this week**: JUnit 5, Mockito, UML diagram tools (draw.io), OpenAPI

---

## Summary View

```
     WEEK 1                WEEK 2                WEEK 3               WEEK 4
 ┌─────────────┐     ┌─────────────┐     ┌──────────────┐     ┌─────────────┐
 │   Backend    │     │  Self-Heal  │     │   Frontend   │     │   Testing   │
 │    Core      │────▶│  Intelligence│────▶│  Dashboard   │────▶│  & Docs     │
 │              │     │  + Recovery │     │  + Admin     │     │  & Demo     │
 │ • Logging    │     │ • Strategy  │     │ • Health     │     │ • Unit      │
 │ • Rate Limit │     │ • Config    │     │ • Charts     │     │ • Integr.   │
 │ • Classifier │     │ • Degraded  │     │ • Audit Feed │     │ • UML       │
 │ • Health     │     │ • Recovery  │     │ • Controls   │     │ • README    │
 │ • Audit      │     │ • Alerts    │     │ • WebSocket  │     │ • Demo      │
 └─────────────┘     └─────────────┘     └──────────────┘     └─────────────┘
   ~20 hours           ~22 hours           ~25 hours            ~23 hours
```

**Total estimated effort: ~90 hours over 28 days ≈ 3-3.5 hours/day**

---

## ⚡ Daily Routine

```
Morning (30 min)  → Read/study the pattern for today's task
Coding (2.5-3 hr) → Build the feature
End of day (30 min) → Test, commit, push to Git
```

---

## 🚨 Non-Negotiable Checkpoints

| Date | Must Be Done |
|---|---|
| **Mar 31** | All backend APIs working, failure classification, audit trail |
| **Apr 7** | Healing engine choosing strategies, recovery pipeline working, alerts firing |
| **Apr 14** | Dashboard showing all data in real-time with charts |
| **Apr 21** | Tests passing, all UML diagrams done |
| **Apr 24** | README complete, demo practiced, project submitted |

---

> **💡 Pro tip**: Commit to Git daily with meaningful messages. A clean Git history with
> 28+ commits shows consistent effort — professors and interviewers both notice this.
