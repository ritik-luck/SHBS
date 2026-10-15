# AI Agent Context — SHBS (Self-Healing Backend System)

**Hello, fellow AI Agent!** 👋 
If you have been prompted to help with this repository, read this document first. It contains the strict architectural context, constraints, and rules you must follow when writing code or assisting the user with this project.

---

## 1. Project Identity & Goal
- **Name:** SHBS (Self-Healing Backend System)
- **Goal:** A production-grade, SRE-inspired distributed system that **autonomously detects, diagnoses, and recovers from failures without human intervention.** It implements cascading failure prevention across multiple services, tracks SLIs, calculates error budget burn rates, and executes pre-programmed self-healing actions entirely on its own. It also uses Machine Learning to **predict** failures before they happen.
- **Tone:** Professional, highly resilient, distributed, and SRE-grade.

---

## 2. Multi-Service Architecture (Cascading Failure Prevention)

The system simulates an E-commerce Order Pipeline with **three distinct service dependencies**, each representing a different failure handling strategy:

| Service | Dependency Type | On Failure |
|---------|----------------|------------|
| **InventoryService** | Critical | Order is **rejected** immediately |
| **PaymentService** | Deferrable | Order saved as `PAYMENT_PENDING`, payment queued for async retry |
| **NotificationService** | Fire-and-Forget | Failure silently ignored, order succeeds |

### Order Pipeline Flow (`OrderService.java`)
```
Customer Request → Inventory Check (Critical) → Payment (Deferrable) → Notification (Optional)
```
Each step is protected by its own **independent circuit breaker**. If one service degrades, the others continue operating normally, preventing cascading failure.

---

## 3. How Self-Healing Works (Deterministic Logic)

The system is **fully autonomous** using hardcoded mathematical thresholds. No human-in-the-loop. Here is the exact pipeline:

### Detection → Decision → Action → Verification

1. **Detection (SLO Engine):** The `SLOService` monitors every HTTP request in a **5-minute rolling window**. It continuously calculates the Error Budget Burn Rate.
2. **Decision (Distributed Circuit Breaker):** If failures exceed the threshold, the `CircuitBreakerService` (backed by **Redis Lua scripts** for atomicity across replicas) transitions through: `CLOSED → OPEN → HALF_OPEN → CLOSED`.
3. **Action (Autonomous Remediation):** The `SelfHealingActionService` executes recovery scripts automatically:
   - `HikariDataSource.softEvictConnections()` — Kills dead database connections.
   - Redis cache flush — Frees memory to prevent `OutOfMemoryError`.
   - `System.gc()` — Triggers garbage collection under memory pressure.
4. **Verification (`RecoveryScheduler`):** Monitors all 3 service circuits. Transitions `OPEN → HALF_OPEN`, sends canary probe requests, and only closes the circuit if probes succeed. If they fail, it trips back to `OPEN`.

> **Key Point for Agents:** The "self-healing" label is justified because **zero human action is required** at any point. The system detects, decides, acts, and verifies entirely on its own.

---

## 4. AI & ML Enhancements (Implemented)

### A. Predictive AIOps — ONNX ML Model (Implemented)
- **What:** A `RandomForestClassifier` trained on a real **Kaggle distributed systems telemetry dataset** (`distributed_system_architecture_stress_dataset.csv`). The model is exported to `.onnx` format and loaded into the JVM via **Microsoft ONNX Runtime**.
- **How:** `PredictiveScheduler` runs every 10 seconds, gathers telemetry (CPU, Memory, Latency, Error Rate), feeds it to `PredictiveMLService`, and if the model predicts an imminent crash, it calls `circuitBreakerService.tripCircuit()` to **preemptively open the circuit breaker** before any requests fail.
- **Fallback:** If the `.onnx` file is not present in `src/main/resources/`, the service transparently falls back to a mathematical heuristic.
- **Training Script:** `docs/ml/train_model.py` (designed to run in Google Colab).

### B. RAG-Powered AI Postmortem Agent (Implemented)
- **What:** When an incident is resolved, a background AI agent queries a **Vector Database** (`RunbookVectorStore`) using semantic search (RAG). It uses an LLM (`PostmortemAiService` via LangChain4j + Groq/Llama 3) to generate a Root Cause Analysis and dynamic runbooks.
- **Pipeline:** Error detected → Circuit opens → Incident auto-created → Autonomous fixes executed → Service recovers → Incident auto-resolved → AI Agent triggered → Postmortem + Runbook saved.

### C. Chaos Engineering Agent — Red Team (Implemented)
- **What:** `ChaosAgentService` runs automated experiments that intentionally inject failures (latency spikes, exception storms) into any of the 3 services.
- **How:** It validates the self-healing pipeline end-to-end:
  1. Ensure clean baseline (all circuits CLOSED)
  2. Inject failure into a random target service
  3. Wait & observe — did the circuit breaker OPEN fast enough?
  4. Remove failure — did the system HEAL autonomously?
  5. Score resilience (0-100) and log immutable audit trail
  6. Send telemetry to LLM for postmortem generation

---

## 5. Technology Stack
- **Backend:** Java 21, Spring Boot 3.3.2
- **Frontend:** React 18, WebSocket (STOMP), `framer-motion` for animations
- **Database:** MySQL 8.0 (Relational data, metrics, audit logs)
- **State & Cache:** Redis (Circuit Breaker state, Rate Limiting, distributed locks)
- **Machine Learning:** Scikit-learn (Python, training) → ONNX → Microsoft ONNX Runtime (Java, inference)
- **AI/LLMs:** LangChain4j, Groq (Llama 3) for automated postmortems & runbooks
- **Observability:** Micrometer, Prometheus, Grafana
- **Infrastructure:** Nginx (Load Balancer), Docker Compose, GitHub Actions (CI/CD)

## 6. Strict Architectural Rules (DO NOT BREAK THESE)

### A. The System is Distributed
The application runs as **2 backend replicas** behind an Nginx load balancer. 
- **Rule:** NEVER use in-memory state (like `ConcurrentHashMap`) to track distributed state like rate limits or circuit breaker status. 
- **Rule:** ALWAYS use **Redis** as the single source of truth for state across replicas.

### B. Security is Stateless
- **Rule:** The system uses JWT (JSON Web Tokens) for authentication (`/admin/**` endpoints). Do not implement HTTP Basic Auth or Session Cookies.

### C. Self-Healing Triggers
- **Rule:** The system uses an SLO engine. Healing actions are triggered based on **Burn Rate**, not just hard failure counts. New healing mechanisms must hook into the `SLOService`.

### D. ML Model Contract
- **Rule:** The ONNX model expects exactly **4 float inputs** in this order: `[cpu_usage, memory_usage, latency_ms, error_rate]`. The `error_rate` must be a decimal (0.0–1.0), not a percentage.
- **Rule:** The `PredictiveMLService` must always have a fallback heuristic so the application works even without the `.onnx` file.

### E. Frontend Design System (NOC)
The frontend is built as a Network Operations Center (NOC) dashboard.
- **Rule:** NO generic UI frameworks (like Tailwind or Bootstrap) unless specifically requested.
- **Rule:** The aesthetic is dark (`#0A0E14` void). Red (`#FF3B30`) is reserved **EXCLUSIVELY** for active failures/alerts.
- **Rule:** Animations (`framer-motion`) should only occur on active state changes.

### F. Deployment
- **Rule:** The deployment model is Docker Compose. Do not suggest Kubernetes or serverless unless explicitly asked.

## 7. Key Directories
- `/backend/src/main/java/.../service/` → Core logic (`CircuitBreakerService`, `PredictiveMLService`, `ChaosAgentService`, `OrderService`, `PaymentService`, `NotificationService`)
- `/backend/src/main/java/.../scheduler/` → `RecoveryScheduler`, `PredictiveScheduler`
- `/backend/src/main/resources/` → `predictive_model.onnx`, `application.properties`
- `/backend/frontend/src/components/` → React UI components
- `/docs/ml/` → ML training script (`train_model.py`) and Kaggle dataset
- `/nginx/` → Load balancer configuration
- `/monitoring/` → Prometheus config and Grafana dashboards
- `/k6/` → Chaos and load testing scripts

---
**Agent Directive:** Align all suggestions with the distributed, resilient, and SRE-focused nature of this repository. All self-healing logic must remain fully autonomous — no human-in-the-loop.
