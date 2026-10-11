package model;

/**
 * Factory and Adapter for creating and mapping concrete Grievance subclasses.
 * Enables polymorphic grievance creation while maintaining 100% backward compatibility
 * with existing JSON persistence, REST APIs, and database models.
 */
public class GrievanceFactory {

    /**
     * Creates the appropriate concrete grievance instance based on the complaint category.
     */
    public static Complaint createGrievance(String id, String complaintId, String citizenId, String citizenName, String citizenMobile,
                                            String category, String description, String location, String department,
                                            int citizenReportedImpact, String priority, String status) {
        String cat = category != null ? category.trim().toLowerCase() : "";
        if (cat.contains("water") || cat.contains("drain")) {
            return new WaterGrievance(id, complaintId, citizenId, citizenName, citizenMobile, category, description, location, department, citizenReportedImpact, priority, status);
        } else if (cat.contains("road")) {
            return new RoadGrievance(id, complaintId, citizenId, citizenName, citizenMobile, category, description, location, department, citizenReportedImpact, priority, status);
        } else {
            return new Complaint(id, complaintId, citizenId, citizenName, citizenMobile, category, description, location, department, citizenReportedImpact, priority, status);
        }
    }

    /**
     * Creates the appropriate concrete grievance instance with explicit severity.
     */
    public static Complaint createGrievance(String id, String complaintId, String citizenId, String citizenName, String citizenMobile,
                                            String category, String description, String location, String department,
                                            int citizenReportedImpact, String severity, String priority, String status) {
        String cat = category != null ? category.trim().toLowerCase() : "";
        Complaint complaint;
        if (cat.contains("water") || cat.contains("drain")) {
            complaint = new WaterGrievance(id, complaintId, citizenId, citizenName, citizenMobile, category, description, location, department, citizenReportedImpact, priority, status);
        } else if (cat.contains("road")) {
            complaint = new RoadGrievance(id, complaintId, citizenId, citizenName, citizenMobile, category, description, location, department, citizenReportedImpact, priority, status);
        } else {
            complaint = new Complaint(id, complaintId, citizenId, citizenName, citizenMobile, category, description, location, department, citizenReportedImpact, severity, priority, status);
        }
        if (severity != null && !severity.trim().isEmpty()) {
            complaint.setSeverity(severity);
        }
        return complaint;
    }

    /**
     * Adapts an existing Complaint instance into its specialized subclass if applicable.
     */
    public static Complaint adapt(Complaint source) {
        if (source == null) return null;
        if (source instanceof WaterGrievance || source instanceof RoadGrievance) {
            return source;
        }
        String cat = source.getCategory() != null ? source.getCategory().trim().toLowerCase() : "";
        if (cat.contains("water") || cat.contains("drain")) {
            return new WaterGrievance(source);
        } else if (cat.contains("road")) {
            return new RoadGrievance(source);
        }
        return source;
    }
}
