UML Overview – Self-Healing Backend System

1. Introduction

This document provides an overview of the UML Use Case Diagram for the Self-Healing Backend System.
The diagram represents the high-level functional behavior of the system, the actors interacting with it, and how the system detects failures, applies self-healing strategies, and recovers automatically while remaining observable to administrators.


⸻

1.1 UML Use Case Diagram

The following UML Use Case Diagram provides a high-level overview of the Self-Healing Backend System.
It illustrates the primary actors, system boundary, major subsystems, and the interactions that enable failure detection, self-healing decisions, recovery management, and administrative monitoring.


![UML Use Case Diagram](../uml.png)

*Figure: UML Use Case Diagram for Self-Healing Backend System*


⸻

2. Actors Description

2.1 Client / API Consumer

The Client represents any external entity that interacts with the backend through REST APIs. This includes frontend applications, mobile clients, or other backend services. The client sends API requests and receives responses without being exposed to internal failures or recovery mechanisms.

⸻

2.2 Admin

The Admin represents a system operator or developer responsible for monitoring and managing the system. The admin can view system health, inspect failure metrics, simulate failures for testing, configure thresholds, and view audit logs.

⸻

2.3 System Scheduler

The System Scheduler is a background component responsible for periodically triggering recovery checks. It enables the system to attempt automatic recovery without manual intervention.

⸻

2.4 Notification Service

The Notification Service is an external system used to send alerts when critical failures occur. It is outside the system boundary and is invoked only when alert conditions are met.

⸻

3. System Boundary

The Self-Healing Backend System boundary encloses all system functionalities implemented using Spring Boot. Any actor or service outside this boundary represents an external dependency.

⸻

4. Functional Overview by Subsystems

4.1 API Processing

This subsystem handles incoming REST API requests from clients.

Key responsibilities:
	•	Accept API requests
	•	Process requests through service logic
	•	Log each request
	•	Apply rate limiting
	•	Detect failures during execution

Logging and failure detection are mandatory operations and are modeled using <<include>> relationships.

⸻

4.2 Failure Monitoring

The Failure Monitoring subsystem is responsible for understanding and recording failures.

Key responsibilities:
	•	Detect runtime failures such as exceptions, timeouts, or dependency issues
	•	Classify failures based on type or severity
	•	Store failure metrics persistently
	•	Update overall system health status
	•	Audit healing actions taken by the system

This subsystem enables the backend to make informed, data-driven decisions.

⸻

4.3 Self-Healing Decision Engine

This subsystem acts as the core intelligence of the system.

Key responsibilities:
	•	Decide healing strategies based on failure frequency and system health
	•	Apply circuit breaker mechanisms to block unstable operations
	•	Provide fallback responses to ensure graceful degradation
	•	Serve degraded responses when necessary

Circuit breaker activation and degraded responses are modeled using <<extend>> relationships since they occur only under specific conditions.

⸻

4.4 Recovery Management

The Recovery Management subsystem ensures that the system can safely return to normal operation.

Key responsibilities:
	•	Periodically trigger recovery attempts using the system scheduler
	•	Attempt controlled recovery
	•	Restore normal operation once the system stabilizes

This completes the self-healing feedback loop.

⸻

4.5 Monitoring & Administration

This subsystem provides visibility and control to administrators.

Key responsibilities:
	•	View system health status
	•	View failure metrics
	•	Simulate failures for testing
	•	Configure healing thresholds
	•	View audit logs

This ensures transparency, trust, and manageability of the self-healing system.

⸻

5. Alerting Mechanism

When failures exceed predefined thresholds or are classified as critical, the system generates alerts. These alerts are sent to an external Notification Service. Alert generation is conditional and therefore modeled using <<extend>>.

⸻

6. Key UML Relationships Used
	•	<<include>>
Represents mandatory behavior that always occurs as part of a use case.
	•	<<extend>>
Represents conditional behavior that occurs only under specific circumstances.

⸻

7. Summary

The UML overview diagram illustrates a complete self-healing lifecycle:
	1.	Request processing
	2.	Failure detection and monitoring
	3.	Intelligent healing decisions
	4.	Automatic recovery
	5.	Administrative observability

This design focuses on fault tolerance, resilience, and reliability, making it suitable for real-world backend systems rather than feature-centric applications.

⸻

8. Conclusion

The UML overview clearly demonstrates how the Self-Healing Backend System handles failures gracefully, minimizes system downtime, and restores normal operation automatically while remaining transparent and controllable by administrators. It serves as a strong foundation for both implementation and evaluation.