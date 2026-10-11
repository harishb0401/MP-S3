package model;

/**
 * Concrete Grievance representing municipal road damage, potholes, and pavement defects.
 * Subclasses Complaint (which extends the Grievance abstraction) and polymorphically overrides responsibleDepartment().
 */
public class RoadGrievance extends Complaint {

    public RoadGrievance() {
        super();
        this.category = "Roads";
        this.department = "Roads Department";
        this.departmentId = "DEPT-ROADS";
    }

    public RoadGrievance(String id, String complaintId, String citizenId, String citizenName, String citizenMobile,
                         String description, String location, int citizenReportedImpact, String priority, String status) {
        super(id, complaintId, citizenId, citizenName, citizenMobile, "Roads", description, location, "Roads Department", citizenReportedImpact, priority, status);
        this.departmentId = "DEPT-ROADS";
    }

    public RoadGrievance(String id, String complaintId, String citizenId, String citizenName, String citizenMobile,
                         String category, String description, String location, String department,
                         int citizenReportedImpact, String priority, String status) {
        super(id, complaintId, citizenId, citizenName, citizenMobile, category != null ? category : "Roads", description, location, department != null ? department : "Roads Department", citizenReportedImpact, priority, status);
        this.departmentId = "DEPT-ROADS";
    }

    public RoadGrievance(Complaint source) {
        super(source);
        if (this.category == null || this.category.isEmpty()) {
            this.category = "Roads";
        }
        if (this.department == null || this.department.isEmpty()) {
            this.department = "Roads Department";
        }
        this.departmentId = "DEPT-ROADS";
    }

    @Override
    public String responsibleDepartment() {
        return "Roads Department";
    }
}
