# Self-Healing Backend System (SHBS) — Component Design

## 1. Component Interaction Diagram

This diagram shows the relationships between all major components in the system, organized by their functional domain.

```mermaid
graph LR
    subgraph "API Gateway Layer"
        NGINX[Nginx/ALB]
        SEC[SecurityConfig<br/>JwtAuthFilter]
        INTERCEPTOR[RequestMetricsInterceptor]
    end

    subgraph "Controller Layer"
        HC[SystemHealthController]
        OC[OrderController]
        SC[SLOController]
        IC[IncidentController]
        AC[AdminController]
        AUTH[AuthController]
    end

    subgraph "Self-Healing Engine"
        CB[CircuitBreakerService v4]
        SLO[SLOService]
        HSE[HealingStrategyEngine]
        SHA[SelfHealingActionService v2]
        RS[RecoveryService v2]
        FCS[FailureClassifierService]
        FMS[FailureMetricService]
    end

    subgraph "Incident Management"
        IS[IncidentService]
        AS[AlertService]
        ENS[EmailNotificationService]
    end

    subgraph "AI / RAG Engine"
        RIS[RunbookIngestionService]
        RVS[RunbookVectorStore]
        PAS[PostmortemAiService]
    end

    subgraph "Support Services"
        AUD[AuditService]
        CFG[ConfigService]
        FF[FeatureFlagService]
        DRS[DegradedResponseService]
        FBS[FallbackService]
        SHS[SystemHealthService]
        INV[InventoryService]
    end

    subgraph "Background Schedulers"
        HSCHED[HealthScheduler<br/>every 5s]
        RSCHED[RecoveryScheduler<br/>every 5s]
        PSCHED[PrometheusSLOChecker<br/>every 30s]
        DSCHED[DataArchivalScheduler<br/>daily 3AM]
    end

    subgraph "Infrastructure"
        MYSQL[(MySQL)]
        REDIS[(Redis)]
        VECSTORE[InMemory<br/>EmbeddingStore]
        GROQ[Groq API<br/>LLaMA3]
        WS[WebSocket<br/>via Redis Pub/Sub]
    end

    NGINX --> SEC --> INTERCEPTOR --> HC
    INTERCEPTOR --> OC
    INTERCEPTOR --> SC
    INTERCEPTOR --> IC
    INTERCEPTOR --> AC

    INTERCEPTOR --> SLO

    OC --> CB
    OC --> INV
    HC --> FMS --> FCS
    HC --> HSE
    HSE --> CB
    HSE --> SHA
    HSE --> SHS

    CB --> REDIS
    CB --> AUD
    CB --> AS
    CB --> IS

    SLO --> AS

    SHA --> DRS
    SHA --> FF
    SHA --> AUD

    RS --> CB
    RS --> SHA
    RS --> INV
    RS --> REDIS

    IS --> PAS
    PAS --> RVS --> VECSTORE
    PAS --> GROQ
    RIS --> VECSTORE

    AS --> WS
    AS --> ENS
    AS --> REDIS

    HSCHED --> SHS
    RSCHED --> RS
    PSCHED --> SLO
    DSCHED --> MYSQL
```

---

## 2. Component Responsibility Matrix

| Component | Responsibility | Dependencies | State Storage |
| :--- | :--- | :--- | :--- |
| **CircuitBreakerService** | Atomic state machine (CLOSED→OPEN→HALF_OPEN→CLOSED) using Lua scripts | Redis, AuditService, AlertService, IncidentService | Redis ZSET + String |
| **SLOService** | Multi-window (5m fast + 1h slow) burn-rate tracking against 99.9% SLO | MeterRegistry | JVM ConcurrentLinkedDeque |
| **HealingStrategyEngine** | Maps (healthScore, circuitState, failureCount) → HealingStrategy enum | SystemHealthService, CircuitBreakerService, SelfHealingActionService | Stateless |
| **SelfHealingActionService** | Executes remediation playbooks with cooldown, idempotency, and distributed locks | DataSource, Redis, AuditService, FeatureFlagService | Redis (cooldown TTLs) |
| **RecoveryService** | Non-blocking, distributed, jittered recovery with canary testing | CircuitBreakerService, InventoryService, Redis | Redis (backoff, locks) |
| **IncidentService** | Full lifecycle: OPEN→ACKNOWLEDGED→MITIGATING→POSTMORTEM_REQUIRED→RESOLVED | AuditService, PostmortemAiService | MySQL |
| **AlertService** | Distributed deduplicated alerting with severity-based routing | Redis, WebSocket, Email | Redis (cooldown key) |
| **PostmortemAiService** | RAG-powered postmortem drafting via Groq LLaMA3 | RunbookVectorStore, ChatLanguageModel | None (async) |
| **RunbookVectorStore** | Semantic search over embedded runbook documents | EmbeddingStore, EmbeddingModel | InMemoryEmbeddingStore |
| **RequestMetricsInterceptor** | Automatically feeds latency + status data to SLOService for every HTTP request | SLOService | Stateless |
| **DataArchivalScheduler** | Archive + batch delete old records; ShedLock ensures single-replica execution | JdbcTemplate, ShedLock (Redis) | MySQL + Filesystem |

---

## 3. Healing Strategy Decision Matrix

The `HealingStrategyEngine` uses the following decision tree:

```mermaid
graph TD
    START[Failure Detected] --> HS{Health Score?}
    HS -->|>= 80| DN[DO_NOTHING]
    HS -->|60-79| RT[REDUCE_TRAFFIC<br/>Tighten rate limits]
    HS -->|50-59| SD[SERVE_DEGRADED<br/>Return cached/stale data]
    HS -->|30-49| DF[DISABLE_FEATURE<br/>Feature flag off]
    HS -->|< 30| FC{Failure Count >= 5<br/>AND Circuit CLOSED?}
    FC -->|Yes| ACB[APPLY_CIRCUIT_BREAKER<br/>Open circuit]
    FC -->|No| CS{Circuit State?}
    CS -->|OPEN| TR[TRIGGER_RECOVERY<br/>Evict connections + flush cache]
    CS -->|Other| AA[ALERT_ADMIN<br/>Escalate to SRE]
```

---

## 4. Remediation Playbook Catalog

| Playbook | Action | Cooldown | Max/Hour | Requires Approval | Rollback |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `EVICT_CONNECTIONS` | `HikariDataSource.softEvictConnections()` | 60s | 10 | No | None |
| `FLUSH_CACHE` | Clears application-level caches | 120s | 5 | No | None |
| `DISABLE_FEATURE` | Sets feature flag to `false` in Redis | 300s | 3 | No | Re-enable flag |
| `REDUCE_TRAFFIC` | Lowers rate limit threshold by 50% | 180s | 5 | No | Restore threshold |
