package model;

/**
 * Concrete class representing a Municipal Grievance Triage Manager.
 * Inherits from User (OOP Inheritance).
 * Responsibilities:
 * - Review newly registered complaints awaiting triage
 * - Assign and reassign complaints to the 4 core departments
 * - Review and confirm/reject duplicate candidates
 * - Monitor department capacities, waiting queues, and workload reports
 * Strict Restrictions:
 * - Cannot create or delete worker accounts
 * - Cannot grant admin privileges
 */
public class Manager extends User {
    private String managerId;
    private String fullName;

    public Manager() {
        super();
        setRole("MANAGER");
        setDepartment("Administration");
    }

    public Manager(String id, String username, String email, String mobile, String passwordHash, String fullName, String createdAt) {
        super(id, username, email, mobile, passwordHash, "MANAGER", "Administration", createdAt);
        this.managerId = id;
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

    @Override
    public boolean canManageDuplicates() {
        return true;
    }

    @Override
    public boolean canManageWorkers() {
        return false;
    }

    public String getManagerId() {
        return managerId != null ? managerId : getId();
    }

    public void setManagerId(String managerId) {
        this.managerId = managerId;
        setId(managerId);
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    @Override
    public String toString() {
        return "Manager: " + getDisplayName() + " [" + getManagerId() + "]";
    }
}
