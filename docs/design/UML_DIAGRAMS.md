# Self-Healing Backend System (SHBS) — UML Diagrams

## 1. Use Case Diagram

```mermaid
graph TB
    subgraph "SHBS System Boundary"
        UC1[Place Order]
        UC2[View System Health]
        UC3[View SLO Status]
        UC4[Trigger Chaos Injection]
        UC5[View Incidents]
        UC6[Acknowledge Incident]
        UC7[Resolve Incident]
        UC8[Submit Postmortem]
        UC9[Login as Admin]
        UC10[Auto-Detect Failure]
        UC11[Open Circuit Breaker]
        UC12[Trigger Self-Healing]
        UC13[Attempt Recovery]
        UC14[Generate AI Postmortem]
        UC15[Send Alert]
        UC16[Auto-Resolve Incident]
        UC17[Archive Old Data]
    end

    USER((End User))
    SRE((SRE Engineer))
    SYSTEM((System Timer))

    USER --> UC1
    USER --> UC2
    USER --> UC3

    SRE --> UC4
    SRE --> UC5
    SRE --> UC6
    SRE --> UC7
    SRE --> UC8
    SRE --> UC9

    SYSTEM --> UC10
    UC10 --> UC11
    UC11 --> UC12
    UC11 --> UC15
    UC12 --> UC13
    UC13 --> UC16
    UC16 --> UC14
    SYSTEM --> UC17

    UC11 -.->|creates| UC5
    UC7 -.->|triggers| UC14
```

---

## 2. Activity Diagram — Failure Detection to Recovery

```mermaid
graph TB
    START([Request Arrives]) --> INTERCEPT[RequestMetricsInterceptor<br/>Records start time]
    INTERCEPT --> CTRL[Controller processes request]
    CTRL --> DOWNSTREAM{Call downstream<br/>service?}
    DOWNSTREAM -->|No| RESPOND[Return response]
    DOWNSTREAM -->|Yes| CB_CHECK{CircuitBreaker<br/>allowRequest?}
    CB_CHECK -->|No — OPEN| FAIL_FAST[Return 503<br/>fail-fast]
    CB_CHECK -->|Yes| CALL[Call InventoryService]
    CALL --> RESULT{Success?}

    RESULT -->|Yes| RECORD_SUCCESS[recordSuccess]
    RECORD_SUCCESS --> RESPOND

    RESULT -->|No| RECORD_FAILURE[recordFailure<br/>Atomic Lua Script]
    RECORD_FAILURE --> THRESHOLD{Threshold<br/>reached?}
    THRESHOLD -->|No| RESPOND_ERR[Return error]
    THRESHOLD -->|Yes| OPEN_CIRCUIT[Open Circuit Breaker]
    OPEN_CIRCUIT --> CREATE_INCIDENT[Create Incident<br/>SEV1 / SRE_ONCALL]
    CREATE_INCIDENT --> SEND_ALERT[Send Alert<br/>Email + WebSocket]
    SEND_ALERT --> RESPOND_ERR

    RESPOND --> METRICS[RequestMetricsInterceptor<br/>Records latency + status to SLO]
    RESPOND_ERR --> METRICS
    FAIL_FAST --> METRICS

    METRICS --> SLO_CHECK{SLO Burn Rate<br/>exceeded?}
    SLO_CHECK -->|No| DONE([Done])
    SLO_CHECK -->|Yes| HEAL[HealingStrategyEngine<br/>decides strategy]
    HEAL --> EXEC[SelfHealingActionService<br/>executes playbook]
    EXEC --> DONE

    subgraph "Background Recovery Loop (every 5s)"
        POLL[RecoveryScheduler] --> RC_CHECK{Circuit OPEN?}
        RC_CHECK -->|No| POLL_END([Skip])
        RC_CHECK -->|Yes| BACKOFF{Backoff elapsed?}
        BACKOFF -->|No| POLL_END
        BACKOFF -->|Yes| LOCK{Acquire<br/>distributed lock?}
        LOCK -->|No| POLL_END
        LOCK -->|Yes| HEALTH[Health Check]
        HEALTH --> CANARY[Canary Test]
        CANARY --> HALF_OPEN[Transition to HALF_OPEN]
        HALF_OPEN --> PROBE{Probe successes<br/>>= threshold?}
        PROBE -->|No| POLL_END
        PROBE -->|Yes| CLOSE[Reset to CLOSED]
        CLOSE --> AUTO_RESOLVE[Auto-resolve incident]
        AUTO_RESOLVE --> AI[Generate AI Postmortem]
    end
```

---

## 3. Deployment Diagram

```mermaid
graph TB
    subgraph "Developer Workstation"
        DEV[Developer] --> GIT[GitHub Repository]
    end

    subgraph "CI/CD Pipeline (GitHub Actions)"
        GIT --> CI[ci.yml<br/>Build + Test + Scan]
        CI --> DEPLOY[deploy.yml<br/>Push to ECR + Deploy to ECS]
    end

    subgraph "AWS ECS Fargate (ap-south-1)"
        subgraph "Public Subnet"
            ALB[Application Load Balancer<br/>HTTPS:443]
        end

        subgraph "Private Subnet A"
            BE1[Backend Container<br/>Spring Boot :8080]
            FE1[Frontend Container<br/>React/Nginx :80]
            INV1[Inventory Container<br/>Node.js :8081]
        end

        subgraph "Private Subnet B"
            BE2[Backend Container<br/>Spring Boot :8080]
        end

        subgraph "Data Tier"
            RDS[(RDS MySQL 8.0<br/>Multi-AZ)]
            REDIS[(ElastiCache Redis 7.x)]
        end
    end

    subgraph "Monitoring Stack"
        PROM[Prometheus] --> GRAFANA[Grafana]
        PROM --> AM[Alertmanager]
        OTEL[OTel Collector] --> TEMPO[Grafana Tempo]
    end

    subgraph "External Services"
        GROQ[Groq AI API]
        SMTP[SMTP Server]
        PD[PagerDuty]
        SLACK[Slack]
    end

    DEPLOY --> ALB
    ALB --> BE1
    ALB --> BE2
    ALB --> FE1
    BE1 --> RDS
    BE2 --> RDS
    BE1 --> REDIS
    BE2 --> REDIS
    BE1 --> INV1
    BE1 --> GROQ
    BE1 --> PROM
    BE1 --> OTEL
    AM --> PD
    AM --> SLACK
    AM --> SMTP
```

---

## 4. Package Diagram

```mermaid
graph TB
    subgraph "com.selfhealing.backend"
        CONFIG[config]
        CONTROLLER[controller]
        DTO[dto]
        EXCEPTION[exception]
        MODEL[model]
        REPOSITORY[repository]
        SCHEDULER[scheduler]
        SECURITY[security]
        SERVICE[service]
        ASPECT[aspect]
    end

    CONTROLLER --> SERVICE
    CONTROLLER --> DTO
    CONTROLLER --> MODEL
    SERVICE --> REPOSITORY
    SERVICE --> MODEL
    SERVICE --> CONFIG
    SCHEDULER --> SERVICE
    SECURITY --> SERVICE
    ASPECT --> SERVICE
    CONFIG --> SERVICE
    EXCEPTION --> CONTROLLER
    REPOSITORY --> MODEL

    subgraph "External Libraries"
        LANGCHAIN[dev.langchain4j]
        MICROMETER[io.micrometer]
        SPRING[org.springframework]
        REDIS_LIB[spring-data-redis]
        SHEDLOCK[net.javacrumbs.shedlock]
    end

    SERVICE --> LANGCHAIN
    SERVICE --> MICROMETER
    CONFIG --> SPRING
    CONFIG --> REDIS_LIB
    SCHEDULER --> SHEDLOCK
```

---

## 5. Object Diagram — Runtime Snapshot (During Active Incident)

```mermaid
graph LR
    subgraph "Redis State"
        S1["cb:inventory-service:state = OPEN"]
        S2["cb:inventory-service:failures = ZSET (7 entries)"]
        S3["recovery:inventory-service:lock = uuid-abc123"]
        S4["recovery:inventory-service:attempts = 2"]
        S5["recovery:inventory-service:backoff_ms = 8000"]
        S6["alert_cooldown:RECOVERY:CRITICAL = active (TTL 18s)"]
        S7["healing:cooldown:EVICT_CONNECTIONS = active (TTL 45s)"]
    end

    subgraph "MySQL State"
        I1["Incident #42<br/>state=MITIGATING<br/>severity=SEV1<br/>owner=ritik<br/>escalationPolicy=SRE_ONCALL"]
        A1["AuditLog #301<br/>CIRCUIT_OPENED<br/>triggeredBy=SYSTEM"]
        A2["AuditLog #302<br/>INCIDENT_CREATED<br/>triggeredBy=SYSTEM"]
        A3["AuditLog #303<br/>HEALING_ACTION_EXECUTED<br/>action=EVICT_CONNECTIONS"]
    end

    subgraph "JVM State"
        SLO1["SLOService.window = 1,247 RequestRecords"]
        SLO2["SLOService.burnRate = 15.2x"]
        VEC1["EmbeddingStore = 47 TextSegments<br/>from 4 runbook files"]
    end
```

---

## 6. Communication Diagram — Alert Propagation

```mermaid
graph LR
    CB[1: CircuitBreakerService<br/>detects threshold] -->|2: sendAlert| AS[AlertService]
    AS -->|3: SETNX cooldown key| REDIS[(Redis)]
    AS -->|4: broadcast to /topic/alerts| RWB[RedisWebSocketBroadcaster]
    RWB -->|5: PUBLISH ws-broadcast| REDIS
    REDIS -->|6: Message delivered to all replicas| RLC[RedisListenerConfig]
    RLC -->|7: convertAndSend /topic/alerts| STOMP[SimpMessagingTemplate]
    STOMP -->|8: WebSocket frame| DASH[React Dashboard]
    AS -->|9: CRITICAL? Send email| EMAIL[EmailNotificationService]
    EMAIL -->|10: SMTP| SMTP[Mail Server]
```
