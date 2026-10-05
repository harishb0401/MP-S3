package service;

import model.Complaint;
import model.ComplaintHistory;
import model.Department;
import model.Notification;
import model.User;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Core Business Logic Controller for the Municipal Grievance System.
 * Enforces:
 * - Citizen Reported Impact immutability
 * - Admin verification and mandatory reason validation
 * - Citizen Notification generation upon verification
 * - Multi-criteria PriorityQueue sorting (Severity -> Impact -> Arrival)
 * - Department Capacity Management (Capacity vs Current Load)
 * - Higher Priority Arrival Rule: in-progress complaints are never interrupted
 * - Duplicate Complaint Detection & Merge/Separate handling
 * - Sequential Audit History tracking
 */
public class GrievanceManager {
    private Database database;

    public GrievanceManager(Database database) {
        this.database = database;
    }

    // Category to Department Mapping
    public String mapCategoryToDepartment(String category) {
        if (category == null) return "General Administration";
        switch (category.trim()) {
            case "Water Supply":
            case "Water":
                return "Water Department";
            case "Road Damage":
            case "Road":
            case "Roads":
                return "Roads Department";
            case "Waste Management":
            case "Public Sanitation":
            case "Sanitation":
                return "Waste Management Department";
            case "Streetlight":
            case "Electrical":
                return "Streetlight Department";
            default:
                return "General Administration";
        }
    }

    // Priority Calculator Algorithm
    public String calculatePriority(String category, String description, int impact) {
        String desc = (description != null) ? description.toLowerCase() : "";
        String cat = (category != null) ? category.toLowerCase() : "";

        // Check for severe critical hazards or high impact count
        if (impact >= 500 || desc.contains("burst") || desc.contains("flooding") ||
            desc.contains("hazardous") || desc.contains("medical waste") ||
            desc.contains("fire") || desc.contains("collapse") ||
            desc.contains("emergency") || desc.contains("school")) {
            return "CRITICAL";
        }

        if (impact >= 200 || cat.contains("water") || cat.contains("streetlight") ||
            desc.contains("overflow") || desc.contains("blockage")) {
            return "HIGH";
        }

        if (cat.contains("waste") || cat.contains("road") || cat.contains("sanitation")) {
            return "MEDIUM";
        }

        return "LOW";
    }

    // 1. CREATE COMPLAINT (CITIZEN PORTAL)
    public Complaint createComplaint(User citizen, String category, String description, String location, int citizenReportedImpact) {
        String id = "GRV-GUID-" + UUID.randomUUID().toString().substring(0, 8);
        String complaintId = database.generateComplaintId();

        int impact = citizenReportedImpact > 0 ? citizenReportedImpact : 1;
        String departmentName = mapCategoryToDepartment(category);
        Department dept = database.getDepartmentByAnyIdentifier(departmentName);
        String departmentId = dept != null ? dept.getDepartmentId() : "DEPT-GEN";

        String priority = calculatePriority(category, description, impact);
        String status = "Registered"; // Initial status as specified

        Complaint complaint = new Complaint(
                id,
                complaintId,
                citizen.getId(),
                citizen.getDisplayName(),
                citizen.getMobile(),
                category,
                description,
                location,
                dept != null ? dept.getDepartmentName() : departmentName,
                impact,
                priority,
                status
        );
        complaint.setDepartmentId(departmentId);

        // Duplicate Check using Category + Location
        List<Complaint> potentialDuplicates = findPotentialDuplicates(category, location);
        if (!potentialDuplicates.isEmpty()) {
            complaint.setDuplicate(true);
            complaint.setDuplicateOfId(potentialDuplicates.get(0).getComplaintId());
        }

        database.addComplaint(complaint);

        // Record Initial Audit History Entry
        String remarks = "Complaint registered by citizen with Reported Impact: " + impact +
                (complaint.isDuplicate() ? " (Flagged potential duplicate of " + complaint.getDuplicateOfId() + ")" : "");
        recordHistory(complaintId, "None", "Registered", citizen.getDisplayName(), remarks);

        return complaint;
    }

    // Compatibility overload
    public Complaint createComplaint(User citizen, String category, String description, String location) {
        return createComplaint(citizen, category, description, location, 1);
    }

    // 2. ADMIN VERIFICATION (ADMIN PORTAL)
    public boolean verifyComplaint(String complaintId, int verifiedImpact, String verificationReason, String severity, User admin) {
        Complaint complaint = database.getComplaintById(complaintId);
        if (complaint == null) return false;

        // Validation: Only Admin can verify
        if (admin != null && !admin.canVerifyComplaints()) {
            throw new SecurityException("Unauthorized: Only Municipal Administrators can verify complaints.");
        }

        // Validation: Verification reason is mandatory
        if (verificationReason == null || verificationReason.trim().isEmpty()) {
            throw new IllegalArgumentException("Verification Reason is mandatory.");
        }

        // Validation: Verified impact must be positive
        if (verifiedImpact <= 0) {
            throw new IllegalArgumentException("Admin Verified Impact must be greater than zero.");
        }

        String oldStatus = complaint.getStatus();

        // Citizen Reported Impact is untouched! Admin Verified Impact is stored separately:
        complaint.setAdminVerifiedImpact(verifiedImpact);
        complaint.setVerificationReason(verificationReason.trim());
        complaint.setSeverity(severity != null && !severity.trim().isEmpty() ? severity.toUpperCase().trim() : "MEDIUM");
        complaint.setStatus("Under Review");
        complaint.setVerifiedLocked(true); // Lock verification data
        complaint.setUpdatedAt(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));

        // Generate Citizen Notification
        Notification notification = new Notification(
                database.generateNotificationId(),
                complaint.getCitizenId(),
                complaint.getComplaintId(),
                complaint.getCitizenReportedImpact(),
                verifiedImpact,
                verificationReason.trim()
        );
        database.addNotification(notification);

        // Record Audit Trail
        String remarks = String.format("Admin verified impact: %d (Citizen Reported: %d). Severity set to %s. Reason: %s",
                verifiedImpact, complaint.getCitizenReportedImpact(), complaint.getSeverity(), verificationReason.trim());
        recordHistory(complaintId, oldStatus, "Under Review", admin != null ? admin.getDisplayName() : "Municipal Admin", remarks);

        database.rebuildPriorityQueueAndCapacity();
        database.saveDatabase();
        return true;
    }

    // 3. ASSIGN DEPARTMENT & STAFF WITH CAPACITY MANAGEMENT
    public boolean assignDepartmentAndStaff(String complaintId, String departmentName, String staffName, User actor) {
        Complaint complaint = database.getComplaintById(complaintId);
        if (complaint == null) return false;

        Department dept = database.getDepartmentByAnyIdentifier(departmentName);
        if (dept == null) dept = database.getDepartmentByAnyIdentifier(complaint.getDepartment());

        complaint.setDepartment(dept.getDepartmentName());
        complaint.setDepartmentId(dept.getDepartmentId());

        if (staffName != null && !staffName.trim().isEmpty()) {
            complaint.setAssignedStaff(staffName.trim());
        }

        String oldStatus = complaint.getStatus();

        // Department Capacity Check
        if (dept.hasAvailableCapacity()) {
            // Department has capacity -> assign immediately
            complaint.setStatus("Assigned");
            dept.incrementLoad();
            String remarks = String.format("Assigned to %s (Officer: %s). Department Load: %d/%d.",
                    dept.getDepartmentName(), complaint.getAssignedStaff(), dept.getCurrentLoad(), dept.getCapacity());
            recordHistory(complaintId, oldStatus, "Assigned", actor != null ? actor.getDisplayName() : "Municipal Admin", remarks);
        } else {
            // Department is at full capacity -> put in department waiting PriorityQueue
            dept.addToWaitingQueue(complaint);
            complaint.setStatus("Assigned");
            String remarks = String.format("Department %s at maximum capacity (%d/%d). Complaint queued in Department Waiting PriorityQueue.",
                    dept.getDepartmentName(), dept.getCapacity(), dept.getCapacity());
            recordHistory(complaintId, oldStatus, "Assigned (Queued)", actor != null ? actor.getDisplayName() : "Municipal Admin", remarks);
        }

        complaint.setUpdatedAt(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        database.rebuildPriorityQueueAndCapacity();
        database.saveDatabase();
        return true;
    }

    // 4. UPDATE COMPLAINT STATUS (DEPARTMENT PORTAL)
    public boolean updateStatus(String complaintId, String newStatus, String remarksText, User actor) {
        Complaint complaint = database.getComplaintById(complaintId);
        if (complaint == null) return false;

        String targetStatus = newStatus.trim();
        String oldStatus = complaint.getStatus();
        complaint.setStatus(targetStatus);

        String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        complaint.setUpdatedAt(now);

        Department dept = database.getDepartmentByAnyIdentifier(complaint.getDepartment());

        // If status marked RESOLVED or CLOSED -> free up department capacity and promote waiting complaints
        if ("RESOLVED".equalsIgnoreCase(targetStatus) || "CLOSED".equalsIgnoreCase(targetStatus)) {
            complaint.setResolvedAt(now);

            if (dept != null) {
                dept.decrementLoad();

                // Capacity Management: Poll next highest priority complaint from waiting queue
                if (!dept.getWaitingQueue().isEmpty()) {
                    Complaint promoted = dept.pollWaitingQueue();
                    if (promoted != null) {
                        promoted.setStatus("Assigned");
                        dept.incrementLoad();
                        promoted.setUpdatedAt(now);
                        recordHistory(promoted.getComplaintId(), "Assigned (Queued)", "Assigned",
                                "Department System", "Promoted from Department Waiting Queue to Active assignment as capacity opened up.");
                    }
                }
            }
        }

        String userDisplay = actor != null ? actor.getDisplayName() : "Municipal Staff";
        String remarks = (remarksText != null && !remarksText.trim().isEmpty()) ? remarksText.trim() : "Status updated to " + targetStatus;
        recordHistory(complaintId, oldStatus, targetStatus, userDisplay, remarks);

        database.rebuildPriorityQueueAndCapacity();
        database.saveDatabase();
        return true;
    }

    public boolean updateStatus(String complaintId, String newStatus) {
        return updateStatus(complaintId, newStatus, "", null);
    }

    // 5. RESOLVE COMPLAINT (DEPARTMENT PORTAL)
    public boolean resolveComplaint(String complaintId, String resolutionNotes, User actor) {
        Complaint complaint = database.getComplaintById(complaintId);
        if (complaint == null) return false;

        complaint.setResolutionNotes(resolutionNotes != null ? resolutionNotes.trim() : "");
        return updateStatus(complaintId, "Resolved", "Resolution Notes: " + resolutionNotes, actor);
    }

    public boolean resolveComplaint(String complaintId, String resolutionNotes) {
        return resolveComplaint(complaintId, resolutionNotes, null);
    }

    // 6. DUPLICATE DETECTION & MERGE / SEPARATE
    public List<Complaint> findPotentialDuplicates(String category, String location) {
        List<Complaint> duplicates = new ArrayList<>();
        if (category == null || location == null) return duplicates;

        String normCat = category.trim().toLowerCase();
        String normLoc = location.trim().toLowerCase();

        for (Complaint c : database.getAllComplaints()) {
            if ("RESOLVED".equalsIgnoreCase(c.getStatus()) || "CLOSED".equalsIgnoreCase(c.getStatus())) {
                continue;
            }
            if (c.getCategory() != null && c.getLocation() != null &&
                c.getCategory().trim().equalsIgnoreCase(normCat) &&
                c.getLocation().trim().equalsIgnoreCase(normLoc)) {
                duplicates.add(c);
            }
        }
        return duplicates;
    }

    public boolean mergeDuplicate(String duplicateComplaintId, String primaryComplaintId, User admin, String reason) {
        Complaint dup = database.getComplaintById(duplicateComplaintId);
        Complaint primary = database.getComplaintById(primaryComplaintId);
        if (dup == null || primary == null) return false;

        dup.setDuplicate(true);
        dup.setDuplicateOfId(primary.getComplaintId());
        dup.setStatus("Resolved");
        dup.setResolutionNotes("Merged into primary complaint " + primary.getComplaintId() + ". Reason: " + reason);

        String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        dup.setResolvedAt(now);
        dup.setUpdatedAt(now);

        recordHistory(duplicateComplaintId, dup.getStatus(), "Resolved (Merged)",
                admin != null ? admin.getDisplayName() : "Admin",
                "Merged into " + primary.getComplaintId() + ". Reason: " + reason);

        database.rebuildPriorityQueueAndCapacity();
        database.saveDatabase();
        return true;
    }

    public boolean keepSeparate(String complaintId, User admin) {
        Complaint complaint = database.getComplaintById(complaintId);
        if (complaint == null) return false;

        complaint.setDuplicate(false);
        complaint.setDuplicateOfId(null);
        recordHistory(complaintId, complaint.getStatus(), complaint.getStatus(),
                admin != null ? admin.getDisplayName() : "Admin",
                "Reviewed by Admin and marked as an independent, separate complaint.");

        database.saveDatabase();
        return true;
    }

    // 7. AUDIT TRAIL TIMELINE
    public void recordHistory(String complaintId, String oldStatus, String newStatus, String updatedBy, String remarks) {
        ComplaintHistory history = new ComplaintHistory(
                database.generateHistoryId(),
                complaintId,
                oldStatus,
                newStatus,
                updatedBy,
                remarks
        );
        database.addHistory(history);
    }

    public List<ComplaintHistory> getComplaintTimeline(String complaintId) {
        return database.getComplaintHistory(complaintId);
    }

    // 8. CITIZEN NOTIFICATIONS
    public List<Notification> getCitizenNotifications(String citizenId) {
        return database.getNotificationsByCitizenId(citizenId);
    }

    // 9. QUERIES & STATS
    public Complaint getComplaintById(String id) {
        return database.getComplaintById(id);
    }

    public List<Complaint> getAllComplaints() {
        return database.getAllComplaints();
    }

    public List<Complaint> getCitizenComplaints(String citizenId) {
        return database.getComplaintsByCitizenId(citizenId);
    }

    public List<Complaint> getPriorityQueueList() {
        return database.getOrderedPriorityQueueList();
    }

    public List<Department> getAllDepartments() {
        return database.getAllUniqueDepartments();
    }

    public Map<String, Integer> getDashboardStats() {
        List<Complaint> all = database.getAllComplaints();
        int total = all.size();
        int pending = 0;
        int inProgress = 0;
        int resolved = 0;
        int critical = 0;

        for (Complaint c : all) {
            String s = c.getStatus() != null ? c.getStatus().toUpperCase() : "";
            if ("REGISTERED".equals(s) || "PENDING".equals(s) || "UNDER REVIEW".equals(s) || "ASSIGNED".equals(s)) {
                pending++;
            } else if ("IN_PROGRESS".equals(s) || "IN PROGRESS".equals(s)) {
                inProgress++;
            } else if ("RESOLVED".equals(s) || "CLOSED".equals(s)) {
                resolved++;
            }

            if ("CRITICAL".equalsIgnoreCase(c.getPriority()) || "HIGH".equalsIgnoreCase(c.getSeverity())) {
                critical++;
            }
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
