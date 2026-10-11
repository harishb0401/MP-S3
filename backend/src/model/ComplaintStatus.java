package model;

/**
 * Standard Complaint Status Enum for the Municipal Grievance State Machine.
 *
 * The four required canonical states are:
 * 1. Registered - Initial grievance registered by citizen.
 * 2. Assigned   - Assigned to municipal department and officer.
 * 3. InProgress - Officer actively performing work / field repairs.
 * 4. Resolved   - Grievance resolved with verified resolution notes.
 */
public enum ComplaintStatus {
    REGISTERED("Registered"),
    ASSIGNED("Assigned"),
    IN_PROGRESS("InProgress"),
    RESOLVED("Resolved");

    private final String canonicalName;

    ComplaintStatus(String canonicalName) {
        this.canonicalName = canonicalName;
    }

    public String getCanonicalName() {
        return canonicalName;
    }

    @Override
    public String toString() {
        return canonicalName;
    }

    /**
     * Parses any status string variant into the canonical ComplaintStatus enum.
     * Handles case-insensitivity, spaces, underscores, and hyphens.
     */
    public static ComplaintStatus fromString(String raw) {
        if (raw == null) return null;
        String s = raw.trim().replaceAll("[\\s_-]", "").toUpperCase();
        switch (s) {
            case "REGISTERED":
            case "PENDING":
            case "SUBMITTED":
            case "UNDERREVIEW":
                return REGISTERED;
            case "ASSIGNED":
                return ASSIGNED;
            case "INPROGRESS":
            case "PROGRESS":
                return IN_PROGRESS;
            case "RESOLVED":
            case "CLOSED":
                return RESOLVED;
            default:
                return null;
        }
    }

    /**
     * Validates whether a given raw string represents one of the 4 valid states.
     */
    public static boolean isValidStatus(String raw) {
        if (raw == null || raw.trim().isEmpty()) return false;
        String s = raw.trim().replaceAll("[\\s_-]", "").toUpperCase();
        return s.equals("REGISTERED") || s.equals("ASSIGNED") || s.equals("INPROGRESS") || s.equals("RESOLVED");
    }

    /**
     * Normalizes a raw string into canonical form ("Registered", "Assigned", "InProgress", "Resolved").
     */
    public static String toCanonical(String raw) {
        ComplaintStatus status = fromString(raw);
        return status != null ? status.getCanonicalName() : null;
    }
}
