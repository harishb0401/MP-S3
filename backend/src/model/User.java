package model;

/**
 * Abstract Base Class representing a User in the Government Grievance System.
 * Demonstrates OOP Abstraction and Encapsulation.
 * Subclasses: Citizen, Admin, DepartmentStaff.
 */
public abstract class User {
    private String id;
    private String username;
    private String email;
    private String mobile;
    private String passwordHash;
    private String role; // "CITIZEN", "ADMIN", "STAFF"
    private String department;
    private String createdAt;

    public User() {}

    public User(String id, String username, String email, String mobile, String passwordHash, String role, String department, String createdAt) {
        this.id = id;
        this.username = username;
        this.email = email;
        this.mobile = mobile;
        this.passwordHash = passwordHash;
        this.role = role;
        this.department = department;
        this.createdAt = createdAt;
    }

    // Abstract methods demonstrating Polymorphism
    public abstract String getDisplayName();
    public abstract boolean canVerifyComplaints();
    public abstract boolean canAssignDepartment();
    public abstract boolean canUpdateStatus();

    // Immutable rule: No user can modify citizen reported impact
    public final boolean canModifyCitizenReportedImpact() {
        return false;
    }

    // Encapsulated Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getMobile() { return mobile; }
    public void setMobile(String mobile) { this.mobile = mobile; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getDepartment() { return department != null ? department : ""; }
    public void setDepartment(String department) { this.department = department; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    // Compatibility methods for JSON serialization and legacy handlers
    public String getName() {
        return getDisplayName();
    }
    public void setName(String name) {
        this.username = name;
    }
}
