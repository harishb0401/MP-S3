package model;

/**
 * Concrete Grievance representing municipal water supply, leaks, and drainage issues.
 * Subclasses Complaint (which extends the Grievance abstraction) and polymorphically overrides responsibleDepartment().
 */
public class WaterGrievance extends Complaint {

    public WaterGrievance() {
        super();
        this.category = "Water Supply";
        this.department = "Water Department";
        this.departmentId = "DEPT-WATER";
    }

    public WaterGrievance(String id, String complaintId, String citizenId, String citizenName, String citizenMobile,
                          String description, String location, int citizenReportedImpact, String priority, String status) {
        super(id, complaintId, citizenId, citizenName, citizenMobile, "Water Supply", description, location, "Water Department", citizenReportedImpact, priority, status);
        this.departmentId = "DEPT-WATER";
    }

    public WaterGrievance(String id, String complaintId, String citizenId, String citizenName, String citizenMobile,
                          String category, String description, String location, String department,
                          int citizenReportedImpact, String priority, String status) {
        super(id, complaintId, citizenId, citizenName, citizenMobile, category != null ? category : "Water Supply", description, location, department != null ? department : "Water Department", citizenReportedImpact, priority, status);
        this.departmentId = "DEPT-WATER";
    }

    public WaterGrievance(Complaint source) {
        super(source);
        if (this.category == null || this.category.isEmpty()) {
            this.category = "Water Supply";
        }
        if (this.department == null || this.department.isEmpty()) {
            this.department = "Water Department";
        }
        this.departmentId = "DEPT-WATER";
    }

    @Override
    public String responsibleDepartment() {
        return "Water Department";
    }
}
