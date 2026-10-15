# SHBS — Production Readiness & AWS Deployment Guide

> **Version**: 1.0  
> **Date**: 2026-07-11  
> **Status**: Planning  
> **Author**: SHBS Engineering

---

## Table of Contents

1. [Executive Summary](#1-executive-summary)
2. [Current System Audit](#2-current-system-audit)
3. [Production Gap Analysis](#3-production-gap-analysis)
4. [Target AWS Architecture](#4-target-aws-architecture)
5. [Phase 0: Code Hardening](#5-phase-0-code-hardening)
6. [Phase 1: Containerization](#6-phase-1-containerization)
7. [Phase 2: AWS Infrastructure](#7-phase-2-aws-infrastructure)
8. [Phase 3: True Self-Healing](#8-phase-3-true-self-healing)
9. [Phase 4: Observability & Security](#9-phase-4-observability--security)
10. [Phase 5: CI/CD & Operations](#10-phase-5-cicd--operations)
11. [Cost Estimation](#11-cost-estimation)
12. [Production Checklist](#12-production-checklist)

---

## 1. Executive Summary

The Self-Healing Backend System (SHBS) is a Spring Boot application demonstrating automated fault detection, circuit breaking, and recovery. While the core architecture (circuit breaker, healing strategy engine, recovery pipeline, real-time dashboard) is well-designed, **the system currently operates as a prototype** with several critical gaps preventing production deployment.

### Key Findings

| Category | Status | Verdict |
|----------|--------|---------|
| **Self-Healing Logic** | ⚠️ Partial | Circuit breaker works, but recovery health checks and canary requests are **hardcoded stubs** (always return `true`) |
| **Security** | 🔴 None | No authentication, no HTTPS, open CORS, hardcoded credentials |
| **Containerization** | 🔴 None | No Dockerfile, no Docker Compose |
| **Infrastructure as Code** | 🔴 None | No Terraform, no CloudFormation |
| **CI/CD** | 🔴 None | No automated pipeline |
| **Testing** | 🔴 Minimal | Only the default Spring Boot test class |
| **Observability** | ⚠️ Partial | WebSocket dashboard exists, but no structured logging, no Prometheus metrics, no CloudWatch |
| **Database Management** | 🔴 Risky | `ddl-auto=update` with no migration tooling |

### Is it a "Self-Healing" System?

**Partially.** The system implements:
- ✅ **Real** circuit breaker state machine (CLOSED → OPEN → HALF_OPEN)
- ✅ **Real** failure classification and severity grading
- ✅ **Real** healing strategy decision engine
- ✅ **Real** exponential backoff in recovery
- ❌ **Fake** health checks (hardcoded `return true`)
- ❌ **Fake** canary/test requests (hardcoded `return true`)
- ❌ **No** actual remediation (no connection pool restart, no service restart, no cache invalidation)

**Verdict**: It's a **self-healing framework** with the decision-making logic in place, but the actual healing actions are stubs. Phase 3 of this document converts it into a genuinely self-healing system.

---

## 2. Current System Audit

### 2.1 Architecture Overview

```
┌─────────────────────────────────────────────────────────────────┐
│                        Frontend (React)                         │
│  Dashboard │ Failure Analytics │ Recovery │ Audit │ Config │ Sim│
└──────────────────────┬──────────────────────────────────────────┘
                       │ WebSocket (STOMP) + REST
┌──────────────────────┴──────────────────────────────────────────┐
│                    Spring Boot Backend                           │
│                                                                  │
│  ┌──────────┐  ┌────────────────┐  ┌──────────────────┐        │
│  │Controllers│  │  Services (14) │  │  Schedulers (2)  │        │
│  │  (4)      │  │                │  │  Health: 5s      │        │
│  │           │  │  CircuitBreaker│  │  Recovery: 5s    │        │
│  │  Admin    │  │  Recovery      │  └──────────────────┘        │
│  │  Hello    │  │  HealingEngine │                               │
│  │  Metrics  │  │  FailureMetric │  ┌──────────────────┐        │
│  │  Health   │  │  Alert         │  │  AOP Aspect      │        │
│  └──────────┘  │  Audit         │  │  RequestLogging   │        │
│                │  Config         │  └──────────────────┘        │
│                │  Fallback       │                               │
│                │  Degraded       │  ┌──────────────────┐        │
│                │  Email          │  │  Exception        │        │
│                │  FailureSim     │  │  GlobalHandler    │        │
│                │  SystemHealth   │  └──────────────────┘        │
│                │  Status         │                               │
│                │  Classifier     │                               │
│                └────────────────┘                               │
│                                                                  │
│  ┌──────────────────┐  ┌───────────────────┐                   │
│  │  Repositories (6)│  │  Models (12)      │                   │
│  │  Alert           │  │  Alert            │                   │
│  │  AuditLog        │  │  AuditLog         │                   │
│  │  FailureMetric   │  │  CircuitBreakerSt.│                   │
│  │  HealthMetric    │  │  FailureCategory  │                   │
│  │  RecoveryMetric  │  │  FailureMetric    │                   │
│  │  SystemConfig    │  │  ...              │                   │
│  └──────────────────┘  └───────────────────┘                   │
│                                                                  │
└──────────────────────┬──────────────────────────────────────────┘
                       │
                ┌──────┴──────┐
                │  MySQL DB   │
                │  (localhost) │
                └─────────────┘
```

### 2.2 Technology Stack

| Component | Technology | Version |
|-----------|-----------|---------|
| Backend Runtime | Java | 17 |
| Backend Framework | Spring Boot | 3.3.2 |
| Resilience Library | Resilience4j | 2.3.0 |
| Database | MySQL | 8.x |
| ORM | Spring Data JPA + Hibernate | (via Boot) |
| WebSocket | Spring WebSocket + STOMP | (via Boot) |
| Email | Spring Mail | (via Boot) |
| Frontend | React | 19.2.4 |
| Charts | Chart.js + react-chartjs-2 | 4.5.1 / 5.3.1 |
| WebSocket Client | @stomp/stompjs | 7.3.0 |
| HTTP Client | Axios | 1.13.6 |
| UI Framework | MUI Material | 7.3.9 |
| Build Tool | Maven | (wrapper) |

### 2.3 Self-Healing Pipeline Flow

```
Failure Occurs
      │
      ▼
FailureMetricService.recordFailure()
      │
      ├── Classify: type → FailureCategory
      ├── Classify: count → FailureSeverity
      ├── Save FailureMetric to DB
      ├── Audit log the failure
      ├── If CRITICAL → AlertService → Email
      ├── CircuitBreakerService.recordFailure()
      │       └── If count >= threshold → state = OPEN
      └── HealingStrategyEngine.decideStrategy()
              │
              ├── Health >= 80 → DO_NOTHING
              ├── Health >= 50 → SERVE_DEGRADED
              ├── Health >= 30 → SERVE_FALLBACK
              ├── Failures >= 5 && CLOSED → APPLY_CIRCUIT_BREAKER
              ├── State == OPEN → TRIGGER_RECOVERY
              └── else → ALERT_ADMIN

Recovery Pipeline (every 5 seconds via scheduler):
      │
      ├── If state != OPEN → skip
      ├── Wait (exponential backoff: 2s → 4s → 8s → ... → 30s max)
      ├── performHealthCheck() → ❌ FAKE (always true)
      ├── performTestRequest() → ❌ FAKE (always true)
      ├── Move to HALF_OPEN
      ├── If success count >= threshold (3)
      │       └── resetCircuit() → state = CLOSED ✅
      └── If failed → increment retry, increase backoff
```

---

## 3. Production Gap Analysis

### 3.1 Critical (Must Fix Before Deployment)

| # | Gap | File(s) Affected | Risk if Not Fixed |
|---|-----|-------------------|-------------------|
| 1 | Health checks return hardcoded `true` | `RecoveryService.java` | Recovery pipeline cannot detect actual failures — system claims recovery when nothing is checked |
| 2 | Canary test requests return hardcoded `true` | `RecoveryService.java` | Traffic could be restored to a still-broken service |
| 3 | Circuit breaker state in memory | `CircuitBreakerService.java` | Server restart resets to CLOSED, masking active failures |
| 4 | No authentication on admin endpoints | `AdminController.java` | Anyone can simulate failures, reset circuits, clear metrics |
| 5 | No Dockerfile | — | Cannot deploy to any container platform |
| 6 | No `/actuator/health` endpoint | `pom.xml` | AWS ALB cannot health-check the application |
| 7 | `ddl-auto=update` | `application.properties` | Can silently alter production schema causing data loss |
| 8 | No database migrations | — | Schema changes are untracked and unreproducible |
| 9 | Frontend hardcoded to `localhost:8080` | `api.js`, `Dashboard.js` | Frontend cannot communicate with deployed backend |
| 10 | No HTTPS/TLS | — | Data transmitted in plaintext |

### 3.2 High (Should Fix Before Deployment)

| # | Gap | File(s) Affected | Impact |
|---|-----|-------------------|--------|
| 11 | `@CrossOrigin("http://localhost:3000")` | `AdminController.java` | CORS blocks requests from production domain |
| 12 | WebSocket `setAllowedOrigins("*")` | `WebSocketConfig.java` | Security vulnerability — any site can connect |
| 13 | Hardcoded email recipient | `EmailNotificationService.java` | Cannot change alert recipient without code change |
| 14 | `application-local.properties` may contain secrets | `resources/` | Credentials potentially in source control |
| 15 | No CI/CD pipeline | — | Manual builds are error-prone and slow |
| 16 | No production Spring profile | — | No environment-specific configuration |

### 3.3 Medium (Should Fix for Production Quality)

| # | Gap | File(s) Affected | Impact |
|---|-----|-------------------|--------|
| 17 | `System.out.println` throughout codebase | Multiple services | Logs are unstructured, cannot be queried in CloudWatch |
| 18 | `Thread.sleep()` blocks scheduler thread | `RecoveryService.java` | Blocks recovery scheduler, delays other scheduled tasks |
| 19 | In-memory degraded response cache | `DegradedResponseService.java` | Cache lost on restart, no TTL management |
| 20 | Field injection via `@Autowired` | All services | Reduces testability, hides dependencies |
| 21 | No structured JSON logging | — | CloudWatch Logs Insights cannot parse logs |
| 22 | No graceful shutdown | — | In-flight requests are dropped during deployment |
| 23 | Default HikariCP connection pool settings | — | May not handle production load |

---

## 4. Target AWS Architecture

### 4.1 Architecture Diagram

```
                    ┌──────────────┐
                    │   Route 53   │
                    │  (DNS)       │
                    └──────┬───────┘
                           │
                    ┌──────┴───────┐
                    │  CloudFront  │
                    │  (CDN)       │
                    └──┬───────┬───┘
                       │       │
              ┌────────┘       └────────┐
              │                         │
     ┌────────┴────────┐      ┌────────┴────────┐
     │   S3 Bucket     │      │  ALB (HTTPS)    │
     │  React Frontend │      │  :443 → :8080   │
     └─────────────────┘      └────────┬────────┘
                                       │
                              ┌────────┴────────┐
                              │  ECS Fargate    │
                              │  Spring Boot    │
                              │  (Private Subnet)│
                              └──┬──────────┬───┘
                                 │          │
                        ┌────────┘          └────────┐
                        │                            │
               ┌────────┴────────┐          ┌────────┴────────┐
               │  RDS MySQL     │          │  ElastiCache    │
               │  (Multi-AZ)   │          │  Redis          │
               │  (Private)    │          │  (CB State)     │
               └───────────────┘          └─────────────────┘
```

### 4.2 Network Layout

| Subnet | Type | Components | CIDR |
|--------|------|-----------|------|
| Public Subnet A | Public | ALB, NAT Gateway | 10.0.1.0/24 |
| Public Subnet B | Public | ALB (multi-AZ) | 10.0.2.0/24 |
| Private Subnet A | Private | ECS Tasks, RDS Primary | 10.0.10.0/24 |
| Private Subnet B | Private | ECS Tasks, RDS Standby | 10.0.20.0/24 |

### 4.3 Security Groups

| Security Group | Inbound Rules | Outbound Rules |
|---------------|---------------|----------------|
| ALB SG | 443 from 0.0.0.0/0 | 8080 to ECS SG |
| ECS SG | 8080 from ALB SG | 3306 to RDS SG, 6379 to Redis SG, 443 to 0.0.0.0/0 |
| RDS SG | 3306 from ECS SG | — |
| Redis SG | 6379 from ECS SG | — |

---

## 5. Phase 0: Code Hardening

**Duration**: 2-3 hours  
**Risk**: Low  
**Prerequisites**: None

### 5.1 Replace System.out with SLF4J

**Affected Files:**
- `CircuitBreakerService.java` — 3 occurrences
- `RecoveryService.java` — 3 occurrences
- `FailureMetricService.java` — 1 occurrence
- `AlertService.java` — 1 occurrence

**Pattern:**
```java
// BEFORE
System.out.println("Circuit breaker moved to half-open");

// AFTER
private static final Logger log = LoggerFactory.getLogger(CircuitBreakerService.class);
log.info("Circuit breaker transitioned to HALF_OPEN state");
```

### 5.2 Spring Profiles

**Create `application-prod.properties`:**
```properties
# Database - use validate to prevent silent schema changes
spring.jpa.hibernate.ddl-auto=validate
spring.jpa.show-sql=false

# Graceful shutdown
server.shutdown=graceful
spring.lifecycle.timeout-per-shutdown-phase=30s

# Actuator - expose health for ALB
management.endpoints.web.exposure.include=health,info,metrics
management.endpoint.health.show-details=when-authorized

# Connection pool tuning
spring.datasource.hikari.maximum-pool-size=20
spring.datasource.hikari.minimum-idle=5
spring.datasource.hikari.connection-timeout=30000
spring.datasource.hikari.idle-timeout=600000
```

**Modify `application.properties`:**
```properties
# Externalized configuration
cors.allowed-origins=${CORS_ORIGINS:http://localhost:3000}
alert.email.recipient=${ALERT_EMAIL_RECIPIENT:ritiklucky48@gmail.com}
server.port=${PORT:8080}
```

### 5.3 Add Spring Boot Actuator

**Add to `pom.xml`:**
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
```

This enables `/actuator/health` which AWS ALB uses for health checks.

### 5.4 Frontend API URL Externalization

**Modify `api.js`:**
```javascript
const API_BASE = process.env.REACT_APP_API_URL || 'http://localhost:8080';

// All axios calls use API_BASE
```

**Create `.env.production`:**
```
REACT_APP_API_URL=https://api.your-domain.com
REACT_APP_WS_URL=wss://api.your-domain.com/ws
```

### 5.5 Database Migrations (Flyway)

**Add to `pom.xml`:**
```xml
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-mysql</artifactId>
</dependency>
```

**Create `V1__baseline_schema.sql`** in `src/main/resources/db/migration/`:
This captures the existing schema as the starting point for version-controlled migrations.

---

## 6. Phase 1: Containerization

**Duration**: 1-2 hours  
**Risk**: Low  
**Prerequisites**: Phase 0 complete

### 6.1 Backend Dockerfile

```dockerfile
# ── Build Stage ──
FROM eclipse-temurin:17-jdk-alpine AS build
WORKDIR /app
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw dependency:go-offline -B
COPY src/ src/
RUN ./mvnw package -DskipTests -B

# ── Runtime Stage ──
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --retries=3 \
    CMD wget -qO- http://localhost:8080/actuator/health || exit 1
ENTRYPOINT ["java", "-XX:+UseG1GC", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
```

### 6.2 Frontend Dockerfile

```dockerfile
# ── Build Stage ──
FROM node:18-alpine AS build
WORKDIR /app
COPY package*.json ./
RUN npm ci --production=false
COPY . .
ARG REACT_APP_API_URL
ARG REACT_APP_WS_URL
RUN npm run build

# ── Serve Stage ──
FROM nginx:alpine
COPY --from=build /app/build /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
```

### 6.3 Docker Compose

```yaml
version: "3.8"
services:
  db:
    image: mysql:8.0
    environment:
      MYSQL_DATABASE: shbs
      MYSQL_ROOT_PASSWORD: ${DB_PASSWORD:-dev-password}
    ports: ["3306:3306"]
    volumes: [mysql_data:/var/lib/mysql]
    healthcheck:
      test: ["CMD", "mysqladmin", "ping", "-h", "localhost"]
      interval: 10s
      retries: 5

  backend:
    build: ./backend
    ports: ["8080:8080"]
    environment:
      - SPRING_PROFILES_ACTIVE=local
      - DB_URL=jdbc:mysql://db:3306/shbs
      - DB_USERNAME=root
      - DB_PASSWORD=${DB_PASSWORD:-dev-password}
    depends_on:
      db:
        condition: service_healthy

  frontend:
    build:
      context: ./backend/frontend
      args:
        REACT_APP_API_URL: http://localhost:8080
        REACT_APP_WS_URL: ws://localhost:8080/ws
    ports: ["3000:80"]
    depends_on: [backend]

volumes:
  mysql_data:
```

---

## 7. Phase 2: AWS Infrastructure

**Duration**: 3-5 hours  
**Risk**: Medium  
**Prerequisites**: Phase 1 complete, AWS account configured

### 7.1 Terraform Project Structure

```
infrastructure/
├── main.tf              # Provider config, S3 backend for state
├── variables.tf         # Input variables
├── outputs.tf           # Exported values (ALB URL, RDS endpoint)
├── vpc.tf               # VPC, subnets, NAT gateway, route tables
├── ecs.tf               # ECS cluster, service, task definition
├── rds.tf               # RDS MySQL instance
├── alb.tf               # Application Load Balancer + target groups
├── ecr.tf               # Container registry
├── cloudfront.tf        # CDN distribution
├── s3.tf                # Frontend static hosting bucket
├── secrets.tf           # Secrets Manager for DB password
├── cloudwatch.tf        # Log groups, metric filters, alarms
├── iam.tf               # ECS task execution role, policies
├── security_groups.tf   # All security groups
└── terraform.tfvars     # Environment-specific values
```

### 7.2 Key Terraform Resources

**ECS Task Definition (simplified):**
```hcl
resource "aws_ecs_task_definition" "backend" {
  family                   = "shbs-backend"
  requires_compatibilities = ["FARGATE"]
  cpu                      = 512      # 0.5 vCPU
  memory                   = 1024     # 1 GB
  network_mode             = "awsvpc"

  container_definitions = jsonencode([{
    name  = "shbs-backend"
    image = "${aws_ecr_repository.backend.repository_url}:latest"
    portMappings = [{ containerPort = 8080 }]
    environment = [
      { name = "SPRING_PROFILES_ACTIVE", value = "prod" },
      { name = "CORS_ORIGINS", value = "https://your-domain.com" }
    ]
    secrets = [
      { name = "DB_URL", valueFrom = aws_secretsmanager_secret.db_url.arn },
      { name = "DB_PASSWORD", valueFrom = aws_secretsmanager_secret.db_password.arn }
    ]
    logConfiguration = {
      logDriver = "awslogs"
      options = {
        "awslogs-group"  = "/ecs/shbs-backend"
        "awslogs-region" = var.region
      }
    }
  }])
}
```

### 7.3 Deployment Steps

```bash
# 1. Initialize Terraform
cd infrastructure
terraform init

# 2. Plan (review what will be created)
terraform plan -out=tfplan

# 3. Apply
terraform apply tfplan

# 4. Build & push backend Docker image
aws ecr get-login-password | docker login --username AWS --password-stdin <account>.dkr.ecr.<region>.amazonaws.com
docker build -t shbs-backend ./backend
docker tag shbs-backend:latest <account>.dkr.ecr.<region>.amazonaws.com/shbs-backend:latest
docker push <account>.dkr.ecr.<region>.amazonaws.com/shbs-backend:latest

# 5. Build & deploy frontend to S3
cd backend/frontend
REACT_APP_API_URL=https://api.your-domain.com npm run build
aws s3 sync build/ s3://shbs-frontend-bucket/
aws cloudfront create-invalidation --distribution-id <ID> --paths "/*"
```

---

## 8. Phase 3: True Self-Healing

**Duration**: 4-6 hours  
**Risk**: Medium-High  
**Prerequisites**: Phase 0 complete

> **This is the most important phase.** It converts SHBS from a demo into a genuinely self-healing system.

### 8.1 Real Health Checks

**Current (FAKE):**
```java
private boolean performHealthCheck() {
    System.out.println("Health check passed");
    return true;  // ← Always says "healthy" — never actually checks anything
}
```

**Target (REAL):**
```java
private boolean performHealthCheck() {
    boolean dbHealthy = checkDatabaseConnectivity();
    boolean memoryHealthy = checkMemoryUsage();
    boolean threadPoolHealthy = checkThreadPoolUtilization();
    
    log.info("Health check results: DB={}, Memory={}, ThreadPool={}", 
             dbHealthy, memoryHealthy, threadPoolHealthy);
    
    return dbHealthy && memoryHealthy && threadPoolHealthy;
}

private boolean checkDatabaseConnectivity() {
    try {
        jdbcTemplate.queryForObject("SELECT 1", Integer.class);
        return true;
    } catch (Exception e) {
        log.error("Database connectivity check failed", e);
        return false;
    }
}

private boolean checkMemoryUsage() {
    Runtime rt = Runtime.getRuntime();
    double usedRatio = (double)(rt.totalMemory() - rt.freeMemory()) / rt.maxMemory();
    return usedRatio < 0.90; // Unhealthy if > 90% memory used
}

private boolean checkThreadPoolUtilization() {
    // Check if thread pool is overwhelmed
    ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
    int threadCount = threadBean.getThreadCount();
    return threadCount < 200; // Threshold for thread exhaustion
}
```

### 8.2 Real Canary Requests

**Current (FAKE):**
```java
private boolean performTestRequest() {
    System.out.println("Test request success");
    return true;  // ← Never sends a real request
}
```

**Target (REAL):**
```java
private boolean performTestRequest() {
    try {
        RestTemplate restTemplate = new RestTemplate();
        restTemplate.getForEntity("http://localhost:8080/health/status", String.class);
        log.info("Canary request succeeded");
        return true;
    } catch (Exception e) {
        log.warn("Canary request failed: {}", e.getMessage());
        return false;
    }
}
```

### 8.3 Persistent Circuit Breaker State (Redis)

**Add to `pom.xml`:**
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>
```

**Modified `CircuitBreakerService.java`:**
```java
@Service
public class CircuitBreakerService {

    private final StringRedisTemplate redis;
    private static final String STATE_KEY = "shbs:circuit:state";
    private static final String FAILURE_TIME_KEY = "shbs:circuit:lastFailureTime";

    public CircuitBreakerState getState() {
        String state = redis.opsForValue().get(STATE_KEY);
        return state != null ? CircuitBreakerState.valueOf(state) : CircuitBreakerState.CLOSED;
    }

    private void setState(CircuitBreakerState newState) {
        redis.opsForValue().set(STATE_KEY, newState.name());
    }
    
    // ... rest of logic uses setState() instead of `this.state = ...`
}
```

### 8.4 Self-Healing Actions (New)

Create `SelfHealingActionService.java` with actual remediation:

```java
@Service
public class SelfHealingActionService {

    public void resetConnectionPool(HikariDataSource dataSource) {
        HikariPoolMXBean poolBean = dataSource.getHikariPoolMXBean();
        poolBean.softEvictConnections();
        log.info("Connection pool soft-evicted — forcing fresh connections");
    }

    public void clearStaleCache(DegradedResponseService degradedService) {
        // Flush expired cached responses
    }

    public void triggerGarbageCollection() {
        System.gc(); // Hint to JVM — not guaranteed but helps under memory pressure
        log.info("Garbage collection hint sent to JVM");
    }
}
```

---

## 9. Phase 4: Observability & Security

**Duration**: 3-4 hours  
**Risk**: Medium  
**Prerequisites**: Phase 0 complete

### 9.1 Spring Security Configuration

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health").permitAll()
                .requestMatchers("/health/**").permitAll()
                .requestMatchers("/ws/**").permitAll()
                .requestMatchers("/admin/**").authenticated()
                .anyRequest().permitAll()
            )
            .httpBasic(Customizer.withDefaults());
        return http.build();
    }
}
```

### 9.2 Structured Logging (logback-spring.xml)

```xml
<configuration>
    <!-- Local: human-readable -->
    <springProfile name="local">
        <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
            <encoder>
                <pattern>%d{HH:mm:ss.SSS} %-5level [%thread] %logger{36} - %msg%n</pattern>
            </encoder>
        </appender>
        <root level="INFO"><appender-ref ref="CONSOLE"/></root>
    </springProfile>

    <!-- Production: JSON for CloudWatch -->
    <springProfile name="prod">
        <appender name="JSON" class="ch.qos.logback.core.ConsoleAppender">
            <encoder class="net.logstash.logback.encoder.LogstashEncoder"/>
        </appender>
        <root level="INFO"><appender-ref ref="JSON"/></root>
    </springProfile>
</configuration>
```

### 9.3 CloudWatch Alarms

| Alarm | Metric | Threshold | Action |
|-------|--------|-----------|--------|
| High Failure Rate | Custom: `shbs.failures.count` | > 10 in 5 min | SNS → Email |
| Circuit Breaker Open | Custom: `shbs.circuit.state` | = OPEN for > 2 min | SNS → Email |
| ECS CPU High | `CPUUtilization` | > 80% for 5 min | Auto-scale |
| ECS Memory High | `MemoryUtilization` | > 85% for 5 min | SNS → Email |
| RDS CPU High | `CPUUtilization` | > 80% for 10 min | SNS → Email |
| RDS Free Storage Low | `FreeStorageSpace` | < 1 GB | SNS → Email |
| ALB 5xx Errors | `HTTPCode_Target_5XX_Count` | > 50 in 5 min | SNS → Email |

---

## 10. Phase 5: CI/CD & Operations

**Duration**: 2-3 hours  
**Risk**: Low  
**Prerequisites**: Phase 2 complete

### 10.1 GitHub Actions Pipeline

```yaml
name: SHBS Deploy Pipeline

on:
  push:
    branches: [main]
  pull_request:
    branches: [main]

jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { java-version: '17', distribution: 'temurin' }
      - run: cd backend && ./mvnw test

  build-and-deploy:
    needs: test
    if: github.ref == 'refs/heads/main'
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      
      # Backend: Build & Push to ECR
      - run: cd backend && ./mvnw package -DskipTests
      - uses: aws-actions/configure-aws-credentials@v4
      - uses: aws-actions/amazon-ecr-login@v2
      - run: |
          docker build -t shbs-backend ./backend
          docker push $ECR_REGISTRY/shbs-backend:$GITHUB_SHA
      
      # Frontend: Build & Deploy to S3
      - run: |
          cd backend/frontend
          npm ci && npm run build
          aws s3 sync build/ s3://$S3_BUCKET/
          aws cloudfront create-invalidation --distribution-id $CF_DIST_ID --paths "/*"
      
      # Update ECS Service
      - run: aws ecs update-service --cluster shbs --service shbs-backend --force-new-deployment
```

### 10.2 Unit Test Plan

| Test Class | What It Tests | Priority |
|-----------|---------------|----------|
| `CircuitBreakerServiceTest` | State transitions: CLOSED→OPEN→HALF_OPEN→CLOSED | P0 |
| `HealingStrategyEngineTest` | Strategy selection based on health score and failure count | P0 |
| `RecoveryServiceTest` | Recovery pipeline: health check → canary → full restore | P0 |
| `SystemHealthServiceTest` | Health score calculation, status thresholds | P1 |
| `FailureMetricServiceTest` | Failure recording, classification, severity | P1 |
| `AlertServiceTest` | Alert cooldown, WebSocket dispatch, email trigger | P2 |
| `ConfigServiceTest` | Config read/write, default values | P2 |

---

## 11. Cost Estimation

### 11.1 Minimum Viable (Single-AZ, Development)

| Service | Spec | Monthly Cost |
|---------|------|-------------|
| ECS Fargate | 0.25 vCPU, 0.5GB | ~$10 |
| RDS MySQL | db.t3.micro, single-AZ | ~$15 |
| ALB | 1 LCU avg | ~$18 |
| S3 | < 1GB static | ~$0.50 |
| CloudFront | < 50GB transfer | ~$5 |
| ECR | < 5 images | ~$1 |
| CloudWatch | Basic logs | ~$5 |
| **Total** | | **~$55/month** |

### 11.2 Production-Grade (Multi-AZ)

| Service | Spec | Monthly Cost |
|---------|------|-------------|
| ECS Fargate | 0.5 vCPU, 1GB × 2 tasks | ~$40 |
| RDS MySQL | db.t3.small, Multi-AZ | ~$50 |
| ALB | 2 LCU avg | ~$22 |
| ElastiCache Redis | cache.t3.micro | ~$15 |
| S3 + CloudFront | Same | ~$6 |
| ECR + CloudWatch | Same | ~$10 |
| Secrets Manager | 3 secrets | ~$2 |
| **Total** | | **~$145/month** |

---

## 12. Production Checklist

### Pre-Deployment

- [ ] All `System.out.println` replaced with SLF4J logger
- [ ] `spring.jpa.hibernate.ddl-auto` set to `validate` in prod profile
- [ ] Flyway baseline migration created and tested
- [ ] Actuator health endpoint responds at `/actuator/health`
- [ ] Docker build succeeds for backend and frontend
- [ ] Docker Compose runs locally with all services
- [ ] Frontend builds with `REACT_APP_API_URL` pointing to production
- [ ] WebSocket URL externalized in frontend
- [ ] Spring Security protects `/admin/**` endpoints
- [ ] CORS origins read from environment variable
- [ ] Email recipient read from environment variable
- [ ] WebSocket origins restricted to production domain
- [ ] All secrets stored in AWS Secrets Manager
- [ ] Circuit breaker tests pass (unit tests)
- [ ] Recovery pipeline tests pass (unit tests)

### Infrastructure

- [ ] Terraform `plan` shows expected resources
- [ ] VPC with public and private subnets created
- [ ] RDS MySQL provisioned and accessible from ECS
- [ ] ECS task definition configured with correct environment
- [ ] ALB with HTTPS listener (ACM certificate)
- [ ] CloudFront distribution serving frontend from S3
- [ ] Security groups configured per specification
- [ ] CloudWatch log groups created

### Post-Deployment

- [ ] Health endpoint returns 200 through ALB
- [ ] WebSocket connection establishes through ALB
- [ ] Failure simulation works from deployed frontend
- [ ] Circuit breaker triggers and recovers
- [ ] Email alerts sent on CRITICAL failures
- [ ] CloudWatch logs flowing from ECS tasks
- [ ] CloudWatch alarms configured and tested
- [ ] GitHub Actions pipeline deploys successfully
- [ ] Zero-downtime rolling deployment verified

---

## Appendix: File Change Summary

### New Files to Create

| File | Phase |
|------|-------|
| `backend/Dockerfile` | Phase 1 |
| `backend/.dockerignore` | Phase 1 |
| `backend/frontend/Dockerfile` | Phase 1 |
| `backend/frontend/nginx.conf` | Phase 1 |
| `docker-compose.yml` | Phase 1 |
| `backend/src/main/resources/application-prod.properties` | Phase 0 |
| `backend/src/main/resources/logback-spring.xml` | Phase 4 |
| `backend/src/main/resources/db/migration/V1__baseline_schema.sql` | Phase 0 |
| `backend/frontend/.env.production` | Phase 0 |
| `infrastructure/*` (Terraform files) | Phase 2 |
| `.github/workflows/deploy.yml` | Phase 5 |
| `backend/src/main/java/.../config/SecurityConfig.java` | Phase 4 |
| `backend/src/main/java/.../config/RedisConfig.java` | Phase 3 |
| `backend/src/main/java/.../service/SelfHealingActionService.java` | Phase 3 |
| Unit test files (7 test classes) | Phase 5 |

### Files to Modify

| File | Phase | Changes |
|------|-------|---------|
| `pom.xml` | Phase 0, 3, 4 | Add actuator, AOP, flyway, redis, security, micrometer |
| `application.properties` | Phase 0 | Externalize config values |
| `WebSocketConfig.java` | Phase 0 | Dynamic CORS origins |
| `AdminController.java` | Phase 0 | Dynamic CORS origins |
| `EmailNotificationService.java` | Phase 0 | Externalize recipient |
| `RecoveryService.java` | Phase 3 | Real health checks + canary requests |
| `CircuitBreakerService.java` | Phase 3 | Redis-backed state |
| `api.js` | Phase 0 | Environment-based API URL |
| `Dashboard.js` | Phase 0 | Environment-based WS URL |
| All services with `System.out.println` | Phase 0 | Replace with SLF4J |
| All services with `@Autowired` fields | Phase 0 | Constructor injection |

---

*This document serves as the complete technical specification for taking SHBS from prototype to production. Each phase is designed to be independently executable and verifiable.*
