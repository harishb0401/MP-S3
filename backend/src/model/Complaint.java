package model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class Complaint implements Comparable<Complaint> {
    private String id;
    private String complaintId;
    private String citizenId;
    private String citizenName;
    private String citizenMobile;
    private String category;
    private String description;
    private String location;
    private String department;
    private String priority; // CRITICAL, HIGH, MEDIUM, LOW
    private String status;   // PENDING, ASSIGNED, IN_PROGRESS, RESOLVED, CLOSED
    private String assignedStaff;
    private String resolutionNotes;
    private String createdAt;
    private String updatedAt;
    private String resolvedAt;

    public Complaint() {}

    public Complaint(String id, String complaintId, String citizenId, String citizenName, String citizenMobile,
                     String category, String description, String location, String department,
                     String priority, String status) {
        this.id = id;
        this.complaintId = complaintId;
        this.citizenId = citizenId;
        this.citizenName = citizenName;
        this.citizenMobile = citizenMobile;
        this.category = category;
        this.description = description;
        this.location = location;
        this.department = department;
        this.priority = priority;
        this.status = status;
        this.assignedStaff = "Unassigned";
        this.resolutionNotes = "";
        
        String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        this.createdAt = now;
        this.updatedAt = now;
        this.resolvedAt = "";
    }

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

    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }

    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getAssignedStaff() { return assignedStaff; }
    public void setAssignedStaff(String assignedStaff) { this.assignedStaff = assignedStaff; }

    public String getResolutionNotes() { return resolutionNotes; }
    public void setResolutionNotes(String resolutionNotes) { this.resolutionNotes = resolutionNotes; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }

    public String getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(String resolvedAt) { this.resolvedAt = resolvedAt; }

    // Numerical weight for PriorityQueue ordering
    public int getPriorityWeight() {
        if (priority == null) return 5;
        switch (priority.toUpperCase()) {
            case "CRITICAL": return 1;
            case "HIGH": return 2;
            case "MEDIUM": return 3;
            case "LOW": return 4;
            default: return 5;
        }
    }

    @Override
    public int compareTo(Complaint other) {
        int weightCompare = Integer.compare(this.getPriorityWeight(), other.getPriorityWeight());
        if (weightCompare != 0) {
            return weightCompare;
        }
        // If weights are equal, older complaints come first (FIFO)
        String t1 = this.createdAt != null ? this.createdAt : "";
        String t2 = other.createdAt != null ? other.createdAt : "";
        return t1.compareTo(t2);
    }
}
