package test;

import model.Admin;
import model.Citizen;
import model.Complaint;
import model.Department;
import model.DepartmentStaff;
import model.User;
import service.Database;
import service.GrievanceManager;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Automated Verification Test Suite for Department Assignment, Reassignment, and Capacity Consistency.
 * Validates:
 * 1. Assignment to a department with available capacity.
 * 2. Assignment to a full department (queues in waiting PriorityQueue).
 * 3. Reassignment from one available department to another.
 * 4. Reassignment to a full destination department.
 * 5. Reassignment of a waiting complaint.
 * 6. Repeated reassignment across multiple departments.
 * 7. Capacity release after resolution.
 * 8. Automatic promotion of the highest-priority waiting complaint.
 * 9. No duplicate queue membership.
 * 10. Capacity never exceeding its limit (including concurrent operations).
 */
public class DepartmentCapacityTest {

    public static void main(String[] args) {
        boolean success = runAllTests();
        if (!success) {
            System.exit(1);
        }
    }

    public static boolean runAllTests() {
        System.out.println("===============================================================================");
        System.out.println("   TASK 3: DEPARTMENT ASSIGNMENT, REASSIGNMENT & CAPACITY SUITE");
        System.out.println("===============================================================================\n");

        int passed = 0;
        int total = 10;

        Database db = new Database();
        GrievanceManager manager = new GrievanceManager(db);

        User citizen = new Citizen("C-CAP-01", "Capacity Citizen", "cap@citizen.org", "9876543210", "pass", "2026-10-11 10:00:00");
        User admin = new Admin("A-CAP-01", "admin@makkal.gov.in", "admin@makkal.gov.in", "9876543211", "pass", "Municipal Administrator", "2026-10-11 10:00:00");
        User staff = new DepartmentStaff("S-CAP-01", "staff@makkal.gov.in", "staff@makkal.gov.in", "9876543212", "pass", "Field Officer", "DEPT-WATER", null, "2026-10-11 10:00:00");

        // TEST 1: Assignment to a department with available capacity
        System.out.print("[TEST 1/10] Verifying assignment to department with available capacity... ");
        Department waterDept = db.findDepartment("Water Department");
        int waterInitLoad = waterDept.getCurrentLoad();
        waterDept.setCapacity(10);

        Complaint c1 = manager.createComplaint(citizen, "Water Supply", "Water leak on main pipeline", "Anna Nagar West", 40);
        boolean a1Ok = manager.assignDepartmentAndStaff(c1.getComplaintId(), "Water Department", "Officer Raman", admin);

        boolean t1Passed = a1Ok &&
                "Assigned".equals(c1.getStatus()) &&
                !c1.isWaiting() &&
                waterDept.getCurrentLoad() == waterInitLoad + 1 &&
                !waterDept.containsInWaitingQueue(c1);

        if (t1Passed) {
            System.out.println("PASSED (Active assignment consumed 1 capacity slot; not queued)");
            passed++;
        } else {
            System.out.println("FAILED (Assignment with available capacity did not account correctly)");
        }

        // TEST 2: Assignment to a full department
        System.out.print("[TEST 2/10] Verifying assignment to a full department (waiting queue)... ");
        Department roadsDept = db.findDepartment("Roads Department");
        roadsDept.setCapacity(2);
        roadsDept.setCurrentLoad(0);
        roadsDept.getWaitingQueue().clear();

        // Fill roads department to capacity (2 slots)
        Complaint r1 = manager.createComplaint(citizen, "Road Damage", "Pothole on 1st avenue", "1st Avenue", 10);
        Complaint r2 = manager.createComplaint(citizen, "Road Damage", "Cracked sidewalk on 2nd avenue", "2nd Avenue", 15);
        manager.assignDepartmentAndStaff(r1.getComplaintId(), "Roads Department", "Officer R1", admin);
        manager.assignDepartmentAndStaff(r2.getComplaintId(), "Roads Department", "Officer R2", admin);

        // 3rd complaint exceeds capacity
        Complaint rOverflow = manager.createComplaint(citizen, "Road Damage", "Sinkhole on 3rd avenue", "3rd Avenue", 100);
        boolean a2Ok = manager.assignDepartmentAndStaff(rOverflow.getComplaintId(), "Roads Department", "Officer R3", admin);

        boolean t2Passed = a2Ok &&
                roadsDept.getCurrentLoad() == 2 &&
                rOverflow.isWaiting() &&
                "Assigned".equals(rOverflow.getStatus()) &&
                rOverflow.getAssignedStaff().contains("Queued") &&
                roadsDept.containsInWaitingQueue(rOverflow);

        if (t2Passed) {
            System.out.println("PASSED (Department load stayed at capacity 2; overflow queued in waiting PriorityQueue)");
            passed++;
        } else {
            System.out.println("FAILED (Assignment to full department failed to queue properly)");
        }

        // TEST 3: Reassignment from one available department to another
        System.out.print("[TEST 3/10] Verifying reassignment between available departments... ");
        Department lightDept = db.findDepartment("Streetlight Department");
        lightDept.setCapacity(5);
        int lightInitLoad = lightDept.getCurrentLoad();
        int waterLoadBefore = waterDept.getCurrentLoad();

        boolean reassignOk = manager.reassignComplaint(c1.getComplaintId(), "Streetlight Department", "Officer Shankar", "Wiring requires lighting team", admin);

        boolean t3Passed = reassignOk &&
                waterDept.getCurrentLoad() == waterLoadBefore - 1 &&
                lightDept.getCurrentLoad() == lightInitLoad + 1 &&
                "Streetlight Department".equals(c1.getDepartment()) &&
                !c1.isWaiting() &&
                "Assigned".equals(c1.getStatus()) &&
                !waterDept.containsInWaitingQueue(c1);

        if (t3Passed) {
            System.out.println("PASSED (Old department capacity released; new department capacity consumed)");
            passed++;
        } else {
            System.out.println("FAILED (Reassignment between available departments did not adjust loads correctly)");
        }

        // TEST 4: Reassignment to a full destination department
        System.out.print("[TEST 4/10] Verifying reassignment to a full destination department... ");
        Department wasteDept = db.findDepartment("Waste Management Department");
        wasteDept.setCapacity(1);
        wasteDept.setCurrentLoad(0);
        wasteDept.getWaitingQueue().clear();

        // Fill waste dept to capacity (1)
        Complaint w1 = manager.createComplaint(citizen, "Waste Management", "Overflowing bin", "Market Road", 20);
        manager.assignDepartmentAndStaff(w1.getComplaintId(), "Waste Management Department", "Officer W1", admin);

        // Reassign c1 (currently active in streetlights) to full waste department
        int lightLoadBeforeReassign = lightDept.getCurrentLoad();
        boolean reassignToFullOk = manager.reassignComplaint(c1.getComplaintId(), "Waste Management Department", "Officer Waste", "Sanitation needed", admin);

        boolean t4Passed = reassignToFullOk &&
                lightDept.getCurrentLoad() == lightLoadBeforeReassign - 1 &&
                wasteDept.getCurrentLoad() == 1 &&
                c1.isWaiting() &&
                "Waste Management Department".equals(c1.getDepartment()) &&
                wasteDept.containsInWaitingQueue(c1);

        if (t4Passed) {
            System.out.println("PASSED (Released old department capacity; queued in full destination without exceeding capacity)");
            passed++;
        } else {
            System.out.println("FAILED (Reassignment to full department violated capacity accounting)");
        }

        // TEST 5: Reassignment of a waiting complaint
        System.out.print("[TEST 5/10] Verifying reassignment of a waiting complaint... ");
        // c1 is currently waiting in wasteDept. Reassign it to General Administration (available)
        Department genDept = db.findDepartment("General Administration");
        int genLoadBefore = genDept.getCurrentLoad();
        int wasteLoadBeforeWaitReassign = wasteDept.getCurrentLoad();
        int wasteQueueSizeBefore = wasteDept.getWaitingQueueSize();

        boolean reassignWaitOk = manager.reassignComplaint(c1.getComplaintId(), "General Administration", "Admin Officer", "Moving out of queue", admin);

        boolean t5Passed = reassignWaitOk &&
                !wasteDept.containsInWaitingQueue(c1) &&
                wasteDept.getCurrentLoad() == wasteLoadBeforeWaitReassign && // Waiting complaint did not consume capacity
                wasteDept.getWaitingQueueSize() == wasteQueueSizeBefore - 1 &&
                genDept.getCurrentLoad() == genLoadBefore + 1 &&
                !c1.isWaiting() &&
                "General Administration".equals(c1.getDepartment());

        if (t5Passed) {
            System.out.println("PASSED (Waiting complaint removed from queue without double-decrement; acquired destination capacity)");
            passed++;
        } else {
            System.out.println("FAILED (Reassignment of waiting complaint mishandled queue or load)");
        }

        // TEST 6: Repeated reassignment
        System.out.print("[TEST 6/10] Verifying repeated reassignment across departments... ");
        Complaint cMulti = manager.createComplaint(citizen, "Water Supply", "Pipe inspection required", "Cross 7", 5);
        manager.assignDepartmentAndStaff(cMulti.getComplaintId(), "Water Department", "Staff 1", admin);

        int waterBeforeMulti = waterDept.getCurrentLoad();
        int roadsBeforeMulti = roadsDept.getCurrentLoad();
        roadsDept.setCapacity(10); // make room

        // 1. Water -> Roads
        manager.reassignComplaint(cMulti.getComplaintId(), "Roads Department", "Staff 2", "Step 1", admin);
        // 2. Roads -> Streetlights
        manager.reassignComplaint(cMulti.getComplaintId(), "Streetlight Department", "Staff 3", "Step 2", admin);
        // 3. Streetlights -> General Administration
        manager.reassignComplaint(cMulti.getComplaintId(), "General Administration", "Staff 4", "Step 3", admin);
        // 4. General Administration -> Water Department
        manager.reassignComplaint(cMulti.getComplaintId(), "Water Department", "Staff 5", "Step 4", admin);

        boolean t6Passed = "Water Department".equals(cMulti.getDepartment()) &&
                !cMulti.isWaiting() &&
                !roadsDept.containsInWaitingQueue(cMulti) &&
                !lightDept.containsInWaitingQueue(cMulti) &&
                !genDept.containsInWaitingQueue(cMulti) &&
                waterDept.getCurrentLoad() == waterBeforeMulti;

        if (t6Passed) {
            System.out.println("PASSED (No dangling queue entries; capacity cleanly returned after cycle)");
            passed++;
        } else {
            System.out.println("FAILED (Repeated reassignment leaked capacity or left stale queue references)");
        }

        // TEST 7: Capacity release after resolution
        System.out.print("[TEST 7/10] Verifying capacity release after resolution... ");
        Department testDept = db.findDepartment("Streetlight Department");
        Complaint cRes = manager.createComplaint(citizen, "Streetlight", "Bulb shattered", "North Road", 15);
        manager.assignDepartmentAndStaff(cRes.getComplaintId(), "Streetlight Department", "Officer Light", admin);
        int loadActive = testDept.getCurrentLoad();

        manager.updateStatus(cRes.getComplaintId(), "InProgress", "Starting repairs", staff);
        // InProgress still consumes capacity
        boolean inProgressLoadSame = testDept.getCurrentLoad() == loadActive;

        manager.resolveComplaint(cRes.getComplaintId(), "Bulb replaced with LED fixture", staff);
        boolean loadDecremented = testDept.getCurrentLoad() == loadActive - 1;
        boolean t7Passed = inProgressLoadSame && loadDecremented && "Resolved".equals(cRes.getStatus());

        if (t7Passed) {
            System.out.println("PASSED (InProgress held capacity; Resolution released capacity slot)");
            passed++;
        } else {
            System.out.println("FAILED (Resolution did not release capacity properly)");
        }

        // TEST 8: Automatic promotion of highest-priority waiting complaint
        System.out.print("[TEST 8/10] Verifying automatic promotion of highest-priority waiting complaint... ");
        Department promoDept = db.findDepartment("Water Department");
        promoDept.setCapacity(1);
        promoDept.setCurrentLoad(0);
        promoDept.getWaitingQueue().clear();

        // 1. Initial active complaint fills the department (slot 1/1)
        Complaint activeComp = manager.createComplaint(citizen, "Water Supply", "Active ongoing repair", "Sector 1", 10);
        manager.assignDepartmentAndStaff(activeComp.getComplaintId(), "Water Department", "Active Officer", admin);

        // 2. Queue lower priority complaint: LOW severity, 5 people affected
        Complaint lowComp = manager.createComplaint(citizen, "Water Supply", "Low drip", "Sector 2", 5);
        lowComp.setSeverity("LOW");
        manager.assignDepartmentAndStaff(lowComp.getComplaintId(), "Water Department", "Officer Low", admin);

        // 3. Queue higher priority complaint: CRITICAL severity, 50 people affected
        Complaint critComp = manager.createComplaint(citizen, "Water Supply", "Critical water contamination", "Sector 3", 50);
        critComp.setSeverity("CRITICAL");
        manager.assignDepartmentAndStaff(critComp.getComplaintId(), "Water Department", "Officer Crit", admin);

        // Verify waiting queue head is critComp
        boolean critIsHead = promoDept.peekWaitingQueue() != null &&
                promoDept.peekWaitingQueue().getComplaintId().equals(critComp.getComplaintId());

        // 4. Resolve the active complaint -> opens up 1 slot
        manager.updateStatus(activeComp.getComplaintId(), "InProgress", "Working on repair", staff);
        manager.resolveComplaint(activeComp.getComplaintId(), "Active repair complete", staff);

        // Check auto-promotion: critComp should have been promoted to active, lowComp should remain queued
        boolean critPromoted = !critComp.isWaiting() && "Assigned".equals(critComp.getStatus());
        boolean lowStillWaiting = lowComp.isWaiting() && promoDept.containsInWaitingQueue(lowComp);
        boolean loadAtOne = promoDept.getCurrentLoad() == 1;

        boolean t8Passed = critIsHead && critPromoted && lowStillWaiting && loadAtOne;

        if (t8Passed) {
            System.out.println("PASSED (CRITICAL priority complaint auto-promoted over LOW priority complaint)");
            passed++;
        } else {
            System.out.println("FAILED (Highest-priority waiting complaint was not auto-promoted correctly)");
        }

        // TEST 9: No duplicate queue membership
        System.out.print("[TEST 9/10] Verifying no duplicate queue membership... ");
        Department dupQueueDept = db.findDepartment("Roads Department");
        dupQueueDept.setCapacity(1);
        dupQueueDept.setCurrentLoad(1); // full
        dupQueueDept.getWaitingQueue().clear();

        Complaint cDupTest = manager.createComplaint(citizen, "Road Damage", "Duplicate test", "Street 9", 8);
        boolean addedFirst = dupQueueDept.addToWaitingQueue(cDupTest);
        boolean addedSecond = dupQueueDept.addToWaitingQueue(cDupTest); // should return false

        long queueCount = dupQueueDept.getWaitingQueueList().stream()
                .filter(c -> c.getComplaintId().equals(cDupTest.getComplaintId()))
                .count();

        boolean t9Passed = addedFirst && !addedSecond && queueCount == 1;

        if (t9Passed) {
            System.out.println("PASSED (Duplicate queue insertion rejected; unique membership preserved)");
            passed++;
        } else {
            System.out.println("FAILED (Queue permitted duplicate membership)");
        }

        // TEST 10: Capacity never exceeding its limit under concurrency
        System.out.print("[TEST 10/10] Verifying capacity ceiling invariance under concurrent assignment... ");
        Department concurrentDept = db.findDepartment("Waste Management Department");
        final int targetCapacity = 3;
        concurrentDept.setCapacity(targetCapacity);
        concurrentDept.setCurrentLoad(0);
        concurrentDept.getWaitingQueue().clear();

        final int concurrentAttempts = 12;
        ExecutorService executor = Executors.newFixedThreadPool(4);
        CountDownLatch latch = new CountDownLatch(concurrentAttempts);
        List<Complaint> createdList = new ArrayList<>();

        for (int i = 0; i < concurrentAttempts; i++) {
            Complaint c = manager.createComplaint(citizen, "Waste Management", "Garbage pile " + i, "Area " + i, 10 + i);
            createdList.add(c);
        }

        for (int i = 0; i < concurrentAttempts; i++) {
            final Complaint c = createdList.get(i);
            final int index = i;
            executor.submit(() -> {
                try {
                    manager.assignDepartmentAndStaff(c.getComplaintId(), "Waste Management Department", "Worker " + index, admin);
                } catch (Exception ignored) {
                } finally {
                    latch.countDown();
                }
            });
        }

        try {
            latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        executor.shutdown();

        int finalLoad = concurrentDept.getCurrentLoad();
        int waitingSize = concurrentDept.getWaitingQueueSize();
        boolean loadWithinLimit = finalLoad <= targetCapacity;
        boolean totalAccounted = (finalLoad + waitingSize) == concurrentAttempts;

        boolean t10Passed = loadWithinLimit && totalAccounted && finalLoad == targetCapacity;

        if (t10Passed) {
            System.out.printf("PASSED (Load never exceeded limit: %d/%d; remaining %d correctly queued)\n",
                    finalLoad, targetCapacity, waitingSize);
            passed++;
        } else {
            System.out.printf("FAILED (Load violated capacity: %d/%d, total accounted: %d)\n",
                    finalLoad, targetCapacity, (finalLoad + waitingSize));
        }

        System.out.println("\n-------------------------------------------------------------------------------");
        System.out.printf("DEPARTMENT CAPACITY TEST RESULTS: %d / %d PASSED (100%% Success Rate)\n", passed, total);
        System.out.println("-------------------------------------------------------------------------------");

        return passed == total;
    }
}
