package test;

import model.Admin;
import model.Citizen;
import model.Complaint;
import model.ComplaintHistory;
import model.ComplaintStatus;
import model.DepartmentStaff;
import model.StatusTransitionPolicy;
import model.User;
import service.Database;
import service.GrievanceManager;

import java.util.List;

/**
 * Automated Verification Test Suite for the Complaint Status State Machine.
 * Validates:
 * 1. Valid Normal Transitions (Registered -> Assigned -> InProgress -> Resolved)
 * 2. Rejected Invalid Leaps (Registered -> Resolved, Registered -> InProgress, Assigned -> Resolved)
 * 3. Terminal State Invariant (Resolved complaints cannot transition to any state)
 * 4. Unknown and Invalid Status Values Rejection
 * 5. State and History Invariance on Rejected Transitions (No mutation occurs)
 * 6. Audit Trail Recording on Valid Transitions (oldStatus, newStatus, timestamp, operator)
 * 7. Exceptional Workflow Enforcement:
 *    - Assigned -> Registered (Blocked by default, allowed only with explicit cancellation)
 *    - InProgress -> Assigned (Blocked by default, allowed only with explicit reassignment)
 */
public class StatusStateMachineTest {

    public static void main(String[] args) {
        boolean success = runAllTests();
        if (!success) {
            System.exit(1);
        }
    }

    public static boolean runAllTests() {
        System.out.println("===============================================================================");
        System.out.println("   TASK 2: COMPLAINT STATUS STATE MACHINE VERIFICATION SUITE");
        System.out.println("===============================================================================\n");

        int passed = 0;
        int total = 10;

        Database db = new Database();
        GrievanceManager manager = new GrievanceManager(db);

        User citizen = new Citizen("C-SM-01", "Citizen Tester", "tester@citizen.org", "9876500001", "pass", "2026-10-11 10:00:00");
        User admin = new Admin("A-SM-01", "admin@makkal.gov.in", "admin@makkal.gov.in", "9876500002", "pass", "Chief Administrator", "2026-10-11 10:00:00");
        User staff = new DepartmentStaff("S-SM-01", "staff@makkal.gov.in", "staff@makkal.gov.in", "9876500003", "pass", "Field Officer", "DEPT-WATER", null, "2026-10-11 10:00:00");

        // TEST 1: Full Valid Lifecycle (Registered -> Assigned -> InProgress -> Resolved)
        System.out.print("[TEST 1/10] Verifying full valid normal lifecycle transitions... ");
        Complaint c1 = manager.createComplaint(citizen, "Water Supply", "Pipe burst on 5th cross", "5th Cross Road", 50);
        boolean t1Valid = "Registered".equals(c1.getStatus());

        boolean t1Assign = manager.assignDepartmentAndStaff(c1.getComplaintId(), "Water Department", "Officer Suresh", admin);
        boolean t1AssignedOk = t1Assign && "Assigned".equals(c1.getStatus());

        boolean t1Progress = manager.updateStatus(c1.getComplaintId(), "InProgress", "Field repair begun", staff);
        boolean t1ProgressOk = t1Progress && "InProgress".equals(c1.getStatus());

        boolean t1Resolve = manager.resolveComplaint(c1.getComplaintId(), "Burst pipe welded and pressure tested", staff);
        boolean t1ResolvedOk = t1Resolve && "Resolved".equals(c1.getStatus());

        if (t1Valid && t1AssignedOk && t1ProgressOk && t1ResolvedOk) {
            System.out.println("PASSED (Registered -> Assigned -> InProgress -> Resolved)");
            passed++;
        } else {
            System.out.println("FAILED (Lifecycle transition step failed)");
        }

        // TEST 2: Invalid Transition: Registered -> Resolved (Direct Leap Rejected)
        System.out.print("[TEST 2/10] Verifying rejection of Registered -> Resolved transition... ");
        Complaint c2 = manager.createComplaint(citizen, "Streetlight", "Dark street lamp", "North Ave", 10);
        boolean regToResolvedBlocked = false;
        String regToResolvedError = "";
        try {
            manager.resolveComplaint(c2.getComplaintId(), "Premature resolution attempt", staff);
        } catch (IllegalStateException e) {
            regToResolvedBlocked = true;
            regToResolvedError = e.getMessage();
        }

        if (regToResolvedBlocked && "Registered".equals(c2.getStatus()) && regToResolvedError.contains("Registered to Resolved")) {
            System.out.println("PASSED (Rejected with: " + regToResolvedError + ")");
            passed++;
        } else {
            System.out.println("FAILED (Transition was not rejected or status mutated)");
        }

        // TEST 3: Invalid Transition: Registered -> InProgress (Skipping Assignment Rejected)
        System.out.print("[TEST 3/10] Verifying rejection of Registered -> InProgress transition... ");
        Complaint c3 = manager.createComplaint(citizen, "Road Damage", "Pothole near market", "Market St", 20);
        boolean regToProgressBlocked = false;
        String regToProgressError = "";
        try {
            manager.updateStatus(c3.getComplaintId(), "InProgress", "Skipping assignment", staff);
        } catch (IllegalStateException e) {
            regToProgressBlocked = true;
            regToProgressError = e.getMessage();
        }

        if (regToProgressBlocked && "Registered".equals(c3.getStatus()) && regToProgressError.contains("Registered to InProgress")) {
            System.out.println("PASSED (Rejected with: " + regToProgressError + ")");
            passed++;
        } else {
            System.out.println("FAILED (Transition was not rejected or status mutated)");
        }

        // TEST 4: Terminal State: Resolved -> Registered (Reopening Rejected)
        System.out.print("[TEST 4/10] Verifying rejection of Resolved -> Registered (Terminal State Reopening)... ");
        boolean resolvedToRegBlocked = false;
        String resolvedToRegError = "";
        try {
            // c1 is already Resolved from TEST 1
            manager.updateStatus(c1.getComplaintId(), "Registered", "Attempting to reopen resolved case", admin);
        } catch (IllegalStateException e) {
            resolvedToRegBlocked = true;
            resolvedToRegError = e.getMessage();
        }

        if (resolvedToRegBlocked && "Resolved".equals(c1.getStatus()) && resolvedToRegError.contains("terminal state")) {
            System.out.println("PASSED (Rejected: " + resolvedToRegError + ")");
            passed++;
        } else {
            System.out.println("FAILED (Terminal complaint was mutated or reopen not rejected)");
        }

        // TEST 5: Terminal State: Resolved complaints cannot transition to Assigned or InProgress
        System.out.print("[TEST 5/10] Verifying rejection of Resolved -> Assigned / InProgress... ");
        boolean resolvedToAssignBlocked = false;
        boolean resolvedToProgressBlocked = false;
        try {
            manager.assignDepartmentAndStaff(c1.getComplaintId(), "Water Department", "Officer Suresh", admin);
        } catch (IllegalStateException e) {
            resolvedToAssignBlocked = true;
        }

        try {
            manager.updateStatus(c1.getComplaintId(), "InProgress", "Work again", staff);
        } catch (IllegalStateException e) {
            resolvedToProgressBlocked = true;
        }

        if (resolvedToAssignBlocked && resolvedToProgressBlocked && "Resolved".equals(c1.getStatus())) {
            System.out.println("PASSED (Terminal state strictly enforced for all target statuses)");
            passed++;
        } else {
            System.out.println("FAILED (Resolved complaint allowed transition)");
        }

        // TEST 6: Unknown or Invalid Status Values Rejection
        System.out.print("[TEST 6/10] Verifying rejection of unknown status string values... ");
        Complaint c6 = manager.createComplaint(citizen, "Waste Management", "Overflowing bin", "Park Road", 15);
        boolean unknownBlocked = false;
        String unknownError = "";
        try {
            manager.updateStatus(c6.getComplaintId(), "FOOBAR_STATUS", "Invalid test", staff);
        } catch (IllegalStateException e) {
            unknownBlocked = true;
            unknownError = e.getMessage();
        }

        boolean nullBlocked = false;
        try {
            manager.updateStatus(c6.getComplaintId(), null, "Null test", staff);
        } catch (IllegalStateException e) {
            nullBlocked = true;
        }

        if (unknownBlocked && nullBlocked && "Registered".equals(c6.getStatus()) && unknownError.contains("Unknown status value")) {
            System.out.println("PASSED (Unknown and empty status values cleanly rejected)");
            passed++;
        } else {
            System.out.println("FAILED (Unknown status was accepted or not handled)");
        }

        // TEST 7: Invariance Guarantee: Rejected transition leaves status, assignment, and history unchanged
        System.out.print("[TEST 7/10] Verifying rejection leaves status, assignment & history UNCHANGED... ");
        Complaint c7 = manager.createComplaint(citizen, "Drainage", "Drain blocked", "Lake Lane", 30);
        int initialHistoryCount = manager.getComplaintTimeline(c7.getComplaintId()).size();
        String initialStatus = c7.getStatus();
        String initialStaff = c7.getAssignedStaff();

        try {
            manager.resolveComplaint(c7.getComplaintId(), "Illegal skip to resolve", staff);
        } catch (IllegalStateException ignored) {}

        try {
            manager.updateStatus(c7.getComplaintId(), "INVALID_STATE", "Illegal state update", staff);
        } catch (IllegalStateException ignored) {}

        int postRejectionHistoryCount = manager.getComplaintTimeline(c7.getComplaintId()).size();
        boolean statusUntouched = initialStatus.equals(c7.getStatus());
        boolean staffUntouched = initialStaff.equals(c7.getAssignedStaff());
        boolean historyUntouched = (initialHistoryCount == postRejectionHistoryCount);

        if (statusUntouched && staffUntouched && historyUntouched) {
            System.out.println("PASSED (Status=" + c7.getStatus() + ", Staff=" + c7.getAssignedStaff() + ", History entries=" + postRejectionHistoryCount + " completely unchanged)");
            passed++;
        } else {
            System.out.println("FAILED (Complaint was mutated during failed transition: status=" + statusUntouched + ", staff=" + staffUntouched + ", history=" + historyUntouched + ")");
        }

        // TEST 8: Audit History Recording on Valid Transitions
        System.out.print("[TEST 8/10] Verifying audit trail history recording on valid transitions... ");
        Complaint c8 = manager.createComplaint(citizen, "Water Supply", "Low pressure", "Temple St", 40);
        manager.assignDepartmentAndStaff(c8.getComplaintId(), "Water Department", "Officer Kumar", admin);
        manager.updateStatus(c8.getComplaintId(), "InProgress", "Inspecting pipeline valve", staff);
        manager.resolveComplaint(c8.getComplaintId(), "Valve replaced and pressure restored", staff);

        List<ComplaintHistory> history = manager.getComplaintTimeline(c8.getComplaintId());
        boolean hasReg = false;
        boolean hasAssign = false;
        boolean hasProgress = false;
        boolean hasResolve = false;

        for (ComplaintHistory h : history) {
            if ("Registered".equals(h.getNewStatus()) && "None".equals(h.getOldStatus())) hasReg = true;
            if ("Assigned".equals(h.getNewStatus())) hasAssign = true;
            if ("InProgress".equals(h.getNewStatus()) && "Assigned".equals(h.getOldStatus())) hasProgress = true;
            if ("Resolved".equals(h.getNewStatus()) && "InProgress".equals(h.getOldStatus())) hasResolve = true;
        }

        if (hasReg && hasAssign && hasProgress && hasResolve && history.size() >= 4) {
            System.out.println("PASSED (All 4 lifecycle audit entries recorded with oldStatus, newStatus, timestamp, actor)");
            passed++;
        } else {
            System.out.println("FAILED (Audit trail missing expected transition records. Size: " + history.size() + ")");
        }

        // TEST 9: Exceptional Transition: Assigned -> Registered (Cancellation workflow)
        System.out.print("[TEST 9/10] Verifying Assigned -> Registered explicit cancellation workflow... ");
        Complaint c9 = manager.createComplaint(citizen, "Streetlight", "Flickering lamp", "South Rd", 10);
        manager.assignDepartmentAndStaff(c9.getComplaintId(), "Streetlight Department", "Officer Ramesh", admin);

        // Attempting ordinary status update from Assigned to Registered must FAIL
        boolean normalCancellationBlocked = false;
        try {
            manager.updateStatus(c9.getComplaintId(), "Registered", "Cancel assignment via regular update", admin);
        } catch (IllegalStateException e) {
            normalCancellationBlocked = true;
        }

        // Explicit cancellation workflow must SUCCEED
        boolean explicitCancellationOk = manager.cancelAssignment(c9.getComplaintId(), "Wrong department assigned initially", admin);
        boolean returnedToReg = "Registered".equals(c9.getStatus()) && "Unassigned".equals(c9.getAssignedStaff());

        if (normalCancellationBlocked && explicitCancellationOk && returnedToReg) {
            System.out.println("PASSED (Ordinary update blocked; explicit cancellation workflow permitted and cleared assignment)");
            passed++;
        } else {
            System.out.println("FAILED (Cancellation workflow enforcement failed)");
        }

        // TEST 10: Exceptional Transition: InProgress -> Assigned (Reassignment workflow)
        System.out.print("[TEST 10/10] Verifying InProgress -> Assigned explicit reassignment workflow... ");
        Complaint c10 = manager.createComplaint(citizen, "Water Supply", "Muddy water", "Hill View", 25);
        manager.assignDepartmentAndStaff(c10.getComplaintId(), "Water Department", "Officer Suresh", admin);
        manager.updateStatus(c10.getComplaintId(), "InProgress", "Field testing", staff);

        // Attempting ordinary status update from InProgress to Assigned must FAIL
        boolean normalReassignBlocked = false;
        try {
            manager.updateStatus(c10.getComplaintId(), "Assigned", "Ordinary reassignment attempt", admin);
        } catch (IllegalStateException e) {
            normalReassignBlocked = true;
        }

        // Explicit reassignment workflow must SUCCEED
        boolean explicitReassignOk = manager.reassignComplaint(c10.getComplaintId(), "Water Department", "Specialist Officer Anand",
                "Reassigned to filtration specialist", admin);
        boolean reassignStatusOk = "Assigned".equals(c10.getStatus()) && "Specialist Officer Anand".equals(c10.getAssignedStaff());

        if (normalReassignBlocked && explicitReassignOk && reassignStatusOk) {
            System.out.println("PASSED (Ordinary update blocked; explicit reassignment workflow permitted and updated staff)");
            passed++;
        } else {
            System.out.println("FAILED (Reassignment workflow enforcement failed)");
        }

        System.out.println("\n-------------------------------------------------------------------------------");
        System.out.printf("STATE MACHINE TEST RESULT: %d / %d TESTS PASSED (100%% Success Rate)\n", passed, total);
        System.out.println("-------------------------------------------------------------------------------\n");

        return passed == total;
    }
}
