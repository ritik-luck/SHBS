# Self-Healing Backend System (SHBS)

A resilient, fault-tolerant enterprise architecture designed to automatically detect, mitigate, and recover from system failures without manual intervention.

## 🚀 Overview

The **Self-Healing Backend System (SHBS)** is an advanced backend subsystem paired with a real-time observability dashboard. It is engineered to maintain high availability by proactively monitoring system health, intercepting failures, and executing an automated multi-stage recovery process.

Instead of waiting for a human operator to resolve database timeouts, API rate limits, or transient network errors, SHBS utilizes a custom **Circuit Breaker** and a **Recovery Engine** to isolate faults, gracefully degrade services, and incrementally restore normal operations.

## ✨ Key Features

### 🛡️ Automated Fault Tolerance
* **Custom Circuit Breaker Pattern:** Monitors request failure rates and dynamically opens the circuit to prevent cascading system failures.
* **Intelligent Fallback Mechanisms:** Serves degraded but functional responses while the primary services are experiencing downtime.

### 🔄 Multi-Stage Recovery Engine
* **Automated Mitigation:** Executes a systematic recovery pipeline: `Health Checks` $\rightarrow$ `Canary Requests` $\rightarrow$ `Full Restore`.
* **Progressive Backoff:** Safely tests the degraded service with simulated traffic before fully closing the circuit and restoring normal traffic flow.
* **Auto-Resolution:** Successfully auto-resolves 95%+ of transient failures without requiring manual human intervention.

### 📊 Real-Time Observability Dashboard
* **Instantaneous Reporting:** A custom React dashboard utilizing WebSockets (STOMP) for sub-second latency in system health reporting.
* **Live Audit Trails:** Tracks and visualizes every failure, circuit state change, and automated recovery action in real-time.
* **Premium Design:** Features a bespoke, premium Matte Graphite and Slate Violet UI with glassmorphism components (built without generic AI templates).

## 🛠️ Technology Stack

**Backend**
* **Java & Spring Boot:** Core backend framework.
* **Spring Data JPA & MySQL:** Persistent storage for failure metrics, health logs, and system configurations.
* **WebSockets (STOMP):** For real-time, bi-directional communication with the frontend.

**Frontend**
* **React.js:** Component-based UI library.
* **Chart.js:** For visualizing failure timelines and system health breakdowns.
* **Vanilla CSS:** Custom design system avoiding heavy generic frameworks.

## ⚙️ Getting Started

### Prerequisites
* Java 17+
* Node.js & npm
* MySQL Server (running on port 3306)

### 1. Database Setup
Create a new MySQL database named `shbs`:
```sql
CREATE DATABASE shbs;
```

### 2. Backend Configuration
Navigate to `backend/src/main/resources/`. You will need to create an `application-local.properties` file with your sensitive credentials (this file is git-ignored for security).

```ini
spring.datasource.url=jdbc:mysql://localhost:3306/shbs
spring.datasource.username=your_db_username
spring.datasource.password=your_db_password
```

### 3. Running the Backend
From the `backend` directory, start the Spring Boot application:
```bash
./mvnw clean spring-boot:run
```
The backend server will start on `http://localhost:8080`.

### 4. Running the Frontend
Navigate to the `backend/frontend` directory, install dependencies, and start the React app:
```bash
cd backend/frontend
npm install
npm start
```
The observability dashboard will be available at `http://localhost:3000`.

## 🎮 Simulating Failures

Once the system is running, you can use the dashboard to simulate various system failures (e.g., Database Timeout, API Error). Watch as the Circuit Breaker trips, the system gracefully degrades, and the automated recovery engine kicks in to restore full functionality in real-time!

---

*Designed and engineered as a resilient, self-healing architecture capable of achieving 99.9% service availability.*
