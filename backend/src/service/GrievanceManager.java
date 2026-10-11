package service;

import model.Complaint;
import model.ComplaintHistory;
import model.ComplaintStatus;
import model.Department;
import model.DuplicateCandidate;
import model.Grievance;
import model.GrievanceFactory;
import model.LocationNormalizer;
import model.Notification;
import model.StatusTransitionPolicy;
import model.StatusUpdate;
import model.User;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    public Complaint createComplaint(User citizen, String category, String description, String location, int citizenReportedImpact, String suppliedSeverity) {
        String id = "GRV-GUID-" + UUID.randomUUID().toString().substring(0, 8);
        String complaintId = database.generateComplaintId();

        int impact = citizenReportedImpact > 0 ? citizenReportedImpact : 1;
        String departmentName = mapCategoryToDepartment(category);
        Department dept = database.getDepartmentByAnyIdentifier(departmentName);
        String departmentId = dept != null ? dept.getDepartmentId() : "DEPT-GEN";

        String calculatedPriority = calculatePriority(category, description, impact);
        String severityToUse = (suppliedSeverity != null && !suppliedSeverity.trim().isEmpty())
                ? Complaint.normalizeSeverity(suppliedSeverity)
                : calculatedPriority;
        String priorityToUse = (suppliedSeverity != null && !suppliedSeverity.trim().isEmpty())
                ? Complaint.normalizeSeverity(suppliedSeverity)
                : calculatedPriority;

        String status = "Registered"; // Initial status as specified

        Complaint complaint = GrievanceFactory.createGrievance(
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
                severityToUse,
                priorityToUse,
                status
        );
        complaint.setDepartmentId(departmentId);

        database.addComplaint(complaint);

        // Record Initial Audit History Entry
        String remarks = "Complaint registered by citizen with Reported Impact: " + impact;
        recordHistory(complaintId, "None", "Registered", citizen.getDisplayName(), remarks);

        return complaint;
    }

    public Complaint createComplaint(User citizen, String category, String description, String location, int citizenReportedImpact) {
        return createComplaint(citizen, category, description, location, citizenReportedImpact, null);
    }

    // Compatibility overload
    public Complaint createComplaint(User citizen, String category, String description, String location) {
        return createComplaint(citizen, category, description, location, 1, null);
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

        // Remove from waiting queues before modifying ranking fields to preserve heap ordering
        Department dept = database.getDepartmentByAnyIdentifier(complaint.getDepartment());
        database.getPriorityQueue().remove(complaint);
        if (dept != null) {
            dept.removeFromWaitingQueue(complaint);
        }

        // Citizen Reported Impact is untouched! Admin Verified Impact is stored separately:
        complaint.setAdminVerifiedImpact(verifiedImpact);
        complaint.setVerificationReason(verificationReason.trim());
        String normSeverity = Complaint.normalizeSeverity(severity != null && !severity.trim().isEmpty() ? severity : "MEDIUM");
        complaint.setSeverity(normSeverity);
        complaint.setPriority(normSeverity);
        String effectiveStatus = oldStatus != null ? ComplaintStatus.toCanonical(oldStatus) : ComplaintStatus.REGISTERED.getCanonicalName();
        complaint.setStatus(effectiveStatus != null ? effectiveStatus : ComplaintStatus.REGISTERED.getCanonicalName());
        complaint.setVerifiedLocked(true); // Lock verification data
        complaint.setUpdatedAt(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));

        // Re-insert into PriorityQueue so the queue remains correctly ordered
        database.getPriorityQueue().add(complaint);
        if (dept != null && !dept.hasAvailableCapacity()) {
            dept.addToWaitingQueue(complaint);
        }

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
        recordHistory(complaintId, oldStatus, complaint.getStatus(), admin != null ? admin.getDisplayName() : "Municipal Admin", remarks);

        database.saveDatabase();
        return true;
    }

    /**
     * Updates the severity / priority or verified impact of a waiting complaint.
     * When any ranking field changes:
     * - Checks that the complaint is not resolved (does not mutate resolved complaints).
     * - Removes the complaint from priority queues (global PriorityQueue and Department waitingQueue).
     * - Updates the ranking fields.
     * - Re-inserts the complaint into the priority queues to re-establish correct heap order.
     */
    public boolean updateWaitingComplaintPriority(String complaintId, String newSeverity, Integer newVerifiedImpact, User actor, String remarks) {
        Complaint complaint = database.getComplaintById(complaintId);
        if (complaint == null) return false;

        boolean isResolved = "RESOLVED".equalsIgnoreCase(complaint.getStatus()) || "CLOSED".equalsIgnoreCase(complaint.getStatus());
        if (isResolved) {
            // Do not unnecessarily reorder or mutate resolved complaints
            return false;
        }

        Department dept = database.getDepartmentByAnyIdentifier(complaint.getDepartment());
        boolean wasInDeptWaiting = dept != null && dept.getWaitingQueue().contains(complaint);

        // 1. Remove from queues before changing ranking fields
        database.getPriorityQueue().remove(complaint);
        if (dept != null) {
            dept.removeFromWaitingQueue(complaint);
        }

        // 2. Update ranking fields
        String oldSeverity = complaint.getSeverity();
        if (newSeverity != null && !newSeverity.trim().isEmpty()) {
            String norm = Complaint.normalizeSeverity(newSeverity);
            complaint.setSeverity(norm);
            complaint.setPriority(norm);
        }

        if (newVerifiedImpact != null && newVerifiedImpact > 0) {
            complaint.setAdminVerifiedImpact(newVerifiedImpact);
        }

        String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        complaint.setUpdatedAt(now);

        // 3. Re-insert into queues to restore heap order
        database.getPriorityQueue().add(complaint);
        if (wasInDeptWaiting && dept != null) {
            dept.addToWaitingQueue(complaint);
        }

        // Audit history
        String userDisplay = actor != null ? actor.getDisplayName() : "Municipal System";
        String r = (remarks != null && !remarks.trim().isEmpty())
                ? remarks
                : String.format("Priority updated from %s to %s", oldSeverity, complaint.getSeverity());
        recordHistory(complaintId, complaint.getStatus(), complaint.getStatus(), userDisplay, r);

        database.saveDatabase();
        return true;
    }

    // Helper methods for department waiting queue promotion
    public synchronized Complaint promoteNextWaitingComplaint(Department dept, String now) {
        if (dept == null) return null;
        if (!dept.hasAvailableCapacity() || dept.getWaitingQueue().isEmpty()) {
            return null;
        }

        Complaint promoted = dept.pollWaitingQueue();
        if (promoted != null) {
            if (dept.tryAcquireCapacity()) {
                promoted.setWaiting(false);
                promoted.setStatus("Assigned");
                if (promoted.getAssignedStaff() != null && promoted.getAssignedStaff().startsWith("Queued (Waiting: ") && promoted.getAssignedStaff().endsWith(")")) {
                    String origStaff = promoted.getAssignedStaff().substring("Queued (Waiting: ".length(), promoted.getAssignedStaff().length() - 1);
                    promoted.setAssignedStaff(origStaff);
                } else if (promoted.getAssignedStaff() == null || promoted.getAssignedStaff().trim().isEmpty() || "Queued".equalsIgnoreCase(promoted.getAssignedStaff())) {
                    promoted.setAssignedStaff("Assigned Officer");
                }
                String timestamp = (now != null && !now.trim().isEmpty()) ? now : LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                promoted.setUpdatedAt(timestamp);

                recordHistory(promoted.getComplaintId(), "Assigned (Queued)", "Assigned",
                        "Department System",
                        String.format("Promoted from %s Waiting Queue to Active assignment as capacity opened up. Current Load: %d/%d.",
                                dept.getDepartmentName(), dept.getCurrentLoad(), dept.getCapacity()));
                return promoted;
            } else {
                dept.addToWaitingQueue(promoted);
            }
        }
        return null;
    }

    public synchronized List<Complaint> promoteAllEligibleWaitingComplaints(Department dept, String now) {
        List<Complaint> promotedList = new ArrayList<>();
        if (dept == null) return promotedList;
        while (dept.hasAvailableCapacity() && !dept.getWaitingQueue().isEmpty()) {
            Complaint p = promoteNextWaitingComplaint(dept, now);
            if (p != null) {
                promotedList.add(p);
            } else {
                break;
            }
        }
        return promotedList;
    }

    // 3. ASSIGN DEPARTMENT & STAFF WITH CAPACITY MANAGEMENT
    public synchronized boolean assignDepartmentAndStaff(String complaintId, String departmentName, String staffName, User actor) {
        Complaint complaint = database.getComplaintById(complaintId);
        if (complaint == null) return false;

        // Terminal state check: Resolved complaints cannot be assigned
        if ("RESOLVED".equalsIgnoreCase(complaint.getStatus()) || "CLOSED".equalsIgnoreCase(complaint.getStatus())) {
            throw new IllegalStateException("Invalid status transition: A resolved complaint is in a terminal state and cannot be assigned.");
        }

        // Validate target department
        Department targetDept = null;
        if (departmentName != null && !departmentName.trim().isEmpty()) {
            targetDept = database.findDepartment(departmentName);
            if (targetDept == null) {
                throw new IllegalArgumentException("Invalid department: '" + departmentName + "'. Department does not exist.");
            }
        } else if (complaint.getDepartment() != null && !complaint.getDepartment().trim().isEmpty()) {
            targetDept = database.findDepartment(complaint.getDepartment());
            if (targetDept == null) {
                targetDept = database.getDepartmentByAnyIdentifier(complaint.getDepartment());
            }
        }
        if (targetDept == null) {
            targetDept = database.findDepartment("General Administration");
        }

        String currentStatus = complaint.getStatus();
        // If not already Assigned, validate transition from currentStatus to "Assigned"
        if (!"ASSIGNED".equalsIgnoreCase(currentStatus)) {
            StatusTransitionPolicy.ValidationResult result = StatusTransitionPolicy.validateTransition(
                    currentStatus, "Assigned", false, false);
            if (!result.isValid()) {
                throw new IllegalStateException(result.getErrorMessage());
            }
        }

        String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        Department oldDept = database.findDepartment(complaint.getDepartmentId() != null ? complaint.getDepartmentId() : complaint.getDepartment());
        if (oldDept == null && complaint.getDepartment() != null) {
            oldDept = database.findDepartment(complaint.getDepartment());
        }

        boolean isChangingDept = oldDept != null && !oldDept.getDepartmentId().equalsIgnoreCase(targetDept.getDepartmentId());

        // If changing department, release old department capacity or remove from old waiting queue
        if (isChangingDept) {
            if (oldDept.containsInWaitingQueue(complaint) || complaint.isWaiting()) {
                oldDept.removeFromWaitingQueue(complaint);
                complaint.setWaiting(false);
            } else if ("ASSIGNED".equalsIgnoreCase(currentStatus) || "IN_PROGRESS".equalsIgnoreCase(currentStatus)) {
                oldDept.decrementLoad();
                promoteAllEligibleWaitingComplaints(oldDept, now);
            }
        }

        complaint.setDepartment(targetDept.getDepartmentName());
        complaint.setDepartmentId(targetDept.getDepartmentId());

        String oldStatus = complaint.getStatus();

        // Check if complaint was ALREADY actively consuming capacity in targetDept
        boolean alreadyActiveInTarget = (!isChangingDept) && (!complaint.isWaiting()) &&
                ("ASSIGNED".equalsIgnoreCase(currentStatus) || "IN_PROGRESS".equalsIgnoreCase(currentStatus));

        if (alreadyActiveInTarget) {
            complaint.setStatus("Assigned");
            if (staffName != null && !staffName.trim().isEmpty()) {
                complaint.setAssignedStaff(staffName.trim());
            }
            String remarks = String.format("Assignment updated in %s (Officer: %s). Department Load: %d/%d.",
                    targetDept.getDepartmentName(), complaint.getAssignedStaff(), targetDept.getCurrentLoad(), targetDept.getCapacity());
            recordHistory(complaintId, oldStatus, "Assigned", actor != null ? actor.getDisplayName() : "Municipal Admin", remarks);
        } else {
            // Needs capacity in targetDept
            if (targetDept.tryAcquireCapacity()) {
                complaint.setStatus("Assigned");
                complaint.setWaiting(false);
                if (staffName != null && !staffName.trim().isEmpty()) {
                    complaint.setAssignedStaff(staffName.trim());
                } else if (complaint.getAssignedStaff() == null || complaint.getAssignedStaff().trim().isEmpty() || complaint.getAssignedStaff().startsWith("Queued")) {
                    complaint.setAssignedStaff("Assigned Officer");
                }
                String remarks = String.format("Assigned to %s (Officer: %s). Department Load: %d/%d.",
                        targetDept.getDepartmentName(), complaint.getAssignedStaff(), targetDept.getCurrentLoad(), targetDept.getCapacity());
                recordHistory(complaintId, oldStatus, "Assigned", actor != null ? actor.getDisplayName() : "Municipal Admin", remarks);
            } else {
                // Target department is full -> queue in Department waiting PriorityQueue
                complaint.setStatus("Assigned");
                complaint.setWaiting(true);
                if (staffName != null && !staffName.trim().isEmpty()) {
                    complaint.setAssignedStaff("Queued (Waiting: " + staffName.trim() + ")");
                } else {
                    complaint.setAssignedStaff("Queued");
                }
                targetDept.addToWaitingQueue(complaint);
                String remarks = String.format("Department %s at maximum capacity (%d/%d). Complaint queued in Department Waiting PriorityQueue.",
                        targetDept.getDepartmentName(), targetDept.getCapacity(), targetDept.getCapacity());
                recordHistory(complaintId, oldStatus, "Assigned (Queued)", actor != null ? actor.getDisplayName() : "Municipal Admin", remarks);
            }
        }

        complaint.setUpdatedAt(now);
        database.saveDatabase();
        return true;
    }

    // 4. UPDATE COMPLAINT STATUS (DEPARTMENT PORTAL)
    public synchronized boolean updateStatus(String complaintId, String newStatus, String remarksText, User actor,
                                             boolean isExplicitCancellation, boolean isExplicitReassignment) {
        Complaint complaint = database.getComplaintById(complaintId);
        if (complaint == null) return false;

        // Invariant: Waiting complaint cannot transition to InProgress while in queue
        if (complaint.isWaiting() && "IN_PROGRESS".equalsIgnoreCase(newStatus)) {
            throw new IllegalStateException("Cannot start progress: Complaint is in waiting queue due to department capacity. It must be promoted to active assignment before entering InProgress.");
        }

        // Central state machine validation policy check
        StatusTransitionPolicy.ValidationResult result = StatusTransitionPolicy.validateTransition(
                complaint.getStatus(), newStatus, isExplicitCancellation, isExplicitReassignment);

        if (!result.isValid()) {
            throw new IllegalStateException(result.getErrorMessage());
        }

        ComplaintStatus targetStatusEnum = result.getTargetStatus();
        String targetCanonical = targetStatusEnum.getCanonicalName();
        String oldStatus = complaint.getStatus();

        // Mutate status and timestamp only after validation succeeds
        complaint.setStatus(targetCanonical);
        String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        complaint.setUpdatedAt(now);

        Department dept = database.findDepartment(complaint.getDepartmentId() != null ? complaint.getDepartmentId() : complaint.getDepartment());
        if (dept == null) dept = database.getDepartmentByAnyIdentifier(complaint.getDepartment());

        // If target status is RESOLVED -> free up department capacity and promote waiting complaints
        if (targetStatusEnum == ComplaintStatus.RESOLVED) {
            complaint.setResolvedAt(now);

            if (dept != null) {
                if (complaint.isWaiting()) {
                    dept.removeFromWaitingQueue(complaint);
                    complaint.setWaiting(false);
                } else {
                    dept.decrementLoad();
                }
                promoteAllEligibleWaitingComplaints(dept, now);
            }
        }

        String userDisplay = actor != null ? actor.getDisplayName() : "Municipal Staff";
        String remarks = (remarksText != null && !remarksText.trim().isEmpty()) ? remarksText.trim() : "Status updated to " + targetCanonical;
        recordHistory(complaintId, oldStatus, targetCanonical, userDisplay, remarks);

        database.saveDatabase();
        return true;
    }

    public boolean updateStatus(String complaintId, String newStatus, String remarksText, User actor) {
        return updateStatus(complaintId, newStatus, remarksText, actor, false, false);
    }

    public boolean updateStatus(String complaintId, String newStatus) {
        return updateStatus(complaintId, newStatus, "", null, false, false);
    }

    // 5. RESOLVE COMPLAINT (DEPARTMENT PORTAL)
    public synchronized boolean resolveComplaint(String complaintId, String resolutionNotes, User actor) {
        Complaint complaint = database.getComplaintById(complaintId);
        if (complaint == null) return false;

        // Transition validation policy check first before altering resolutionNotes
        StatusTransitionPolicy.ValidationResult result = StatusTransitionPolicy.validateTransition(
                complaint.getStatus(), "Resolved", false, false);
        if (!result.isValid()) {
            throw new IllegalStateException(result.getErrorMessage());
        }

        complaint.setResolutionNotes(resolutionNotes != null ? resolutionNotes.trim() : "");
        return updateStatus(complaintId, "Resolved", "Resolution Notes: " + resolutionNotes, actor, false, false);
    }

    public boolean resolveComplaint(String complaintId, String resolutionNotes) {
        return resolveComplaint(complaintId, resolutionNotes, null);
    }

    /**
     * Explicit workflow to cancel/remove an assignment and return the complaint to Registered status.
     * Only permitted through this explicit cancellation workflow.
     */
    public synchronized boolean cancelAssignment(String complaintId, String reason, User actor) {
        Complaint complaint = database.getComplaintById(complaintId);
        if (complaint == null) return false;

        StatusTransitionPolicy.ValidationResult result = StatusTransitionPolicy.validateTransition(
                complaint.getStatus(), "Registered", true, false);
        if (!result.isValid()) {
            throw new IllegalStateException(result.getErrorMessage());
        }

        Department dept = database.findDepartment(complaint.getDepartmentId() != null ? complaint.getDepartmentId() : complaint.getDepartment());
        if (dept == null) dept = database.getDepartmentByAnyIdentifier(complaint.getDepartment());

        String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        if (dept != null) {
            if (complaint.isWaiting()) {
                dept.removeFromWaitingQueue(complaint);
                complaint.setWaiting(false);
            } else {
                dept.decrementLoad();
                promoteAllEligibleWaitingComplaints(dept, now);
            }
        }
        complaint.setAssignedStaff("Unassigned");

        String remarks = (reason != null && !reason.trim().isEmpty()) ? reason.trim() : "Assignment cancelled by administrator";
        return updateStatus(complaintId, "Registered", remarks, actor, true, false);
    }

    /**
     * Explicit workflow to reassign a complaint to a new department or staff.
     * Enforces strict validation of destination department, capacity accounting,
     * waiting queue management, and transaction-like state preservation on failure.
     */
    public synchronized boolean reassignComplaint(String complaintId, String newDepartment, String newStaff, String reason, User actor) {
        Complaint complaint = database.getComplaintById(complaintId);
        if (complaint == null) return false;

        // Terminal state check
        if ("RESOLVED".equalsIgnoreCase(complaint.getStatus()) || "CLOSED".equalsIgnoreCase(complaint.getStatus())) {
            throw new IllegalStateException("Invalid status transition: A resolved complaint is in a terminal state and cannot be reassigned.");
        }

        // Validate destination department strictly
        if (newDepartment == null || newDepartment.trim().isEmpty()) {
            throw new IllegalArgumentException("Destination department is required for reassignment.");
        }
        Department targetDept = database.findDepartment(newDepartment);
        if (targetDept == null) {
            throw new IllegalArgumentException("Invalid destination department: '" + newDepartment + "'. Destination department does not exist.");
        }

        // Validate status transition
        StatusTransitionPolicy.ValidationResult result = StatusTransitionPolicy.validateTransition(
                complaint.getStatus(), "Assigned", false, true);
        if (!result.isValid()) {
            throw new IllegalStateException(result.getErrorMessage());
        }

        String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String oldStatus = complaint.getStatus();
        Department oldDept = database.findDepartment(complaint.getDepartmentId() != null ? complaint.getDepartmentId() : complaint.getDepartment());
        if (oldDept == null && complaint.getDepartment() != null) {
            oldDept = database.findDepartment(complaint.getDepartment());
        }

        boolean isSameDept = oldDept != null && oldDept.getDepartmentId().equalsIgnoreCase(targetDept.getDepartmentId());
        String userDisplay = actor != null ? actor.getDisplayName() : "Municipal Admin";

        if (isSameDept) {
            // Reassignment within same department (e.g. staff update)
            if (newStaff != null && !newStaff.trim().isEmpty()) {
                if (complaint.isWaiting()) {
                    complaint.setAssignedStaff("Queued (Waiting: " + newStaff.trim() + ")");
                } else {
                    complaint.setAssignedStaff(newStaff.trim());
                }
            }
            complaint.setStatus("Assigned");
            String remarks = (reason != null && !reason.trim().isEmpty()) ? reason.trim() : "Reassigned within department";
            remarks += String.format(" (Officer: %s).", complaint.getAssignedStaff());
            recordHistory(complaintId, oldStatus, complaint.isWaiting() ? "Assigned (Queued)" : "Assigned", userDisplay, remarks);
        } else {
            // Moving across departments
            // Step 1: Release old department capacity or remove from waiting queue
            if (oldDept != null) {
                if (oldDept.containsInWaitingQueue(complaint) || complaint.isWaiting()) {
                    oldDept.removeFromWaitingQueue(complaint);
                    complaint.setWaiting(false);
                } else if ("ASSIGNED".equalsIgnoreCase(oldStatus) || "IN_PROGRESS".equalsIgnoreCase(oldStatus)) {
                    oldDept.decrementLoad();
                    promoteAllEligibleWaitingComplaints(oldDept, now);
                }
            }

            // Step 2: Assign or queue in destination department
            complaint.setDepartment(targetDept.getDepartmentName());
            complaint.setDepartmentId(targetDept.getDepartmentId());

            if (targetDept.tryAcquireCapacity()) {
                // Acquired capacity in destination department
                complaint.setStatus("Assigned");
                complaint.setWaiting(false);
                if (newStaff != null && !newStaff.trim().isEmpty()) {
                    complaint.setAssignedStaff(newStaff.trim());
                } else if (complaint.getAssignedStaff() == null || complaint.getAssignedStaff().trim().isEmpty() || complaint.getAssignedStaff().startsWith("Queued")) {
                    complaint.setAssignedStaff("Assigned Officer");
                }
                String remarks = (reason != null && !reason.trim().isEmpty()) ? reason.trim() : "Reassigned to " + targetDept.getDepartmentName();
                remarks += String.format(" (Officer: %s). Department Load: %d/%d.", complaint.getAssignedStaff(), targetDept.getCurrentLoad(), targetDept.getCapacity());
                recordHistory(complaintId, oldStatus, "Assigned", userDisplay, remarks);
            } else {
                // Destination department is full -> queue in waiting queue
                complaint.setStatus("Assigned");
                complaint.setWaiting(true);
                if (newStaff != null && !newStaff.trim().isEmpty()) {
                    complaint.setAssignedStaff("Queued (Waiting: " + newStaff.trim() + ")");
                } else {
                    complaint.setAssignedStaff("Queued");
                }
                targetDept.addToWaitingQueue(complaint);
                String remarks = (reason != null && !reason.trim().isEmpty()) ? reason.trim() : "Reassigned to " + targetDept.getDepartmentName();
                remarks += String.format(". Department %s is at maximum capacity (%d/%d). Complaint queued in Department Waiting PriorityQueue.",
                        targetDept.getDepartmentName(), targetDept.getCapacity(), targetDept.getCapacity());
                recordHistory(complaintId, oldStatus, "Assigned (Queued)", userDisplay, remarks);
            }
        }

        complaint.setUpdatedAt(now);
        database.saveDatabase();
        return true;
    }

    public boolean reassignComplaint(String complaintId, String newDepartment, String newStaff, String reason) {
        return reassignComplaint(complaintId, newDepartment, newStaff, reason, null);
    }

    public boolean reassignComplaint(String complaintId, String newDepartment) {
        return reassignComplaint(complaintId, newDepartment, null, null, null);
    }

    // 6. SAFE DUPLICATE-CANDIDATE DETECTION & OPERATOR CONFIRMATION

    /**
     * Finds potential duplicate candidates for a given complaint.
     * Evaluates only relevant OPEN complaints using:
     * - Complaint category
     * - Normalized location (safe rules, excluding malformed locations)
     * - Existing open-issue references (e.g. references to complaint IDs)
     * Does NOT automatically mutate complaints or set duplicateOfId.
     * Respects previous operator rejections.
     */
    public List<DuplicateCandidate> findDuplicateCandidates(String complaintId) {
        List<DuplicateCandidate> candidates = new ArrayList<>();
        if (complaintId == null || complaintId.trim().isEmpty()) return candidates;

        Complaint source = database.getComplaintById(complaintId);
        if (source == null) return candidates;

        // Relevant open complaints only: Resolved complaints cannot be treated as active duplicates
        if ("RESOLVED".equalsIgnoreCase(source.getStatus()) || "CLOSED".equalsIgnoreCase(source.getStatus())) {
            return candidates;
        }

        String normCatA = LocationNormalizer.normalizeCategory(source.getCategory());
        String normLocA = LocationNormalizer.normalizeLocation(source.getLocation());
        String refA = LocationNormalizer.extractIssueReference(source.getDescription());

        for (Complaint target : database.getAllComplaints()) {
            // Cannot be a candidate of itself
            if (target.getComplaintId().equalsIgnoreCase(source.getComplaintId()) ||
                target.getId().equalsIgnoreCase(source.getId())) {
                continue;
            }

            // Target must also be an open complaint
            if ("RESOLVED".equalsIgnoreCase(target.getStatus()) || "CLOSED".equalsIgnoreCase(target.getStatus())) {
                continue;
            }

            // Already confirmed link
            if (source.isDuplicate() && target.getComplaintId().equalsIgnoreCase(source.getDuplicateOfId())) {
                continue;
            }
            if (target.isDuplicate() && source.getComplaintId().equalsIgnoreCase(target.getDuplicateOfId())) {
                continue;
            }

            // Operator previously rejected this candidate
            if (source.hasRejectedCandidate(target.getComplaintId()) || target.hasRejectedCandidate(source.getComplaintId())) {
                continue;
            }

            String normCatB = LocationNormalizer.normalizeCategory(target.getCategory());
            String normLocB = LocationNormalizer.normalizeLocation(target.getLocation());
            String refB = LocationNormalizer.extractIssueReference(target.getDescription());

            boolean refMatch = (refA != null && refA.equalsIgnoreCase(target.getComplaintId())) ||
                               (refB != null && refB.equalsIgnoreCase(source.getComplaintId()));

            boolean catAndLocMatch = (normLocA != null && normLocB != null && normLocA.equals(normLocB)) &&
                                     (normCatA != null && normCatB != null && normCatA.equals(normCatB));

            if (refMatch || catAndLocMatch) {
                String reason;
                if (refMatch && catAndLocMatch) {
                    reason = "Open-issue reference and matching Category & Normalized Location";
                } else if (refMatch) {
                    reason = "Explicit open-issue reference match (" + (refA != null ? refA : refB) + ")";
                } else {
                    reason = "Same category (" + source.getCategory() + ") and normalized location (" + normLocA + ")";
                }

                candidates.add(new DuplicateCandidate(
                        source.getComplaintId(),
                        target.getComplaintId(),
                        reason,
                        target.getCategory(),
                        target.getLocation(),
                        target.getStatus(),
                        target.getCitizenName(),
                        target.getCreatedAt()
                ));
            }
        }

        return candidates;
    }

    /**
     * Backward-compatible helper method for category/location duplicate detection.
     */
    public List<Complaint> findPotentialDuplicates(String category, String location) {
        List<Complaint> duplicates = new ArrayList<>();
        if (category == null || location == null) return duplicates;

        String normCat = LocationNormalizer.normalizeCategory(category);
        String normLoc = LocationNormalizer.normalizeLocation(location);
        if (normCat.isEmpty() || normLoc == null) return duplicates;

        for (Complaint c : database.getAllComplaints()) {
            if ("RESOLVED".equalsIgnoreCase(c.getStatus()) || "CLOSED".equalsIgnoreCase(c.getStatus())) {
                continue;
            }
            String cCat = LocationNormalizer.normalizeCategory(c.getCategory());
            String cLoc = LocationNormalizer.normalizeLocation(c.getLocation());
            if (cCat.equals(normCat) && cLoc != null && cLoc.equals(normLoc)) {
                duplicates.add(c);
            }
        }
        return duplicates;
    }

    /**
     * Confirms a duplicate relationship between two complaints.
     * Enforces:
     * - Staff or Admin authorization check on backend
     * - Self-link prevention
     * - Duplicate-link prevention
     * - Circular-link prevention (direct and transitive chain)
     * - Original complaint record and history preservation
     * - Comprehensive audit history recording on both complaints
     */
    public synchronized boolean confirmDuplicateLink(String complaintId, String primaryComplaintId, User operator, String remarks) {
        if (operator == null || !operator.canManageDuplicates()) {
            throw new SecurityException("Unauthorized: Staff or Admin privileges required to confirm duplicate links.");
        }

        if (complaintId == null || primaryComplaintId == null ||
            complaintId.trim().isEmpty() || primaryComplaintId.trim().isEmpty()) {
            throw new IllegalArgumentException("Complaint IDs cannot be null or empty.");
        }

        Complaint complaint = database.getComplaintById(complaintId.trim());
        Complaint primary = database.getComplaintById(primaryComplaintId.trim());

        if (complaint == null) {
            throw new IllegalArgumentException("Complaint not found: " + complaintId);
        }
        if (primary == null) {
            throw new IllegalArgumentException("Primary complaint not found: " + primaryComplaintId);
        }

        // Self-link prevention
        if (complaint.getComplaintId().equalsIgnoreCase(primary.getComplaintId()) ||
            complaint.getId().equalsIgnoreCase(primary.getId())) {
            throw new IllegalArgumentException("Self-link rejected: A complaint cannot be linked to itself.");
        }

        // Duplicate-link prevention (already linked to this primary)
        if (complaint.isDuplicate() && primary.getComplaintId().equalsIgnoreCase(complaint.getDuplicateOfId())) {
            throw new IllegalStateException("Duplicate link rejected: Complaint " + complaint.getComplaintId() +
                    " is already confirmed as a duplicate of " + primary.getComplaintId() + ".");
        }

        // Circular-link prevention: Direct
        if (primary.isDuplicate() && complaint.getComplaintId().equalsIgnoreCase(primary.getDuplicateOfId())) {
            throw new IllegalStateException("Circular link rejected: Primary complaint " + primary.getComplaintId() +
                    " is already linked to " + complaint.getComplaintId() + ".");
        }

        // Circular-link prevention: Transitive chain
        Set<String> chain = new HashSet<>();
        chain.add(complaint.getComplaintId());
        Complaint curr = primary;
        while (curr != null && curr.isDuplicate() && curr.getDuplicateOfId() != null) {
            String ancestorId = curr.getDuplicateOfId();
            if (chain.contains(ancestorId)) {
                throw new IllegalStateException("Circular link rejected: Linking " + complaint.getComplaintId() +
                        " to " + primary.getComplaintId() + " creates a circular link loop through " + ancestorId + ".");
            }
            chain.add(ancestorId);
            curr = database.getComplaintById(ancestorId);
        }

        // Set confirmed duplicate relationship while preserving complaint record & status
        complaint.setDuplicate(true);
        complaint.setDuplicateOfId(primary.getComplaintId());
        complaint.removeRejectedCandidate(primary.getComplaintId());

        String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        complaint.setUpdatedAt(now);

        String opName = operator.getDisplayName();
        String reason = (remarks != null && !remarks.trim().isEmpty()) ? remarks.trim() : "Confirmed by municipal operator";

        recordHistory(complaint.getComplaintId(), complaint.getStatus(), complaint.getStatus(), opName,
                "Duplicate link confirmed to primary " + primary.getComplaintId() + ". Operator remarks: " + reason);

        recordHistory(primary.getComplaintId(), primary.getStatus(), primary.getStatus(), opName,
                "Linked duplicate complaint " + complaint.getComplaintId() + " confirmed. Operator remarks: " + reason);

        database.saveDatabase();
        return true;
    }

    /**
     * Explicitly rejects a duplicate candidate for a complaint.
     * Prevents candidate from being repeatedly presented as newly detected candidate.
     * Enforces:
     * - Staff or Admin authorization check on backend
     * - Self-rejection prevention
     * - Audit trail recording
     */
    public synchronized boolean rejectDuplicateCandidate(String complaintId, String candidateComplaintId, User operator, String remarks) {
        if (operator == null || !operator.canManageDuplicates()) {
            throw new SecurityException("Unauthorized: Staff or Admin privileges required to reject duplicate candidates.");
        }

        if (complaintId == null || candidateComplaintId == null ||
            complaintId.trim().isEmpty() || candidateComplaintId.trim().isEmpty()) {
            throw new IllegalArgumentException("Complaint IDs cannot be null or empty.");
        }

        Complaint complaint = database.getComplaintById(complaintId.trim());
        Complaint candidate = database.getComplaintById(candidateComplaintId.trim());

        if (complaint == null) {
            throw new IllegalArgumentException("Complaint not found: " + complaintId);
        }
        if (candidate == null) {
            throw new IllegalArgumentException("Candidate complaint not found: " + candidateComplaintId);
        }

        if (complaint.getComplaintId().equalsIgnoreCase(candidate.getComplaintId())) {
            throw new IllegalArgumentException("Self-rejection rejected: A complaint cannot reject itself.");
        }

        complaint.addRejectedCandidate(candidate.getComplaintId());
        candidate.addRejectedCandidate(complaint.getComplaintId());

        String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        complaint.setUpdatedAt(now);

        String opName = operator.getDisplayName();
        String reason = (remarks != null && !remarks.trim().isEmpty()) ? remarks.trim() : "Determined to be separate, distinct issue";

        recordHistory(complaint.getComplaintId(), complaint.getStatus(), complaint.getStatus(), opName,
                "Duplicate candidate " + candidate.getComplaintId() + " rejected. Operator remarks: " + reason);

        database.saveDatabase();
        return true;
    }

    /**
     * Merges a duplicate complaint into a primary complaint.
     * Performs link confirmation followed by resolution and department capacity release.
     */
    public synchronized boolean mergeDuplicate(Complaint dup, Complaint primary, User admin, String reason) {
        if (dup == null || primary == null) return false;
        return mergeDuplicate(dup.getComplaintId(), primary.getComplaintId(), admin, reason);
    }

    public synchronized boolean mergeDuplicate(String duplicateComplaintId, String primaryComplaintId, User admin, String reason) {
        if (admin == null || !admin.canManageDuplicates()) {
            throw new SecurityException("Unauthorized: Staff or Admin privileges required to merge duplicates.");
        }

        Complaint dup = database.getComplaintById(duplicateComplaintId);
        Complaint primary = database.getComplaintById(primaryComplaintId);
        if (dup == null || primary == null) return false;

        // Perform safe link confirmation (validates self-link, circular-link, duplicate-link)
        confirmDuplicateLink(duplicateComplaintId, primaryComplaintId, admin, reason);

        // Update status to Resolved
        dup.setStatus("Resolved");
        dup.setResolutionNotes("Merged into primary complaint " + primary.getComplaintId() + ". Reason: " + reason);

        String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        dup.setResolvedAt(now);
        dup.setUpdatedAt(now);

        // Release capacity from dup's department if it was consuming capacity
        Department dept = database.findDepartment(dup.getDepartmentId() != null ? dup.getDepartmentId() : dup.getDepartment());
        if (dept == null) dept = database.getDepartmentByAnyIdentifier(dup.getDepartment());
        if (dept != null) {
            if (dup.isWaiting()) {
                dept.removeFromWaitingQueue(dup);
                dup.setWaiting(false);
            } else {
                dept.decrementLoad();
            }
            promoteAllEligibleWaitingComplaints(dept, now);
        }

        recordHistory(duplicateComplaintId, "Assigned", "Resolved (Merged)",
                admin != null ? admin.getDisplayName() : "Admin",
                "Merged into " + primary.getComplaintId() + ". Reason: " + reason);

        database.saveDatabase();
        return true;
    }

    public synchronized boolean keepSeparate(String complaintId, User admin) {
        if (admin == null || !admin.canManageDuplicates()) {
            throw new SecurityException("Unauthorized: Staff or Admin privileges required.");
        }

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
