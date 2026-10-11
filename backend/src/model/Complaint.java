package model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.HashSet;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Model representing a Citizen Grievance / Complaint.
 * Implements Comparable<Complaint> for multi-criteria PriorityQueue prioritization:
 * 1. Severity, highest first (CRITICAL > HIGH > MEDIUM > LOW).
 * 2. Affected-person count, highest first (Descending order).
 * 3. Arrival order, earliest first (FIFO timestamp with immutable arrival sequence tie-breaker).
 *
 * Enforces business rules:
 * - Citizen Reported Impact is immutable and cannot be overwritten.
 * - Admin Verified Impact is stored separately.
 * - Verification Reason is mandatory.
 * - Verified data is locked after citizen notification.
 */
public class Complaint extends Grievance implements Comparable<Complaint> {

    public Complaint() {
        super();
    }

    public Complaint(String id, String complaintId, String citizenId, String citizenName, String citizenMobile,
                     String category, String description, String location, String department,
                     int citizenReportedImpact, String priority, String status) {
        super(id, complaintId, citizenId, citizenName, citizenMobile, category, description, location, department, citizenReportedImpact, priority, status);

        // Fix: Do not silently initialize severity to LOW when priority/severity is provided
        String effectivePriority = (priority != null && !priority.trim().isEmpty()) ? priority.trim().toUpperCase() : "MEDIUM";
        this.priority = effectivePriority;
        this.severity = normalizeSeverity(effectivePriority);

        this.status = (status != null && !status.isEmpty()) ? ComplaintStatus.toCanonical(status) : ComplaintStatus.REGISTERED.getCanonicalName();
        if (this.status == null) this.status = status;
    }

    // Extended constructor with explicit severity
    public Complaint(String id, String complaintId, String citizenId, String citizenName, String citizenMobile,
                     String category, String description, String location, String department,
                     int citizenReportedImpact, String severity, String priority, String status) {
        super(id, complaintId, citizenId, citizenName, citizenMobile, category, description, location, department, citizenReportedImpact, priority, status);

        String effectiveSeverity = (severity != null && !severity.trim().isEmpty()) ? severity : priority;
        this.severity = normalizeSeverity(effectiveSeverity);
        this.priority = (priority != null && !priority.trim().isEmpty()) ? priority.trim().toUpperCase() : this.severity;

        this.status = (status != null && !status.isEmpty()) ? ComplaintStatus.toCanonical(status) : ComplaintStatus.REGISTERED.getCanonicalName();
        if (this.status == null) this.status = status;
    }

    // Copy constructor for polymorphic grievance subclasses and adapters
    public Complaint(Complaint source) {
        super();
        if (source != null) {
            this.id = source.id;
            this.complaintId = source.complaintId;
            this.citizenId = source.citizenId;
            this.citizenName = source.citizenName;
            this.citizenMobile = source.citizenMobile;
            this.category = source.category;
            this.description = source.description;
            this.location = source.location;
            this.departmentId = source.departmentId;
            this.department = source.department;
            this.citizenReportedImpact = source.citizenReportedImpact;
            this.adminVerifiedImpact = source.adminVerifiedImpact;
            this.verificationReason = source.verificationReason;
            this.isVerifiedLocked = source.isVerifiedLocked;
            this.severity = source.severity;
            this.priority = source.priority;
            this.status = source.status;
            this.assignedStaff = source.assignedStaff;
            this.resolutionNotes = source.resolutionNotes;
            this.isDuplicate = source.isDuplicate;
            this.duplicateOfId = source.duplicateOfId;
            this.rejectedCandidateIds = new HashSet<>(source.getRejectedCandidateIds());
            this.isWaiting = source.isWaiting;
            this.arrivalSequence = source.arrivalSequence;
            this.createdAt = source.createdAt;
            this.updatedAt = source.updatedAt;
            this.resolvedAt = source.resolvedAt;
        }
    }

    // Convenience constructor for legacy callers
    public Complaint(String id, String complaintId, String citizenId, String citizenName, String citizenMobile,
                     String category, String description, String location, String department,
                     String priority, String status) {
        this(id, complaintId, citizenId, citizenName, citizenMobile, category, description, location, department, 1, priority, status);
    }

    /**
     * Consistent Severity Ordering:
     * Rank 0: CRITICAL - Emergency, hazard, structural or water disaster
     * Rank 1: HIGH     - Major infrastructure interruption
     * Rank 2: MEDIUM   - Regular municipal grievance
     * Rank 3: LOW      - Minor / cosmetic grievance
     * Rank 4: Default fallback for any unrecognized value
     */
    public static int getSeverityRank(String severity, String fallbackPriority) {
        String s = severity != null ? severity.toUpperCase().trim() : "";
        if (s.isEmpty() && fallbackPriority != null) {
            s = fallbackPriority.toUpperCase().trim();
        }
        switch (s) {
            case "CRITICAL": return 0;
            case "HIGH": return 1;
            case "MEDIUM": return 2;
            case "LOW": return 3;
            default: return 4;
        }
    }

    public static String normalizeSeverity(String raw) {
        if (raw == null) return "LOW";
        String s = raw.trim().toUpperCase();
        switch (s) {
            case "CRITICAL": return "CRITICAL";
            case "HIGH": return "HIGH";
            case "MEDIUM": return "MEDIUM";
            case "LOW": return "LOW";
            default: return s.isEmpty() ? "LOW" : s;
        }
    }

    // Priority comparator helper methods
    public int getSeverityRank() {
        return getSeverityRank(this.severity, this.priority);
    }

    public int getEffectiveImpact() {
        if (adminVerifiedImpact != null && adminVerifiedImpact > 0) {
            return adminVerifiedImpact;
        }
        return Math.max(1, citizenReportedImpact);
    }

    public int getAffectedPersonCount() {
        return getEffectiveImpact();
    }

    /**
     * Reusable Comparator implementing exact priority ranking criteria:
     * 1. Severity, highest first (CRITICAL > HIGH > MEDIUM > LOW).
     * 2. Affected-person count, highest first (Descending order).
     * 3. Arrival order, earliest first:
     *    a. Submission timestamp (createdAt), earliest first (FIFO).
     *    b. Immutable arrival sequence number as tie-breaker when timestamps are identical.
     *    c. Deterministic unique identifier tie-breaker for distinct complaints.
     */
    public static final GrievancePriorityPolicy DEFAULT_PRIORITY_POLICY = new StandardGrievancePriorityPolicy();
    public static final Comparator<Complaint> PRIORITY_COMPARATOR = DEFAULT_PRIORITY_POLICY;

    @Override
    public String responsibleDepartment() {
        if (department != null && !department.trim().isEmpty()) {
            return department.trim();
        }
        return "General Administration";
    }

    /**
     * Priority Queue reinsertion helper.
     * Removes and re-inserts the complaint into the given queue to restore heap order.
     */
    public static boolean reinsertInQueue(PriorityQueue<Complaint> queue, Complaint complaint) {
        if (queue == null || complaint == null) return false;
        boolean removed = queue.remove(complaint);
        if (removed) {
            queue.add(complaint);
            return true;
        }
        return false;
    }

    @Override
    public int compareTo(Complaint other) {
        return DEFAULT_PRIORITY_POLICY.compare(this, other);
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getComplaintId() { return complaintId; }
    public void setComplaintId(String complaintId) { this.complaintId = complaintId; }

    public String getCitizenId() { return citizenId; }
    public void setCitizenId(String citizenId) { this.citizenId = citizenId; }

    public String getCitizenName() { return citizenName; }
    public void setCitizenName(String citizenName) { this.citizenName = citizenName; }

    public String getCitizenMobile() { return citizenMobile; }
    public void setCitizenMobile(String citizenMobile) { this.citizenMobile = citizenMobile; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public String getDepartmentId() { return departmentId; }
    public void setDepartmentId(String departmentId) { this.departmentId = departmentId; }

    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }

    // IMMUTABILITY SAFEGUARD for Citizen Reported Impact
    public int getCitizenReportedImpact() { return citizenReportedImpact; }
    public void setCitizenReportedImpact(int impact) {
        // Can only be initialized once; cannot be modified by staff or admin
        if (this.citizenReportedImpact <= 0 || this.citizenReportedImpact == 1) {
            this.citizenReportedImpact = impact > 0 ? impact : 1;
        } else if (this.citizenReportedImpact != impact) {
            throw new IllegalStateException("CRITICAL POLICY VIOLATION: Citizen Reported Impact is immutable and cannot be overwritten!");
        }
    }

    public void forceSetCitizenReportedImpactFromPersistence(int impact) {
        this.citizenReportedImpact = impact > 0 ? impact : 1;
    }

    public Integer getAdminVerifiedImpact() { return adminVerifiedImpact; }
    public void setAdminVerifiedImpact(Integer adminVerifiedImpact) {
        if (isVerifiedLocked) {
            throw new IllegalStateException("Verification data is locked after citizen notification. Modifications require a new audit record!");
        }
        this.adminVerifiedImpact = adminVerifiedImpact;
    }

    public void forceSetAdminVerifiedImpactFromPersistence(Integer adminVerifiedImpact) {
        this.adminVerifiedImpact = adminVerifiedImpact;
    }

    public String getVerificationReason() { return verificationReason; }
    public void setVerificationReason(String verificationReason) {
        if (isVerifiedLocked) {
            throw new IllegalStateException("Verification reason is locked after citizen notification. Modifications require a new audit record!");
        }
        this.verificationReason = verificationReason;
    }

    public void forceSetVerificationReasonFromPersistence(String verificationReason) {
        this.verificationReason = verificationReason;
    }

    public boolean isVerifiedLocked() { return isVerifiedLocked; }
    public void setVerifiedLocked(boolean verifiedLocked) { isVerifiedLocked = verifiedLocked; }

    public long getArrivalSequence() { return arrivalSequence; }
    public void forceSetArrivalSequenceFromPersistence(long arrivalSequence) {
        this.arrivalSequence = arrivalSequence;
        SEQUENCE_GENERATOR.updateAndGet(curr -> Math.max(curr, arrivalSequence));
    }

    public String getSeverity() { return severity; }
    public void setSeverity(String severity) {
        this.severity = normalizeSeverity(severity);
    }
    public void forceSetRawSeverity(String severity) {
        this.severity = severity;
    }

    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }

    public String getStatus() { return status; }
    public void setStatus(String status) {
        String canonical = ComplaintStatus.toCanonical(status);
        this.status = canonical != null ? canonical : status;
    }

    public String getAssignedStaff() { return assignedStaff; }
    public void setAssignedStaff(String assignedStaff) { this.assignedStaff = assignedStaff; }

    public String getResolutionNotes() { return resolutionNotes; }
    public void setResolutionNotes(String resolutionNotes) { this.resolutionNotes = resolutionNotes; }

    public boolean isDuplicate() { return isDuplicate; }
    public void setDuplicate(boolean duplicate) { isDuplicate = duplicate; }

    public String getDuplicateOfId() { return duplicateOfId; }
    public void setDuplicateOfId(String duplicateOfId) { this.duplicateOfId = duplicateOfId; }

    public Set<String> getRejectedCandidateIds() { return rejectedCandidateIds; }
    public boolean hasRejectedCandidate(String candidateId) {
        return candidateId != null && rejectedCandidateIds.contains(candidateId.trim());
    }
    public void addRejectedCandidate(String candidateId) {
        if (candidateId != null && !candidateId.trim().isEmpty()) {
            rejectedCandidateIds.add(candidateId.trim());
        }
    }
    public void removeRejectedCandidate(String candidateId) {
        if (candidateId != null) {
            rejectedCandidateIds.remove(candidateId.trim());
        }
    }
    public String getRejectedCandidateIdsCsv() {
        return String.join(";", rejectedCandidateIds);
    }
    public void setRejectedCandidateIdsFromCsv(String csv) {
        rejectedCandidateIds.clear();
        if (csv != null && !csv.trim().isEmpty()) {
            for (String part : csv.split(";")) {
                if (!part.trim().isEmpty()) {
                    rejectedCandidateIds.add(part.trim());
                }
            }
        }
    }

    public boolean isWaiting() { return isWaiting; }
    public void setWaiting(boolean isWaiting) { this.isWaiting = isWaiting; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }

    public String getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(String resolvedAt) { this.resolvedAt = resolvedAt; }
}
