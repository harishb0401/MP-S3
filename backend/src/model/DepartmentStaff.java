package model;

/**
 * Concrete class representing Municipal Department Staff.
 * Inherits from User (OOP Inheritance).
 * Department information is linked internally to the account.
 * Permissions:
 * - View Assigned Complaints
 * - Update Complaint Status (Assigned -> In Progress -> Resolved)
 * - Add Remarks
 * - Mark Complaint as Resolved
 * Strict Restrictions:
 * - Cannot modify Citizen Reported Impact
 * - Cannot modify Admin Verified Impact
 * - Cannot modify Severity
 * - Cannot modify Verification Reason
 */
public class DepartmentStaff extends User {
    private String staffId;
    private String staffName;
    private String departmentId;
    private Department department;

    public DepartmentStaff() {
        super();
        setRole("STAFF");
    }

    public DepartmentStaff(String id, String username, String email, String mobile, String passwordHash,
                           String staffName, String departmentId, Department department, String createdAt) {
        super(id, username, email, mobile, passwordHash, "STAFF",
                department != null ? department.getDepartmentName() : "General", createdAt);
        this.staffId = id;
        this.staffName = staffName;
        this.departmentId = departmentId;
        this.department = department;
    }

    @Override
    public String getDisplayName() {
        String deptName = (department != null) ? department.getDepartmentName() : getDepartment();
        return staffName + " (" + (deptName != null && !deptName.isEmpty() ? deptName : "Unassigned") + ")";
    }

    @Override
    public boolean canVerifyComplaints() {
        return false; // Department staff cannot modify impact, severity, or verification
    }

    @Override
    public boolean canAssignDepartment() {
        return false;
    }

    @Override
    public boolean canUpdateStatus() {
        return true; // Can update Assigned -> In Progress -> Resolved
    }

    public String getStaffId() { return staffId != null ? staffId : getId(); }
    public void setStaffId(String staffId) { this.staffId = staffId; setId(staffId); }

    public String getStaffName() { return staffName; }
    public void setStaffName(String staffName) { this.staffName = staffName; }

    public String getDepartmentId() { return departmentId; }
    public void setDepartmentId(String departmentId) { this.departmentId = departmentId; }

    public Department getDepartmentObj() { return department; }
    public void setDepartmentObj(Department department) {
        this.department = department;
        if (department != null) {
            setDepartment(department.getDepartmentName());
            this.departmentId = department.getDepartmentId();
        }
    }

    @Override
    public String toString() {
        return getDisplayName();
    }
}
