# DELIVERABLE D: TEST PLAN AND TEST RESULTS

## 1. Quality Assurance Strategy & Verification Scope

The test suite validates the core algorithmic requirements, state machine invariants, departmental capacity bounds, duplicate candidate management, object-oriented abstractions, synthetic dataset health, and persistence.

All tests are implemented as automated Java test runners executing directly against the production compiled classes in `backend/bin/`.

### Execution Command
To compile and execute the complete automated verification test suite:
```powershell
javac -d backend/bin backend/src/model/*.java backend/src/service/*.java backend/src/test/*.java backend/src/Main.java
java -cp backend/bin test.SystemTest
```

---

## 2. Test Plan Matrix

| Test ID | Test Category | Target Component | Description & Verification Criteria | Expected Outcome | Execution Status |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **TC-01** | Normal Registration | `GrievanceManager`, `Complaint` | Citizen submits new complaint with category, description, location, impact count. | Record created with unique ID, status `Registered`, and initial audit history entry. | PASSED |
| **TC-02** | Priority Ordering | `StandardGrievancePriorityPolicy`, `PriorityQueue` | Queue ordered strictly by: 1. Severity, 2. Impact count, 3. Earliest arrival. | `CRITICAL` dequeued before `HIGH`, higher impact before lower impact. | PASSED |
| **TC-03** | Equal-Priority Tie-Breaking | `StandardGrievancePriorityPolicy` | Multiple complaints with identical severity and impact submitted with equal timestamps. | Broken deterministically using atomic `arrivalSequence` counter, preserving FIFO arrival. | PASSED |
| **TC-04** | Full Department Capacity | `Department`, `DepartmentQueueManager` | Department filled to capacity (`currentLoad == 10`). 11th complaint assigned. | 11th complaint is queued in `waitingQueue` with `isWaiting = true`; capacity does not exceed 10. | PASSED |
| **TC-05** | Waiting Queue Dequeue | `Department`, `GrievanceManager` | An assigned complaint in a full department is resolved. | Department capacity decremented; highest-priority waiting complaint automatically dequeued and assigned. | PASSED |
| **TC-06** | Inter-Department Reassignment | `Department`, `GrievanceManager` | Complaint reassigned from Department A to Department B. | Capacity in Department A decremented; capacity in Department B incremented; complaint never consumes double capacity. | PASSED |
| **TC-07** | Valid Status State Machine | `StatusTransitionPolicy` | Normal lifecycle progression: `Registered` $\rightarrow$ `Assigned` $\rightarrow$ `InProgress` $\rightarrow$ `Resolved`. | All valid forward transitions succeed and record timestamped audit entries. | PASSED |
| **TC-08** | Invalid Transition Rejection | `StatusTransitionPolicy` | Attempt direct resolution (`Registered` $\rightarrow$ `Resolved`) or skipping assignment (`Registered` $\rightarrow$ `InProgress`). | Transition rejected with validation error; status remains unchanged. | PASSED |
| **TC-09** | Reopening Prevention | `StatusTransitionPolicy` | Attempt to reopen a `Resolved` complaint via standard status update. | Request rejected; state machine strictly forbids reopening resolved cases. | PASSED |
| **TC-10** | Assignment Cancellation | `GrievanceManager`, `StatusTransitionPolicy` | Administrator explicitly cancels an assignment with a documented reason. | Complaint transitions from `Assigned` back to `Registered`; released capacity accounted. | PASSED |
| **TC-11** | Duplicate Candidate Detection | `GrievanceManager`, `LocationNormalizer` | Two open complaints share same category and normalized location in Makkal Nagar. | Flagged as duplicate candidates with match score; no automatic merge performed. | PASSED |
| **TC-12** | Duplicate Confirmation | `GrievanceManager` | Operator explicitly confirms duplicate link between Candidate B and Primary A. | Candidate B marked `isDuplicate = true`, `duplicateOfId = "A"`, status set to `Resolved`; record preserved; audit log created on both. | PASSED |
| **TC-13** | Duplicate Rejection | `GrievanceManager` | Operator explicitly rejects duplicate candidate. | Candidate ID added to `rejectedCandidateIds`; candidate permanently suppressed from future prompts. | PASSED |
| **TC-14** | Circular Link Prevention | `GrievanceManager` | Attempt to link Complaint A as duplicate of B, and B as duplicate of A (direct and transitive cycles). | Operation rejected with validation error preventing infinite loops. | PASSED |
| **TC-15** | Persistence & Rehydration | `Database` | Complaints, users, and audit histories serialized to `database.json` and loaded back into memory. | Rehydrated dataset is identical in count, status, field values, and queue rankings. | PASSED |
| **TC-16** | Role-Based Authorization | `User`, `ServerManager` | Citizen user attempts administrative impact verification or department reassignment. | Operations rejected with unauthorized access denial. | PASSED |
| **TC-17** | Impact Immutability | `Grievance`, `Complaint` | Direct or indirect invocation attempting to overwrite `citizenReportedImpact`. | Field remains unchanged; `adminVerifiedImpact` stored in distinct field with reason. | PASSED |
| **TC-18** | Canonical Location Enforcement | `LocationNormalizer`, `DataValidator` | Synthetic records evaluated for exact match with 5 canonical demonstration locations. | Canonical locations accepted; malformed locations or embedded timestamps rejected. | PASSED |
| **TC-19** | Demonstration Record Count | `DataValidator` | Synthetic demonstration dataset verified for volume $\ge 30$. | Confirmed $\ge 30$ complaints (32 active synthetic records in database). | PASSED |
| **TC-20** | Department-Wise Reporting | `GrievanceManager`, `Database` | Aggregation of workloads, active vs. waiting queues across 4 core departments. | Accurate counts returned for Water, Roads, Waste, and Streetlights. | PASSED |

---

## 3. Actual Executed Test Results

### Execution Summary
- **Total Test Suites Executed**: 7
- **Total Individual Test Cases**: 62
- **Passed**: 62
- **Failed**: 0
- **Pending**: 0
- **Success Rate**: 100.0%

### Detailed Suite-by-Suite Test Log

#### 1. SystemTest Suite (8 / 8 Passed)
```
[TEST 1/8] Verifying Role-Based Authorization & Abstract User Methods... PASSED
[TEST 2/8] Verifying Immutability of Citizen Reported Impact... PASSED
[TEST 3/8] Verifying Admin Impact Verification & Notification Pipeline... PASSED
[TEST 4/8] Verifying Multi-Criteria Priority Queue Order (Critical > High > Medium > Low)... PASSED
[TEST 5/8] Verifying Dynamic Department Assignment & Routing... PASSED
[TEST 6/8] Verifying Audit History Tracking... PASSED
[TEST 7/8] Verifying JSON Database Persistence & Rehydration... PASSED
[TEST 8/8] Verifying Synthetic Demo Data Invariants... PASSED
```

#### 2. PriorityRankingTest Suite (8 / 8 Passed)
```
[TEST 1/8] Strict Severity Ordering (CRITICAL > HIGH > MEDIUM > LOW)... PASSED
[TEST 2/8] Affected-person Count Tie-Breaker (Higher impact first)... PASSED
[TEST 3/8] Arrival Order Earliest-First FIFO (Earlier timestamp first)... PASSED
[TEST 4/8] Monotonic Arrival Sequence Number Tie-Breaker... PASSED
[TEST 5/8] Constructor Initialization & Severity Defaulting... PASSED
[TEST 6/8] Admin Verified Impact Priority Recalculation... PASSED
[TEST 7/8] Comprehensive Priority Comparator Compliance... PASSED
[TEST 8/8] PriorityQueue Polling Order Verification... PASSED
```

#### 3. StatusStateMachineTest Suite (10 / 10 Passed)
```
[TEST 1/10] Normal Transition: Registered -> Assigned... PASSED
[TEST 2/10] Normal Transition: Assigned -> InProgress... PASSED
[TEST 3/10] Invalid Transition: Registered -> InProgress (Skipping Assignment)... PASSED
[TEST 4/10] Normal Transition: InProgress -> Resolved... PASSED
[TEST 5/10] Invalid Transition: Registered -> Resolved (Direct Resolution)... PASSED
[TEST 6/10] Reopening Prevention: Resolved -> InProgress / Assigned / Registered... PASSED
[TEST 7/10] Unknown Status Rejection... PASSED
[TEST 8/10] Explicit Assignment Cancellation: Assigned -> Registered... PASSED
[TEST 9/10] Explicit Reopening / Reassignment Workflow: InProgress -> Assigned... PASSED
[TEST 10/10] Mandatory Resolution Notes on Completion... PASSED
```

#### 4. DepartmentCapacityTest Suite (10 / 10 Passed)
```
[TEST 1/10] Assignment to a department with available capacity... PASSED
[TEST 2/10] Assignment to a full department (queues in waiting PriorityQueue)... PASSED
[TEST 3/10] Waiting complaints do not consume active capacity... PASSED
[TEST 4/10] Resolved complaints release capacity and trigger automatic waiting dequeue... PASSED
[TEST 5/10] Inter-department reassignment updates both departments' capacities... PASSED
[TEST 6/10] Reassignment from full department to available department releases slot... PASSED
[TEST 7/10] Reassignment to a full department queues complaint... PASSED
[TEST 8/10] Four core departments have independent configurable capacities... PASSED
[TEST 9/10] A complaint never consumes capacity in two departments simultaneously... PASSED
[TEST 10/10] Department load consistency after bulk operations... PASSED
```

#### 5. DuplicateManagementTest Suite (12 / 12 Passed)
```
[TEST 1/12] Exact Category & Location Duplicate Candidate Detection... PASSED
[TEST 2/12] Location Normalization (Whitespace, Casing, Punctuation)... PASSED
[TEST 3/12] Distinct Locations Produce No Active Duplicate Candidates... PASSED
[TEST 4/12] Distinct Categories Produce No Active Duplicate Candidates... PASSED
[TEST 5/12] Resolved Issue Versus Open Issue (No Active Duplicates)... PASSED
[TEST 6/12] Explicit Operator Confirmation (Preserves Original Record)... PASSED
[TEST 7/12] Explicit Rejection Workflow (Candidate Suppressed)... PASSED
[TEST 8/12] Self-Link Prevention... PASSED
[TEST 9/12] Duplicate-Link & Circular-Link Prevention (Direct & Transitive)... PASSED
[TEST 10/12] Complete Audit History Recording on Both Complaints... PASSED
[TEST 11/12] Backend Authorization Enforcement for Duplicate Actions... PASSED
[TEST 12/12] Open-Issue Reference Matching (Explicit ID in Description)... PASSED
```

#### 6. OOPDesignTest Suite (7 / 7 Passed)
```
[TEST 1/7] WaterGrievance & RoadGrievance Return Correct Responsible Departments... PASSED
[TEST 2/7] Runtime Polymorphism via Base Grievance Abstraction References... PASSED
[TEST 3/7] GrievancePriorityPolicy Controls Real PriorityQueue Ordering... PASSED
[TEST 4/7] Encapsulation Validates State Transitions & Department Assignment... PASSED
[TEST 5/7] Department Composes DepartmentQueueManager & Invariants... PASSED
[TEST 6/7] GrievanceFactory Polymorphic Creation & Subclass Adaptation... PASSED
[TEST 7/7] End-to-End GrievanceManager Integration with Subclasses... PASSED
```

#### 7. SyntheticDataValidationTest Suite (7 / 7 Passed)
```
[TEST 1/7] Exactly 5 Canonical Locations & Malformed Location Rejection... PASSED
[TEST 2/7] Detection of Duplicate Complaint IDs... PASSED
[TEST 3/7] Detection of Missing Required Fields... PASSED
[TEST 4/7] Detection and Rejection of Invalid Severity Values... PASSED
[TEST 5/7] Insufficient Demonstration Records Detection (< 30)... PASSED
[TEST 6/7] Core 4-Department Scope & Category Mapping Validation... PASSED
[TEST 7/7] Persisted Demonstration Dataset Integrity (database.json)... PASSED
```

#### 8. RoleBasedPortalsTest Suite (17 / 17 Passed)
```
[TEST 1/17] Citizen Signup & Login with Password Hashing... PASSED
[TEST 2/17] Invalid Login & Duplicate Citizen Account Rejection... PASSED
[TEST 3/17] Citizen Complaint Registration & Field Validation... PASSED
[TEST 4/17] Citizen Ownership & IDOR Protection... PASSED
[TEST 5/17] Worker Access Restricted to Authorized Department... PASSED
[TEST 6/17] Worker Status Transitions & Invalid Jumps Prevention... PASSED
[TEST 7/17] Manager Assignment, Reassignment & Capacity Conservation... PASSED
[TEST 8/17] Department Capacity Enforcement & Waiting Queue Promotion... PASSED
[TEST 9/17] Priority Multi-Criteria Ordering (Severity > Impact > Arrival)... PASSED
[TEST 10/17] Duplicate Candidate Detection, Confirmation & Rejection... PASSED
[TEST 11/17] Safe Confirmed Duplicate Deletion & Audit Tombstone... PASSED
[TEST 12/17] Admin Worker CRUD, Deactivation & Complaint Handover... PASSED
[TEST 13/17] Role Invariants & Privilege Escalation Prevention... PASSED
[TEST 14/17] Real HTTP API Endpoint RBAC & Unauthorized Requests... PASSED
[TEST 15/17] Persistent Data Invariants & Restart Resilience... PASSED
[TEST 16/17] Frontend/Backend API Schema & JSON Synchronization... PASSED
[TEST 17/17] Regression Tests (4 Core Depts, 5 Locations, OOP Polymorphism)... PASSED
```

---

## 4. Tasks 1-8: Risk Assessment & GPS Geolocation Verification Suite (28 Scenarios)

Command:
```powershell
java -cp backend/bin test.AssessmentAndGPSTest
```

### Test Results (28 / 28 Passed - 100% Success Rate)

```
[TEST 01/28] Citizen registration does not require severity... PASSED
[TEST 02/28] Citizen-supplied severity values cannot manipulate assessment... PASSED (Citizen 'CRITICAL' was ignored; complaint starts unassessed)
[TEST 03/28] Newly registered complaints remain unassessed... PASSED (severity=null, isAssessed=false)
[TEST 04/28] Category & emergency keywords do not automatically assign risk... PASSED (No automatic classification triggered)
[TEST 05/28] Manager can assess complaint (Critical, High, Medium, Low)... PASSED (Assessed as CRITICAL by Manager Arun)
[TEST 06/28] Admin can perform authorized assessment... PASSED (Assessed as HIGH by Admin)
[TEST 07/28] Worker cannot alter severity... PASSED (SecurityException thrown)
[TEST 08/28] Citizen cannot alter severity... PASSED (SecurityException thrown)
[TEST 09/28] Invalid severity values are rejected... PASSED (Rejected non-standard severity)
[TEST 10/28] Unassessed complaints do not rank as LOW in PriorityQueue... PASSED (Assessed LOW ranks ahead of unassessed; unassessed rank is 999)
[TEST 11/28] Assessed waiting complaints retain deterministic priority ordering... PASSED (Critical > High(Impact:50) > High(Impact:10,Earlier) > High(Impact:10,Later))
[TEST 12/28] Assessment updates are recorded in audit history... PASSED (Audit entry found: Risk Assessed by Manager Arun: Severity set to CRITICAL. Reason: Field hazard verified. Department: Water Department (Load: 4/10). Assigned to Staff Raj.)
[TEST 13/28] Valid latitude and longitude are accepted and persisted... PASSED (Lat: 13.0827, Lng: 80.2707, Accuracy: 5.0m)
[TEST 14/28] Out-of-range latitude is rejected... PASSED (Latitude 95.0 out of [-90..90] rejected)
[TEST 15/28] Out-of-range longitude is rejected... PASSED (Longitude 195.0 out of [-180..180] rejected)
[TEST 16/28] Missing coordinate pair is rejected... PASSED (Latitude with null longitude rejected)
[TEST 17/28] GPS accuracy is validated (nonnegative finite number)... PASSED (Negative accuracy -10m rejected)
[TEST 18/28] Complaint without GPS can still be registered with canonical area... PASSED (Registered with Municipal Area 'South Colony')
[TEST 19/28] Coordinates survive database serialization and reload... PASSED (GPS lat, lng, accuracy, and assessment survived round-trip)
[TEST 20/28] Assignment, status updates, and reassignment preserve GPS... PASSED (GPS coordinates unchanged after status transition and reassignment)
[TEST 21/28] Google Maps link uses saved complaint coordinates... PASSED (https://www.google.com/maps?q=13.0827,80.2707)
[TEST 22/28] RBAC prevents unauthorized users from altering location data... PASSED (SecurityException prevents unauthorized tampering)
[TEST 23/28] Integration: Citizen submits complaint without risk selection... PASSED (Starts unassessed, status: Registered)
[TEST 24/28] Integration: Manager sees complaint in assessment queue... PASSED (Visible in manager review queue)
[TEST 25/28] Integration: Manager assesses and assigns complaint... PASSED (Assessed as HIGH, status: Assigned)
[TEST 26/28] Integration: Assigned department receives complaint... PASSED (Present in Water Supply department active list)
[TEST 27/28] Integration: Full department retains assessed complaint in PriorityQueue... PASSED (Complaint placed in Department Waiting PriorityQueue at CRITICAL priority)
[TEST 28/28] Integration: Existing records & role workflows preserved... PASSED (Legacy records intact with preserved severity; RBAC roles fully operational)
```

