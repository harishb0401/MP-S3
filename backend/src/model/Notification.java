package model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Model representing a Notification in the Government Grievance System.
 * Supports notifications across Citizen, Admin, Manager, and Worker roles.
 */
public class Notification {
    private String notificationId;
    private String citizenId;         // Kept for full backwards-compatibility; stores recipient id or role
    private String recipientId;       // Target user ID, citizen ID, or worker ID (or null/empty if role-targeted)
    private String recipientRole;     // "CITIZEN", "ADMIN", "MANAGER", "STAFF", "WORKER", "ADMIN_OR_MANAGER", "ALL"
    private String complaintId;       // Associated complaint ID (or empty for system events)
    private int originalImpact;
    private int verifiedImpact;
    private String verificationReason;
    private String title;
    private String message;
    private String type;              // "info", "success", "warning", "urgent"
    private String createdAt;
    private boolean isRead;

    public Notification() {}

    // Legacy verification constructor (keeps SystemTest & existing code working)
    public Notification(String notificationId, String citizenId, String complaintId,
                        int originalImpact, int verifiedImpact, String verificationReason) {
        this.notificationId = notificationId;
        this.citizenId = citizenId;
        this.recipientId = citizenId;
        this.recipientRole = "CITIZEN";
        this.complaintId = complaintId;
        this.originalImpact = originalImpact;
        this.verifiedImpact = verifiedImpact;
        this.verificationReason = verificationReason;
        this.title = "Complaint " + complaintId + " Verified";
        this.message = String.format(
                "Complaint %s has been reviewed. Citizen Reported Impact: %d | Admin Verified Impact: %d | Reason: %s. Your original submission remains preserved in the system.",
                complaintId, originalImpact, verifiedImpact, verificationReason
        );
        this.type = "success";
        this.createdAt = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        this.isRead = false;
    }

    // Extended legacy constructor
    public Notification(String notificationId, String citizenId, String complaintId,
                        int originalImpact, int verifiedImpact, String verificationReason,
                        String title, String message, String createdAt, boolean isRead) {
        this.notificationId = notificationId;
        this.citizenId = citizenId;
        this.recipientId = citizenId;
        this.complaintId = complaintId;
        this.originalImpact = originalImpact;
        this.verifiedImpact = verifiedImpact;
        this.verificationReason = verificationReason;
        this.title = title;
        this.message = message;
        this.type = "info";
        this.createdAt = createdAt != null ? createdAt : LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        this.isRead = isRead;
    }

    // Convenience constructor for general role/recipient notifications
    public Notification(String notificationId, String recipientId, String recipientRole,
                        String complaintId, String title, String message, String type) {
        this.notificationId = notificationId;
        this.citizenId = recipientId;
        this.recipientId = recipientId;
        this.recipientRole = recipientRole;
        this.complaintId = complaintId != null ? complaintId : "";
        this.title = title != null ? title : "";
        this.message = message != null ? message : "";
        this.type = (type != null && !type.isEmpty()) ? type : "info";
        this.originalImpact = 0;
        this.verifiedImpact = 0;
        this.verificationReason = "";
        this.createdAt = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        this.isRead = false;
    }

    // Full constructor
    public Notification(String notificationId, String citizenId, String recipientId, String recipientRole,
                        String complaintId, int originalImpact, int verifiedImpact, String verificationReason,
                        String title, String message, String type, String createdAt, boolean isRead) {
        this.notificationId = notificationId;
        this.citizenId = (citizenId != null && !citizenId.isEmpty()) ? citizenId : recipientId;
        this.recipientId = recipientId;
        this.recipientRole = recipientRole;
        this.complaintId = complaintId != null ? complaintId : "";
        this.originalImpact = originalImpact;
        this.verifiedImpact = verifiedImpact;
        this.verificationReason = verificationReason != null ? verificationReason : "";
        this.title = title != null ? title : "";
        this.message = message != null ? message : "";
        this.type = (type != null && !type.isEmpty()) ? type : "info";
        this.createdAt = createdAt != null ? createdAt : LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        this.isRead = isRead;
    }

    // Getters & Setters
    public String getNotificationId() { return notificationId; }
    public void setNotificationId(String notificationId) { this.notificationId = notificationId; }

    public String getCitizenId() { return citizenId != null ? citizenId : recipientId; }
    public void setCitizenId(String citizenId) { this.citizenId = citizenId; }

    public String getRecipientId() { return recipientId != null ? recipientId : citizenId; }
    public void setRecipientId(String recipientId) { this.recipientId = recipientId; }

    public String getRecipientRole() { return recipientRole; }
    public void setRecipientRole(String recipientRole) { this.recipientRole = recipientRole; }

    public String getComplaintId() { return complaintId; }
    public void setComplaintId(String complaintId) { this.complaintId = complaintId; }

    public int getOriginalImpact() { return originalImpact; }
    public void setOriginalImpact(int originalImpact) { this.originalImpact = originalImpact; }

    public int getVerifiedImpact() { return verifiedImpact; }
    public void setVerifiedImpact(int verifiedImpact) { this.verifiedImpact = verifiedImpact; }

    public String getVerificationReason() { return verificationReason; }
    public void setVerificationReason(String verificationReason) { this.verificationReason = verificationReason; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getType() { return type != null ? type : "info"; }
    public void setType(String type) { this.type = type; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public boolean isRead() { return isRead; }
    public void setRead(boolean read) { isRead = read; }
}
