package model;

/**
 * Staff compatibility wrapper extending DepartmentStaff.
 */
public class Staff extends DepartmentStaff {
    public Staff() {
        super();
    }

    public Staff(String staffId, String staffName, Department department) {
        super(staffId, staffName, null, null, null, staffName,
                department != null ? department.getDepartmentId() : null, department, null);
    }
}
