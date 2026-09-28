package service;

import model.Complaint;
import model.User;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class GrievanceManager {
    private Database database;

    public GrievanceManager(Database database) {
        this.database = database;
    }

    // Category to Department Mapping
    public String mapCategoryToDepartment(String category) {
        if (category == null) return "General Administration";
        switch (category.trim()) {
            case "Streetlight":
                return "Electrical";
            case "Waste Management":
            case "Public Sanitation":
            case "Sanitation":
                return "Sanitation";
            case "Road Damage":
            case "Road":
                return "Roads";
            case "Water Supply":
                return "Water Supply";
            case "Drainage":
                return "Drainage";
            default:
                return "General Administration";
        }
    }

    // Priority Calculator Algorithm
    public String calculatePriority(String category, String description) {
        String desc = (description != null) ? description.toLowerCase() : "";
        String cat = (category != null) ? category.toLowerCase() : "";

        // Check for severe critical keywords
        if (desc.contains("burst") || desc.contains("flooding") || desc.contains("hazardous") ||
            desc.contains("medical waste") || desc.contains("fire") || desc.contains("collapse") ||
            desc.contains("emergency") || desc.contains("school")) {
            return "CRITICAL";
        }

        if (cat.contains("water") || cat.contains("streetlight") || desc.contains("overflow") || desc.contains("blockage")) {
            return "HIGH";
        }

        if (cat.contains("waste") || cat.contains("road") || cat.contains("sanitation")) {
            return "MEDIUM";
        }

        return "LOW";
    }

    // Process & Store New Complaint
    public Complaint createComplaint(User citizen, String category, String description, String location) {
        String id = "GRV-GUID-" + UUID.randomUUID().toString().substring(0, 8);
        String complaintId = database.generateComplaintId();

        String department = mapCategoryToDepartment(category);
        String priority = calculatePriority(category, description);
        String status = "PENDING";

        Complaint complaint = new Complaint(
                id,
                complaintId,
                citizen.getId(),
                citizen.getName(),
                citizen.getMobile(),
                category,
                description,
                location,
                department,
                priority,
                status
        );

        database.addComplaint(complaint);
        return complaint;
    }

    // Get Complaint by ID
    public Complaint getComplaintById(String id) {
        return database.getComplaintById(id);
    }

    // Get All Complaints
    public List<Complaint> getAllComplaints() {
        return database.getAllComplaints();
    }

    // Get Complaints for specific Citizen
    public List<Complaint> getCitizenComplaints(String citizenId) {
        return database.getComplaintsByCitizenId(citizenId);
    }

    // Get Ordered Priority Queue
    public List<Complaint> getPriorityQueueList() {
        return database.getOrderedPriorityQueueList();
    }

    // Assign Department & Staff
    public boolean assignDepartmentAndStaff(String complaintId, String department, String staffName) {
        Complaint complaint = database.getComplaintById(complaintId);
        if (complaint != null) {
            if (department != null && !department.trim().isEmpty()) {
                complaint.setDepartment(department);
            }
            if (staffName != null && !staffName.trim().isEmpty()) {
                complaint.setAssignedStaff(staffName);
            }
            if ("PENDING".equalsIgnoreCase(complaint.getStatus())) {
                complaint.setStatus("ASSIGNED");
            }
            complaint.setUpdatedAt(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            database.rebuildPriorityQueue();
            database.saveDatabase();
            return true;
        }
        return false;
    }

    // Update Complaint Status
    public boolean updateStatus(String complaintId, String newStatus) {
        Complaint complaint = database.getComplaintById(complaintId);
        if (complaint != null) {
            complaint.setStatus(newStatus.toUpperCase());
            String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            complaint.setUpdatedAt(now);
            if ("RESOLVED".equalsIgnoreCase(newStatus) || "CLOSED".equalsIgnoreCase(newStatus)) {
                complaint.setResolvedAt(now);
            }
            database.rebuildPriorityQueue();
            database.saveDatabase();
            return true;
        }
        return false;
    }

    // Resolve Complaint with Notes
    public boolean resolveComplaint(String complaintId, String resolutionNotes) {
        Complaint complaint = database.getComplaintById(complaintId);
        if (complaint != null) {
            complaint.setStatus("RESOLVED");
            complaint.setResolutionNotes(resolutionNotes);
            String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            complaint.setUpdatedAt(now);
            complaint.setResolvedAt(now);
            database.rebuildPriorityQueue();
            database.saveDatabase();
            return true;
        }
        return false;
    }

    // Statistics Calculation from Database
    public Map<String, Integer> getDashboardStats() {
        List<Complaint> all = database.getAllComplaints();
        int total = all.size();
        int pending = 0;
        int inProgress = 0;
        int resolved = 0;
        int critical = 0;

        for (Complaint c : all) {
            String s = c.getStatus() != null ? c.getStatus().toUpperCase() : "";
            if ("PENDING".equals(s) || "ASSIGNED".equals(s)) pending++;
            else if ("IN_PROGRESS".equals(s)) inProgress++;
            else if ("RESOLVED".equals(s) || "CLOSED".equals(s)) resolved++;

            if ("CRITICAL".equalsIgnoreCase(c.getPriority())) critical++;
        }

        Map<String, Integer> stats = new HashMap<>();
        stats.put("total", total);
        stats.put("pending", pending);
        stats.put("inProgress", inProgress);
        stats.put("resolved", resolved);
        stats.put("critical", critical);
        return stats;
    }
}
