# Self-Healing Backend System (SHBS) — Sequence Diagrams

## 1. Order Request — Happy Path (Circuit CLOSED)

```mermaid
sequenceDiagram
    actor User
    participant ALB as Nginx/ALB
    participant JWT as JwtAuthFilter
    participant INT as RequestMetricsInterceptor
    participant OC as OrderController
    participant CB as CircuitBreakerService
    participant INV as InventoryService
    participant SLO as SLOService
    participant DB as MySQL

    User->>ALB: POST /api/orders
    ALB->>JWT: Forward request
    JWT->>INT: Pass (public endpoint)
    INT->>INT: Start timer
    INT->>OC: Forward to handler
    OC->>CB: allowRequest("inventory-service")
    CB->>CB: getState() == CLOSED → allow
    CB-->>OC: true
    OC->>INV: checkStock(product, quantity)
    INV-->>OC: Stock available
    OC->>DB: INSERT INTO orders
    DB-->>OC: Order saved (id=42)
    OC->>CB: recordSuccess("inventory-service")
    OC-->>INT: Response 200
    INT->>SLO: recordRequest("/api/orders", true, 45ms)
    INT-->>User: {"id": 42, "status": "CONFIRMED"}
```

---

## 2. Order Request — Failure Path (Circuit Opens)

```mermaid
sequenceDiagram
    actor User
    participant OC as OrderController
    participant CB as CircuitBreakerService
    participant INV as InventoryService
    participant SLO as SLOService
    participant IS as IncidentService
    participant AS as AlertService
    participant AUD as AuditService

    User->>OC: POST /api/orders
    OC->>CB: allowRequest("inventory-service")
    CB-->>OC: true (still CLOSED)
    OC->>INV: checkStock(product, quantity)
    INV-->>OC: ❌ Timeout / 500 Error
    OC->>CB: recordFailure("inventory-service")

    Note over CB: Lua Script executes atomically:<br/>1. Add failure to ZSET<br/>2. Prune old entries<br/>3. Count >= threshold?<br/>4. CAS transition CLOSED→OPEN

    CB->>CB: Threshold reached! CAS → OPEN
    CB->>AUD: log("CIRCUIT_OPENED", ...)
    CB->>AS: sendAlert("CRITICAL", "Circuit opened for inventory-service")
    AS->>AS: Redis SET alert_cooldown:RECOVERY:CRITICAL (TTL 30s)
    AS->>AS: Broadcast via WebSocket
    AS->>AS: Send email (CRITICAL)
    CB->>IS: createIncident("Circuit OPEN: inventory-service", "SEV1", ...)
    IS->>IS: Set escalationPolicy="SRE_ONCALL", ackTarget=5min
    IS->>AUD: log("INCIDENT_CREATED", ...)

    CB-->>OC: true (circuit just opened)
    OC-->>User: 503 Service Unavailable

    Note over User: Subsequent requests fail-fast
    User->>OC: POST /api/orders (next request)
    OC->>CB: allowRequest("inventory-service")
    CB-->>OC: false (OPEN, recovery time not elapsed)
    OC-->>User: 503 Service Unavailable (fail-fast)
```

---

## 3. Automated Recovery Flow (OPEN → HALF_OPEN → CLOSED)

```mermaid
sequenceDiagram
    participant SCHED as RecoveryScheduler<br/>(every 5s)
    participant RS as RecoveryService
    participant CB as CircuitBreakerService
    participant REDIS as Redis
    participant DS as DataSource
    participant INV as InventoryService
    participant AUD as AuditService
    participant IS as IncidentService

    SCHED->>RS: attemptRecovery("inventory-service")
    RS->>CB: getState("inventory-service")
    CB-->>RS: OPEN

    RS->>REDIS: GET recovery:inventory-service:last_attempt_ms
    REDIS-->>RS: 1693000000 (backoff elapsed ✓)

    RS->>REDIS: SET recovery:inventory-service:lock (UUID, TTL=30s)
    REDIS-->>RS: true (lock acquired)

    RS->>RS: performHealthCheck()
    RS->>DS: getConnection() → isValid(2)
    DS-->>RS: ✅ Valid
    RS->>RS: Check memory (>5% free) ✅

    RS->>RS: performCanaryTest()
    RS->>INV: isHealthy()
    INV-->>RS: ✅ true

    RS->>CB: tryRecovery("inventory-service")
    Note over CB: CAS: OPEN → HALF_OPEN

    RS->>REDIS: INCR recovery:inventory-service:successes
    REDIS-->>RS: 3 (>= threshold)

    RS->>CB: resetCircuit("inventory-service")
    Note over CB: CAS: HALF_OPEN → CLOSED

    RS->>AUD: log("RECOVERY_SUCCESS", "inventory-service fully recovered")
    RS->>REDIS: DELETE recovery:inventory-service:{attempts,successes,backoff,last_attempt}

    RS->>IS: autoResolveForService("inventory-service")
    IS->>IS: resolve(incidentId, "Auto-resolved: service recovered")
    IS->>IS: PostmortemAiService.generatePostmortem(incidentId)

    RS->>REDIS: Release lock via Lua (token-safe)
```

---

## 4. SLO Burn-Rate Preemptive Healing

```mermaid
sequenceDiagram
    participant SCHED as PrometheusSLOChecker<br/>(every 30s)
    participant SLO as SLOService
    participant AS as AlertService
    participant HSE as HealingStrategyEngine
    participant SHA as SelfHealingActionService

    SCHED->>SLO: getBurnRate()
    SLO->>SLO: Prune window (remove entries > 5min)
    SLO->>SLO: Calculate: (failures / total) / (1 - 0.999)
    SLO-->>SCHED: burnRate = 15.2

    SCHED->>SLO: isBudgetExhausted()
    SLO-->>SCHED: true (15.2 > 14.4 fast threshold)

    SCHED->>AS: sendAlert("WARNING", "SLO budget burning fast: 15.2x")
    SCHED->>HSE: decideAndExecute(failureCount)
    HSE->>HSE: healthScore=55 → SERVE_DEGRADED
    HSE->>SHA: executePlaybook(FLUSH_CACHE)
    SHA->>SHA: Check cooldown → OK
    SHA->>SHA: Acquire lock → OK
    SHA->>SHA: Clear caches
    SHA->>SHA: Set cooldown TTL
```

---

## 5. AI Postmortem Generation (RAG Pipeline)

```mermaid
sequenceDiagram
    participant IS as IncidentService
    participant PAS as PostmortemAiService
    participant RVS as RunbookVectorStore
    participant EM as EmbeddingModel<br/>(Local MiniLM)
    participant ES as InMemoryEmbeddingStore
    participant GROQ as Groq API<br/>(LLaMA3-8b)
    participant DB as MySQL

    Note over IS: Incident resolved by SRE or auto-recovery

    IS->>PAS: generatePostmortem(incidentId) [ASYNC]
    PAS->>DB: findById(incidentId)
    DB-->>PAS: Incident (title, timeline, service, severity)

    PAS->>RVS: searchSimilarIncidents(title, service)
    RVS->>EM: embed("Find runbooks for inventory-service...")
    EM-->>RVS: Embedding vector [0.12, -0.34, ...]
    RVS->>ES: findRelevant(vector, maxResults=3, minScore=0.6)
    ES-->>RVS: [Match1: "deployment.md chunk", Match2: "incident-response.md chunk"]
    RVS-->>PAS: Context string (retrieved runbook chunks)

    PAS->>PAS: buildPrompt(incident, context)
    Note over PAS: "You are an SRE expert...<br/>INCIDENT: Circuit OPEN inventory-service<br/>RAG CONTEXT: deployment runbook steps..."

    PAS->>GROQ: POST /openai/v1/chat/completions
    GROQ-->>PAS: "#### Executive Summary\nThe inventory-service experienced..."

    PAS->>DB: UPDATE incident SET resolution_notes = notes + AI draft
    PAS->>PAS: auditService.log("AI_POSTMORTEM_GENERATED")
```

---

## 6. Incident Lifecycle State Machine

```mermaid
stateDiagram-v2
    [*] --> OPEN : Circuit opens OR SLO burns
    OPEN --> ACKNOWLEDGED : SRE acknowledges (sets owner)
    ACKNOWLEDGED --> MITIGATING : SRE begins active mitigation
    MITIGATING --> POSTMORTEM_REQUIRED : Issue resolved, pending review
    OPEN --> POSTMORTEM_REQUIRED : Auto-resolved by RecoveryService
    POSTMORTEM_REQUIRED --> RESOLVED : Postmortem approved by human
    RESOLVED --> [*]

    note right of OPEN
        ackTargetMinutes: 5 (SEV1), 30 (other)
        escalationPolicy: SRE_ONCALL / DEV_TEAM
    end note

    note right of POSTMORTEM_REQUIRED
        AI drafts postmortem via Groq
        Human reviews and approves
    end note
```

---

## 7. Circuit Breaker State Machine

```mermaid
stateDiagram-v2
    [*] --> CLOSED
    CLOSED --> OPEN : Failures >= threshold (atomic Lua CAS)
    OPEN --> HALF_OPEN : Recovery timeout elapsed + canary passes
    HALF_OPEN --> CLOSED : N consecutive successes (probe counter)
    HALF_OPEN --> OPEN : Any probe failure
    CLOSED --> CLOSED : Failures < threshold (windowed)

    note right of OPEN
        All requests rejected (fail-fast)
        RecoveryService polls every 5s
        Jittered exponential backoff
    end note

    note right of HALF_OPEN
        Limited concurrent probes (max 3)
        Each success increments probe counter
    end note
```
