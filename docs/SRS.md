# Software Requirements Specification (SRS)

## Project Title
**Self-Healing Backend System**

---

## 1. Introduction

### 1.1 Purpose
The purpose of this document is to define the functional and non-functional requirements of the **Self-Healing Backend System**. This system aims to improve backend reliability by detecting failures, handling them gracefully, and preparing the system for automatic recovery without manual intervention.

This document is intended for:
- Project supervisors / professors  
- Developers  
- Reviewers and evaluators  

---

### 1.2 Scope
The Self-Healing Backend System is a Spring Boot–based backend application focused on **fault tolerance and system resilience**. Unlike traditional CRUD-based applications, this project emphasizes backend behavior under failure conditions.

The system:
- Exposes REST APIs
- Logs system behavior
- Stores failure metrics in a database
- Handles failures gracefully
- Lays the foundation for self-healing mechanisms such as circuit breakers and automated recovery

---

### 1.3 Definitions, Acronyms, and Abbreviations

| Term | Description |
|----|----|
| API | Application Programming Interface |
| REST | Representational State Transfer |
| SRS | Software Requirements Specification |
| DB | Database |
| Fault Tolerance | Ability of a system to continue functioning despite failures |
| Self-Healing | Automatic detection and recovery from failures |

---

## 2. Overall Description

### 2.1 Product Perspective
The system is a standalone backend application developed using **Spring Boot**. It follows a **layered architecture** consisting of Controller, Service, and Repository layers.

The system interacts with:
- Clients (Browser / Postman / Frontend)
- MySQL database for persistent storage

---

### 2.2 Product Functions
At a high level, the system performs the following functions:
- Accepts API requests
- Processes requests through service logic
- Detects and logs failures
- Stores failure-related data
- Returns structured responses to clients
- Prepares for automated recovery mechanisms

---

### 2.3 User Classes and Characteristics

| User Type | Description |
|----|----|
| Developer / Admin | Monitors system behavior and failure metrics |
| Client | Sends API requests to the backend |

*Note: Authentication is not required in the prototype phase.*

---

### 2.4 Operating Environment
- Programming Language: Java  
- Framework: Spring Boot  
- Database: MySQL  
- Operating System: Platform Independent  
- API Client: Browser / Postman  

---

### 2.5 Design and Implementation Constraints
- The system is implemented as a prototype.
- Advanced distributed system features are simplified.
- The application runs as a single backend service.

---

## 3. System Architecture

### 3.1 Architectural Overview
The system follows a **layered architecture**:

```
Client
  ↓
Controller Layer
  ↓
Service Layer
  ↓
Repository Layer
  ↓
MySQL Database
```

Cross-cutting concerns such as logging and exception handling are implemented centrally.

---

## 4. Functional Requirements

### 4.1 API Request Handling
- The system shall expose REST APIs to retrieve system status.
- The system shall process incoming HTTP requests through the controller layer.

---

### 4.2 Failure Detection
- The system shall simulate failures for testing purposes.
- The system shall detect runtime exceptions during request processing.

---

### 4.3 Exception Handling
- The system shall handle exceptions globally.
- The system shall return structured JSON error responses to clients.

---

### 4.4 Logging
- The system shall log API execution flow.
- The system shall log failure events using appropriate severity levels.

---

### 4.5 Failure Metrics Storage
- The system shall store failure occurrences in a MySQL database.
- The system shall maintain persistent failure records for analysis.

---

### 4.6 System Status Monitoring
- The system shall expose a system health endpoint.
- The system shall indicate whether the system is in a healthy or degraded state.

---

### 4.7 Self-Healing (Future Scope)
- The system shall support circuit breaker mechanisms.
- The system shall support fallback responses.
- The system shall support automated recovery logic.

---

## 5. Non-Functional Requirements

### 5.1 Reliability
- The system shall remain operational during partial failures.
- The system shall avoid cascading failures.

---

### 5.2 Performance
- The system shall handle concurrent API requests in a prototype environment.
- Performance optimization is not the primary focus of the current phase.

---

### 5.3 Scalability
- The system design shall allow future horizontal scaling.

---

### 5.4 Maintainability
- The system shall follow clean code practices and layered architecture.
- Logging and exception handling shall be centralized.

---

### 5.5 Security
- Sensitive data shall not be logged.
- Authentication and authorization are out of scope for the prototype phase.

---

## 6. Assumptions and Dependencies
- MySQL database is available and running.
- The system is deployed in a controlled environment.
- Network failures are simulated programmatically.

---

## 7. Future Enhancements
- Circuit breaker implementation
- Degraded mode responses
- Automatic recovery scheduler
- Frontend monitoring dashboard
- Advanced observability tools

---

## 8. Conclusion
The Self-Healing Backend System provides a foundational prototype for building reliable backend services. By focusing on failure detection, observability, and recovery preparedness, the system demonstrates real-world backend engineering principles applicable to enterprise systems.
