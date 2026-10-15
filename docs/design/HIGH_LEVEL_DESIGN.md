# Self-Healing Backend System (SHBS) — High-Level Design (HLD)

## 1. Executive Summary

The Self-Healing Backend System (SHBS) is an enterprise-grade, SRE-inspired distributed system that **automatically detects, isolates, remediates, and recovers from production failures** without human intervention. It protects downstream business services (e.g., Inventory, Orders) using circuit breakers, SLO-driven burn-rate analysis, and AI-augmented postmortem generation via RAG (Retrieval-Augmented Generation).

---

## 2. System Context Diagram

This diagram shows SHBS as a black box and its relationships with external actors and systems.

```mermaid
C4Context
    title System Context Diagram — SHBS

    Person(sre, "SRE / On-Call Engineer", "Monitors dashboards, reviews postmortems, acknowledges incidents")
    Person(user, "End User / Client App", "Sends API requests (orders, queries)")

    System(shbs, "Self-Healing Backend System", "Detects failures, opens circuit breakers, triggers remediation, generates AI postmortems")

    System_Ext(inventory, "Inventory Microservice", "Downstream business service for stock management")
    System_Ext(prometheus, "Prometheus", "Scrapes /actuator/prometheus for metrics")
    System_Ext(grafana, "Grafana", "Dashboards and visualization")
    System_Ext(tempo, "Grafana Tempo", "Distributed trace storage")
    System_Ext(alertmanager, "Alertmanager", "Routes alerts to Slack/PagerDuty/Email")
    System_Ext(groq, "Groq LLM API", "AI inference for postmortem generation")

    Rel(user, shbs, "HTTP/REST API")
    Rel(sre, shbs, "WebSocket dashboard, Admin API")
    Rel(shbs, inventory, "HTTP calls protected by Circuit Breaker")
    Rel(shbs, prometheus, "Exposes /actuator/prometheus")
    Rel(prometheus, grafana, "Data source")
    Rel(prometheus, alertmanager, "Fires alert rules")
    Rel(shbs, tempo, "OTLP traces via OTel Collector")
    Rel(shbs, groq, "LLM API for RAG postmortems")
```

---

## 3. High-Level Architecture Diagram

```mermaid
graph TB
    subgraph "External Clients"
        A[End Users / Client Apps]
        B[SRE / Admin Dashboard]
    end

    subgraph "Load Balancer / Gateway"
        C[Nginx / AWS ALB<br/>TLS Termination]
    end

    subgraph "Application Tier — Spring Boot"
        D[REST Controllers<br/>OrderController, HelloController, etc.]
        E[RequestMetricsInterceptor<br/>Auto-records latency/status]
        F[Security Layer<br/>JWT Auth + RBAC]
    end

    subgraph "Self-Healing Core"
        G[CircuitBreakerService v4<br/>Atomic Lua / Redis ZSET]
        H[SLOService<br/>Multi-window Burn Rate]
        I[HealingStrategyEngine<br/>Decision Router]
        J[SelfHealingActionService v2<br/>Playbook Executor]
        K[RecoveryService v2<br/>Non-blocking Canary Recovery]
        L[IncidentService<br/>Lifecycle Management]
    end

    subgraph "AI / RAG Layer"
        M[RunbookIngestionService<br/>Embeds docs/runbooks on startup]
        N[RunbookVectorStore<br/>LangChain4j Semantic Search]
        O[PostmortemAiService<br/>Groq LLaMA3 Generation]
    end

    subgraph "Data Stores"
        P[(MySQL 8.0<br/>Orders, Incidents, Audit Logs)]
        Q[(Redis 7.x<br/>Circuit State, Locks, Rate Limits)]
        R[InMemory EmbeddingStore<br/>Vectorized Runbooks]
    end

    subgraph "Observability Stack"
        S[Prometheus]
        T[Grafana]
        U[Tempo / Jaeger]
        V[Alertmanager]
    end

    subgraph "Downstream Services"
        W[Inventory Microservice<br/>Port 8081]
    end

    A --> C --> F --> E --> D
    B --> C
    D --> G
    D --> H
    E --> H
    G --> I
    H --> I
    I --> J
    J --> K
    G --> L
    L --> O
    O --> N
    N --> R
    M --> R
    D --> P
    G --> Q
    K --> Q
    J --> Q
    D --> W
    D -.-> S
    S --> T
    S --> V
```

---

## 4. Infrastructure Architecture (AWS ECS Fargate)

```mermaid
graph TB
    subgraph "AWS ap-south-1"
        subgraph "VPC 10.0.0.0/16"
            subgraph "Public Subnets"
                ALB[Application Load Balancer<br/>HTTPS:443]
            end

            subgraph "Private Subnets"
                subgraph "ECS Fargate Cluster"
                    BE1[Backend Task 1]
                    BE2[Backend Task 2]
                    FE[Frontend Task<br/>React/Nginx]
                    INV[Inventory Task<br/>Node.js]
                end

                RDS[(RDS MySQL 8.0<br/>Multi-AZ)]
                EC[(ElastiCache Redis 7.x<br/>Cluster Mode)]
            end
        end

        SM[Secrets Manager<br/>DB_PASSWORD, JWT_SECRET]
        CW[CloudWatch<br/>Logs + Alarms]
        Budget[AWS Budgets<br/>Cost Anomaly Detection]
        ASG[App Auto Scaling<br/>CPU Target: 70%]
    end

    Internet((Internet)) --> ALB
    ALB --> BE1
    ALB --> BE2
    ALB --> FE
    BE1 --> RDS
    BE2 --> RDS
    BE1 --> EC
    BE2 --> EC
    BE1 --> INV
    ASG -.-> BE1
    ASG -.-> BE2
```

---

## 5. Technology Stack

| Layer | Technology | Purpose |
| :--- | :--- | :--- |
| **Backend** | Spring Boot 3.3.2, Java 17 | Core application framework |
| **Frontend** | React 19, MUI 7, Chart.js | NOC Dashboard |
| **Database** | MySQL 8.0 (RDS) | Persistent storage for orders, incidents, audit |
| **Cache/State** | Redis 7.x (ElastiCache) | Circuit breaker state, distributed locks, rate limits |
| **AI/RAG** | LangChain4j 0.33, Groq (LLaMA3-8b) | Postmortem generation via RAG |
| **Embeddings** | all-minilm-l6-v2-q (local ONNX) | Zero-cost local document vectorization |
| **Observability** | Prometheus, Grafana, Tempo, Alertmanager | Metrics, dashboards, traces, alert routing |
| **Tracing** | OpenTelemetry (Micrometer Bridge) | Distributed request tracing |
| **IaC** | Terraform | AWS VPC, ECS, RDS, ElastiCache, Budgets |
| **CI/CD** | GitHub Actions | Build, test, deploy, rollback |
| **Security** | JWT (jjwt 0.12.6), Spring Security | Authentication and RBAC |
| **Scheduling** | ShedLock + Redis | Distributed cron coordination |

---

## 6. Non-Functional Requirements

| Attribute | Target |
| :--- | :--- |
| **Availability** | 99.9% SLO (0.1% error budget) |
| **Latency** | p99 < 500ms for order API |
| **MTTR** | < 30 seconds (automated recovery) |
| **Scalability** | 2–10 ECS tasks via auto-scaling |
| **Audit Retention** | 1 year hot (MySQL) + 7 years cold (S3 archive) |
| **Security** | JWT auth, append-only audit, TLS termination |
