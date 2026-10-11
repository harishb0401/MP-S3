# DELIVERABLE F: FINAL PROJECT REPORT

## Project Title
**Makkal Nagar Municipal Grievance Management and Priority Resolution System**  
*A Robust Java-Based Public Utility Redressal Platform with Multi-Criteria Queue Optimization and Capacity Management*

---

## 1. Executive Summary & Problem Context

Municipal administrations are tasked with maintaining essential urban infrastructure—including water distribution, roadways, solid waste sanitation, and public lighting. In typical civic environments, grievance redressal portals operate on an ad-hoc or purely chronological (First-In, First-Out) dispatch model. This results in severe systemic shortcomings:
1. **Critical Hazards Delayed**: Emergency failures (such as major drinking water pipeline bursts or hazardous open manholes) wait in queue behind minor aesthetic issues simply because the minor issues were reported earlier.
2. **Departmental Saturation**: Departments become overwhelmed beyond physical field capacity without formal tracking or bounded waiting queues.
3. **Data Pollution from Duplicates**: Redundant reports from the same neighborhood dilute staff resources and obscure genuine unique problems.
4. **Distrusted Administrative Modifications**: Citizen impact assessments are frequently overwritten or dismissed without transparent justification.

The **Makkal Nagar Municipal Grievance Management System** resolves these challenges by introducing:
- A mathematically deterministic **multi-attribute Priority Queue** prioritizing hazard severity, population impact, and arrival sequence.
- Rigid **departmental capacity bounds** backed by dedicated priority waiting queues.
- A **formal finite-state machine** governing all status transitions.
- A **non-destructive duplicate candidate detection and operator confirmation engine**.
- Fully audited, immutable history logs and decoupled administrative impact verification.

> **Academic Disclaimer**: The priority ranking model (Severity $\rightarrow$ Population Impact $\rightarrow$ Arrival Order) and department capacity rules implemented herein represent an academic and educational software engineering model. They do not represent official municipal government policy.

---

## 2. Project Objectives

1. **Object-Oriented Design & Extensibility**: Implement a modular domain architecture featuring an abstract base `Grievance`, polymorphic concrete subclasses (`WaterGrievance`, `RoadGrievance`), encapsulated state transitions, and a pluggable comparator strategy (`GrievancePriorityPolicy`).
2. **Deterministic Priority Ranking**: Provide an ordering guarantee that sorts complaints by Severity (`CRITICAL` > `HIGH` > `MEDIUM` > `LOW`), followed by Affected-Person Count (descending), followed by Arrival Order (FIFO timestamp and monotonic sequence tie-breaker).
3. **Capacity Management & Fair Queuing**: Restrict active workloads to fixed departmental capacities across the 4 core departments (Water, Roads, Waste, Streetlights), automatically buffering excess complaints in an ordered priority waiting queue and promoting them as capacity frees up.
4. **Lifecycle Integrity**: Enforce a strict state machine (`Registered` $\rightarrow$ `Assigned` $\rightarrow$ `InProgress` $\rightarrow$ `Resolved`), forbidding illegal transitions, arbitrary status skips, and unauthorized reopening of resolved cases.
5. **Safe Duplicate Management**: Generate candidate duplicate pairings without automated destruction; allow authorized operators to confirm or reject links while maintaining complete audit trails.
6. **Synthetic Data Validation**: Provide startup diagnostics and health reports for synthetic records across five canonical Makkal Nagar zones without silently corrupting or discarding records.

---

## 3. System Architecture & Component Design

The platform uses a layered architecture entirely built on core Java (JDK 17+) and lightweight frontend technologies without heavy third-party framework overhead:

```
+-------------------------------------------------------------+
|                      PRESENTATION TIER                      |
|   HTML5 / CSS3 / Modern Vanilla JS Single Page Application  |
|      (Citizen Dashboard, Staff Triage, Analytics & Admin)   |
+-------------------------------------------------------------+
                              |  HTTP / REST (JSON)
+-------------------------------------------------------------+
|                     SERVICE / ROUTING TIER                  |
|    ServerManager (JDK HttpServer)  <-->  GrievanceManager   |
|   - Auth / Session Handler         - Business Invariants    |
|   - REST API Controllers           - State Machine Engine   |
+-------------------------------------------------------------+
                              |
+-------------------------------------------------------------+
|                     DOMAIN / LOGIC TIER                     |
|  - Grievance Abstraction (Complaint, WaterGrievance, Road)  |
|  - GrievancePriorityPolicy (Standard Strategy Comparator)   |
|  - Department & DepartmentQueueManager (Capacity & Queues)  |
|  - LocationNormalizer & Duplicate Candidate Detection       |
+-------------------------------------------------------------+
                              |
+-------------------------------------------------------------+
|                     PERSISTENCE TIER                        |
|   Database (In-Memory HashMaps & PriorityQueues)            |
|   DataValidator (Startup Diagnostics & Data Health Checks)  |
|   JSON File Storage (backend/data/database.json)            |
+-------------------------------------------------------------+
```

---

## 4. Class Design & OOP Structure

The implementation leverages core OOP principles:

1. **Inheritance & Polymorphism**:
   - `Grievance`: Defines common grievance fields and the abstract operation `responsibleDepartment()`.
   - `Complaint`: Concrete class extending `Grievance` and implementing `Comparable<Complaint>` using `GrievancePriorityPolicy`.
   - `WaterGrievance` & `RoadGrievance`: Subclasses inheriting from `Complaint` that override `responsibleDepartment()`, returning `"Water Department"` and `"Roads Department"`, respectively.
   - `User`: Abstract base class with concrete subclasses `Citizen`, `Admin`, and `DepartmentStaff` enforcing role-based permissions (`canVerifyComplaints()`, `canAssignDepartment()`, `canManageDuplicates()`).
2. **Composition**:
   - `Department` composes `DepartmentQueueManager`, which encapsulates the thread-safe `PriorityQueue<Complaint>` buffering excess tasks.
3. **Encapsulation & Immutability**:
   - `citizenReportedImpact` is permanently immutable; attempts to overwrite it are rejected.
   - `adminVerifiedImpact` is stored in a separate field alongside mandatory written justification (`verificationReason`).
4. **Strategy Pattern**:
   - `GrievancePriorityPolicy` provides an interchangeable comparator interface implemented by `StandardGrievancePriorityPolicy`.

---

## 5. Core Algorithms & Data Structures

- **Priority Queue (`java.util.PriorityQueue`)**: Operates as a min-heap on priority rank, executing insertions in $O(\log N)$ and minimum-element extractions in $O(\log N)$.
- **Hash Table (`java.util.HashMap`)**: Stores indexed complaints and registered users for instant $O(1)$ lookup by ID.
- **Set (`java.util.HashSet`)**: Enforces unique complaint IDs during persistence validation and suppresses rejected candidate IDs in $O(1)$ time.
- **Dynamic Array (`java.util.ArrayList`)**: Maintains chronological audit records (`historyList`) with $O(1)$ amortized append.
- **Heuristic Duplicate Detection**: Matches active records on exact category, normalized canonical location, or explicit issue reference in $O(N)$ scan time.

---

## 6. Verification & Test Summary

The automated test suite contains 62 test cases spanning 7 specialized test files:

```
===============================================================================
                     COMPREHENSIVE TEST SUITE EXECUTION
===============================================================================
[TASK 0] SystemTest Regression Suite               : 8 / 8  PASSED (100%)
[TASK 1] PriorityRankingTest Suite                 : 8 / 8  PASSED (100%)
[TASK 2] StatusStateMachineTest Suite              : 10 / 10 PASSED (100%)
[TASK 3] DepartmentCapacityTest Suite              : 10 / 10 PASSED (100%)
[TASK 4] DuplicateManagementTest Suite             : 12 / 12 PASSED (100%)
[TASK 5] OOPDesignTest Suite                       : 7 / 7  PASSED (100%)
[TASK 6] SyntheticDataValidationTest Suite         : 7 / 7  PASSED (100%)
-------------------------------------------------------------------------------
TOTAL TESTS EXECUTED: 62 | ALL 62 PASSED (100% Success Rate)
===============================================================================
```

---

## 7. Required Screenshots to Capture Manually

Because this automated development environment operates in headless terminal mode without interactive GUI display hardware, real browser screenshots must be captured manually by the student.

The student should start the application (`start.bat` or `java -cp backend/bin Main`), open `http://localhost:8080/`, and capture the following exact screens:

| Figure # | Target Screen / View | Exact Actions to Perform Before Capturing | Expected Visual Elements to Verify |
| :--- | :--- | :--- | :--- |
| **Figure 1** | **Citizen Login & Registration** | Navigate to `http://localhost:8080/`. Click the Citizen tab. | Clean login card, role selector, email and password input fields, Makkal Nagar branding. |
| **Figure 2** | **Citizen Dashboard & History** | Log in as `ramesh@test.com` / `123456`. | Welcome banner for Ramesh, stats tiles (Total Complaints, Resolved, In Progress), active complaints table with badges. |
| **Figure 3** | **Submit Grievance Form** | Click **Submit Complaint** in citizen sidebar. Fill form with category `Road Damage`, location `Central Market`, impact `250`. | Multi-step form, canonical location dropdown, impact input, severity selector, live validation indicators. |
| **Figure 4** | **Executive Municipal Dashboard** | Log out, log in as `admin@makkal.gov.in` / `admin123`. | Stat cards (Total: 32, In Progress, Resolved), department distribution charts, recent activity feed. |
| **Figure 5** | **Priority Resolution Queue** | In Admin sidebar, click **Priority Queue**. | Table showing grievances ordered strictly with `CRITICAL` at the top, sorted by impact count, showing rank badges. |
| **Figure 6** | **Impact Verification Modal** | Click **Process** on a complaint. Locate Impact Verification panel. | Locked Citizen Reported Impact, Admin Verified Impact field, mandatory Verification Reason input, Save button. |
| **Figure 7** | **Status Transition State Machine** | In Process modal, click Status dropdown. Select valid next status (`InProgress` or `Resolved`). | Status update form, resolution notes textarea, audit remarks field. |
| **Figure 8** | **Department Capacity Allocation** | In Admin sidebar, click **Departments**. | Cards for the 4 core departments (Water, Roads, Waste, Streetlights) showing current capacity load bars (e.g., `7/10`). |
| **Figure 9** | **Duplicate Detection & Operator Review** | In Process modal for a complaint, view Duplicate Candidates section. | Candidate list showing match percentage (e.g., `85% Match - Same Location`), **Confirm Duplicate** and **Reject Candidate** action buttons. |
| **Figure 10** | **Analytics & Municipal Intelligence** | In Admin sidebar, click **Analytics & Reports**. | Bar charts for category breakdown, department workload distribution, priority ratios, and status breakdown. |

---

## 8. Limitations & Scope Boundaries

1. **In-Memory Volatility with File Sync**: The persistence model is flat-file JSON (`database.json`). While simple and portable without external database installation, it is suitable for moderate demonstration volumes rather than concurrent multi-node civic deployments.
2. **Fixed Department Set**: Core operations are confined to the 4 municipal departments (Water, Roads, Waste, Streetlights). Custom or temporary civic committees are not dynamically provisioned.
3. **Synchronous Single-Process Execution**: The embedded Java HTTP server handles concurrent requests synchronously or with default thread pools, but lacks an asynchronous event loop (e.g., Netty) or dedicated message broker.

---

## 9. Future Enhancements

1. **RDBMS & JPA Migration**: Transition data persistence to PostgreSQL or MySQL with Flyway database migrations for transactions and ACID-compliant indexing.
2. **GIS & Geospatial Spatial Clustering**: Integrate PostGIS and OpenStreetMap to calculate duplicate candidate distances using Haversine geographic coordinates rather than string-normalized zone boundaries.
3. **Automated SMS & WhatsApp Alerts**: Integrate notification gateways (Twilio / GovPush) to send real-time SMS updates when impact is verified or grievances are resolved.
4. **Machine Learning NLP Triage**: Deploy a lightweight natural language processing classifier to automatically suggest categories and severity based on citizen unstructured text descriptions.

---

## 10. Conclusion

The **Makkal Nagar Municipal Grievance Management and Priority Resolution System** successfully demonstrates a clean, robust, and mathematically grounded civic software platform. By unifying object-oriented abstractions, multi-criteria priority queues, bounded department capacity management, rigid finite-state machine transitions, and operator-confirmed duplicate detection, the project achieves equitable, transparent, and resilient public utility management.
