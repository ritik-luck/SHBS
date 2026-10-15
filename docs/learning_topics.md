# SHBS — YouTube & Web Study Syllabus

Copy and paste these exact headings into YouTube or Google to learn the foundational concepts required to understand and explain this project.

## 1. System Architecture & Spring Boot
*Search these on YouTube to understand how the backend code is structured:*
- `Spring Boot 3 architecture explained`
- `Dependency Injection and Inversion of Control in Spring Boot`
- `Spring Data JPA and Hibernate crash course`
- `How to use Redis with Spring Boot`
- `Global Exception Handling in Spring Boot (@RestControllerAdvice)`
- `What is an API Gateway vs Load Balancer?`

## 2. Site Reliability Engineering (SRE) & Resilience
*Search these to understand the core logic of the Self-Healing system:*
- `Circuit Breaker Design Pattern explained (Microservices)`
- `What is Exponential Backoff and Jitter in system design?`
- `Resilience4j Spring Boot tutorial`
- `Difference between SLI, SLO, and SLA (SRE concepts)`
- `What is an Error Budget and Burn Rate?`
- `Chaos Engineering explained (k6 load testing)`

## 3. Distributed Systems & State
*Search these to understand why we used Redis and JWT:*
- `Stateful vs Stateless backend architecture`
- `Why use Redis for distributed caching and state?`
- `Round-Robin Load Balancing explained (Nginx)`
- `How to scale a Spring Boot application horizontally`

## 4. Security (JWT)
*Search these to understand the authentication flow:*
- `How JWT (JSON Web Tokens) work behind the scenes`
- `Stateless authentication vs Session-based authentication`
- `Spring Security JWT implementation tutorial`

## 5. Observability & Monitoring
*Search these to understand the metrics dashboard:*
- `What is Observability in software engineering?`
- `Prometheus and Grafana architecture explained`
- `Spring Boot Actuator and Micrometer tutorial`
- `Pull vs Push metrics monitoring (Why Prometheus pulls)`

## 6. Frontend & Real-Time Communication
*Search these to understand how the NOC Dashboard works:*
- `React useEffect and custom hooks explained`
- `How WebSockets work (Real-time client-server communication)`
- `Spring Boot WebSockets (STOMP protocol) tutorial`
- `Framer Motion React tutorial (UI animations)`

## 7. Infrastructure & DevOps
*Search these to understand the deployment pipeline:*
- `Docker and Docker Compose crash course for beginners`
- `What is a Reverse Proxy? (Nginx explained)`
- `GitHub Actions CI/CD pipeline tutorial`
- `HikariCP connection pool explained`

---
**💡 Interview Tip:** When watching these videos, always ask yourself: *"How did I apply this specific concept in the SHBS project?"* (e.g., when watching a video on Circuit Breakers, remind yourself that in SHBS, the circuit breaker state is stored in Redis so both Spring Boot replicas can share it).
