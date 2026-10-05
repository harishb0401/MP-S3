package model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Model representing a Citizen Notification.
 * Generated automatically after Admin verification to keep the citizen informed
 * of their original impact value, verified impact value, verification reason, and date/time.
 */
public class Notification {
    private String notificationId;
    private String citizenId;
    private String complaintId;
    private int originalImpact;
    private int verifiedImpact;
    private String verificationReason;
    private String title;
    private String message;
    private String createdAt;
    private boolean isRead;

    public Notification() {}

    public Notification(String notificationId, String citizenId, String complaintId,
                        int originalImpact, int verifiedImpact, String verificationReason) {
        this.notificationId = notificationId;
        this.citizenId = citizenId;
        this.complaintId = complaintId;
        this.originalImpact = originalImpact;
        this.verifiedImpact = verifiedImpact;
        this.verificationReason = verificationReason;
        this.title = "Complaint " + complaintId + " Verified";
        this.message = String.format(
                "Complaint %s has been reviewed. Citizen Reported Impact: %d | Admin Verified Impact: %d | Reason: %s. Your original submission remains preserved in the system.",
                complaintId, originalImpact, verifiedImpact, verificationReason
        );
        this.createdAt = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        this.isRead = false;
    }

    public Notification(String notificationId, String citizenId, String complaintId,
                        int originalImpact, int verifiedImpact, String verificationReason,
                        String title, String message, String createdAt, boolean isRead) {
        this.notificationId = notificationId;
        this.citizenId = citizenId;
        this.complaintId = complaintId;
        this.originalImpact = originalImpact;
        this.verifiedImpact = verifiedImpact;
        this.verificationReason = verificationReason;
        this.title = title;
        this.message = message;
        this.createdAt = createdAt;
        this.isRead = isRead;
    }

    public String getNotificationId() { return notificationId; }
    public void setNotificationId(String notificationId) { this.notificationId = notificationId; }

    public String getCitizenId() { return citizenId; }
    public void setCitizenId(String citizenId) { this.citizenId = citizenId; }

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

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public boolean isRead() { return isRead; }
    public void setRead(boolean read) { isRead = read; }
}
