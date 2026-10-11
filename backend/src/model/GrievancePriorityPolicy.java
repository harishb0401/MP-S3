package model;

import java.util.Comparator;

/**
 * Strategy/Policy abstraction for ordering grievances in priority queues.
 * Enforces the required multi-criteria ordering:
 * 1. Severity descending (CRITICAL > HIGH > MEDIUM > LOW)
 * 2. Affected-person count descending
 * 3. Arrival order ascending (earliest submission time / sequence number first)
 */
public interface GrievancePriorityPolicy extends Comparator<Complaint> {

    @Override
    int compare(Complaint c1, Complaint c2);

    default String getPolicyName() {
        return "Grievance Priority Policy";
    }
}
