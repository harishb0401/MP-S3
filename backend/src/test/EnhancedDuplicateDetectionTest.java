package test;

import model.Citizen;
import model.Complaint;
import model.Department;
import model.DepartmentStaff;
import model.DuplicateCandidate;
import model.Notification;
import model.User;
import service.Database;
import service.GrievanceManager;

import java.io.File;
import java.util.List;

/**
 * Automated Verification Suite for Enhanced Duplicate Complaint Detection & Notification System.
 *
 * Verifies all requirements:
 * 1. Multiple citizens reporting the same issue identified across different citizen accounts.
 * 2. Independent notification generation for both Admin and Manager roles.
 * 3. Configurable GPS proximity matching & description similarity thresholds.
 * 4. False duplicate prevention for unrelated issues at the same location.
 * 5. Distinct citizen count calculation and incremental alert updates on 3rd/4th citizen reports.
 * 6. Anti-spam protection: repeated checks do NOT generate duplicate notifications.
 * 7. Persistent tracking survives application restart (disk reload).
 * 8. Preservation of all original citizen complaint records (no automatic merging/deletion).
 * 9. Review, confirm link, and candidate rejection workflows.
 */
public class EnhancedDuplicateDetectionTest {

    private static final String TEST_DB_FILE = "backend/data/test_duplicate_detection_db.json";

    public static void main(String[] args) {
        boolean success = runAllTests();
        if (!success) {
            System.err.println("One or more tests failed!");
            System.exit(1);
        }
    }

    public static boolean runAllTests() {
        System.out.println("===============================================================================");
        System.out.println("   ENHANCED DUPLICATE DETECTION & NOTIFICATION AUTOMATED TEST SUITE");
        System.out.println("===============================================================================\n");

        int passed = 0;
        int total = 10;

        // Clean up test file if present
        File testFile = new File(TEST_DB_FILE);
        if (testFile.exists()) testFile.delete();

        Database db = new Database(TEST_DB_FILE);
        GrievanceManager manager = new GrievanceManager(db);

        // 3 Different Citizen Accounts
        User citizenA = db.registerUser("Anand", "anand", "anand@test.com", "9876543201", "Citizen@123", "CITIZEN", null, "284950183011");
        User citizenB = db.registerUser("Bhuvanesh", "bhuvanesh", "bhuvanesh@test.com", "9876543202", "Citizen@123", "CITIZEN", null, "284950183012");
        User citizenC = db.registerUser("Chitra", "chitra", "chitra@test.com", "9876543203", "Citizen@123", "CITIZEN", null, "284950183013");

        // Admin and Manager accounts
        User admin = db.registerUser("Admin Officer", "admin_dup", "admin.dup@makkal.gov.in", "9876543200", "Admin@123", "ADMIN", "General Administration", null);
        User managerStaff = db.registerUser("Manager Raman", "manager_roads", "manager.roads@makkal.gov.in", "9876543210", "Manager@123", "MANAGER", "Roads Department", null);
        db.saveDatabase();

        String location = "Anna Nagar, Street 5";
        String category = "Road Damage";

        // -----------------------------------------------------------------------------------
        // STEP 1: Citizen A submits Road Damage complaint at Anna Nagar, Street 5
        // -----------------------------------------------------------------------------------
        System.out.print("[TEST 1/10] Citizen A submits baseline complaint at Anna Nagar, Street 5... ");
        Complaint compA = manager.createComplaint(
                citizenA,
                category,
                "Severe road damage and deep pothole causing traffic slowdown near street 5",
                location,
                20
        );

        List<DuplicateCandidate> candA = manager.findDuplicateCandidates(compA.getComplaintId());
        if (candA.isEmpty() && !compA.isDuplicate()) {
            System.out.println("PASSED (First complaint registered; 0 duplicates detected)");
            passed++;
        } else {
            System.out.println("FAILED (Unexpected duplicates detected on first complaint)");
        }

        // -----------------------------------------------------------------------------------
        // STEP 2: Citizen B submits similar Road Damage complaint at same location
        // -----------------------------------------------------------------------------------
        System.out.print("[TEST 2/10] Citizen B submits similar complaint -> Matches Citizen A (2 distinct citizens)... ");
        int adminNotifsBefore = db.getNotificationsForUser(admin).size();
        int mgrNotifsBefore = db.getNotificationsForUser(managerStaff).size();

        Complaint compB = manager.createComplaint(
                citizenB,
                category,
                "Dangerous deep potholes and road damage near street 5 cross",
                location,
                25
        );

        List<DuplicateCandidate> candB = manager.findDuplicateCandidates(compB.getComplaintId());
        boolean foundA = candB.stream().anyMatch(c -> c.getCandidateComplaintId().equalsIgnoreCase(compA.getComplaintId()));
        int distinctCountB = candB.isEmpty() ? 0 : candB.get(0).getDistinctCitizenCount();

        if (foundA && distinctCountB == 2 && !compB.isDuplicate()) {
            System.out.println("PASSED (Matched compA; distinct citizen count = 2; original record preserved)");
            passed++;
        } else {
            System.out.println("FAILED (Match not found or distinct citizen count incorrect: " + distinctCountB + ")");
        }

        // -----------------------------------------------------------------------------------
        // STEP 3: Verify Admin and Manager independently receive notification for duplicate
        // -----------------------------------------------------------------------------------
        System.out.print("[TEST 3/10] Verify Admin and Manager both independently receive duplicate notification... ");
        List<Notification> adminNotifs = db.getNotificationsForUser(admin);
        List<Notification> mgrNotifs = db.getNotificationsForUser(managerStaff);

        boolean adminReceived = adminNotifs.stream().anyMatch(n ->
                n.getTitle().contains("Multiple Citizens Reported the Same Issue") &&
                n.getMessage().contains(compB.getComplaintId()) &&
                n.getMessage().contains(compA.getComplaintId()) &&
                n.getMessage().contains("2"));

        boolean mgrReceived = mgrNotifs.stream().anyMatch(n ->
                n.getTitle().contains("Multiple Citizens Reported the Same Issue") &&
                n.getMessage().contains(compB.getComplaintId()) &&
                n.getMessage().contains(compA.getComplaintId()) &&
                n.getMessage().contains("2"));

        if (adminReceived && mgrReceived) {
            System.out.println("PASSED (Both Admin and Manager independently notified with matching complaint IDs and citizen count)");
            passed++;
        } else {
            System.out.println("FAILED (Admin received=" + adminReceived + ", Manager received=" + mgrReceived + ")");
        }

        // -----------------------------------------------------------------------------------
        // STEP 4: Citizen C submits 3rd similar complaint -> Citizen count updates to 3 & alerts sent
        // -----------------------------------------------------------------------------------
        System.out.print("[TEST 4/10] Citizen C submits 3rd report -> Reported citizen count updates to 3... ");
        int adminCountBeforeC = db.getNotificationsForUser(admin).size();

        Complaint compC = manager.createComplaint(
                citizenC,
                category,
                "Massive pothole crater on road surface near Anna Nagar 5th street",
                "Anna Nagar 5th St",
                30
        );

        List<DuplicateCandidate> candC = manager.findDuplicateCandidates(compC.getComplaintId());
        boolean matchedAorB = candC.stream().anyMatch(c ->
                c.getCandidateComplaintId().equalsIgnoreCase(compA.getComplaintId()) ||
                c.getCandidateComplaintId().equalsIgnoreCase(compB.getComplaintId()));

        int distinctCountC = candC.isEmpty() ? 0 : candC.get(0).getDistinctCitizenCount();
        int adminCountAfterC = db.getNotificationsForUser(admin).size();
        boolean adminAlertedForThird = (adminCountAfterC > adminCountBeforeC);

        List<Notification> latestAdminNotifs = db.getNotificationsForUser(admin);
        Notification latestAdminNotif = latestAdminNotifs.isEmpty() ? null : latestAdminNotifs.get(latestAdminNotifs.size() - 1);
        boolean messageMentions3 = latestAdminNotif != null && latestAdminNotif.getMessage().contains("3");

        if (matchedAorB && distinctCountC == 3 && adminAlertedForThird && messageMentions3) {
            System.out.println("PASSED (Citizen count updated to 3; additional report alert received)");
            passed++;
        } else {
            System.out.println("FAILED (Count=" + distinctCountC + ", Alerted=" + adminAlertedForThird + ")");
        }

        // -----------------------------------------------------------------------------------
        // STEP 5: Anti-Spam: Repeated checks do NOT generate duplicate notifications
        // -----------------------------------------------------------------------------------
        System.out.print("[TEST 5/10] Anti-Spam: Repeated checkAndAlert calls do NOT generate spam notifications... ");
        int notifCountBeforeRecheck = db.getNotificationsForUser(admin).size();

        // Repeated check on compB and compC
        manager.checkAndAlertDuplicateCandidates(compB);
        manager.checkAndAlertDuplicateCandidates(compC);
        manager.checkAndAlertDuplicateCandidates(compB);

        int notifCountAfterRecheck = db.getNotificationsForUser(admin).size();
        boolean noSpam = (notifCountAfterRecheck == notifCountBeforeRecheck);

        if (noSpam) {
            System.out.println("PASSED (0 duplicate notifications generated across repeated checks)");
            passed++;
        } else {
            System.out.println("FAILED (Notifs grew from " + notifCountBeforeRecheck + " to " + notifCountAfterRecheck + ")");
        }

        // -----------------------------------------------------------------------------------
        // STEP 6: Negative Matching: Unrelated complaint at same location is NOT flagged
        // -----------------------------------------------------------------------------------
        System.out.print("[TEST 6/10] Negative Matching: Unrelated complaint at same location rejected... ");
        // Same location, but completely different issue description (e.g. fallen tree branch)
        Complaint compUnrelated = manager.createComplaint(
                citizenA,
                category,
                "Fallen large banyan tree branch blocking pedestrian walking pathway",
                location,
                10
        );

        List<DuplicateCandidate> candUnrelated = manager.findDuplicateCandidates(compUnrelated.getComplaintId());
        boolean noFalsePositive = candUnrelated.isEmpty();

        // Also different category: Streetlight at same location
        Complaint compDiffCategory = manager.createComplaint(
                citizenB,
                "Streetlight",
                "Streetlight pole sparking and dark near street 5",
                location,
                15
        );
        List<DuplicateCandidate> candDiffCat = manager.findDuplicateCandidates(compDiffCategory.getComplaintId());
        boolean diffCatNoMatch = candDiffCat.isEmpty();

        if (noFalsePositive && diffCatNoMatch) {
            System.out.println("PASSED (Unrelated descriptions and different categories avoid false duplicate alerts)");
            passed++;
        } else {
            System.out.println("FAILED (False positive detected: same-desc=" + !noFalsePositive + ", diff-cat=" + !diffCatNoMatch + ")");
        }

        // -----------------------------------------------------------------------------------
        // STEP 7: GPS Proximity Matching (Configurable Threshold)
        // -----------------------------------------------------------------------------------
        System.out.print("[TEST 7/10] GPS Proximity matching within threshold vs outside threshold... ");
        // Coordinate 1: Anna Nagar (13.085000, 80.210000)
        Complaint compGps1 = manager.createComplaint(
                citizenA,
                "Water Supply",
                "Underground pipeline water leakage flooding street",
                "Anna Nagar West",
                20,
                13.085000,
                80.210000,
                5.0,
                "Water Leak 1"
        );

        // Coordinate 2: 70 meters away (13.085500, 80.210400) -> should match (threshold = 300m)
        Complaint compGpsNear = manager.createComplaint(
                citizenB,
                "Water Supply",
                "Severe pipeline burst water leaking on road",
                "Different Street Name Text",
                25,
                13.085500,
                80.210400,
                5.0,
                "Water Leak 2"
        );

        List<DuplicateCandidate> candNear = manager.findDuplicateCandidates(compGpsNear.getComplaintId());
        boolean gpsNearMatched = candNear.stream().anyMatch(c -> c.getCandidateComplaintId().equalsIgnoreCase(compGps1.getComplaintId()));

        // Coordinate 3: 2.5 kilometers away (13.105000, 80.210000) -> outside 300m threshold
        Complaint compGpsFar = manager.createComplaint(
                citizenC,
                "Water Supply",
                "Underground pipeline water leakage flooding street",
                "Different Street Far",
                20,
                13.105000,
                80.210000,
                5.0,
                "Water Leak Far"
        );

        List<DuplicateCandidate> candFar = manager.findDuplicateCandidates(compGpsFar.getComplaintId());
        boolean gpsFarExcluded = candFar.stream().noneMatch(c -> c.getCandidateComplaintId().equalsIgnoreCase(compGps1.getComplaintId()));

        if (gpsNearMatched && gpsFarExcluded) {
            System.out.println("PASSED (GPS distance within 300m matched; GPS distance > 2km safely rejected)");
            passed++;
        } else {
            System.out.println("FAILED (gpsNearMatched=" + gpsNearMatched + ", gpsFarExcluded=" + gpsFarExcluded + ")");
        }

        // -----------------------------------------------------------------------------------
        // STEP 8: Persistent Tracking Survives Application Restart
        // -----------------------------------------------------------------------------------
        System.out.print("[TEST 8/10] Persistent Notification Tracking survives application restart... ");
        db.saveDatabase();

        // Simulate application restart by loading a fresh Database & Manager instance from disk
        Database reloadedDb = new Database(TEST_DB_FILE);
        GrievanceManager reloadedManager = new GrievanceManager(reloadedDb);

        int reloadedAdminNotifsBefore = reloadedDb.getNotificationsForUser(admin).size();

        // Check on compB using reloaded manager
        Complaint reloadedCompB = reloadedDb.getComplaintById(compB.getComplaintId());
        reloadedManager.checkAndAlertDuplicateCandidates(reloadedCompB);

        int reloadedAdminNotifsAfter = reloadedDb.getNotificationsForUser(admin).size();
        boolean persistentNoSpam = (reloadedAdminNotifsAfter == reloadedAdminNotifsBefore);

        // Verify that duplicate tracking was restored from disk
        int trackedCount = reloadedDb.getNotifiedDuplicateCitizenCount(compA.getComplaintId() + ":" + compB.getComplaintId());
        if (trackedCount == 0) {
            trackedCount = reloadedDb.getNotifiedDuplicateCitizenCount(compB.getComplaintId() + ":" + compA.getComplaintId());
        }
        boolean trackingRestored = (trackedCount >= 2);

        if (persistentNoSpam && trackingRestored) {
            System.out.println("PASSED (Tracking state reloaded from disk; zero spam after restart)");
            passed++;
        } else {
            System.out.println("FAILED (NoSpam=" + persistentNoSpam + ", TrackedCount=" + trackedCount + ")");
        }

        // -----------------------------------------------------------------------------------
        // STEP 9: Operator Confirm Duplicate Link Workflow
        // -----------------------------------------------------------------------------------
        System.out.print("[TEST 9/10] Operator confirms duplicate link -> Original record & history preserved... ");
        boolean linkConfirmed = reloadedManager.confirmDuplicateLink(
                compB.getComplaintId(),
                compA.getComplaintId(),
                managerStaff,
                "Verified exact same road damage by field engineer"
        );

        Complaint updatedB = reloadedDb.getComplaintById(compB.getComplaintId());
        boolean isLinked = updatedB != null && updatedB.isDuplicate() && compA.getComplaintId().equalsIgnoreCase(updatedB.getDuplicateOfId());
        boolean originalRecordExists = updatedB != null && "Road Damage".equals(updatedB.getCategory());

        if (linkConfirmed && isLinked && originalRecordExists) {
            System.out.println("PASSED (Duplicate link confirmed; original complaint preserved without deletion)");
            passed++;
        } else {
            System.out.println("FAILED (linkConfirmed=" + linkConfirmed + ", isLinked=" + isLinked + ")");
        }

        // -----------------------------------------------------------------------------------
        // STEP 10: Operator Rejection Workflow suppresses candidate presentation
        // -----------------------------------------------------------------------------------
        System.out.print("[TEST 10/10] Operator rejects candidate -> Candidate suppressed from future alerts... ");
        // Reject compC against compA
        boolean rejected = reloadedManager.rejectDuplicateCandidate(
                compC.getComplaintId(),
                compA.getComplaintId(),
                admin,
                "Operator determined issue requires separate asphalt work"
        );

        List<DuplicateCandidate> candidatesAfterReject = reloadedManager.findDuplicateCandidates(compC.getComplaintId());
        boolean compASuppressed = candidatesAfterReject.stream().noneMatch(c -> c.getCandidateComplaintId().equalsIgnoreCase(compA.getComplaintId()));

        if (rejected && compASuppressed) {
            System.out.println("PASSED (Candidate rejected and mutually suppressed from presentation)");
            passed++;
        } else {
            System.out.println("FAILED (rejected=" + rejected + ", compASuppressed=" + compASuppressed + ")");
        }

        // Clean up test file
        if (testFile.exists()) testFile.delete();

        System.out.println("\n-------------------------------------------------------------------------------");
        System.out.println("RESULT: " + passed + " / " + total + " TESTS PASSED (" + (passed * 100 / total) + "% Success Rate)");
        System.out.println("===============================================================================\n");

        return passed == total;
    }
}
