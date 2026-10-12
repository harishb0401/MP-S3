package model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Base Grievance Abstraction.
 * Encapsulates core shared grievance identity, location, reporting metadata,
 * impact verification, and defines the polymorphic responsibleDepartment() operation.
 */
public abstract class Grievance {
    protected static final AtomicLong SEQUENCE_GENERATOR = new AtomicLong(0);

    protected String id;
    protected String complaintId;
    protected String citizenId;
    protected String citizenName;
    protected String citizenMobile;
    protected String title;
    protected String category;
    protected String description;
    protected String location;
    protected String departmentId;
    protected String department;

    // GPS Location fields
    protected Double latitude;
    protected Double longitude;
    protected Double locationAccuracyMeters;

    // Assessment fields (Manager/Admin authorized)
    protected String assessedBy;
    protected String assessedAt;
    protected String assessmentReason;

    // Impact fields
    protected int citizenReportedImpact;          // Original value, IMMUTABLE
    protected Integer adminVerifiedImpact;        // Stored separately
    protected String verificationReason;          // Mandatory on verification
    protected boolean isVerifiedLocked;           // Locked after notification sent

    protected String severity;                    // CRITICAL, HIGH, MEDIUM, LOW (null = UNASSESSED)
    protected String priority;                    // Calculated Priority Level or UNASSESSED
    protected String status;                      // Registered, Assigned, InProgress, Resolved
    protected String assignedStaff;
    protected String resolutionNotes;

    // Duplicate detection fields
    protected boolean isDuplicate;
    protected String duplicateOfId;
    protected Set<String> rejectedCandidateIds = new HashSet<>();

    // Department capacity & queue status
    protected boolean isWaiting;

    // Evidence Photo field
    protected String imageUrl;

    // Arrival order & sequence fields
    protected long arrivalSequence;               // Immutable sequential arrival order assigned on registration
    protected String createdAt;
    protected String updatedAt;
    protected String resolvedAt;

    public Grievance() {
        this.status = "Registered";
        this.assignedStaff = "Unassigned";
        this.resolutionNotes = "";
        this.isVerifiedLocked = false;
        this.isDuplicate = false;
        this.duplicateOfId = null;
        this.isWaiting = false;
        this.citizenReportedImpact = 1;
        this.arrivalSequence = SEQUENCE_GENERATOR.incrementAndGet();
        String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        this.createdAt = now;
        this.updatedAt = now;
        this.resolvedAt = "";
    }

    public Grievance(String id, String complaintId, String citizenId, String citizenName, String citizenMobile,
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
        this.arrivalSequence = SEQUENCE_GENERATOR.incrementAndGet();

        if (priority != null && !priority.trim().isEmpty() && !"UNASSESSED".equalsIgnoreCase(priority.trim())) {
            String effectivePriority = priority.trim().toUpperCase();
            this.priority = effectivePriority;
            this.severity = effectivePriority;
        } else {
            this.priority = null;
            this.severity = null;
        }

        String effectiveStatus = (status != null && !status.trim().isEmpty()) ? status.trim() : "Registered";
        this.status = effectiveStatus;
        this.assignedStaff = "Unassigned";
        this.resolutionNotes = "";
        this.isDuplicate = false;
        this.duplicateOfId = null;
        this.isWaiting = false;

        String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        this.createdAt = now;
        this.updatedAt = now;
        this.resolvedAt = "";
    }

    /**
     * Polymorphic operation returning the official municipal department responsible
     * for handling this grievance. Subclasses override this method to provide specific departments.
     */
    public abstract String responsibleDepartment();

    /**
     * Validates and assigns department and staff to this grievance under strict encapsulation.
     */
    public synchronized boolean assignDepartment(String deptName, String staffName) {
        if ("RESOLVED".equalsIgnoreCase(this.status) || "CLOSED".equalsIgnoreCase(this.status)) {
            throw new IllegalStateException("Encapsulation violation: Cannot assign a department to a resolved grievance.");
        }
        if (deptName == null || deptName.trim().isEmpty()) {
            throw new IllegalArgumentException("Department name cannot be null or empty.");
        }
        this.department = deptName.trim();
        if (staffName != null && !staffName.trim().isEmpty()) {
            this.assignedStaff = staffName.trim();
        }
        this.updatedAt = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        return true;
    }

    /**
     * Encapsulated status transition enforcing valid state-machine rules.
     */
    public synchronized boolean transitionStatus(String newStatus, boolean explicitCancellation, boolean explicitReassignment) {
        StatusTransitionPolicy.ValidationResult result = StatusTransitionPolicy.validateTransition(
                this.status, newStatus, explicitCancellation, explicitReassignment);
        if (!result.isValid()) {
            throw new IllegalStateException(result.getErrorMessage());
        }
        this.status = result.getTargetStatus() != null ? result.getTargetStatus().getCanonicalName() : newStatus;
        this.updatedAt = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        if ("Resolved".equalsIgnoreCase(this.status)) {
            this.resolvedAt = this.updatedAt;
        }
        return true;
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

    public boolean isAssessed() {
        return this.severity != null && !this.severity.trim().isEmpty() &&
               !"UNASSESSED".equalsIgnoreCase(this.severity.trim()) &&
               !"AWAITING_REVIEW".equalsIgnoreCase(this.severity.trim());
    }

    public int getSeverityRank() {
        if (!isAssessed()) {
            return 999;
        }
        String sev = this.severity != null ? this.severity.trim().toUpperCase() : "";
        if (sev.isEmpty() && this.priority != null) {
            sev = this.priority.trim().toUpperCase();
        }
        switch (sev) {
            case "CRITICAL": return 1;
            case "HIGH":     return 2;
            case "MEDIUM":   return 3;
            case "LOW":      return 4;
            default:         return 999;
        }
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

    public String getTitle() {
        if (title != null && !title.trim().isEmpty()) return title.trim();
        return description != null ? description : (category != null ? category : "Complaint");
    }
    public void setTitle(String title) { this.title = title; }

    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }

    public String getAssignedDepartment() {
        return department != null ? department : "Unassigned";
    }

    public int getCitizenReportedImpact() { return citizenReportedImpact; }
    public void setCitizenReportedImpact(int impact) {
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
    public void setSeverity(String severity) {
        if (severity == null || severity.trim().isEmpty() || "UNASSESSED".equalsIgnoreCase(severity.trim())) {
            this.severity = null;
        } else {
            this.severity = severity.trim().toUpperCase();
        }
    }
    public void forceSetRawSeverity(String severity) {
        this.severity = severity;
    }

    public Double getLatitude() { return latitude; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }

    public Double getLongitude() { return longitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }

    public Double getLocationAccuracyMeters() { return locationAccuracyMeters; }
    public void setLocationAccuracyMeters(Double locationAccuracyMeters) { this.locationAccuracyMeters = locationAccuracyMeters; }

    public boolean hasGpsCoordinates() {
        return latitude != null && longitude != null;
    }

    public String getGoogleMapsUrl() {
        if (!hasGpsCoordinates()) return null;
        return "https://www.google.com/maps?q=" + latitude + "," + longitude;
    }

    public String getAssessedBy() { return assessedBy; }
    public void setAssessedBy(String assessedBy) { this.assessedBy = assessedBy; }

    public String getAssessedAt() { return assessedAt; }
    public void setAssessedAt(String assessedAt) { this.assessedAt = assessedAt; }

    public String getAssessmentReason() { return assessmentReason; }
    public void setAssessmentReason(String assessmentReason) { this.assessmentReason = assessmentReason; }

    public String getPriority() { return priority; }
    public void setPriority(String priority) {
        this.priority = priority;
    }

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

    public Set<String> getRejectedCandidateIds() {
        if (rejectedCandidateIds == null) rejectedCandidateIds = new HashSet<>();
        return rejectedCandidateIds;
    }
    public void setRejectedCandidateIds(Set<String> rejectedCandidateIds) {
        this.rejectedCandidateIds = rejectedCandidateIds != null ? rejectedCandidateIds : new HashSet<>();
    }
    public void addRejectedCandidate(String candidateComplaintId) {
        if (candidateComplaintId != null && !candidateComplaintId.trim().isEmpty()) {
            getRejectedCandidateIds().add(candidateComplaintId.trim());
        }
    }
    public void removeRejectedCandidate(String candidateComplaintId) {
        if (candidateComplaintId != null) {
            getRejectedCandidateIds().remove(candidateComplaintId.trim());
        }
    }
    public boolean hasRejectedCandidate(String candidateComplaintId) {
        return candidateComplaintId != null && getRejectedCandidateIds().contains(candidateComplaintId.trim());
    }

    public boolean isWaiting() { return isWaiting; }
    public void setWaiting(boolean waiting) { isWaiting = waiting; }

    public long getArrivalSequence() { return arrivalSequence; }
    public void setArrivalSequenceFromPersistence(long arrivalSequence) {
        this.arrivalSequence = arrivalSequence;
        SEQUENCE_GENERATOR.updateAndGet(current -> Math.max(current, arrivalSequence));
    }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }

    public String getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(String resolvedAt) { this.resolvedAt = resolvedAt; }

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
}
