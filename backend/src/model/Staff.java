package model;

public class Staff {
    private String staffId;
    private String staffName;
    private Department department;

    public Staff(String staffId, String staffName, Department department) {
        this.staffId = staffId;
        this.staffName = staffName;
        this.department = department;
    }

    public String getStaffId() {
        return staffId;
    }

    public void setStaffId(String staffId) {
        this.staffId = staffId;
    }

    public String getStaffName() {
        return staffName;
    }

    public void setStaffName(String staffName) {
        this.staffName = staffName;
    }

    public Department getDepartment() {
        return department;
    }

    public void setDepartment(Department department) {
        this.department = department;
    }

    @Override
    public String toString() {
        return staffName + " (" + (department != null ? department.getDepartmentName() : "Unassigned") + ")";
    }
}
