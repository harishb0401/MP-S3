package model;

/**
 * Central State Machine Validation Policy for Complaint Status Transitions.
 *
 * Normal transitions:
 * - Registered -> Assigned
 * - Assigned -> InProgress
 * - InProgress -> Resolved
 *
 * Exceptional transitions:
 * - Assigned -> Registered: ONLY when an assignment is explicitly cancelled or removed.
 * - InProgress -> Assigned: ONLY when a documented reassignment/reopening workflow explicitly permits it.
 *
 * Terminal state:
 * - Resolved: Cannot be reopened or transitioned to any other state through ordinary requests.
 *
 * All controllers and services must validate transitions through this policy.
 */
public class StatusTransitionPolicy {

    public static class ValidationResult {
        private final boolean valid;
        private final String errorMessage;
        private final ComplaintStatus targetStatus;

        public ValidationResult(boolean valid, String errorMessage, ComplaintStatus targetStatus) {
            this.valid = valid;
            this.errorMessage = errorMessage;
            this.targetStatus = targetStatus;
        }

        public boolean isValid() {
            return valid;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public ComplaintStatus getTargetStatus() {
            return targetStatus;
        }
    }

    /**
     * Central transition validation method.
     *
     * @param currentStatusRaw The current status of the complaint.
     * @param newStatusRaw     The proposed new status.
     * @param isExplicitCancellation True if the action is an explicit assignment cancellation.
     * @param isExplicitReassignment True if the action is an explicit documented reassignment.
     * @return ValidationResult indicating whether the transition is valid and the canonical target status or error.
     */
    public static ValidationResult validateTransition(String currentStatusRaw, String newStatusRaw,
                                                       boolean isExplicitCancellation, boolean isExplicitReassignment) {
        if (newStatusRaw == null || newStatusRaw.trim().isEmpty()) {
            return new ValidationResult(false, "Status update rejected: Target status cannot be empty.", null);
        }

        // Validate that target is a known valid status value
        if (!ComplaintStatus.isValidStatus(newStatusRaw)) {
            return new ValidationResult(false,
                    "Status update rejected: Unknown status value '" + newStatusRaw.trim() +
                    "'. Permitted states are: Registered, Assigned, InProgress, Resolved.", null);
        }

        ComplaintStatus target = ComplaintStatus.fromString(newStatusRaw);
        ComplaintStatus current = ComplaintStatus.fromString(currentStatusRaw);

        // If current status is unparseable or null, permit initializing to the target status
        if (current == null) {
            return new ValidationResult(true, null, target);
        }

        // Idempotent transition (same status)
        if (current == target) {
            if (isExplicitReassignment && target == ComplaintStatus.ASSIGNED) {
                return new ValidationResult(true, null, target);
            }
            return new ValidationResult(false,
                    "Status update rejected: Complaint is already in status '" + current.getCanonicalName() + "'.", target);
        }

        // Terminal state check: Resolved complaints cannot be transitioned/reopened
        if (current == ComplaintStatus.RESOLVED) {
            return new ValidationResult(false,
                    "Invalid status transition: A resolved complaint is in a terminal state and cannot be reopened or transitioned from Resolved to "
                    + target.getCanonicalName() + ".", null);
        }

        switch (current) {
            case REGISTERED:
                if (target == ComplaintStatus.ASSIGNED) {
                    return new ValidationResult(true, null, target);
                }
                if (target == ComplaintStatus.IN_PROGRESS) {
                    return new ValidationResult(false,
                            "Invalid status transition: Cannot transition directly from Registered to InProgress. Complaint must first be Assigned.", null);
                }
                if (target == ComplaintStatus.RESOLVED) {
                    return new ValidationResult(false,
                            "Invalid status transition: Cannot transition directly from Registered to Resolved. Complaint must be Assigned and InProgress first.", null);
                }
                break;

            case ASSIGNED:
                if (target == ComplaintStatus.IN_PROGRESS) {
                    return new ValidationResult(true, null, target);
                }
                if (target == ComplaintStatus.REGISTERED) {
                    if (isExplicitCancellation) {
                        return new ValidationResult(true, null, target);
                    } else {
                        return new ValidationResult(false,
                                "Invalid status transition: Cannot transition from Assigned to Registered without an explicit assignment cancellation workflow.", null);
                    }
                }
                if (target == ComplaintStatus.RESOLVED) {
                    return new ValidationResult(false,
                            "Invalid status transition: Cannot transition directly from Assigned to Resolved. Complaint must first enter InProgress.", null);
                }
                break;

            case IN_PROGRESS:
                if (target == ComplaintStatus.RESOLVED) {
                    return new ValidationResult(true, null, target);
                }
                if (target == ComplaintStatus.ASSIGNED) {
                    if (isExplicitReassignment) {
                        return new ValidationResult(true, null, target);
                    } else {
                        return new ValidationResult(false,
                                "Invalid status transition: Cannot transition from InProgress to Assigned without an explicit documented reassignment workflow.", null);
                    }
                }
                if (target == ComplaintStatus.REGISTERED) {
                    return new ValidationResult(false,
                            "Invalid status transition: Cannot transition directly from InProgress to Registered.", null);
                }
                break;
        }

        return new ValidationResult(false,
                "Invalid status transition: Unsupported transition from " + current.getCanonicalName() +
                " to " + target.getCanonicalName() + ".", null);
    }

    public static ValidationResult validateTransition(String currentStatusRaw, String newStatusRaw) {
        return validateTransition(currentStatusRaw, newStatusRaw, false, false);
    }
}
