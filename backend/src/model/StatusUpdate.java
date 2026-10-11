package model;

/**
 * Model representing a Status Transition Request.
 * Encapsulates the target status, optional remarks, operator/actor, and exceptional workflow flags.
 */
public class StatusUpdate {
    private String status;
    private String remarks;
    private boolean explicitCancellation;
    private boolean explicitReassignment;

    public StatusUpdate() {}

    public StatusUpdate(String status, String remarks) {
        this.status = status;
        this.remarks = remarks;
        this.explicitCancellation = false;
        this.explicitReassignment = false;
    }

    public StatusUpdate(String status, String remarks, boolean explicitCancellation, boolean explicitReassignment) {
        this.status = status;
        this.remarks = remarks;
        this.explicitCancellation = explicitCancellation;
        this.explicitReassignment = explicitReassignment;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getRemarks() {
        return remarks;
    }

    public void setRemarks(String remarks) {
        this.remarks = remarks;
    }

    public boolean isExplicitCancellation() {
        return explicitCancellation;
    }

    public void setExplicitCancellation(boolean explicitCancellation) {
        this.explicitCancellation = explicitCancellation;
    }

    public boolean isExplicitReassignment() {
        return explicitReassignment;
    }

    public void setExplicitReassignment(boolean explicitReassignment) {
        this.explicitReassignment = explicitReassignment;
    }
}
