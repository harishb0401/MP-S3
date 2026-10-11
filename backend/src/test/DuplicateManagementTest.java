package test;

import model.Admin;
import model.Citizen;
import model.Complaint;
import model.ComplaintHistory;
import model.Department;
import model.DepartmentStaff;
import model.DuplicateCandidate;
import model.LocationNormalizer;
import model.User;
import service.Database;
import service.GrievanceManager;

import java.util.List;

/**
 * Automated Verification Test Suite for Task 4:
 * Safe Duplicate-Candidate Detection and Operator Confirmation.
 *
 * Requirements Verified:
 * 1. Same category and location candidate detection.
 * 2. Different categories (no candidate).
 * 3. Different locations (no candidate).
 * 4. Different capitalization or whitespace in equivalent locations & malformed location safety.
 * 5. Resolved issue versus open issue (resolved not treated as active duplicate).
 * 6. Explicit confirmation workflow (preserves original record, sets confirmed link).
 * 7. Explicit rejection workflow (suppresses repeated candidate presentation).
 * 8. Self-link prevention.
 * 9. Duplicate-link and circular-link prevention (direct and transitive chains).
 * 10. Audit history recording on both complaints.
 * 11. Unauthorized confirmation and rejection attempts (enforced on backend).
 * 12. Open-issue reference detection (explicit ID reference in description).
 */
public class DuplicateManagementTest {

    public static void main(String[] args) {
        boolean success = runAllTests();
        if (!success) {
            System.exit(1);
        }
    }

    public static boolean runAllTests() {
        System.out.println("===============================================================================");
        System.out.println("   TASK 4: SAFE DUPLICATE DETECTION & OPERATOR CONFIRMATION SUITE");
        System.out.println("===============================================================================\n");

        int passed = 0;
        int total = 12;

        Database db = new Database();
        GrievanceManager manager = new GrievanceManager(db);

        User citizen = new Citizen("C_DUP_1", "Kavitha", "kavitha@test.com", "9876543201", "hash", "2026-10-11 10:00:00");
        User citizen2 = new Citizen("C_DUP_2", "Manoj", "manoj@test.com", "9876543202", "hash", "2026-10-11 10:05:00");
        User admin = new Admin("A_DUP_1", "admin.dup@makkal.gov.in", "admin.dup@makkal.gov.in", "9876543200", "hash", "Commissioner", "2026-10-11 00:00:00");
        Department waterDept = db.getDepartmentByAnyIdentifier("Water Department");
        User staff = new DepartmentStaff("S_DUP_1", "staff.dup@makkal.gov.in", "staff.dup@makkal.gov.in", "9876543210",
                "hash", "Officer Anitha", waterDept.getDepartmentId(), waterDept, "2026-10-11 00:00:00");

        long ts = System.currentTimeMillis();

        // -----------------------------------------------------------------------------------
        // TEST 1: SAME CATEGORY AND LOCATION
        // -----------------------------------------------------------------------------------
        System.out.print("[TEST 1/12] Same Category and Location Candidate Detection... ");
        String loc1 = "100 Feet Road Indiranagar-" + ts;
        Complaint c1 = manager.createComplaint(citizen, "Roads", "Pothole on main lane", loc1, 10);
        Complaint c2 = manager.createComplaint(citizen2, "Roads", "Deep crater on road", loc1, 15);

        // Candidate detection must NOT automatically merge or set duplicateOfId upon registration
        boolean notAutoLinked = !c2.isDuplicate() && c2.getDuplicateOfId() == null;
        List<DuplicateCandidate> candidates1 = manager.findDuplicateCandidates(c2.getComplaintId());
        boolean foundC1 = candidates1.stream().anyMatch(cand -> cand.getCandidateComplaintId().equalsIgnoreCase(c1.getComplaintId()));

        if (notAutoLinked && foundC1) {
            System.out.println("PASSED (Candidate detected without automatic merging)");
            passed++;
        } else {
            System.out.println("FAILED (Candidate not found or auto-linked prematurely)");
        }

        // -----------------------------------------------------------------------------------
        // TEST 2: DIFFERENT CATEGORIES
        // -----------------------------------------------------------------------------------
        System.out.print("[TEST 2/12] Different Categories (No Candidate Match)... ");
        Complaint c3 = manager.createComplaint(citizen, "Water Supply", "Pipe burst on road", loc1, 20);
        List<DuplicateCandidate> candidatesC3 = manager.findDuplicateCandidates(c3.getComplaintId());
        boolean noCatMatch = candidatesC3.stream().noneMatch(cand ->
                cand.getCandidateComplaintId().equalsIgnoreCase(c1.getComplaintId()) ||
                cand.getCandidateComplaintId().equalsIgnoreCase(c2.getComplaintId()));

        if (noCatMatch) {
            System.out.println("PASSED (Complaints with different categories not treated as candidates)");
            passed++;
        } else {
            System.out.println("FAILED (Incorrect match across differing categories)");
        }

        // -----------------------------------------------------------------------------------
        // TEST 3: DIFFERENT LOCATIONS
        // -----------------------------------------------------------------------------------
        System.out.print("[TEST 3/12] Different Locations (No Candidate Match)... ");
        String locDiff = "MG Road Brigade Junction-" + ts;
        Complaint c4 = manager.createComplaint(citizen, "Roads", "Pothole repair needed", locDiff, 8);
        List<DuplicateCandidate> candidatesC4 = manager.findDuplicateCandidates(c4.getComplaintId());
        boolean noLocMatch = candidatesC4.stream().noneMatch(cand ->
                cand.getCandidateComplaintId().equalsIgnoreCase(c1.getComplaintId()) ||
                cand.getCandidateComplaintId().equalsIgnoreCase(c2.getComplaintId()));

        if (noLocMatch) {
            System.out.println("PASSED (Different locations do not generate candidates)");
            passed++;
        } else {
            System.out.println("FAILED (Incorrect match across differing locations)");
        }

        // -----------------------------------------------------------------------------------
        // TEST 4: CAPITALIZATION, WHITESPACE NORMALIZATION & MALFORMED LOCATIONS
        // -----------------------------------------------------------------------------------
        System.out.print("[TEST 4/12] Capitalization, Whitespace & Location Normalization Rules... ");
        String baseLocName = "Anna Nagar 4th Avenue Block 2-" + ts;
        String messyLocName = "   anna   nagar   4th  ave.   block   2-" + ts + "   ";
        Complaint cNorm1 = manager.createComplaint(citizen, "Streetlights", "Dark corner light out", baseLocName, 12);
        Complaint cNorm2 = manager.createComplaint(citizen2, "Streetlights", "Lamp blinking", messyLocName, 14);

        List<DuplicateCandidate> candidatesNorm = manager.findDuplicateCandidates(cNorm2.getComplaintId());
        boolean normMatch = candidatesNorm.stream().anyMatch(cand -> cand.getCandidateComplaintId().equalsIgnoreCase(cNorm1.getComplaintId()));

        // Also test that malformed / placeholder locations are safely rejected and do not match
        boolean malformedRejected1 = !LocationNormalizer.isValidLocation("N/A");
        boolean malformedRejected2 = !LocationNormalizer.isValidLocation("---");
        boolean malformedRejected3 = !LocationNormalizer.isValidLocation("???");
        boolean malformedRejected4 = LocationNormalizer.normalizeLocation("??") == null;

        if (normMatch && malformedRejected1 && malformedRejected2 && malformedRejected3 && malformedRejected4) {
            System.out.println("PASSED (Case/whitespace/token normalization matches; malformed locations rejected)");
            passed++;
        } else {
            System.out.println("FAILED (Normalization failed or malformed location accepted)");
        }

        // -----------------------------------------------------------------------------------
        // TEST 5: RESOLVED ISSUE VERSUS OPEN ISSUE
        // -----------------------------------------------------------------------------------
        System.out.print("[TEST 5/12] Resolved Issue Versus Open Issue (No Active Duplicates)... ");
        String locRes = "Gandhi Circle Ward 12-" + ts;
        Complaint cOpen = manager.createComplaint(citizen, "Waste Management", "Garbage pile", locRes, 10);
        Complaint cToResolve = manager.createComplaint(citizen2, "Waste Management", "Overflowing bin", locRes, 15);

        // Resolve cToResolve
        manager.assignDepartmentAndStaff(cToResolve.getComplaintId(), "Waste Management Department", "Staff Waste", admin);
        manager.updateStatus(cToResolve.getComplaintId(), "InProgress", "Cleaning team working", admin);
        manager.resolveComplaint(cToResolve.getComplaintId(), "Cleared and sanitized");

        // Now query duplicate candidates for cOpen: cToResolve must NOT be an active candidate
        List<DuplicateCandidate> candidatesOpen = manager.findDuplicateCandidates(cOpen.getComplaintId());
        boolean resolvedExcluded = candidatesOpen.stream().noneMatch(cand -> cand.getCandidateComplaintId().equalsIgnoreCase(cToResolve.getComplaintId()));

        // Also query duplicate candidates for resolved cToResolve: must be empty
        List<DuplicateCandidate> candidatesForResolved = manager.findDuplicateCandidates(cToResolve.getComplaintId());
        boolean resolvedQueryEmpty = candidatesForResolved.isEmpty();

        if (resolvedExcluded && resolvedQueryEmpty) {
            System.out.println("PASSED (Resolved complaints excluded from active duplicate candidate detection)");
            passed++;
        } else {
            System.out.println("FAILED (Resolved complaint incorrectly flagged as active candidate)");
        }

        // -----------------------------------------------------------------------------------
        // TEST 6: EXPLICIT OPERATOR CONFIRMATION WORKFLOW
        // -----------------------------------------------------------------------------------
        System.out.print("[TEST 6/12] Explicit Operator Confirmation (Preserves Original Record)... ");
        String locConfirm = "Subramanya Nagar Main Road-" + ts;
        Complaint cPrimary = manager.createComplaint(citizen, "Roads", "Road sinkhole", locConfirm, 20);
        Complaint cSecondary = manager.createComplaint(citizen2, "Roads", "Dangerous sinkhole on road", locConfirm, 25);

        boolean confirmResult = manager.confirmDuplicateLink(cSecondary.getComplaintId(), cPrimary.getComplaintId(), admin, "Verified identical defect by field officer");

        boolean secondaryLinked = cSecondary.isDuplicate() && cPrimary.getComplaintId().equalsIgnoreCase(cSecondary.getDuplicateOfId());
        boolean primaryPreserved = !cPrimary.isDuplicate() && cPrimary.getDuplicateOfId() == null && "Registered".equalsIgnoreCase(cPrimary.getStatus());

        if (confirmResult && secondaryLinked && primaryPreserved) {
            System.out.println("PASSED (Operator confirmed link; original complaint preserved without deletion)");
            passed++;
        } else {
            System.out.println("FAILED (Confirmation failed or original record corrupted)");
        }

        // -----------------------------------------------------------------------------------
        // TEST 7: EXPLICIT REJECTION WORKFLOW
        // -----------------------------------------------------------------------------------
        System.out.print("[TEST 7/12] Explicit Rejection Workflow (Candidate Suppressed)... ");
        String locReject = "Koramangala 80 Feet Road-" + ts;
        Complaint cRej1 = manager.createComplaint(citizen, "Water Supply", "Low pressure near house 12", locReject, 10);
        Complaint cRej2 = manager.createComplaint(citizen2, "Water Supply", "No water near house 45", locReject, 15);

        // Verify they are candidates initially
        List<DuplicateCandidate> initCands = manager.findDuplicateCandidates(cRej2.getComplaintId());
        boolean initFound = initCands.stream().anyMatch(cand -> cand.getCandidateComplaintId().equalsIgnoreCase(cRej1.getComplaintId()));

        // Reject candidate
        boolean rejResult = manager.rejectDuplicateCandidate(cRej2.getComplaintId(), cRej1.getComplaintId(), staff, "Different water lines; separate issues");

        // Verify cRej1 is no longer presented as a candidate for cRej2, and vice versa
        List<DuplicateCandidate> postRejCands2 = manager.findDuplicateCandidates(cRej2.getComplaintId());
        List<DuplicateCandidate> postRejCands1 = manager.findDuplicateCandidates(cRej1.getComplaintId());
        boolean notPresentedFor2 = postRejCands2.stream().noneMatch(cand -> cand.getCandidateComplaintId().equalsIgnoreCase(cRej1.getComplaintId()));
        boolean notPresentedFor1 = postRejCands1.stream().noneMatch(cand -> cand.getCandidateComplaintId().equalsIgnoreCase(cRej2.getComplaintId()));
        boolean keptSeparate = !cRej2.isDuplicate() && !cRej1.isDuplicate();

        if (initFound && rejResult && notPresentedFor2 && notPresentedFor1 && keptSeparate) {
            System.out.println("PASSED (Candidate rejected mutually; suppressed from repeated presentation)");
            passed++;
        } else {
            System.out.println("FAILED (Rejection failed or candidate still presented)");
        }

        // -----------------------------------------------------------------------------------
        // TEST 8: SELF-LINK PREVENTION
        // -----------------------------------------------------------------------------------
        System.out.print("[TEST 8/12] Self-Link Prevention... ");
        boolean selfLinkBlocked = false;
        try {
            manager.confirmDuplicateLink(cPrimary.getComplaintId(), cPrimary.getComplaintId(), admin, "Self link attempt");
        } catch (IllegalArgumentException e) {
            selfLinkBlocked = e.getMessage().toLowerCase().contains("self-link");
        }

        boolean selfRejectionBlocked = false;
        try {
            manager.rejectDuplicateCandidate(cPrimary.getComplaintId(), cPrimary.getComplaintId(), admin, "Self reject attempt");
        } catch (IllegalArgumentException e) {
            selfRejectionBlocked = e.getMessage().toLowerCase().contains("self-rejection");
        }

        if (selfLinkBlocked && selfRejectionBlocked) {
            System.out.println("PASSED (Self-linking and self-rejection strictly prevented)");
            passed++;
        } else {
            System.out.println("FAILED (Self-linking or self-rejection was not blocked)");
        }

        // -----------------------------------------------------------------------------------
        // TEST 9: DUPLICATE-LINK AND CIRCULAR-LINK PREVENTION
        // -----------------------------------------------------------------------------------
        System.out.print("[TEST 9/12] Duplicate-Link & Circular-Link Prevention (Direct & Transitive)... ");
        String locCirc = "BTM Layout Ring Road-" + ts;
        Complaint cA = manager.createComplaint(citizen, "Roads", "Road issue A", locCirc, 10);
        Complaint cB = manager.createComplaint(citizen, "Roads", "Road issue B", locCirc, 10);
        Complaint cC = manager.createComplaint(citizen, "Roads", "Road issue C", locCirc, 10);

        // Link B -> A
        manager.confirmDuplicateLink(cB.getComplaintId(), cA.getComplaintId(), admin, "Link B to A");

        // Attempt 1: Duplicate-link prevention (linking B -> A again)
        boolean dupLinkBlocked = false;
        try {
            manager.confirmDuplicateLink(cB.getComplaintId(), cA.getComplaintId(), admin, "Link B to A again");
        } catch (IllegalStateException e) {
            dupLinkBlocked = e.getMessage().toLowerCase().contains("already confirmed as a duplicate");
        }

        // Attempt 2: Direct circular link (linking A -> B when B is already linked to A)
        boolean directCircBlocked = false;
        try {
            manager.confirmDuplicateLink(cA.getComplaintId(), cB.getComplaintId(), admin, "Link A to B circular");
        } catch (IllegalStateException e) {
            directCircBlocked = e.getMessage().toLowerCase().contains("circular");
        }

        // Link C -> B (Chain is now C -> B -> A)
        manager.confirmDuplicateLink(cC.getComplaintId(), cB.getComplaintId(), admin, "Link C to B");

        // Attempt 3: Transitive circular link (linking A -> C creates A -> C -> B -> A cycle)
        boolean transitiveCircBlocked = false;
        try {
            manager.confirmDuplicateLink(cA.getComplaintId(), cC.getComplaintId(), admin, "Link A to C transitive cycle");
        } catch (IllegalStateException e) {
            transitiveCircBlocked = e.getMessage().toLowerCase().contains("circular");
        }

        if (dupLinkBlocked && directCircBlocked && transitiveCircBlocked) {
            System.out.println("PASSED (Duplicate link and both direct & transitive cycles prevented)");
            passed++;
        } else {
            System.out.printf("FAILED (dup=%b, direct=%b, trans=%b)\n", dupLinkBlocked, directCircBlocked, transitiveCircBlocked);
        }

        // -----------------------------------------------------------------------------------
        // TEST 10: AUDIT HISTORY RECORDING
        // -----------------------------------------------------------------------------------
        System.out.print("[TEST 10/12] Complete Audit History Recording on Both Complaints... ");
        List<ComplaintHistory> histSecondary = manager.getComplaintTimeline(cSecondary.getComplaintId());
        List<ComplaintHistory> histPrimary = manager.getComplaintTimeline(cPrimary.getComplaintId());

        boolean secHasAudit = histSecondary.stream().anyMatch(h ->
                h.getRemarks().contains("Duplicate link confirmed") &&
                h.getUpdatedBy().contains("Commissioner"));

        boolean priHasAudit = histPrimary.stream().anyMatch(h ->
                h.getRemarks().contains("Linked duplicate complaint") &&
                h.getUpdatedBy().contains("Commissioner"));

        List<ComplaintHistory> histRej = manager.getComplaintTimeline(cRej2.getComplaintId());
        boolean rejHasAudit = histRej.stream().anyMatch(h ->
                h.getRemarks().contains("rejected") &&
                h.getUpdatedBy().contains("Officer Anitha"));

        if (secHasAudit && priHasAudit && rejHasAudit) {
            System.out.println("PASSED (Audit history recorded with operator name, timestamp, and details)");
            passed++;
        } else {
            System.out.println("FAILED (Audit entries missing or incomplete)");
        }

        // -----------------------------------------------------------------------------------
        // TEST 11: UNAUTHORIZED CONFIRMATION ATTEMPTS
        // -----------------------------------------------------------------------------------
        System.out.print("[TEST 11/12] Backend Authorization Enforcement for Duplicate Actions... ");
        Complaint cAuth1 = manager.createComplaint(citizen, "Roads", "Auth test 1", "Auth Loc-" + ts, 5);
        Complaint cAuth2 = manager.createComplaint(citizen2, "Roads", "Auth test 2", "Auth Loc-" + ts, 5);

        boolean citizenConfirmBlocked = false;
        try {
            manager.confirmDuplicateLink(cAuth2.getComplaintId(), cAuth1.getComplaintId(), citizen, "Citizen trying to confirm");
        } catch (SecurityException e) {
            citizenConfirmBlocked = true;
        }

        boolean citizenRejectBlocked = false;
        try {
            manager.rejectDuplicateCandidate(cAuth2.getComplaintId(), cAuth1.getComplaintId(), citizen, "Citizen trying to reject");
        } catch (SecurityException e) {
            citizenRejectBlocked = true;
        }

        boolean nullOperatorBlocked = false;
        try {
            manager.confirmDuplicateLink(cAuth2.getComplaintId(), cAuth1.getComplaintId(), null, "No user");
        } catch (SecurityException e) {
            nullOperatorBlocked = true;
        }

        if (citizenConfirmBlocked && citizenRejectBlocked && nullOperatorBlocked) {
            System.out.println("PASSED (Backend enforces Staff/Admin authorization; citizen/null rejected)");
            passed++;
        } else {
            System.out.println("FAILED (Authorization checks failed to prevent unauthorized actions)");
        }

        // -----------------------------------------------------------------------------------
        // TEST 12: OPEN-ISSUE REFERENCE CANDIDATE MATCHING
        // -----------------------------------------------------------------------------------
        System.out.print("[TEST 12/12] Open-Issue Reference Matching (Explicit ID in Description)... ");
        Complaint cOrigIssue = manager.createComplaint(citizen, "Streetlights", "Streetlight pole bent", "Corner of Park Street-" + ts, 10);
        String descWithRef = "Second report regarding previous grievance " + cOrigIssue.getComplaintId() + " pole is sparking";
        Complaint cRefIssue = manager.createComplaint(citizen2, "Streetlights", descWithRef, "Different description location text", 10);

        List<DuplicateCandidate> refCandidates = manager.findDuplicateCandidates(cRefIssue.getComplaintId());
        boolean refMatched = refCandidates.stream().anyMatch(cand ->
                cand.getCandidateComplaintId().equalsIgnoreCase(cOrigIssue.getComplaintId()) &&
                cand.getReason().toLowerCase().contains("reference"));

        if (refMatched) {
            System.out.println("PASSED (Candidate matched via open-issue reference in complaint description)");
            passed++;
        } else {
            System.out.println("FAILED (Open-issue reference failed to match)");
        }

        System.out.println("\n-------------------------------------------------------------------------------");
        System.out.printf("RESULT: %d / %d TESTS PASSED (%s)\n", passed, total, (passed == total ? "100% Success Rate" : "Failures Present"));
        System.out.println("-------------------------------------------------------------------------------\n");

        return passed == total;
    }
}
