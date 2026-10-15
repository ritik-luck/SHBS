# SHBS — Exhaustive Project Explanation
### From First Principles to Production-Grade Architecture

This document explains every single component of the Self-Healing Backend System (SHBS) — **why** it exists, **how** it works internally, **what design decisions** were made, and **how each piece would be implemented differently in a real production environment** at companies like Netflix, Amazon, or Google.

---

# Part 1: The Problem

## 1.1 What Problem Does This Solve?

In modern software, no single application runs in isolation. A typical e-commerce checkout page talks to:
- An **Inventory Service** to check stock
- A **Payment Gateway** (Stripe, Razorpay) to charge the card
- A **Notification Service** (SendGrid, SES) to send a confirmation email
- A **Database** (MySQL, PostgreSQL) to persist the order
- A **Cache** (Redis) to speed up lookups

**The problem:** Any one of these can fail at any time. A database might run out of connections. A payment gateway might timeout. An email provider might be down for maintenance.

When one dependency fails, a naive application will:
1. Keep sending requests to the dead service
2. Each request hangs for 30 seconds waiting for a timeout
3. The application's thread pool fills up with stuck threads
4. No threads are left to serve healthy requests
5. The entire application crashes — even though only ONE dependency was down

This is called a **Cascading Failure**. One small fire burns down the entire house.

## 1.2 Why Can't We Just Use Try-Catch?

A `try-catch` block handles the *exception*, but it doesn't solve the *systemic problem*:
- The application still sends requests to a dead service (wasting time and resources)
- Every request still waits for the full timeout period (e.g., 30 seconds)
- The thread pool still gets exhausted
- There is no automatic recovery — a human engineer must manually restart the service

**What we need is a system that:**
1. Detects that a dependency is failing (after N failures, not just one)
2. Stops sending requests to the dead dependency immediately (fail-fast)
3. Returns a fallback response instead (graceful degradation)
4. Periodically checks if the dependency has recovered
5. Automatically resumes normal traffic when it's healthy again
6. Does all of this **without any human intervention**

This is exactly what SHBS does.

## 1.3 Why Did I Build This Instead of Using Resilience4j or Hystrix?

| Library | Limitation |
|---------|-----------|
| **Netflix Hystrix** | Deprecated since 2018. No longer maintained. |
| **Resilience4j** | Excellent library, but it stores circuit breaker state **in-memory** inside the JVM. If you run 2 replicas behind a load balancer, each replica has its own independent circuit breaker. Replica A might think the service is down, while Replica B is still sending traffic to it. |

**Our approach:** We built a custom circuit breaker that stores its state in **Redis** using **Lua scripts** for atomic operations. This means all replicas share one single source of truth. When any replica detects a failure and opens the circuit, ALL replicas immediately stop sending traffic. This is genuinely distributed and is the same approach used at large-scale companies.

> **Interview Defense:** *"I chose to build a custom circuit breaker over Resilience4j because Resilience4j's default in-memory state doesn't support distributed coordination across replicas. My implementation uses Redis Lua scripts for atomic CAS (Compare-And-Swap) state transitions, ensuring consistency across a multi-replica deployment behind a load balancer."*

---

# Part 2: The Circuit Breaker (The Heart of the System)

## 2.1 What is a Circuit Breaker?

A Circuit Breaker is a design pattern borrowed from electrical engineering. Just like a physical circuit breaker in your house trips to prevent an electrical fire, a software circuit breaker "trips" to prevent a cascading failure.

It has **three states**:

```
┌──────────┐    Failures ≥ Threshold    ┌──────────┐
│  CLOSED  │ ──────────────────────────► │   OPEN   │
│ (Normal) │                             │(Blocking)│
└──────────┘                             └──────────┘
     ▲                                        │
     │                                        │ After cooldown period
     │                                        ▼
     │         Probe succeeds            ┌───────────┐
     └────────────────────────────────── │ HALF_OPEN │
                                         │ (Testing) │
               Probe fails               └───────────┘
               ───────────────────────────────┘
                    (Back to OPEN)
```

### State Machine:
1. **CLOSED (Normal):** All requests pass through normally. The circuit breaker silently counts failures in a rolling time window.
2. **OPEN (Blocking):** Too many failures were detected. ALL requests are immediately rejected with a fallback response. No traffic reaches the failing service. This protects the system.
3. **HALF_OPEN (Testing):** After a cooldown period, the circuit breaker allows a small number of "probe" requests through to test if the service has recovered. If the probe succeeds → CLOSED. If it fails → back to OPEN.

## 2.2 Our Implementation (`CircuitBreakerService.java`)

### Why Redis + Lua Scripts?

The circuit breaker state must be shared across multiple application replicas. We use Redis because:
- It is an in-memory data store with sub-millisecond latency
- It supports **Lua scripting**, which lets us execute multiple Redis commands atomically (like a database transaction)

### The Atomic Failure Recording (Lua Script)

When a request to a dependency fails, the circuit breaker needs to:
1. Add the failure timestamp to a sorted set
2. Remove old failures outside the rolling window
3. Count remaining failures
4. If count ≥ threshold, atomically transition state from CLOSED → OPEN

All 4 steps must happen **atomically** (as one indivisible operation). If two replicas execute this concurrently, only one should trigger the transition. This is why we use a Lua script — Redis executes the entire script as a single atomic operation.

### Compare-And-Swap (CAS) for State Transitions

To prevent race conditions (two replicas both trying to open the circuit simultaneously), we use a CAS pattern:
```
IF current_state == "CLOSED" THEN
    SET state = "OPEN"
    RETURN 1  (transition succeeded)
ELSE
    RETURN 0  (someone else already transitioned it)
```

This ensures exactly one replica performs each state transition, preventing duplicate alerts and audit log entries.

### Key Configuration Parameters:
| Parameter | Default | Description |
|-----------|---------|-------------|
| `cb_threshold` | 5 | Number of failures in the window to trip the circuit |
| `cb_window_ms` | 30000 | Rolling time window in milliseconds |
| `cb_recovery_ms` | 30000 | Cooldown before transitioning from OPEN → HALF_OPEN |
| `cb_max_probes` | 3 | Max concurrent probe requests in HALF_OPEN state |

### Production-Grade Equivalent:
In a real production environment at a big tech company:
- The **same Redis-backed approach** would be used (this part is already production-grade)
- You would add **Redis Sentinel** or **Redis Cluster** for high availability of the state store itself
- You might add **exponential backoff** to the OPEN → HALF_OPEN cooldown (each consecutive trip doubles the wait time)
- Circuit breaker configuration would be stored in a **feature flag system** (like LaunchDarkly) for runtime tuning without redeployment

---

# Part 3: The Multi-Service Order Pipeline

## 3.1 Why Three Services?

In distributed systems, not all dependencies are equally important. We designed three services to demonstrate the three fundamental dependency types:

### Service Classification:

| Service | Type | On Failure | Why This Classification |
|---------|------|-----------|------------------------|
| **InventoryService** | Critical | Reject the order | You cannot sell something you don't have. Allowing an order without stock creates data inconsistency. |
| **PaymentService** | Deferrable | Save order as `PAYMENT_PENDING` | The customer's intent to buy is captured. Payment can be retried later (like how Uber charges you after the ride, not during). |
| **NotificationService** | Fire-and-Forget | Silently ignore | The order succeeded. Missing an email is annoying but not catastrophic. The customer can check their order status on the website. |

### The Order Pipeline (`OrderService.java`):

```java
// Step 1: CRITICAL — Must succeed or order is rejected
InventoryResult inventory = inventoryService.checkAndReserve(itemId, quantity);
if (!circuitBreakerService.allowRequest("inventory-service")) {
    return fallbackResponse(); // Immediate rejection
}

// Step 2: DEFERRABLE — Graceful degradation on failure
if (circuitBreakerService.allowRequest("payment-service")) {
    try {
        paymentService.charge(order);
        order.setStatus("CONFIRMED");
    } catch (Exception e) {
        order.setStatus("PAYMENT_PENDING"); // Save for later retry
        circuitBreakerService.recordFailure("payment-service");
    }
} else {
    order.setStatus("PAYMENT_PENDING"); // Circuit is open, skip payment
}

// Step 3: FIRE-AND-FORGET — Best effort
if (circuitBreakerService.allowRequest("notification-service")) {
    try {
        notificationService.sendConfirmation(order);
    } catch (Exception e) {
        // Silently swallow — order already succeeded
        circuitBreakerService.recordFailure("notification-service");
    }
}
```

### Why This Matters in an Interview:
This demonstrates that you understand **graceful degradation** — the system loses capabilities incrementally rather than crashing completely. This is exactly how Amazon's checkout works: if their recommendation engine is down, you can still buy things. If their email service is down, you still get charged and your order ships.

### Production-Grade Equivalent:
| What We Built | Production Version |
|--------------|-------------------|
| Services as Java classes in one JVM | Each service is a separate deployable microservice with its own database, Docker image, and CI/CD pipeline |
| Direct method calls between services | REST/gRPC calls over the network, with service discovery (Consul, Eureka, or Kubernetes DNS) |
| Shared JVM memory/threads | Process-level isolation — one service's OOM crash cannot affect another |
| `PAYMENT_PENDING` saved in DB | A message queue (Kafka, RabbitMQ) with a dead-letter queue for failed payment retries |

> **Interview Defense:** *"The services share a JVM because this is a demonstration of the architectural pattern, not an infrastructure deployment exercise. The circuit breaker logic, dependency classification, and fallback strategies are identical whether the services run in one process or across 50 Kubernetes pods. Extracting them into separate deployables is a DevOps concern — the resilience code doesn't change."*

---

# Part 4: The Self-Healing Engine

## 4.1 The Recovery Scheduler (`RecoveryScheduler.java`)

This is the autonomous recovery mechanism. It runs as a Spring `@Scheduled` task every 10 seconds and performs the following:

```
Every 10 seconds:
  ├─ For each service (inventory, payment, notification):
  │    ├─ Check circuit breaker state from Redis
  │    ├─ If OPEN and cooldown has elapsed:
  │    │    ├─ Transition to HALF_OPEN
  │    │    ├─ Send a canary probe request
  │    │    ├─ If probe succeeds → CLOSE the circuit (service restored!)
  │    │    └─ If probe fails → Re-OPEN (wait longer)
  │    └─ If CLOSED: do nothing (service is healthy)
  └─ Log all state transitions to the audit trail
```

### What is a Canary Request?
A "canary" is a single, lightweight test request sent to the recovering service. The term comes from coal miners who brought canaries into mines — if the canary died, the air was toxic. Similarly, if our canary request fails, the service is still unhealthy.

### The Healing Strategy Engine (`HealingStrategyEngine.java`)

Beyond circuit breaker recovery, the system can execute specific healing actions based on the type of failure detected:

| Failure Type | Healing Action | Why |
|-------------|---------------|-----|
| Database connection exhaustion | `HikariDataSource.softEvictConnections()` | Silently kills stale/dead connections and forces the pool to create fresh ones |
| Memory pressure | `System.gc()` + Redis cache flush | Frees heap memory by triggering garbage collection and clearing the cache |
| High latency | Increase circuit breaker timeout thresholds | Gives the struggling service more breathing room |

### SLO Engine (`SLOService.java`)

The SLO (Service Level Objective) engine provides a higher-level view of system health beyond individual circuit breakers:

- **SLI (Service Level Indicator):** The ratio of successful requests to total requests in a 5-minute rolling window.
- **SLO (Service Level Objective):** The target availability (e.g., 99.9%).
- **Error Budget:** The allowed number of failures = Total Requests × (1 - SLO). For example, at 99.9% SLO with 10,000 requests, you're allowed 10 failures.
- **Burn Rate:** How fast the error budget is being consumed. A burn rate of 5x means you'll exhaust your entire error budget in 1/5th of the expected time.

When the burn rate exceeds the threshold, the SLO engine triggers preemptive healing actions — even before the circuit breaker formally trips.

### Production-Grade Equivalent:
| What We Built | Production Version |
|--------------|-------------------|
| `@Scheduled` task in Spring | A Kubernetes CronJob or a dedicated SRE sidecar container |
| `System.gc()` as a healing action | Kubernetes pod restart via liveness probe failure, or auto-scaling to add fresh instances |
| SLO burn rate calculated in-app | Google SRE's SLO monitoring via Prometheus + Alertmanager with PagerDuty/Opsgenie integration |

---

# Part 5: Chaos Engineering Agent (Red Team)

## 5.1 Why Chaos Engineering?

You cannot prove a system is resilient by just writing code and hoping it works. You must **intentionally break it** and observe whether the self-healing mechanisms actually kick in.

Netflix invented this discipline with **Chaos Monkey** — a tool that randomly kills production servers to ensure their architecture survives. Our Chaos Agent does the same thing at the application level.

## 5.2 How the Chaos Agent Works (`ChaosAgentService.java`)

The agent runs a structured, 5-phase experiment:

```
Phase 1: BASELINE
  └─ Verify all circuits are CLOSED (clean starting state)

Phase 2: INJECT FAILURE
  └─ Pick a random target service (inventory / payment / notification)
  └─ Pick a random attack type:
       ├─ "timeout" — Inject 5000ms artificial latency
       ├─ "exception" — Force 100% of requests to throw exceptions
       └─ "high_error_rate" — Inject 80% failure rate

Phase 3: OBSERVE DETECTION
  └─ Wait up to 60 seconds for the circuit breaker to OPEN
  └─ Measure: "How fast did the system detect the failure?"
  └─ If circuit didn't open → EXPERIMENT FAILED (detection is broken)

Phase 4: REMOVE FAILURE & OBSERVE HEALING
  └─ Stop injecting the failure
  └─ Wait up to 120 seconds for the circuit to CLOSE
  └─ Measure: "How fast did the system heal itself?"
  └─ If circuit didn't close → EXPERIMENT FAILED (healing is broken)

Phase 5: SCORE & ANALYZE
  └─ Calculate resilience score (0-100) based on:
       ├─ Detection speed (faster = better)
       ├─ Healing speed (faster = better)
       └─ Whether healing succeeded at all
  └─ Save experiment to database (immutable audit trail)
  └─ Send telemetry to LLM for postmortem generation
```

### The Resilience Score Formula:
```
Base Score = 100
If detection took > 10s:    Score -= 20
If healing took > 60s:      Score -= 20
If healing failed entirely: Score = 0
If everything was fast:     Score = 100 (Perfect resilience)
```

### Production-Grade Equivalent:
| What We Built | Production Version |
|--------------|-------------------|
| In-process fault injection (setting a flag in Redis that makes the service throw exceptions) | Infrastructure-level fault injection: Toxiproxy (network proxy that injects real latency/packet loss), Gremlin (kills actual containers), AWS Fault Injection Simulator |
| Experiments run against your own JVM | Experiments run against real production traffic (with safeguards like blast radius limits and automatic rollback) |
| Manual trigger via REST endpoint | Scheduled via CI/CD pipeline, gated behind feature flags, with automatic abort if SLO violation exceeds safe threshold |

> **Interview Defense:** *"My chaos agent validates the self-healing pipeline end-to-end within the application. In production, I would use Toxiproxy or Gremlin for infrastructure-level fault injection. However, the experimental structure — baseline → inject → observe → remove → score — is identical to what Netflix and Google use in their chaos engineering platforms."*

---

# Part 6: AI-Powered Incident Response (LLM + RAG)

## 6.1 The Problem with Incident Postmortems

When a production incident occurs, engineers spend hours:
1. Gathering logs and metrics
2. Reconstructing the timeline
3. Identifying the root cause
4. Writing a postmortem document
5. Creating a runbook to prevent recurrence

This process typically takes 2-4 hours per incident. We automated it.

## 6.2 How the LLM Integration Works

### The RAG Pipeline:

```
Incident Occurs
     │
     ▼
RunbookVectorStore (Semantic Search)
  └─ Searches past incidents and runbooks for similar patterns
  └─ Returns top-K most relevant past incidents as context
     │
     ▼
PostmortemAiService
  └─ Constructs a prompt with:
       ├─ Current incident timeline (timestamps, error messages)
       ├─ Affected services and circuit breaker states
       ├─ Similar past incidents (from RAG)
       └─ Current system metrics (CPU, memory, latency)
     │
     ▼
LLM (Groq / Llama 3 via LangChain4j)
  └─ Generates:
       ├─ Root Cause Analysis (What went wrong and why)
       ├─ Impact Assessment (What customers were affected)
       ├─ Mitigation Steps (What the system did to heal)
       └─ Dynamic Runbook (Bash/Python script to prevent recurrence)
     │
     ▼
Postmortem saved to Incident record in MySQL
```

### Why RAG (Retrieval-Augmented Generation)?

Plain LLMs hallucinate. They don't know anything about YOUR specific system. By feeding the LLM context from your own incident history (via vector search), the generated postmortem is **grounded in your system's real behavior** rather than generic advice.

### Why Groq / Llama 3?

| Option | Why Not / Why Yes |
|--------|------------------|
| OpenAI GPT-4 | Expensive, requires sending potentially sensitive production data to a third party |
| Self-hosted LLM | Requires GPU infrastructure, significant operational overhead |
| **Groq (Llama 3)** ✅ | Free tier available, extremely fast inference (Groq's custom LPU hardware), open-source model (Llama 3), good enough for structured postmortem generation |

### Production-Grade Equivalent:
| What We Built | Production Version |
|--------------|-------------------|
| `RunbookVectorStore` (in-memory) | pgvector (PostgreSQL extension), Pinecone, or Weaviate for persistent, scalable vector storage |
| Single LLM call per incident | A multi-agent pipeline: one agent extracts root cause, another generates the runbook, a third validates it against known patterns |
| Groq free tier | Self-hosted Llama 3 on company infrastructure, or enterprise OpenAI/Anthropic API with data privacy agreements |

---

# Part 7: Predictive AIOps (ONNX ML Model)

## 7.1 The Leap from Reactive to Proactive

Everything described so far is **reactive** — the system waits for failures to happen, then heals. But what if we could **predict** failures before they happen?

This is the cutting-edge concept called **AIOps (Artificial Intelligence for IT Operations)**.

## 7.2 The MLOps Pipeline

### Step 1: Data Collection
We use a real Kaggle dataset: **"Distributed System Architecture Stress & Failure"** (`distributed_system_architecture_stress_dataset.csv`). This contains 100,000+ rows of server telemetry with labeled outcomes (healthy vs. degraded vs. failed).

### Step 2: Feature Selection
From the dataset, we extract 4 features that map directly to real-time server metrics:

| Dataset Column | Our Feature Name | What It Measures |
|---------------|-----------------|-----------------|
| `cpu_utilization_percent` | `cpu_usage` | How much CPU the server is consuming |
| `memory_utilization_percent` | `memory_usage` | How much RAM is being used |
| `avg_latency_ms` | `latency_ms` | Average response time of the service |
| `error_rate_percent` | `error_rate` | Percentage of requests that are failing (converted to 0.0-1.0 decimal) |

### Step 3: Model Training (Google Colab — `docs/ml/train_model.py`)

**Why Random Forest?**
| Model | Why Not / Why Yes |
|-------|------------------|
| Linear Regression | Can't capture nonlinear interactions between metrics |
| Neural Network (Deep Learning) | Overkill for 4 features, requires GPU, hard to explain in interviews |
| **Random Forest** ✅ | Excellent on tabular data, fast inference, explainable (you can show feature importance), no GPU needed, well-understood by interviewers |

**Training Process:**
```python
# Load real Kaggle data
df = pd.read_csv('distributed_system_architecture_stress_dataset.csv')

# Features: CPU, Memory, Latency, Error Rate
X = df[['cpu_utilization_percent', 'memory_utilization_percent', 
        'avg_latency_ms', 'error_rate_percent']]

# Target: Is the system about to fail? (1 = yes, 0 = no)
y = (df['system_state'] != 'healthy').astype(int)

# Train
clf = RandomForestClassifier(n_estimators=50, max_depth=5)
clf.fit(X_train, y_train)
# Accuracy: ~95%+
```

### Step 4: Export to ONNX

**Why ONNX?**
The model is trained in Python (scikit-learn), but our application is Java (Spring Boot). We need a way to bridge the two worlds.

ONNX (Open Neural Network Exchange) is a universal model format supported by:
- Python (scikit-learn, PyTorch, TensorFlow)
- Java (Microsoft ONNX Runtime)
- C++ (for edge devices)

```python
from skl2onnx import convert_sklearn
onx = convert_sklearn(clf, initial_types=[('float_input', FloatTensorType([None, 4]))])
with open("predictive_model.onnx", "wb") as f:
    f.write(onx.SerializeToString())
```

### Step 5: Java Inference (`PredictiveMLService.java`)

The Spring Boot application loads the `.onnx` file at startup using Microsoft's ONNX Runtime:

```java
// Load model from src/main/resources/predictive_model.onnx
OrtEnvironment env = OrtEnvironment.getEnvironment();
OrtSession session = env.createSession(modelBytes, new OrtSession.SessionOptions());

// Inference
float[][] inputs = {{cpu, memory, latency, errorRate}};
OnnxTensor tensor = OnnxTensor.createTensor(env, inputs);
Result result = session.run(Map.of("float_input", tensor));
long prediction = ((long[]) result.get(0).getValue())[0];
// prediction == 1 → Failure imminent!
```

**Graceful Fallback:** If the `.onnx` file is not present (e.g., during development), the service falls back to a mathematical heuristic:
```java
private boolean predictWithHeuristic(double cpu, double mem, double lat, double err) {
    int riskScore = 0;
    if (cpu > 80.0) riskScore += 2;
    if (mem > 85.0) riskScore += 2;
    if (lat > 1000.0) riskScore += 3;
    if (err > 0.1) riskScore += 4;
    return riskScore >= 4;
}
```

### Step 6: Proactive Circuit Breaking (`PredictiveScheduler.java`)

Every 10 seconds, the scheduler:
1. Gathers current system telemetry
2. Feeds it to the ONNX model
3. If prediction == FAILURE_IMMINENT:
   - Calls `circuitBreakerService.tripCircuit("inventory-service")`
   - The circuit opens **before any customer request fails**
   - All traffic is instantly rerouted to the fallback/cache

### Production-Grade Equivalent:
| What We Built | Production Version |
|--------------|-------------------|
| Trained once on Kaggle data, static `.onnx` file | Model trained on real production telemetry from Prometheus/Datadog, with measured precision/recall/F1 score |
| No retraining pipeline | Automated retraining pipeline (MLflow, Kubeflow) triggered weekly or when model drift is detected |
| No A/B testing | Shadow mode: model runs predictions but doesn't act on them for 2 weeks, comparing predictions against actual incidents to measure accuracy before enabling proactive circuit tripping |
| Random telemetry in scheduler | Real Micrometer metrics scraped from the application's `/actuator/prometheus` endpoint |

> **Interview Defense:** *"The model achieves ~95% accuracy on the Kaggle dataset. In production, I would run it in shadow mode first — making predictions but not acting on them — to measure precision and recall against real incidents. I would only enable proactive circuit tripping after validating that the false-positive rate is below an acceptable threshold (e.g., <2%), because a false positive means unnecessarily blocking real customer traffic."*

---

# Part 8: Observability & Dashboard

## 8.1 Metrics Pipeline

```
Application Code
     │
     ├─ Micrometer (in-app metrics library)
     │    ├─ Counter: circuit_breaker_opened_total
     │    ├─ Timer: order_processing_duration
     │    ├─ Gauge: circuit_breaker_state (0=CLOSED, 1=HALF_OPEN, 2=OPEN)
     │    └─ Counter: healing_actions_executed_total
     │
     ▼
Prometheus (scrapes /actuator/prometheus every 15s)
     │
     ▼
Grafana (dashboards and alerting)
```

## 8.2 React Dashboard (NOC — Network Operations Center)

The frontend is a real-time dashboard built with:
- **React 18** for the UI
- **WebSocket (STOMP)** for live updates without polling
- **framer-motion** for state-change animations

Key panels:
- Live circuit breaker states for all 3 services (color-coded: green/yellow/red)
- Recovery event timeline
- Chaos experiment results with resilience scores
- Audit log of every automated action the system has taken

### Design Philosophy:
- **Dark theme** (`#0A0E14`) — Inspired by real NOC dashboards at data centers
- **Red is reserved for failures only** — If the dashboard is all dark/green, the system is healthy
- **Animations only on state changes** — The dashboard is dead still when healthy, creating visual contrast when something breaks

---

# Part 9: Security & Infrastructure

## 9.1 Authentication (JWT)

All `/admin/**` endpoints (chaos controls, circuit breaker overrides) are protected by JWT tokens:
```
POST /auth/login → Returns JWT token
GET /admin/chaos/* → Requires valid JWT in Authorization header
```

**Why JWT instead of session cookies?**
Because the application runs as multiple stateless replicas behind a load balancer. Session cookies require sticky sessions or a shared session store. JWT tokens are self-contained — any replica can validate them independently.

## 9.2 Infrastructure (Docker Compose)

```
┌─────────────────────────────────────────────────┐
│                   Nginx (Port 80)               │
│               Load Balancer (Round Robin)        │
│    ┌──────────────┐       ┌──────────────┐      │
│    │  Backend :1   │       │  Backend :2  │      │
│    │  (Replica 1)  │       │  (Replica 2) │      │
│    └──────┬───────┘       └──────┬───────┘      │
│           │                       │              │
│    ┌──────┴───────────────────────┴──────┐      │
│    │              Redis (Port 6379)       │      │
│    │    Circuit Breaker State Store       │      │
│    └─────────────────────────────────────┘      │
│    ┌─────────────────────────────────────┐      │
│    │           MySQL (Port 3306)          │      │
│    │    Orders, Incidents, Audit Logs     │      │
│    └─────────────────────────────────────┘      │
│    ┌─────────────────────────────────────┐      │
│    │         Prometheus + Grafana         │      │
│    │          Metrics & Dashboards        │      │
│    └─────────────────────────────────────┘      │
└─────────────────────────────────────────────────┘
```

### Production-Grade Equivalent:
| What We Built | Production Version |
|--------------|-------------------|
| Docker Compose on one machine | Kubernetes (EKS/GKE) with auto-scaling, multi-AZ deployment |
| Single Redis instance | Redis Sentinel or Redis Cluster for automatic failover |
| Single MySQL instance | MySQL with read replicas, or Amazon Aurora |
| Nginx round-robin | AWS ALB / Envoy service mesh with health-check-aware routing |

---

# Part 10: Complete File Map

## Services (Business Logic)
| File | Purpose |
|------|---------|
| `CircuitBreakerService.java` | Redis-backed distributed circuit breaker with Lua CAS scripts |
| `OrderService.java` | 3-step pipeline: Inventory → Payment → Notification |
| `InventoryService.java` | Simulated stock check (Critical dependency) |
| `PaymentService.java` | Simulated payment gateway (Deferrable dependency) |
| `NotificationService.java` | Simulated email sender (Fire-and-forget) |
| `PredictiveMLService.java` | ONNX Runtime inference engine with fallback heuristic |
| `ChaosAgentService.java` | Automated chaos experiment lifecycle |
| `RecoveryService.java` | Autonomous healing action executor |
| `SLOService.java` | Error budget burn rate calculator |
| `PostmortemAiService.java` | LLM-powered incident analysis via LangChain4j |
| `RunbookGeneratorService.java` | Dynamic runbook generation |
| `RunbookVectorStore.java` | In-memory vector store for RAG |
| `IncidentService.java` | Incident lifecycle management (create → resolve) |
| `AlertService.java` | Alert dispatching |
| `AuditService.java` | Immutable audit trail logging |

## Schedulers (Background Tasks)
| File | Purpose |
|------|---------|
| `RecoveryScheduler.java` | Monitors all 3 circuits, sends canary probes |
| `PredictiveScheduler.java` | Runs ML model every 10s for proactive prevention |
| `ChaosAgentScheduler.java` | Periodically triggers chaos experiments |
| `HealthScheduler.java` | Records system health metrics |
| `PrometheusSLOChecker.java` | Checks SLO burn rates from Prometheus |

## Controllers (API Layer)
| File | Purpose |
|------|---------|
| `OrderController.java` | POST /orders — Create orders |
| `AdminController.java` | Chaos injection, circuit breaker controls |
| `SystemHealthController.java` | GET /health — System health status |
| `IncidentController.java` | Incident CRUD |
| `MetricsController.java` | Recovery and failure metrics |
| `AuthController.java` | JWT login |

## ML Pipeline
| File | Purpose |
|------|---------|
| `docs/ml/train_model.py` | Python training script for Google Colab |
| `docs/ml/distributed_system_architecture_stress_dataset.csv` | Real Kaggle dataset |
| `src/main/resources/predictive_model.onnx` | Exported trained model |

---

# Part 11: The One-Minute Elevator Pitch

> *"I built SHBS — a self-healing distributed backend in Spring Boot that prevents cascading failures across microservices. It uses a custom Redis-backed circuit breaker with Lua atomic scripts to coordinate failure detection across replicas. The system classifies dependencies as Critical, Deferrable, or Fire-and-Forget, allowing it to degrade gracefully instead of crashing. I built a Chaos Engineering agent that attacks the system in real-time to prove resilience. When incidents occur, an LLM automatically generates root-cause postmortems using RAG. Most importantly, I trained a Random Forest model on real Kaggle telemetry data, exported it to ONNX, and embedded it in the JVM to predict and prevent failures before they impact customers."*
