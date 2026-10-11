# DELIVERABLE B: DATA STRUCTURES AND ALGORITHMS

## 1. Overview of Data Structures

The Makkal Nagar Municipal Grievance Management System utilizes fundamental and specialized data structures to guarantee predictable performance, high data integrity, deterministic ordering, and thread safety.

Below is an overview of the primary data structures used across the backend:

| Data Structure | Implementation Class | Core Usage | Time Complexity (Average) | Trade-Offs Considered |
| :--- | :--- | :--- | :--- | :--- |
| **Priority Queue** | `java.util.PriorityQueue` | Department-level waiting queues (`DepartmentQueueManager`) & global triaged priority queue (`Database.systemPriorityQueue`) | Insert: $O(\log N)$<br>Extract-Min: $O(\log N)$<br>Peek: $O(1)$ | Provides instantaneous access to highest-priority grievances without full sorting. Trade-off: Non-$O(1)$ random access / search ($O(N)$ for arbitrary removal). |
| **Hash Map** | `java.util.HashMap` / `ConcurrentHashMap` | Fast primary key lookups (`complaintMap`, `userMap`), department category mapping (`CATEGORY_TO_DEPARTMENT`), and load caches | Lookup: $O(1)$<br>Insert: $O(1)$<br>Delete: $O(1)$ | Provides instant associative access. Trade-off: Memory overhead for bucket nodes/hashing; does not maintain sorted order. |
| **Hash Set** | `java.util.HashSet` | Unique complaint ID validation (`uniqueComplaintIds`), candidate suppression sets (`rejectedCandidateIds`), canonical location indexing | Add: $O(1)$<br>Contains: $O(1)$<br>Remove: $O(1)$ | Prevents duplicate keys and enforces uniqueness. Trade-off: Memory overhead for hash buckets; unordered iteration. |
| **Array List** | `java.util.ArrayList` | Chronological audit history log (`historyList`), ordered complaint report exports, candidate presentation lists | Append: $O(1)$ amortized<br>Get by Index: $O(1)$<br>Iteration: $O(N)$ | Fast sequential traversal and dynamic resizing. Trade-off: Shifting cost $O(N)$ on intermediate deletions/insertions. |

---

## 2. In-Depth Analysis of Data Structures & Trade-offs

### 2.1 PriorityQueue for Department Waiting Cases & Triaged Queue
- **Where Applied**:
  - `DepartmentQueueManager`: When a department reaches its fixed operational capacity (`currentLoad >= capacity`), incoming grievances are not rejected; instead, they are buffered into a department-level `PriorityQueue<Complaint>`.
  - `Database.systemPriorityQueue`: Maintained across all active complaints to power the administrative master triage dashboard.
- **Why It Is Appropriate**:
  - The municipal system requires that when capacity opens up in a department (e.g., when an assigned complaint transitions to `Resolved`), the most critical grievance must be dequeued first. A min-heap/max-heap structure allows $O(\log N)$ enqueue and $O(\log N)$ dequeue operations, ensuring near-instantaneous selection of the next urgent complaint without sorting the entire dataset ($O(N \log N)$).
- **Trade-Offs**:
  - Iterating over a `PriorityQueue` directly does not yield sorted order unless elements are repeatedly drained using `poll()`, which destroys or requires copying the queue ($O(N \log N)$).
  - Arbitrary updates or deletions from within the queue require $O(N)$ linear scans to locate elements before restoring the heap property.

### 2.2 HashMap for Direct Lookups & Category-to-Department Mapping
- **Where Applied**:
  - `Database.complaintMap`: Maps complaint identifiers (`complaintId` e.g., `"GRV-2026-0001"` and internal GUIDs) directly to `Complaint` instances.
  - `Database.userMap`: Maps user IDs and normalized emails to `User` objects.
  - `GrievanceManager.CATEGORY_TO_DEPARTMENT`: Static immutable routing table mapping categories (`"Water Supply"`, `"Road Damage"`, `"Streetlight"`, `"Waste Management"`) to their authoritative department.
- **Why It Is Appropriate**:
  - REST endpoints frequently request individual records by ID (e.g., `GET /api/complaints/{id}`, `POST /api/complaints/{id}/verify`). A hash table guarantees $O(1)$ average time complexity for key lookups, avoiding costly linear scans through large complaint histories.
- **Trade-Offs**:
  - Hash tables consume higher memory per stored item than contiguous arrays due to bucket references and hash collisions. Requires effective hashing functions to avoid bucket degradation to $O(N)$.

### 2.3 HashSet for Uniqueness Invariants & Candidate Suppression
- **Where Applied**:
  - `DataValidator.uniqueComplaintIds`: Validates during startup and data persistence that no synthetic record shares a duplicated ID.
  - `Grievance.rejectedCandidateIds`: Stores IDs of duplicate candidates that an operator explicitly rejected, ensuring they are suppressed from appearing in future candidate detection lists.
  - `LocationNormalizer.CANONICAL_DEMO_LOCATIONS`: Set of 5 canonical Makkal Nagar zones.
- **Why It Is Appropriate**:
  - Checking whether an ID has already been registered or whether a candidate has already been rejected must be instantaneous ($O(1)$).
- **Trade-Offs**:
  - Unordered; iterating through a `HashSet` does not preserve arrival or registration sequence.

### 2.4 ArrayList for Status History & Reporting
- **Where Applied**:
  - `Database.historyList`: Stores `ComplaintHistory` audit records in strict chronological order.
  - Reporting pipelines: Aggregating rows for analytics, CSV exports, and dashboard tables.
- **Why It Is Appropriate**:
  - Audit logs are append-only. `ArrayList` offers $O(1)$ amortized append operations and contiguous memory layout with CPU cache locality, making iteration and JSON serialization fast.
- **Trade-Offs**:
  - Dynamic resizing requires allocating a new array and copying elements ($O(N)$) when capacity is exceeded, though amortized append time remains $O(1)$.

---

## 3. Priority Comparator & Deterministic Tie-Breaking Algorithm

### 3.1 Three-Tier Priority Specification
Every complaint in the priority queue is evaluated using the following ordering hierarchy:
1. **Tier 1: Severity (Highest First)**:
   - Rank 1: `CRITICAL`
   - Rank 2: `HIGH`
   - Rank 3: `MEDIUM`
   - Rank 4: `LOW`
2. **Tier 2: Effective Affected-Person Count (Highest First)**:
   - Evaluated descending: higher population impact receives higher processing precedence.
   - Evaluates `adminVerifiedImpact` if verified; otherwise defaults to immutable `citizenReportedImpact`.
3. **Tier 3: Arrival Order (Earliest First - FIFO)**:
   - Evaluated by submission timestamp (`createdAt`).
   - **Deterministic Tie-Breaker**: In the event that two complaints share identical severity, identical impact counts, and identical timestamp strings (such as batch synthetic imports), an immutable `arrivalSequence` (atomic monotonic counter `AtomicLong`) breaks the tie. Distinct complaint IDs provide the final lexicographical safeguard.

### 3.2 Comparator Algorithm Implementation
Implemented in `StandardGrievancePriorityPolicy.java`:

```java
@Override
public int compare(Complaint c1, Complaint c2) {
    if (c1 == c2) return 0;
    if (c1 == null) return 1;
    if (c2 == null) return -1;

    // Tier 1: Severity (Lower rank integer = higher priority)
    int sevCompare = Integer.compare(c1.getSeverityRank(), c2.getSeverityRank());
    if (sevCompare != 0) {
        return sevCompare;
    }

    // Tier 2: Affected-person Count (Descending: higher impact first)
    int impactCompare = Integer.compare(c2.getEffectiveImpact(), c1.getEffectiveImpact());
    if (impactCompare != 0) {
        return impactCompare;
    }

    // Tier 3a: Timestamp Comparison (Earliest first / FIFO)
    String t1 = c1.getCreatedAt() != null ? c1.getCreatedAt().trim() : "";
    String t2 = c2.getCreatedAt() != null ? c2.getCreatedAt().trim() : "";
    int timeCompare = t1.compareTo(t2);
    if (timeCompare != 0) {
        return timeCompare;
    }

    // Tier 3b: Atomic Arrival Sequence Tie-Breaker (Lower sequence first)
    int seqCompare = Long.compare(c1.getArrivalSequence(), c2.getArrivalSequence());
    if (seqCompare != 0) {
        return seqCompare;
    }

    // Tier 3c: Lexicographical ID Tie-Breaker (Absolute determinism)
    String id1 = c1.getComplaintId() != null ? c1.getComplaintId() : "";
    String id2 = c2.getComplaintId() != null ? c2.getComplaintId() : "";
    return id1.compareTo(id2);
}
```

### 3.3 Complexity Analysis of Priority Queue Operations
- **Enqueue (New Grievance / Waiting Case)**: $O(\log N)$ heap sift-up comparisons.
- **Dequeue Next Best (Staff Processing / Capacity Released)**: $O(\log N)$ heap sift-down comparisons.
- **Master Queue Snapshot Generation**: When extracting sorted list for reporting, copying elements to an array and executing TimSort takes $O(N \log N)$ time and $O(N)$ auxiliary space.

---

## 4. Duplicate Candidate Matching Algorithm

The duplicate candidate detection engine (`GrievanceManager.findDuplicateCandidates`) avoids automated merging while detecting likely duplicates using a 3-pass heuristic:

1. **Active Filter**: Excludes self, resolved complaints (`Resolved`), and already confirmed/merged complaints.
2. **Open-Issue Reference Scan**: Checks if the complaint description references an existing open issue ID (e.g., `"GRV-2026-0002"`), assigning an immediate match with score `1.0`.
3. **Exact Category & Normalized Location Match**:
   - Compares category equality (`category.equalsIgnoreCase(other.category)`).
   - Normalizes locations using `LocationNormalizer.normalize(loc)` (trimming whitespace, standardizing case, removing accidental tokens).
   - If both category and normalized location match, flags candidate with score `0.85`.
4. **Suppression Check**:
   - Queries `rejectedCandidateIds.contains(candidateId)` in $O(1)$ time to exclude previously dismissed pairings.

**Algorithm Complexity**:
- Let $N$ be the number of open complaints. Scanning all open complaints for a given target takes $O(N)$ time with $O(1)$ lookup per candidate.
