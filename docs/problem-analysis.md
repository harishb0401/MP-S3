# DELIVERABLE C: PROBLEM ANALYSIS AND DESIGN

## 1. Problem Statement

Municipal governance in urban centers faces significant challenges in public grievance redressing. In fast-growing municipal zones such as **Makkal Nagar**, municipal authorities receive high volumes of citizen complaints concerning basic public utilities—including damaged roads, overflowing garbage bins, drinking water pipeline leaks, and broken street lighting.

Traditional complaint mechanisms suffer from several critical shortcomings:
1. **Unprincipled FIFO Processing**: Complaints are often handled on a simple first-come-first-served basis without factoring in public hazard severity or the population impacted.
2. **Resource & Capacity Bottlenecks**: Departments accept unbounded workloads without tracking physical team bandwidth, leading to unmonitored backlogs and stalled resolution.
3. **Data Pollution & Redundancy**: Multiple citizens in the same neighborhood frequently report the same incident (e.g., an open manhole or broken transformer), overwhelming departmental officers with duplicate records.
4. **Lack of Transparency**: Citizens have limited visibility into priority adjustments, verification status, and audit trails.

The **Makkal Nagar Municipal Grievance Management and Priority Resolution System** provides an automated, transparent, and fair software solution that triages grievances through multi-attribute priority queues, enforces departmental capacity limits, prevents duplicate flooding through operator-confirmed candidate detection, and maintains an immutable audit trail.

> **CRITICAL DISCLAIMER**:
> The multi-criteria priority ranking policy (Severity $\rightarrow$ Affected Count $\rightarrow$ Arrival Order) and department capacity parameters implemented in this project are **academic and classroom design assumptions** developed for algorithmic demonstration, software engineering analysis, and educational modeling. They do not constitute official municipal or state government regulations or policies.

---

## 2. Stakeholders

| Stakeholder Group | Role in System | Key Needs & Objectives |
| :--- | :--- | :--- |
| **Citizens** | End-users reporting public infrastructure faults in Makkal Nagar | Fast reporting, transparent status tracking, notification when admin verifies impact, assurance that reported impact cannot be maliciously erased. |
| **Department Staff (Officers)** | Municipal field officers assigned to specific departments (Water, Roads, Waste, Streetlights) | Clear daily task queues sorted by urgency, ability to transition task statuses (`Assigned` $\rightarrow$ `InProgress` $\rightarrow$ `Resolved`), clear view of department capacity. |
| **Municipal Administrators / Triage Officers** | Senior municipal management and triage supervisors | City-wide dashboard analytics, capacity monitoring across departments, verification of high-impact reports, confirmation/rejection of duplicate candidates. |
| **Municipal Engineers / Auditors** | Quality assurance and civic integrity auditors | Immutable audit history logs, non-repudiation of status transitions, verification of fair algorithmic resource distribution. |

---

## 3. Requirements Specification

### 3.1 Functional Requirements (FR)
- **FR-1 Citizen Registration & Authentication**: Secure sign-up and login for citizens, municipal staff, and administrators with role-based dashboard redirection.
- **FR-2 Grievance Submission**: Multi-step submission capturing citizen ID, category, description, canonical location, and initial citizen-reported affected person count.
- **FR-3 Strict Priority Queue Ranking**: Dynamic ranking of grievances based on:
  1. Severity (`CRITICAL` > `HIGH` > `MEDIUM` > `LOW`)
  2. Effective affected person count (descending)
  3. Arrival order (earliest submission FIFO, backed by monotonic sequence tie-breaker).
- **FR-4 Department Assignment & Capacity Accounting**: Enforcing fixed capacities on the 4 core departments (Water, Roads, Waste, Streetlights). Incoming grievances exceeding department capacity are held in an ordered waiting queue.
- **FR-5 Validated Status State Machine**: Rigid enforcement of lifecycle states: `Registered` $\rightarrow$ `Assigned` $\rightarrow$ `InProgress` $\rightarrow$ `Resolved`. Exceptional transitions require documented workflows.
- **FR-6 Duplicate Candidate Detection & Operator Confirmation**: Heuristic matching identifying likely duplicates without automated merges; staff/admin confirmation links duplicate with primary record and preserves audit history.
- **FR-7 Impact Immutability & Administrative Verification**: Citizen reported impact is permanently preserved; admin verified impact is stored separately with mandatory written rationale and citizen notification.
- **FR-8 Persistence & Diagnostics**: Robust JSON persistence with automated startup validation of dataset health and canonical location conformity.

### 3.2 Non-Functional Requirements (NFR)
- **NFR-1 Determinism & Consistency**: Priority queue comparisons must be deterministic across restarts and never produce indefinite ordering flips.
- **NFR-2 Data Integrity & Immutability**: Critical historical records (`ComplaintHistory`, citizen reported impact) must be append-only and tamper-proof.
- **NFR-3 Response Time & Scalability**: In-memory priority queue operations and direct hash lookups must execute in under 10ms for real-time dashboard interactions.
- **NFR-4 Security & Privacy**: Password hashing using SHA-256; role-based access control protecting administrative endpoints; zero real citizen personally identifiable information (PII).

---

## 4. Architectural Rules and Project Boundaries

### 4.1 Four Core Departments & Fallback Scope
The system strictly scopes municipal operations to four core service departments:
1. **Water Supply Department** (`DEPT-WATER` / `DEP001`): Pipelines, water contamination, pressure, drainage leaks.
2. **Roads Department** (`DEPT-ROADS` / `DEP002`): Potholes, damaged asphalt, open manholes, road cave-ins.
3. **Waste Management Department** (`DEPT-WASTE` / `DEP003`): Garbage bins, illegal dumping, market waste, sanitation.
4. **Streetlight Department** (`DEPT-LIGHT` / `DEP004`): Broken luminaires, sagging overhead lines, dark streets.
- **General Administration** (`DEPT-ADMIN` / `DEP005`): Functions exclusively as a triage fallback queue for unmapped or misclassified categories. It does not alter the four-department operational scope.

### 4.2 Department Capacity Rules
- Each department has a fixed, configurable operational capacity (default: 10 concurrent active tasks).
- Active capacity is defined strictly as:
  $$\text{Active Load} = \text{Count}(\text{Assigned}) + \text{Count}(\text{InProgress})$$
- Waiting complaints (`isWaiting = true`, `status = "Registered"`) do **not** consume active capacity.
- Resolved complaints (`status = "Resolved"`) do **not** consume active capacity.
- A complaint can never consume capacity in two departments simultaneously; reassignment must atomically decrement the source department before incrementing the target.

### 4.3 Valid Status State Machine
- **Normal Transitions**:
  - `Registered` $\rightarrow$ `Assigned` (upon department assignment with available capacity).
  - `Assigned` $\rightarrow$ `InProgress` (when field officer begins physical work).
  - `InProgress` $\rightarrow$ `Resolved` (upon completion of work with mandatory resolution notes).
- **Exceptional Transitions**:
  - `Assigned` $\rightarrow$ `Registered`: Permitted only upon explicit administrative cancellation with documented reason.
  - `InProgress` $\rightarrow$ `Assigned`: Permitted only upon documented reassignment workflow.
- **Prohibited Transitions**:
  - `Resolved` $\rightarrow$ any status: Reopening a resolved complaint via ordinary status update is strictly forbidden.
  - Arbitrary status skips (e.g., `Registered` $\rightarrow$ `Resolved`) are rejected.

### 4.4 Duplicate Detection & Operator Confirmation
- Automated merging is strictly forbidden.
- Candidates are matched using:
  - Exact category match
  - Normalized canonical location match (case-insensitive, trimmed)
  - Explicit open-issue ID references in the complaint description
- When confirmed by an operator:
  - The duplicate is marked `isDuplicate = true`, `duplicateOfId = primaryId`, and transitioned to `Resolved` with resolution notes referencing the primary ID.
  - The duplicate record is **never deleted**, preserving full public transparency.
  - Both primary and duplicate records receive audit history entries.
- If rejected by an operator:
  - The candidate ID is added to `rejectedCandidateIds` and suppressed from future prompts.

---

## 5. Privacy & Synthetic Data Assumptions

1. **Synthetic Demonstration Data Only**: All citizen names (e.g., `Ramesh`, `Kalai`, `Citizen 4235`), phone numbers (`9876543210`), and emails are entirely synthetic. No real personal information is used.
2. **Canonical Fictional Geography**: All geographic references are confined to the five canonical demonstration locations of the fictional municipality of Makkal Nagar:
   - `Central Market`
   - `North Street`
   - `South Colony`
   - `Lake Road`
   - `Government Hospital Area`
3. **Data Boundary**: The system maintains at least 30 synthetic complaints to simulate realistic multi-department loads, waiting queues, and historical resolutions.

---

## 6. Sustainable Development Goals (SDGs) Alignment

| SDG Target | Connection & Contribution of the Project |
| :--- | :--- |
| **SDG 10: Reduced Inequalities** | Algorithmic prioritization removes subjective bias, VIP favoritism, and bureaucratic discrimination in municipal response times. Every grievance is objectively ranked by public hazard severity and verified population impact, ensuring marginalized and high-density neighborhoods receive equitable public utility restoration. |
| **SDG 11: Sustainable Cities and Communities** | Provides municipal corporations with tools for rapid response to critical urban infrastructure failures (potable water pipeline bursts, hazardous open manholes, roadway cave-ins, and dark streets), directly fostering safer, resilient, and sustainable urban living environments. |
| **SDG 16: Peace, Justice, and Strong Institutions** | Establishes institutional transparency and accountability. The immutable audit trail (`ComplaintHistory`), non-repudiation of status transitions, and preservation of citizen-reported impact build citizen trust in municipal institutions, eliminating opacity in public service delivery. |
