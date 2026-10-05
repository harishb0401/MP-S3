package model;

/**
 * Concrete class representing an Administrator.
 * Inherits from User (OOP Inheritance).
 * Responsibilities:
 * - Review & Verify Complaints
 * - Set Severity & Verified Impact
 * - Mandatory Verification Reason
 * - Detect Duplicate Complaints & Merge / Keep Separate
 * - Assign Departments
 * - View Audit History & Reports
 */
public class Admin extends User {
    private String adminId;
    private String fullName;

    public Admin() {
        super();
        setRole("ADMIN");
    }

    public Admin(String id, String username, String email, String mobile, String passwordHash, String fullName, String createdAt) {
        super(id, username, email, mobile, passwordHash, "ADMIN", "Administration", createdAt);
        this.adminId = id;
        this.fullName = fullName;
    }

    @Override
    public String getDisplayName() {
        return (fullName != null && !fullName.isEmpty()) ? fullName : getUsername();
    }

    @Override
    public boolean canVerifyComplaints() {
        return true;
    }

    @Override
    public boolean canAssignDepartment() {
        return true;
    }

    @Override
    public boolean canUpdateStatus() {
        return true;
    }

    public String getAdminId() { return adminId != null ? adminId : getId(); }
    public void setAdminId(String adminId) { this.adminId = adminId; setId(adminId); }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    @Override
    public String toString() {
        return "Admin: " + getDisplayName() + " [" + getAdminId() + "]";
    }
}
