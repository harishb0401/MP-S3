package model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Model representing a Citizen Grievance / Complaint.
 * Implements Comparable<Complaint> for multi-criteria PriorityQueue prioritization:
 * 1. Severity (High > Medium > Low)
 * 2. Impact Count (Verified Impact if present, else Citizen Impact; Descending)
 * 3. Arrival Order (Submission Timestamp; Ascending FIFO)
 *
 * Enforces business rules:
 * - Citizen Reported Impact is immutable and cannot be overwritten.
 * - Admin Verified Impact is stored separately.
 * - Verification Reason is mandatory.
 * - Verified data is locked after citizen notification.
 */
public class Complaint implements Comparable<Complaint> {
    private String id;
    private String complaintId;
    private String citizenId;
    private String citizenName;
    private String citizenMobile;
    private String category;
    private String description;
    private String location;
    private String departmentId;
    private String department;
    
    // Impact fields
    private int citizenReportedImpact;          // Original value, IMMUTABLE
    private Integer adminVerifiedImpact;        // Stored separately
    private String verificationReason;          // Mandatory on verification
    private boolean isVerifiedLocked;           // Locked after notification sent

    private String severity;                    // HIGH, MEDIUM, LOW
    private String priority;                    // Calculated Priority Level
    private String status;                      // Registered, Under Review, Assigned, In Progress, Resolved, Closed
    private String assignedStaff;
    private String resolutionNotes;

    // Duplicate detection fields
    private boolean isDuplicate;
    private String duplicateOfId;

    private String createdAt;
    private String updatedAt;
    private String resolvedAt;

    public Complaint() {
        this.status = "Registered";
        this.assignedStaff = "Unassigned";
        this.resolutionNotes = "";
        this.isVerifiedLocked = false;
        this.isDuplicate = false;
        this.duplicateOfId = null;
        this.citizenReportedImpact = 1; // default initial
        String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        this.createdAt = now;
        this.updatedAt = now;
        this.resolvedAt = "";
    }

    public Complaint(String id, String complaintId, String citizenId, String citizenName, String citizenMobile,
                     String category, String description, String location, String department,
                     int citizenReportedImpact, String priority, String status) {
        this.id = id;
        this.complaintId = complaintId;
        this.citizenId = citizenId;
        this.citizenName = citizenName;
        this.citizenMobile = citizenMobile;
        this.category = category;
        this.description = description;
        this.location = location;
        this.department = department;
        this.citizenReportedImpact = citizenReportedImpact > 0 ? citizenReportedImpact : 1;
        this.adminVerifiedImpact = null;
        this.verificationReason = "";
        this.isVerifiedLocked = false;
        this.severity = "LOW";
        this.priority = priority != null ? priority : "MEDIUM";
        this.status = (status != null && !status.isEmpty()) ? status : "Registered";
        this.assignedStaff = "Unassigned";
        this.resolutionNotes = "";
        this.isDuplicate = false;
        this.duplicateOfId = null;

        String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        this.createdAt = now;
        this.updatedAt = now;
        this.resolvedAt = "";
    }

    // Convenience constructor for legacy callers
    public Complaint(String id, String complaintId, String citizenId, String citizenName, String citizenMobile,
                     String category, String description, String location, String department,
                     String priority, String status) {
        this(id, complaintId, citizenId, citizenName, citizenMobile, category, description, location, department, 1, priority, status);
    }

    // Priority comparator helper methods
    public int getSeverityRank() {
        String s = severity != null ? severity.toUpperCase().trim() : "";
        if (s.isEmpty() && priority != null) {
            s = priority.toUpperCase().trim();
        }
        switch (s) {
            case "CRITICAL": return 0;
            case "HIGH": return 1;
            case "MEDIUM": return 2;
            case "LOW": return 3;
            default: return 4;
        }
    }

    public int getEffectiveImpact() {
        if (adminVerifiedImpact != null && adminVerifiedImpact > 0) {
            return adminVerifiedImpact;
        }
        return Math.max(1, citizenReportedImpact);
    }

    /**
     * Priority Comparator implementing exact specification:
     * 1. Severity (High > Medium > Low)
     * 2. Impact Count (Descending: higher impact count comes first)
     * 3. Arrival Order (Ascending FIFO: earlier timestamp comes first)
     */
    @Override
    public int compareTo(Complaint other) {
        if (other == null) return -1;

        // 1. Severity Comparison
        int sevCompare = Integer.compare(this.getSeverityRank(), other.getSeverityRank());
        if (sevCompare != 0) {
            return sevCompare;
        }

        // 2. Impact Count Comparison (Descending)
        int impactCompare = Integer.compare(other.getEffectiveImpact(), this.getEffectiveImpact());
        if (impactCompare != 0) {
            return impactCompare;
        }

        // 3. Arrival Order Comparison (FIFO: earlier timestamp comes first)
        String t1 = this.createdAt != null ? this.createdAt : "";
        String t2 = other.createdAt != null ? other.createdAt : "";
        return t1.compareTo(t2);
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

    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }

    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getAssignedStaff() { return assignedStaff; }
    public void setAssignedStaff(String assignedStaff) { this.assignedStaff = assignedStaff; }

    public String getResolutionNotes() { return resolutionNotes; }
    public void setResolutionNotes(String resolutionNotes) { this.resolutionNotes = resolutionNotes; }

    public boolean isDuplicate() { return isDuplicate; }
    public void setDuplicate(boolean duplicate) { isDuplicate = duplicate; }

    public String getDuplicateOfId() { return duplicateOfId; }
    public void setDuplicateOfId(String duplicateOfId) { this.duplicateOfId = duplicateOfId; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }

    public String getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(String resolvedAt) { this.resolvedAt = resolvedAt; }
}
