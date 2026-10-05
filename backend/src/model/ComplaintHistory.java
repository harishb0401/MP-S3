package model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Model representing an Audit History entry for a Complaint.
 * Tracks all state transitions, verification events, assignments, and resolution notes.
 */
public class ComplaintHistory {
    private String historyId;
    private String complaintId;
    private String oldStatus;
    private String newStatus;
    private String updatedBy;
    private String updateTime;
    private String remarks;

    public ComplaintHistory() {}

    public ComplaintHistory(String historyId, String complaintId, String oldStatus, String newStatus, String updatedBy, String remarks) {
        this.historyId = historyId;
        this.complaintId = complaintId;
        this.oldStatus = oldStatus;
        this.newStatus = newStatus;
        this.updatedBy = updatedBy;
        this.updateTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        this.remarks = remarks;
    }

    public ComplaintHistory(String historyId, String complaintId, String oldStatus, String newStatus, String updatedBy, String updateTime, String remarks) {
        this.historyId = historyId;
        this.complaintId = complaintId;
        this.oldStatus = oldStatus;
        this.newStatus = newStatus;
        this.updatedBy = updatedBy;
        this.updateTime = updateTime;
        this.remarks = remarks;
    }

    public String getHistoryId() { return historyId; }
    public void setHistoryId(String historyId) { this.historyId = historyId; }

    public String getComplaintId() { return complaintId; }
    public void setComplaintId(String complaintId) { this.complaintId = complaintId; }

    public String getOldStatus() { return oldStatus; }
    public void setOldStatus(String oldStatus) { this.oldStatus = oldStatus; }

    public String getNewStatus() { return newStatus; }
    public void setNewStatus(String newStatus) { this.newStatus = newStatus; }

    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }

    public String getUpdateTime() { return updateTime; }
    public void setUpdateTime(String updateTime) { this.updateTime = updateTime; }

    public String getRemarks() { return remarks; }
    public void setRemarks(String remarks) { this.remarks = remarks; }

    @Override
    public String toString() {
        return "[" + updateTime + "] " + oldStatus + " -> " + newStatus + " by " + updatedBy + " (" + remarks + ")";
    }
}
