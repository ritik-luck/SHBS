# SHBS — SRE Upgrade Checklist

> This document tracks the full implementation of the SRE-grade upgrade.
> Each item links to the file it modifies or creates.

---

## 1. Load & Chaos Testing (k6)
- [x] Create [`k6/load-test.js`](../k6/load-test.js) — baseline load test script
- [x] Create [`k6/chaos-test.js`](../k6/chaos-test.js) — failure injection under load
- [x] Create [`k6/README.md`](../k6/README.md) — instructions for running and interpreting results

## 2. Observability Stack (Prometheus + Grafana)
- [x] Add `micrometer-registry-prometheus` to `pom.xml`
- [x] Configure Prometheus metrics endpoint in `application.properties`
- [x] Create [`monitoring/prometheus.yml`](../monitoring/prometheus.yml) — Prometheus scrape config
- [x] Create [`monitoring/grafana/provisioning/datasources/datasource.yml`](../monitoring/grafana/provisioning/datasources/datasource.yml)
- [x] Create [`monitoring/grafana/provisioning/dashboards/dashboard.yml`](../monitoring/grafana/provisioning/dashboards/dashboard.yml)
- [x] Create [`monitoring/grafana/dashboards/shbs-dashboard.json`](../monitoring/grafana/dashboards/shbs-dashboard.json)
- [x] Add `prometheus` and `grafana` containers to `docker-compose.yml`

## 3. SLO / Error Budget Engine
- [x] Create [`SLOService.java`](../backend/src/main/java/com/selfhealing/backend/service/SLOService.java) — rolling window SLI/SLO tracker
- [x] Create [`SLOController.java`](../backend/src/main/java/com/selfhealing/backend/controller/SLOController.java) — expose `/metrics/slo` endpoint
- [x] Wire SLO checks into `FailureMetricService.java`

## 4. Security Upgrade (JWT)
- [x] Add `jjwt` dependencies to `pom.xml`
- [x] Create [`JwtUtil.java`](../backend/src/main/java/com/selfhealing/backend/security/JwtUtil.java) — token generation & validation
- [x] Create [`JwtAuthFilter.java`](../backend/src/main/java/com/selfhealing/backend/security/JwtAuthFilter.java) — stateless request filter
- [x] Rewrite [`SecurityConfig.java`](../backend/src/main/java/com/selfhealing/backend/config/SecurityConfig.java) — JWT filter chain
- [x] Create [`AuthController.java`](../backend/src/main/java/com/selfhealing/backend/controller/AuthController.java) — `/admin/login` endpoint
- [x] Update [`api.js`](../backend/frontend/src/api.js) — JWT-based auth interceptors

## 5. Automated Tests (JUnit + Mockito)
- [x] Create [`CircuitBreakerServiceTest.java`](../backend/src/test/java/com/selfhealing/backend/service/CircuitBreakerServiceTest.java)
- [x] Create [`SLOServiceTest.java`](../backend/src/test/java/com/selfhealing/backend/service/SLOServiceTest.java)

## 6. Distributed Replicas (Nginx LB + Multi-Instance)
- [x] Create [`nginx/nginx.conf`](../nginx/nginx.conf) — upstream round-robin config
- [x] Update [`docker-compose.yml`](../docker-compose.yml) — Nginx LB, 2x backend replicas, Prometheus, Grafana

## 7. Architecture Documentation
- [x] Rewrite [`README.md`](../README.md) — Mermaid diagrams, SLI/SLO definitions, tradeoff discussions

## 8. Production Readiness & Infrastructure
- [x] Secure Configuration & Ports (`docker-compose.yml`, `.env.example`)
- [x] CI/CD Pipeline Rebuild (`.github/workflows/ci.yml`, `.github/workflows/deploy.yml`)
- [x] Docker & Compose Hardening (`Dockerfile`, `nginx/nginx.conf`)
- [x] Deployment & Rollback Runbooks (`docs/runbooks/`)
- [x] AWS & Terraform Infrastructure (`infrastructure/terraform/`)
