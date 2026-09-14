package model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class Complaint implements Comparable<Complaint> {
    private String complaintId;
    private Citizen citizen;
    private String complaintType;
    private String description;
    private String location;
    private String priority; // Emergency, High, Medium, Low
    private String status;   // Pending, In Progress, Resolved, Rejected
    private String assignedDepartment;
    private String createdDate;

    public Complaint(String complaintId, Citizen citizen, String complaintType, 
                     String description, String location, String priority, 
                     String status, String assignedDepartment) {
        this.complaintId = complaintId;
        this.citizen = citizen;
        this.complaintType = complaintType;
        this.description = description;
        this.location = location;
        this.priority = priority;
        this.status = status;
        this.assignedDepartment = assignedDepartment;
        this.createdDate = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
    }

    public String getComplaintId() {
        return complaintId;
    }

    public void setComplaintId(String complaintId) {
        this.complaintId = complaintId;
    }

    public Citizen getCitizen() {
        return citizen;
    }

    public void setCitizen(Citizen citizen) {
        this.citizen = citizen;
    }

    public String getComplaintType() {
        return complaintType;
    }

    public void setComplaintType(String complaintType) {
        this.complaintType = complaintType;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getPriority() {
        return priority;
    }

    public void setPriority(String priority) {
        this.priority = priority;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getAssignedDepartment() {
        return assignedDepartment;
    }

    public void setAssignedDepartment(String assignedDepartment) {
        this.assignedDepartment = assignedDepartment;
    }

    public String getCreatedDate() {
        return createdDate;
    }

    public void setCreatedDate(String createdDate) {
        this.createdDate = createdDate;
    }

    // Convert priority string to numerical weight for PriorityQueue ordering
    public int getPriorityWeight() {
        switch (priority != null ? priority.toLowerCase() : "") {
            case "emergency":
                return 1;
            case "high":
                return 2;
            case "medium":
                return 3;
            case "low":
                return 4;
            default:
                return 5;
        }
    }

    @Override
    public int compareTo(Complaint other) {
        // Lower weight means higher priority (Emergency=1 comes before Low=4)
        return Integer.compare(this.getPriorityWeight(), other.getPriorityWeight());
    }

    @Override
    public String toString() {
        return String.format("[%s] %s | Type: %s | Loc: %s | Priority: %s | Status: %s | Dept: %s",
                complaintId, citizen.getName(), complaintType, location, priority, status, assignedDepartment);
    }
}
