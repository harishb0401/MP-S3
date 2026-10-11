package model;

/**
 * Concrete GrievancePriorityPolicy implementing the project's strict three-tier ordering:
 * 1. Severity, highest first (CRITICAL > HIGH > MEDIUM > LOW).
 * 2. Affected-person count, highest first (Descending order).
 * 3. Arrival order, earliest first:
 *    a. Submission timestamp (createdAt), earliest first (FIFO).
 *    b. Immutable arrival sequence number as tie-breaker when timestamps are identical.
 *    c. Deterministic unique identifier tie-breaker for distinct complaints.
 */
public class StandardGrievancePriorityPolicy implements GrievancePriorityPolicy {

    @Override
    public int compare(Complaint c1, Complaint c2) {
        if (c1 == c2) return 0;
        if (c1 == null) return 1;
        if (c2 == null) return -1;

        // 0. Assessment status check:
        // Assessed complaints have priority over unassessed complaints in the active queue.
        // Unassessed complaints awaiting manager review are ordered strictly in FIFO arrival order.
        boolean a1 = c1.isAssessed();
        boolean a2 = c2.isAssessed();
        if (a1 && !a2) return -1;
        if (!a1 && a2) return 1;

        if (a1 && a2) {
            // 1. Severity Comparison for assessed complaints: Highest first (CRITICAL > HIGH > MEDIUM > LOW)
            int sevCompare = Integer.compare(c1.getSeverityRank(), c2.getSeverityRank());
            if (sevCompare != 0) {
                return sevCompare;
            }

            // 2. Affected-person Count Comparison: Highest first (Descending order)
            int impactCompare = Integer.compare(c2.getEffectiveImpact(), c1.getEffectiveImpact());
            if (impactCompare != 0) {
                return impactCompare;
            }
        }

        // 3. Arrival Order: Earliest first
        // 3a. Timestamp comparison
        String t1 = c1.getCreatedAt() != null ? c1.getCreatedAt().trim() : "";
        String t2 = c2.getCreatedAt() != null ? c2.getCreatedAt().trim() : "";
        int timeCompare = t1.compareTo(t2);
        if (timeCompare != 0) {
            return timeCompare;
        }

        // 3b. Arrival Sequence Number tie-breaker (Earliest / lower sequence number first)
        int seqCompare = Long.compare(c1.getArrivalSequence(), c2.getArrivalSequence());
        if (seqCompare != 0) {
            return seqCompare;
        }

        // 3c. Deterministic tie-breaker for distinct complaints
        String id1 = c1.getComplaintId() != null ? c1.getComplaintId() : (c1.getId() != null ? c1.getId() : "");
        String id2 = c2.getComplaintId() != null ? c2.getComplaintId() : (c2.getId() != null ? c2.getId() : "");
        return id1.compareTo(id2);
    }

    @Override
    public String getPolicyName() {
        return "Severity Descending > Affected-Person Count Descending > Arrival Sequence Ascending";
    }
}
