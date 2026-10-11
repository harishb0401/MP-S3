# DELIVERABLE A: UML CLASS DIAGRAM

## 1. Architectural Overview & Object-Oriented Design

The Makkal Nagar Municipal Grievance Management and Priority Resolution System is designed using clean Object-Oriented Programming (OOP) principles in Java. It models municipal domain entities, strict multi-criteria queue prioritization, finite-state lifecycle management, and departmental capacity accounting.

### Key Architectural Concepts
1. **Grievance Abstraction & Polymorphism**:
   - `Grievance`: The abstract base model encapsulating core attributes (identity, citizen reference, category, description, location, reported impact, admin-verified impact, severity, priority, status, duplicate metadata, arrival sequence, and timestamps) and defining the polymorphic operation `responsibleDepartment()`.
   - `Complaint`: Concrete grievance implementation that implements `Comparable<Complaint>` and delegates queue comparison to `GrievancePriorityPolicy`.
   - `WaterGrievance` and `RoadGrievance`: Subclasses inheriting from `Complaint` that override `responsibleDepartment()` to dynamically dispatch to their respective departments (`Water Department` and `Roads Department`).
   - `GrievanceFactory`: Factory demonstrating polymorphic instantiation and adaptation of base complaints to concrete subclasses.

2. **User Abstraction & Role-Based Authorization**:
   - `User`: Abstract base class providing common profile fields (`id`, `username`, `email`, `mobile`, `passwordHash`, `role`, `department`) and polymorphic authorization operations (`canVerifyComplaints()`, `canAssignDepartment()`, `canUpdateStatus()`, `canManageDuplicates()`).
   - `Citizen`, `Admin`, `DepartmentStaff`: Subclasses encapsulating specific operational rights.

3. **Priority Policy Strategy Pattern**:
   - `GrievancePriorityPolicy`: Comparator strategy interface defining `compare(Complaint c1, Complaint c2)`.
   - `StandardGrievancePriorityPolicy`: Concrete policy implementing the three-tier ranking:
     1. Severity descending (`CRITICAL` > `HIGH` > `MEDIUM` > `LOW`)
     2. Affected-person count descending
     3. Arrival order ascending (timestamp FIFO with sequence number tie-breaker)

4. **Department Capacity & Waiting Queue Management**:
   - `Department`: Models municipal departments with a fixed configurable capacity (`capacity`) and current active load (`currentLoad`).
   - `DepartmentQueueManager`: Composed within `Department` to manage waiting cases using a thread-safe `PriorityQueue<Complaint>`.
   - *Note on Assignment*: Assignment is modeled as an associative domain relationship between `Complaint`, `Department`, and `DepartmentStaff` (tracking `assignedDepartment`, `assignedStaff`, and load counters in `Department`), rather than a disconnected isolated entity.

5. **Audit History & Status Transitions**:
   - `StatusTransitionPolicy`: State machine validator enforcing allowed transitions (`Registered` &rarr; `Assigned` &rarr; `InProgress` &rarr; `Resolved`).
   - `StatusUpdate`: Data transfer model capturing transition intent and remarks.
   - `ComplaintHistory`: Immutable audit log recording every transition, actor, timestamp, and remark.

---

## 2. Mermaid UML Class Diagram

```mermaid
classDiagram
    direction TB

    %% ==========================================
    %% USER HIERARCHY
    %% ==========================================
    class User {
        <<abstract>>
        #String id
        #String username
        #String email
        #String mobile
        #String passwordHash
        #String role
        #String department
        #String createdAt
        +getDisplayName()* String
        +canVerifyComplaints()* boolean
        +canAssignDepartment()* boolean
        +canUpdateStatus()* boolean
        +canManageDuplicates() boolean
        +canModifyCitizenReportedImpact() boolean
    }

    class Citizen {
        -String name
        +getDisplayName() String
        +canVerifyComplaints() boolean
        +canAssignDepartment() boolean
        +canUpdateStatus() boolean
    }

    class Admin {
        -String fullName
        +getDisplayName() String
        +canVerifyComplaints() boolean
        +canAssignDepartment() boolean
        +canUpdateStatus() boolean
    }

    class DepartmentStaff {
        -String staffName
        -String departmentId
        -String departmentName
        +getDisplayName() String
        +canVerifyComplaints() boolean
        +canAssignDepartment() boolean
        +canUpdateStatus() boolean
    }

    User <|-- Citizen : extends
    User <|-- Admin : extends
    User <|-- DepartmentStaff : extends

    %% ==========================================
    %% GRIEVANCE HIERARCHY
    %% ==========================================
    class Grievance {
        <<abstract>>
        #String id
        #String complaintId
        #String citizenId
        #String citizenName
        #String citizenMobile
        #String category
        #String description
        #String location
        #String departmentId
        #String department
        #int citizenReportedImpact
        #Integer adminVerifiedImpact
        #String verificationReason
        #boolean isVerifiedLocked
        #String severity
        #String priority
        #String status
        #String assignedStaff
        #String resolutionNotes
        #boolean isDuplicate
        #String duplicateOfId
        #Set~String~ rejectedCandidateIds
        #boolean isWaiting
        #long arrivalSequence
        #String createdAt
        #String updatedAt
        #String resolvedAt
        +responsibleDepartment()* String
        +getEffectiveImpact() int
        +getSeverityRank() int
    }

    class Complaint {
        +responsibleDepartment() String
        +compareTo(Complaint other) int
        +normalizeSeverity(String input) String
    }

    class WaterGrievance {
        +responsibleDepartment() String
    }

    class RoadGrievance {
        +responsibleDepartment() String
    }

    Grievance <|-- Complaint : extends
    Complaint <|-- WaterGrievance : extends
    Complaint <|-- RoadGrievance : extends

    %% ==========================================
    %% PRIORITY POLICY PATTERN
    %% ==========================================
    class GrievancePriorityPolicy {
        <<interface>>
        +compare(Complaint c1, Complaint c2)* int
        +getPolicyName() String
    }

    class StandardGrievancePriorityPolicy {
        +compare(Complaint c1, Complaint c2) int
        +getPolicyName() String
    }

    GrievancePriorityPolicy <|.. StandardGrievancePriorityPolicy : implements
    Complaint ..> GrievancePriorityPolicy : uses for comparison

    %% ==========================================
    %% DEPARTMENT & QUEUE
    %% ==========================================
    class Department {
        -String departmentId
        -String departmentName
        -int capacity
        -int currentLoad
        -DepartmentQueueManager queueManager
        +hasAvailableCapacity() boolean
        +tryAcquireCapacity() boolean
        +incrementLoad() void
        +decrementLoad() void
        +enqueueWaiting(Complaint c) void
        +pollNextWaiting() Complaint
        +getWaitingQueueSize() int
        +getWaitingList() List~Complaint~
    }

    class DepartmentQueueManager {
        -PriorityQueue~Complaint~ waitingQueue
        -GrievancePriorityPolicy priorityPolicy
        +offer(Complaint c) boolean
        +poll() Complaint
        +peek() Complaint
        +size() int
        +getOrderedList() List~Complaint~
    }

    Department *-- DepartmentQueueManager : composes
    DepartmentQueueManager o-- GrievancePriorityPolicy : uses
    DepartmentQueueManager o-- Complaint : buffers in queue

    %% ==========================================
    %% AUDIT & STATE MACHINE
    %% ==========================================
    class StatusUpdate {
        -String status
        -String remarks
        -boolean explicitCancellation
        -boolean explicitReassignment
        +getStatus() String
        +getRemarks() String
        +isExplicitCancellation() boolean
        +isExplicitReassignment() boolean
    }

    class ComplaintHistory {
        -String historyId
        -String complaintId
        -String oldStatus
        -String newStatus
        -String updatedBy
        -String updateTime
        -String remarks
        +getHistoryId() String
        +getComplaintId() String
        +getOldStatus() String
        +getNewStatus() String
        +getUpdatedBy() String
    }

    class StatusTransitionPolicy {
        <<utility>>
        +validateTransition(Complaint c, StatusUpdate update, User actor)$ ValidationResult
    }

    Complaint "1" --> "*" ComplaintHistory : audited by
    StatusTransitionPolicy ..> StatusUpdate : validates
    StatusTransitionPolicy ..> Complaint : verifies state

    %% ==========================================
    %% DUPLICATE CANDIDATE MODEL
    %% ==========================================
    class DuplicateCandidate {
        -String candidateId
        -String existingIssueId
        -String category
        -String normalizedLocation
        -String matchReason
        -double similarityScore
        +getCandidateId() String
        +getExistingIssueId() String
    }

    %% ==========================================
    %% SERVICE / COORDINATION LAYER
    %% ==========================================
    class GrievanceManager {
        -Database database
        -Map~String, Department~ departments
        -StandardGrievancePriorityPolicy priorityPolicy
        +createComplaint(User citizen, ...) Complaint
        +assignDepartment(String complaintId, String deptName, ...) boolean
        +updateComplaintStatus(String complaintId, StatusUpdate update, User actor) boolean
        +confirmDuplicateLink(String primaryId, String duplicateId, User actor, String reason) boolean
        +rejectDuplicateCandidate(String compId, String candId, User actor) boolean
        +findDuplicateCandidates(String complaintId) List~DuplicateCandidate~
        +getPriorityQueueList() List~Complaint~
        +getDashboardStats() Map~String, Integer~
    }

    class Database {
        -Map~String, Complaint~ complaintMap
        -Map~String, User~ userMap
        -Map~String, Department~ departmentMap
        -List~ComplaintHistory~ historyList
        -PriorityQueue~Complaint~ systemPriorityQueue
        +saveDatabase() void
        +loadDatabase() void
        +getComplaintById(String id) Complaint
        +addComplaint(Complaint c) void
    }

    GrievanceManager --> Database : coordinates
    GrievanceManager --> Department : manages capacity
    GrievanceManager ..> DuplicateCandidate : produces
    GrievanceManager ..> GrievanceFactory : creates subclasses
    Database "1" o-- "*" Complaint : indexes
    Database "1" o-- "*" User : stores
    Database "1" o-- "*" ComplaintHistory : persists
```

---

## 3. Class Relationship Narrative

| Relationship | Type | Source & Target | Structural & Behavioral Role |
| :--- | :--- | :--- | :--- |
| **Grievance Inheritance** | Inheritance | `Complaint` $\rightarrow$ `Grievance`<br>`WaterGrievance` $\rightarrow$ `Complaint`<br>`RoadGrievance` $\rightarrow$ `Complaint` | Implements polymorphic domain dispatch where `responsibleDepartment()` overrides return `"Water Department"` and `"Roads Department"`. |
| **User Inheritance** | Inheritance | `Citizen`, `Admin`, `DepartmentStaff` $\rightarrow$ `User` | Encapsulates role-based authorization methods (`canVerifyComplaints()`, `canAssignDepartment()`, `canManageDuplicates()`). |
| **Strategy Pattern** | Realization / Dependency | `StandardGrievancePriorityPolicy` $\rightarrow$ `GrievancePriorityPolicy` | Pluggable policy comparator providing deterministic three-tier ordering for `PriorityQueue`. |
| **Department Queue Composition** | Composition | `Department` $\rightarrow$ `DepartmentQueueManager` | Each department manages its own waiting queue using a dedicated `DepartmentQueueManager` that orders waiting complaints when department capacity is exhausted. |
| **Audit Logging** | Association | `Complaint` $\rightarrow$ `ComplaintHistory` | Every change in status, department assignment, or impact verification creates an immutable, timestamped `ComplaintHistory` audit entry. |
| **Service Coordination** | Dependency | `GrievanceManager` $\rightarrow$ `Database`, `Department` | Acts as the central domain orchestrator enforcing business invariants across persistence, priority queues, and state transitions. |
