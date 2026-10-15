# Self-Healing Backend System (SHBS) — Data Flow Diagrams

## 1. Level 0 — Context Data Flow Diagram

```mermaid
graph LR
    EU[End Users] -->|HTTP Requests| SHBS((SHBS))
    SRE[SRE Engineers] -->|Admin API / WebSocket| SHBS
    SHBS -->|Protected HTTP calls| INV[Inventory Service]
    SHBS -->|Metrics scrape| PROM[Prometheus]
    SHBS -->|OTLP traces| TEMPO[Tempo]
    SHBS -->|Alert rules fire| AM[Alertmanager]
    AM -->|Routes alerts| SLACK[Slack / PagerDuty / Email]
    SHBS -->|LLM API call| GROQ[Groq AI]
    SHBS <-->|Read/Write| MYSQL[(MySQL)]
    SHBS <-->|State/Locks| REDIS[(Redis)]
```

---

## 2. Level 1 — Internal Process Data Flow

```mermaid
graph TB
    subgraph "Inbound Data Flow"
        REQ[HTTP Request] --> INTERCEPT[RequestMetricsInterceptor]
        INTERCEPT --> |latency + status| SLO[SLO Service<br/>Burn Rate Calculation]
        INTERCEPT --> CTRL[Controller<br/>Business Logic]
    end

    subgraph "Failure Detection & Classification"
        CTRL --> |Exception/Timeout| FCS[FailureClassifierService]
        FCS --> |FailureCategory + Severity| FMS[FailureMetricService]
        FMS --> |Persist| MYSQL[(MySQL<br/>failure_metric)]
        FMS --> |failureCount| CB[CircuitBreakerService]
    end

    subgraph "Circuit Breaker State Machine"
        CB --> |Atomic Lua| REDIS[(Redis<br/>cb:* keys)]
        CB --> |State change event| HSE[HealingStrategyEngine]
        CB --> |Circuit OPEN| IS[IncidentService]
        CB --> |Circuit OPEN| AS[AlertService]
    end

    subgraph "Self-Healing Decision Engine"
        SLO --> |Burn rate exceeded| HSE
        HSE --> |Strategy chosen| SHA[SelfHealingActionService]
        SHA --> |Evict connections| DS[HikariDataSource]
        SHA --> |Disable feature| FF[FeatureFlagService]
        SHA --> |Audit trail| AUD[AuditService]
        AUD --> |Persist| MYSQL2[(MySQL<br/>audit_log)]
    end

    subgraph "Recovery Loop"
        RSCHED[RecoveryScheduler<br/>every 5s] --> RS[RecoveryService]
        RS --> |Health check| DS
        RS --> |Canary test| INV[InventoryService]
        RS --> |Reset circuit| CB
        RS --> |State coordination| REDIS
    end

    subgraph "Incident & AI Pipeline"
        IS --> |Resolve| PAS[PostmortemAiService]
        PAS --> |Query| RVS[RunbookVectorStore]
        RVS --> |Semantic search| VECSTORE[EmbeddingStore<br/>In-Memory]
        PAS --> |Generate| GROQ[Groq LLM API]
        PAS --> |Save draft| MYSQL3[(MySQL<br/>incident)]
    end

    subgraph "Outbound Data Flow"
        AS --> |Broadcast| WS[WebSocket<br/>Dashboard]
        AS --> |Email| EMAIL[SMTP]
        AS --> |Dedup key| REDIS
    end
```

---

## 3. Level 2 — Circuit Breaker Internal Data Flow

```mermaid
graph LR
    subgraph "recordFailure(serviceName)"
        A[Incoming Failure] --> LUA[Atomic Lua Script]
        LUA --> B[ZADD cb:svc:failures<br/>score=timestamp, member=UUID]
        LUA --> C[ZREMRANGEBYSCORE<br/>Remove entries outside 60s window]
        LUA --> D[ZCARD<br/>Count remaining failures]
        LUA --> E{Count >= Threshold?}
        E -->|No| F[Return 0 — still CLOSED]
        E -->|Yes| G[GET cb:svc:state]
        G --> H{CAS: state == CLOSED?}
        H -->|Yes| I[SET cb:svc:state = OPEN<br/>EXPIRE 10m]
        H -->|No| J[Return 0 — already transitioned]
        I --> K[Return 1 — circuit just opened]
    end
```

---

## 4. Level 2 — Recovery Service Internal Data Flow

```mermaid
graph TB
    A[RecoveryScheduler triggers] --> B{Circuit OPEN?}
    B -->|No| SKIP[Skip — nothing to recover]
    B -->|Yes| C{Backoff elapsed?}
    C -->|No| SKIP
    C -->|Yes| D[Acquire distributed lock<br/>SETNX recovery:svc:lock UUID TTL=30s]
    D --> E{Lock acquired?}
    E -->|No| SKIP2[Another replica is handling it]
    E -->|Yes| F[Health Check]
    F --> G{DB valid? Memory > 5%?}
    G -->|No| H[Increment attempts<br/>Increase backoff x2<br/>Execute remediation]
    G -->|Yes| I[Canary Test]
    I --> J{inventoryService.isHealthy()?}
    J -->|No| K[Increment attempts<br/>Increase backoff]
    J -->|Yes| L[CAS: OPEN → HALF_OPEN]
    L --> M[Increment success counter]
    M --> N{Successes >= threshold?}
    N -->|No| O[Wait for next scheduler tick]
    N -->|Yes| P[CAS: HALF_OPEN → CLOSED]
    P --> Q[Reset recovery state<br/>Auto-resolve incidents]
    Q --> R[Release lock via Lua<br/>if token matches]
    H --> R
    K --> R
    O --> R
```

---

## 5. Level 2 — RAG Pipeline Data Flow

```mermaid
graph LR
    subgraph "Startup — Ingestion"
        MD[docs/runbooks/*.md] --> PARSE[TextDocumentParser]
        PARSE --> SPLIT[DocumentSplitters.recursive<br/>500 chars, 50 overlap]
        SPLIT --> EMBED[AllMiniLmL6V2<br/>Local ONNX Model]
        EMBED --> STORE[InMemoryEmbeddingStore<br/>Vectors + TextSegments]
    end

    subgraph "Runtime — Retrieval"
        QUERY[Incident title + service] --> QEMBED[EmbeddingModel.embed]
        QEMBED --> SEARCH[embeddingStore.findRelevant<br/>top 3, minScore 0.6]
        SEARCH --> CONTEXT[Retrieved runbook chunks]
    end

    subgraph "Runtime — Generation"
        CONTEXT --> PROMPT[Build SRE Prompt<br/>Incident + Timeline + Context]
        PROMPT --> GROQ[Groq API<br/>llama3-8b-8192]
        GROQ --> DRAFT[AI Postmortem Draft]
        DRAFT --> DB[(MySQL<br/>incident.resolution_notes)]
    end
```

---

## 6. Data Retention & Archival Flow

```mermaid
graph TB
    subgraph "Daily at 3:00 AM"
        SCHED[DataArchivalScheduler] --> LOCK[ShedLock<br/>Acquire distributed lock via Redis]
        LOCK --> |Lock acquired| BATCH[SELECT * FROM table<br/>WHERE timestamp < cutoff<br/>LIMIT 1000]
        BATCH --> ARCHIVE[Write batch to<br/>archives/table/archive_YYYYMMDD.json]
        ARCHIVE --> DELETE[DELETE FROM table<br/>WHERE timestamp < cutoff<br/>LIMIT 1000]
        DELETE --> MORE{More rows?}
        MORE -->|Yes| BATCH
        MORE -->|No| NEXT[Next table]
        NEXT --> RELEASE[Release ShedLock]
    end

    subgraph "Retention Periods"
        METRICS[failure_metric, health_metric<br/>30 days hot]
        ALERTS[alert<br/>30 days hot]
        AUDIT[audit_log<br/>365 days hot]
    end
```

---

## 7. WebSocket Real-Time Broadcast Flow

```mermaid
graph LR
    subgraph "Backend (Publisher)"
        AS[AlertService] --> RWB[RedisWebSocketBroadcaster]
        RWB --> RPUB[Redis PUBLISH<br/>Channel: ws-broadcast]
    end

    subgraph "Redis Pub/Sub"
        RPUB --> RSUB[Redis SUBSCRIBE<br/>Channel: ws-broadcast]
    end

    subgraph "Backend Replicas (Subscribers)"
        RSUB --> RLC1[RedisListenerConfig<br/>Replica 1]
        RSUB --> RLC2[RedisListenerConfig<br/>Replica 2]
        RLC1 --> STOMP1[SimpMessagingTemplate<br/>convertAndSend /topic/alerts]
        RLC2 --> STOMP2[SimpMessagingTemplate<br/>convertAndSend /topic/alerts]
    end

    subgraph "Frontend Clients"
        STOMP1 --> DASH1[React Dashboard<br/>Client 1]
        STOMP2 --> DASH2[React Dashboard<br/>Client 2]
    end
```
