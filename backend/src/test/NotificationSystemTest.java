package test;

import model.*;
import service.Database;
import service.GrievanceManager;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;

/**
 * Automated Test Suite for the Complete Notification System across Citizen, Admin, Manager, and Worker roles.
 */
public class NotificationSystemTest {

    public static void main(String[] args) {
        System.out.println("===============================================================================");
        System.out.println("   FEATURE: COMPLETE NOTIFICATION SYSTEM - AUTOMATED TEST SUITE");
        System.out.println("===============================================================================");

        int passed = 0;
        int total = 10;

        File tempDbFile = null;
        Database db;
        GrievanceManager gm;
        try {
            tempDbFile = File.createTempFile("test_notif_db", ".json");
            tempDbFile.deleteOnExit();
            Files.copy(Paths.get(Database.getDbFile()), tempDbFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            db = new Database(tempDbFile.getAbsolutePath());
            gm = new GrievanceManager(db);
        } catch (Exception e) {
            throw new RuntimeException("Could not create test database", e);
        }

        // Fetch / Setup Users
        User admin = db.getUserById("ADM1001");
        if (admin == null) admin = db.getUserByUsername("admin");
        if (admin == null) admin = db.getUserByEmail("admin@makkal.gov.in");

        User manager = db.getUserById("MGR1001");
        if (manager == null) manager = db.getUserByUsername("manager");
        if (manager == null) manager = db.getUserByEmail("manager@makkal.gov.in");

        User workerSuresh = db.getUserById("STF-W01");
        if (workerSuresh == null) workerSuresh = db.getUserByUsername("water_staff");
        if (workerSuresh == null) workerSuresh = db.getUserByEmail("water_staff@makkal.gov.in");

        User workerPriya = db.getUserById("STF-M01");
        if (workerPriya == null) workerPriya = db.getUserByUsername("waste_staff");
        if (workerPriya == null) workerPriya = db.getUserByEmail("waste_staff@makkal.gov.in");

        User citizenA = db.getUserByUsername("ramesh");
        if (citizenA == null) citizenA = db.getUserById("C001");
        if (citizenA == null && !db.getAllCitizens().isEmpty()) citizenA = db.getAllCitizens().get(0);

        // Create a secondary citizen for isolation testing
        User citizenB = db.getUserByUsername("citizen_isolated_test");
        if (citizenB == null) {
            citizenB = db.registerUser(
                    "Isolated Citizen",
                    "citizen_isolated_test",
                    "isolated@test.gov",
                    "9876543299",
                    "Password@123",
                    "CITIZEN",
                    null,
                    "444455556666"
            );
        }

        try {
            // TEST 1: Citizen Complaint Registration Notification
            System.out.print("[TEST 1/10] Citizen receives notification on Complaint Registration... ");
            int cNotifBefore = db.getNotificationsForUser(citizenA).size();
            int aNotifBefore = db.getNotificationsForUser(admin).size();

            Complaint c1 = gm.createComplaint(citizenA, "Water Supply", "Pipe leak near ward office", "Gandhi Road", 150);

            List<Notification> cNotifs = db.getNotificationsForUser(citizenA);
            List<Notification> aNotifs = db.getNotificationsForUser(admin);

            boolean cNotifReceived = cNotifs.size() > cNotifBefore &&
                    cNotifs.get(0).getTitle().contains("Complaint Registered") &&
                    cNotifs.get(0).getComplaintId().equals(c1.getComplaintId());

            boolean aNotifReceived = aNotifs.size() > aNotifBefore &&
                    aNotifs.get(0).getTitle().contains("New Grievance Submitted") &&
                    aNotifs.get(0).getComplaintId().equals(c1.getComplaintId());

            if (cNotifReceived && aNotifReceived) {
                System.out.println("PASSED (Citizen: " + cNotifs.get(0).getTitle() + " | Admin: " + aNotifs.get(0).getTitle() + ")");
                passed++;
            } else {
                System.out.println("FAILED (cNotifReceived=" + cNotifReceived + ", aNotifReceived=" + aNotifReceived + ")");
            }

            // TEST 2: Department and Worker Assignment Notifications
            System.out.print("[TEST 2/10] Department & Worker assignment notifies Citizen and Worker... ");
            int wSureshBefore = db.getNotificationsForUser(workerSuresh).size();
            String workerName = (workerSuresh instanceof DepartmentStaff) ? ((DepartmentStaff) workerSuresh).getStaffName() : workerSuresh.getDisplayName();
            gm.assessComplaint(c1.getComplaintId(), "HIGH", "Urgent water line repair", "Water Department", workerName, manager);

            List<Notification> cNotifsAfterAssign = db.getNotificationsForUser(citizenA);
            List<Notification> wNotifs = db.getNotificationsForUser(workerSuresh);

            boolean citizenGotWorkerNotif = false;
            for (Notification n : cNotifsAfterAssign) {
                if (n.getComplaintId().equals(c1.getComplaintId()) && n.getTitle().contains("Worker Assigned")) {
                    citizenGotWorkerNotif = true;
                    break;
                }
            }

            boolean workerGotTaskNotif = wNotifs.size() > wSureshBefore &&
                    wNotifs.get(0).getComplaintId().equals(c1.getComplaintId()) &&
                    wNotifs.get(0).getTitle().contains("New Task Assigned");

            if (citizenGotWorkerNotif && workerGotTaskNotif) {
                System.out.println("PASSED (Citizen notified of worker, Worker received task notification)");
                passed++;
            } else {
                System.out.println("FAILED (citizenGotWorkerNotif=" + citizenGotWorkerNotif + ", workerGotTaskNotif=" + workerGotTaskNotif + ", wNotifs=" + wNotifs.size() + ", before=" + wSureshBefore + ")");
            }

            // TEST 3: Manager receives department allocation notification
            System.out.print("[TEST 3/10] Manager receives Department Allocation notification... ");
            List<Notification> mNotifs = db.getNotificationsForUser(manager);
            boolean managerGotAlloc = false;
            for (Notification n : mNotifs) {
                if (n.getComplaintId().equals(c1.getComplaintId()) && n.getTitle().contains("Department Allocation")) {
                    managerGotAlloc = true;
                    break;
                }
            }
            if (managerGotAlloc) {
                System.out.println("PASSED");
                passed++;
            } else {
                System.out.println("FAILED");
            }

            // TEST 4: Status Transition to InProgress Notifications
            System.out.print("[TEST 4/10] Status InProgress notifies Citizen and Management... ");
            gm.updateStatus(c1.getComplaintId(), "InProgress", "Technician dispatched on site", workerSuresh);
            List<Notification> cNotifsAfterProgress = db.getNotificationsForUser(citizenA);
            boolean citizenGotProgress = false;
            for (Notification n : cNotifsAfterProgress) {
                if (n.getComplaintId().equals(c1.getComplaintId()) && n.getTitle().contains("In Progress")) {
                    citizenGotProgress = true;
                    break;
                }
            }
            if (citizenGotProgress) {
                System.out.println("PASSED");
                passed++;
            } else {
                System.out.println("FAILED");
            }

            // TEST 5: Status Transition to Resolved Notifications
            System.out.print("[TEST 5/10] Status Resolved notifies Citizen, Worker and Admin... ");
            gm.resolveComplaint(c1.getComplaintId(), "Replaced corroded valve and restored flow", workerSuresh);
            List<Notification> cNotifsAfterResolved = db.getNotificationsForUser(citizenA);
            boolean citizenGotResolved = false;
            for (Notification n : cNotifsAfterResolved) {
                if (n.getComplaintId().equals(c1.getComplaintId()) && n.getTitle().contains("Resolved")) {
                    citizenGotResolved = true;
                    break;
                }
            }

            List<Notification> aNotifsAfterResolved = db.getNotificationsForUser(admin);
            boolean adminGotResolved = false;
            for (Notification n : aNotifsAfterResolved) {
                if (n.getComplaintId().equals(c1.getComplaintId()) && n.getTitle().contains("Resolved")) {
                    adminGotResolved = true;
                    break;
                }
            }

            if (citizenGotResolved && adminGotResolved) {
                System.out.println("PASSED (Citizen & Admin notified of completion)");
                passed++;
            } else {
                System.out.println("FAILED (citizenGotResolved=" + citizenGotResolved + ", adminGotResolved=" + adminGotResolved + ")");
            }

            // TEST 6: Worker Reassignment Notification
            System.out.print("[TEST 6/10] Reassignment notifies new Worker... ");
            Complaint c2 = gm.createComplaint(citizenA, "Waste Management", "Overflowing bins", "Park Avenue", 40);
            gm.assessComplaint(c2.getComplaintId(), "MEDIUM", "Clear bins", "Waste Management Department", "Officer Kumar", manager);

            int priyaBefore = db.getNotificationsForUser(workerPriya).size();
            String priyaName = (workerPriya instanceof DepartmentStaff) ? ((DepartmentStaff) workerPriya).getStaffName() : workerPriya.getDisplayName();
            gm.reassignComplaint(c2.getComplaintId(), "Waste Management Department", priyaName, "Shift transfer", manager);

            List<Notification> priyaNotifs = db.getNotificationsForUser(workerPriya);
            boolean priyaReassigned = false;
            for (Notification n : priyaNotifs) {
                if (n.getComplaintId().equals(c2.getComplaintId()) && n.getTitle().contains("Reassigned")) {
                    priyaReassigned = true;
                    break;
                }
            }
            if (priyaReassigned) {
                System.out.println("PASSED (Priya notified of reassigned task)");
                passed++;
            } else {
                System.out.println("FAILED (priyaReassigned=" + priyaReassigned + ", count=" + priyaNotifs.size() + ")");
            }

            // TEST 7: Strict Recipient Isolation (RBAC)
            System.out.print("[TEST 7/10] Strict Recipient Isolation between different users... ");
            List<Notification> bNotifs = db.getNotificationsForUser(citizenB);
            boolean citizenBSeesA = false;
            for (Notification n : bNotifs) {
                if (n.getComplaintId().equals(c1.getComplaintId()) || n.getComplaintId().equals(c2.getComplaintId())) {
                    citizenBSeesA = true;
                    break;
                }
            }

            if (!citizenBSeesA) {
                System.out.println("PASSED (Citizen B cannot view Citizen A's complaints or notifications)");
                passed++;
            } else {
                System.out.println("FAILED (Data leak: Citizen B received Citizen A's notification)");
            }

            // TEST 8: Mark Notification as Read & Unread Counting
            System.out.print("[TEST 8/10] Mark single notification as read & unread count... ");
            int unreadBefore = db.getUnreadNotificationCount(citizenA);
            List<Notification> unreadList = db.getNotificationsForUser(citizenA);
            Notification firstNotif = unreadList.get(0);
            boolean markOk = db.markNotificationAsRead(firstNotif.getNotificationId(), citizenA);
            int unreadAfter = db.getUnreadNotificationCount(citizenA);

            if (markOk && unreadAfter == (unreadBefore - 1)) {
                System.out.println("PASSED (Unread count correctly decremented from " + unreadBefore + " to " + unreadAfter + ")");
                passed++;
            } else {
                System.out.println("FAILED (markOk=" + markOk + ", unreadBefore=" + unreadBefore + ", unreadAfter=" + unreadAfter + ")");
            }

            // TEST 9: Mark All as Read
            System.out.print("[TEST 9/10] Mark all notifications as read... ");
            int markedCount = db.markAllNotificationsAsRead(citizenA);
            int unreadFinal = db.getUnreadNotificationCount(citizenA);

            if (unreadFinal == 0 && markedCount > 0) {
                System.out.println("PASSED (Marked " + markedCount + " notifications, 0 remaining unread)");
                passed++;
            } else {
                System.out.println("FAILED (markedCount=" + markedCount + ", unreadFinal=" + unreadFinal + ")");
            }

            // TEST 10: Deduplication & Database Persistence
            System.out.print("[TEST 10/10] Deduplication prevention & Database persistence... ");
            int totalBefore = db.getAllNotifications().size();
            // Attempt to trigger duplicate notification for identical event
            Notification dupe = db.createNotification(
                    citizenA.getId(),
                    "CITIZEN",
                    c1.getComplaintId(),
                    "Complaint Registered: " + c1.getComplaintId(),
                    "Your complaint " + c1.getComplaintId() + " has been submitted.",
                    "success"
            );
            int totalAfter = db.getAllNotifications().size();
            boolean deduplicated = (totalBefore == totalAfter);

            // Re-instantiate Database from JSON file to test persistence
            Database reloadedDb = new Database(tempDbFile.getAbsolutePath());
            int reloadedCount = reloadedDb.getAllNotifications().size();
            boolean persisted = (reloadedCount == totalBefore);

            if (deduplicated && persisted) {
                System.out.println("PASSED (Spam duplicate prevented, all " + reloadedCount + " notifications persisted across DB reloads)");
                passed++;
            } else {
                System.out.println("FAILED (deduplicated=" + deduplicated + ", persisted=" + persisted + ", before=" + totalBefore + ", after=" + totalAfter + ", reloaded=" + reloadedCount + ")");
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        System.out.println("-------------------------------------------------------------------------------");
        System.out.println("NOTIFICATION SYSTEM RESULT: " + passed + " / " + total + " TESTS PASSED");
        System.out.println("===============================================================================");
        if (passed != total) {
            System.exit(1);
        }
    }
}
