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
