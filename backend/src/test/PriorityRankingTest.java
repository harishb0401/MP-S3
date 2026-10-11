package test;

import model.Complaint;
import model.User;
import model.Citizen;
import model.Admin;
import service.Database;
import service.GrievanceManager;

import java.util.PriorityQueue;

/**
 * Automated Verification Suite for Task 1: Complaint Priority Ranking.
 *
 * Covers all required verification scenarios:
 * 1. Critical versus High severity ranking.
 * 2. Equal severity but different affected-person counts.
 * 3. Equal severity and impact, with earlier arrival winning.
 * 4. Two complaints with identical timestamps (tie-breaking via immutable arrival sequence).
 * 5. Correct ordering after a waiting complaint's priority changes (removal + reinsertion).
 * 6. Correct ordering across all four severity levels (CRITICAL > HIGH > MEDIUM > LOW).
 * 7. Constructor & Registration severity initialization (no silent default to LOW).
 */
public class PriorityRankingTest {

    public static boolean runAllTests() {
        Database.setPersistenceDisabled(true);
        System.out.println("===============================================================================");
        System.out.println("   TASK 1: COMPLAINT PRIORITY RANKING - AUTOMATED VERIFICATION SUITE");
        System.out.println("===============================================================================\n");

        int testsPassed = 0;
        int totalTests = 7;

        if (testCriticalVsHighSeverity()) testsPassed++;
        if (testEqualSeverityDifferentImpactCounts()) testsPassed++;
        if (testEqualSeverityAndImpactEarlierArrivalWins()) testsPassed++;
        if (testIdenticalTimestampsTieBreaker()) testsPassed++;
        if (testWaitingComplaintPriorityChangeReordering()) testsPassed++;
        if (testAllFourSeverityLevelsOrdering()) testsPassed++;
        if (testConstructorAndRegistrationSeverityPreservation()) testsPassed++;

        System.out.println("\n-------------------------------------------------------------------------------");
        System.out.printf("PRIORITY RANKING TEST RESULTS: %d / %d PASSED\n", testsPassed, totalTests);
        System.out.println("-------------------------------------------------------------------------------");

        return testsPassed == totalTests;
    }

    public static void main(String[] args) {
        boolean allPassed = runAllTests();
        if (!allPassed) {
            System.err.println("\n[FAILURE] One or more priority ranking tests failed!");
            System.exit(1);
        } else {
            System.out.println("\n>>> ALL PRIORITY RANKING TESTS PASSED! <<<");
        }
    }

    /**
     * Requirement 9.1: Critical versus High severity.
     * CRITICAL must rank ahead of HIGH regardless of impact count or earlier arrival.
     */
    public static boolean testCriticalVsHighSeverity() {
        System.out.print("[TEST 1/7] Critical vs High severity... ");

        Complaint compHigh = new Complaint("ID-H", "GRV-H", "C001", "Citizen H", "999",
                "Water", "Water leak", "Main Rd", "Water Department", 10000, "HIGH", "Registered");
        compHigh.setCreatedAt("2026-01-01 00:00:00");

        Complaint compCrit = new Complaint("ID-C", "GRV-C", "C001", "Citizen C", "999",
                "Water", "Burst pipe hazard", "Main Rd", "Water Department", 1, "CRITICAL", "Registered");
        compCrit.setCreatedAt("2026-10-10 12:00:00");

        PriorityQueue<Complaint> queue = new PriorityQueue<>(Complaint.PRIORITY_COMPARATOR);
        queue.add(compHigh);
        queue.add(compCrit);

        Complaint first = queue.poll();
        Complaint second = queue.poll();

        if (first == compCrit && second == compHigh) {
            System.out.println("PASSED (CRITICAL ranked ahead of HIGH despite smaller impact & later arrival)");
            return true;
        } else {
            System.out.println("FAILED: Expected CRITICAL first, got " + (first != null ? first.getSeverity() : "null"));
            return false;
        }
    }

    /**
     * Requirement 9.2: Equal severity but different affected-person counts.
     * Higher affected-person count must rank ahead of lower count.
     */
    public static boolean testEqualSeverityDifferentImpactCounts() {
        System.out.print("[TEST 2/7] Equal severity, different affected-person counts... ");

        Complaint compSmall = new Complaint("ID-S", "GRV-S", "C001", "Citizen S", "999",
                "Roads", "Pothole", "Street 1", "Roads Department", 25, "HIGH", "Registered");
        compSmall.setCreatedAt("2026-09-01 10:00:00");

        Complaint compMed = new Complaint("ID-M", "GRV-M", "C001", "Citizen M", "999",
                "Roads", "Pothole cluster", "Street 2", "Roads Department", 100, "HIGH", "Registered");
        compMed.setCreatedAt("2026-09-01 10:30:00");

        Complaint compLarge = new Complaint("ID-L", "GRV-L", "C001", "Citizen L", "999",
                "Roads", "Road cave-in", "Street 3", "Roads Department", 500, "HIGH", "Registered");
        compLarge.setCreatedAt("2026-09-01 11:00:00");

        PriorityQueue<Complaint> queue = new PriorityQueue<>(Complaint.PRIORITY_COMPARATOR);
        queue.add(compSmall);
        queue.add(compLarge);
        queue.add(compMed);

        Complaint r1 = queue.poll();
        Complaint r2 = queue.poll();
        Complaint r3 = queue.poll();

        if (r1 == compLarge && r2 == compMed && r3 == compSmall) {
            System.out.println("PASSED (Impact 500 > 100 > 25 correctly ordered)");
            return true;
        } else {
            System.out.println("FAILED: Ordering by impact failed.");
            return false;
        }
    }

    /**
     * Requirement 9.3: Equal severity and impact, with earlier arrival winning.
     * Earliest timestamp must rank ahead.
     */
    public static boolean testEqualSeverityAndImpactEarlierArrivalWins() {
        System.out.print("[TEST 3/7] Equal severity and impact, earlier arrival wins... ");

        Complaint c1 = new Complaint("ID-1", "GRV-1", "C001", "A", "9",
                "Sanitation", "Trash", "Loc", "Waste", 100, "MEDIUM", "Registered");
        c1.setCreatedAt("2026-09-01 09:00:00");

        Complaint c2 = new Complaint("ID-2", "GRV-2", "C001", "B", "9",
                "Sanitation", "Trash", "Loc", "Waste", 100, "MEDIUM", "Registered");
        c2.setCreatedAt("2026-09-01 09:15:00");

        Complaint c3 = new Complaint("ID-3", "GRV-3", "C001", "C", "9",
                "Sanitation", "Trash", "Loc", "Waste", 100, "MEDIUM", "Registered");
        c3.setCreatedAt("2026-09-01 09:30:00");

        PriorityQueue<Complaint> queue = new PriorityQueue<>(Complaint.PRIORITY_COMPARATOR);
        queue.add(c3);
        queue.add(c1);
        queue.add(c2);

        Complaint r1 = queue.poll();
        Complaint r2 = queue.poll();
        Complaint r3 = queue.poll();

        if (r1 == c1 && r2 == c2 && r3 == c3) {
            System.out.println("PASSED (FIFO order 09:00:00 > 09:15:00 > 09:30:00 preserved)");
            return true;
        } else {
            System.out.println("FAILED: FIFO ordering by arrival timestamp failed.");
            return false;
        }
    }

    /**
     * Requirement 9.4: Two complaints with identical timestamps.
     * Deterministic tie-breaker uses immutable arrival sequence number.
     */
    public static boolean testIdenticalTimestampsTieBreaker() {
        System.out.print("[TEST 4/7] Two complaints with identical timestamps (arrival sequence tie-breaker)... ");

        Complaint cEarly = new Complaint("ID-E", "GRV-E", "C001", "E", "9",
                "Streetlight", "Dark", "Pole 1", "Streetlight", 50, "HIGH", "Registered");
        cEarly.setCreatedAt("2026-09-01 10:00:00");

        Complaint cLate = new Complaint("ID-L", "GRV-L", "C001", "L", "9",
                "Streetlight", "Dark", "Pole 2", "Streetlight", 50, "HIGH", "Registered");
        cLate.setCreatedAt("2026-09-01 10:00:00"); // Identical timestamp string

        // Verify arrivalSequence: cEarly was instantiated before cLate
        boolean sequenceOrdered = cEarly.getArrivalSequence() < cLate.getArrivalSequence();

        PriorityQueue<Complaint> queue = new PriorityQueue<>(Complaint.PRIORITY_COMPARATOR);
        // Add in reverse order
        queue.add(cLate);
        queue.add(cEarly);

        Complaint polled1 = queue.poll();
        Complaint polled2 = queue.poll();

        if (sequenceOrdered && polled1 == cEarly && polled2 == cLate) {
            System.out.println("PASSED (Tie broken deterministically by immutable sequence #" +
                    cEarly.getArrivalSequence() + " vs #" + cLate.getArrivalSequence() + ")");
            return true;
        } else {
            System.out.println("FAILED: Identical timestamp tie-breaking was non-deterministic.");
            return false;
        }
    }

    /**
     * Requirement 9.5: Correct ordering after a waiting complaint's priority changes.
     * When ranking field changes on a waiting complaint, remove and reinsert so queue remains correctly ordered.
     * Resolved complaints are not mutated or reordered.
     */
    public static boolean testWaitingComplaintPriorityChangeReordering() {
        System.out.print("[TEST 5/7] Correct ordering after waiting complaint priority changes... ");

        // 1. Direct PriorityQueue reordering verification
        PriorityQueue<Complaint> queue = new PriorityQueue<>(Complaint.PRIORITY_COMPARATOR);

        Complaint waitingLow = new Complaint("ID-WL", "GRV-WL", "C001", "Low User", "999",
                "General", "Minor issue", "Loc 1", "General Administration", 10, "LOW", "Registered");
        Complaint waitingMed = new Complaint("ID-WM", "GRV-WM", "C001", "Med User", "999",
                "General", "Medium issue", "Loc 2", "General Administration", 10, "MEDIUM", "Registered");

        queue.add(waitingLow);
        queue.add(waitingMed);

        // Initially: waitingMed (MEDIUM) must be head of queue before waitingLow (LOW)
        Complaint initialHead = queue.peek();
        boolean initialHeadCorrect = (initialHead == waitingMed);

        // Mutate ranking field of waitingLow to CRITICAL
        waitingLow.setSeverity("CRITICAL");
        waitingLow.setPriority("CRITICAL");

        // Remove and re-insert into queue as required by Requirement 7
        boolean reinserted = Complaint.reinsertInQueue(queue, waitingLow);

        // After reinsertion, waitingLow (CRITICAL) must be at the head of queue
        Complaint updatedHead = queue.peek();
        boolean reorderCorrect = (updatedHead == waitingLow);

        // 2. Department waitingQueue reordering verification
        model.Department testDept = new model.Department("DEPT-T", "Test Dept", 1, 1); // Capacity 1, Load 1 (at capacity)
        testDept.addToWaitingQueue(waitingMed);
        testDept.addToWaitingQueue(waitingLow);

        // Upgrade waitingLow in department waiting queue
        boolean deptUpdateOk = testDept.updateComplaintPriority(waitingLow);
        Complaint deptTop = testDept.pollWaitingQueue();
        boolean deptHeadCorrect = (deptTop == waitingLow);

        // 3. Resolved complaints protection: must NOT be mutated or reordered
        Complaint resolvedComp = new Complaint("ID-WR", "GRV-WR", "C001", "Res User", "999",
                "General", "Old issue", "Loc 3", "General Administration", 10, "LOW", "Resolved");
        boolean deptResolvedBlocked = !testDept.updateComplaintPriority(resolvedComp);

        Database db = new Database();
        GrievanceManager gm = new GrievanceManager(db);
        User admin = new Admin("A-TEST", "admin@test.gov", "admin@test.gov", "9876543200", "hash", "Commissioner", "2026-09-01 10:00:00");
        db.addComplaint(resolvedComp);
        boolean gmResolvedBlocked = !gm.updateWaitingComplaintPriority(resolvedComp.getComplaintId(), "CRITICAL", 999, admin, "Should fail");

        if (initialHeadCorrect && reinserted && reorderCorrect && deptUpdateOk && deptHeadCorrect && deptResolvedBlocked && gmResolvedBlocked) {
            System.out.println("PASSED (Waiting complaint removed & re-inserted into queue; became queue head; resolved complaint untouched)");
            return true;
        } else {
            System.out.println("FAILED: Queue reordering or resolved complaint guard failed.");
            return false;
        }
    }

    /**
     * Requirement 9.6: Correct behavior for all four severity levels.
     * CRITICAL > HIGH > MEDIUM > LOW
     */
    public static boolean testAllFourSeverityLevelsOrdering() {
        System.out.print("[TEST 6/7] Correct behavior for all four severity levels (CRITICAL > HIGH > MEDIUM > LOW)... ");

        Complaint cLow = new Complaint("ID-4", "GRV-4", "C001", "D", "9", "Cat", "Desc", "Loc", "Dept", 10, "LOW", "Registered");
        cLow.setCreatedAt("2026-09-01 10:00:00");

        Complaint cMed = new Complaint("ID-3", "GRV-3", "C001", "C", "9", "Cat", "Desc", "Loc", "Dept", 10, "MEDIUM", "Registered");
        cMed.setCreatedAt("2026-09-01 10:00:00");

        Complaint cHigh = new Complaint("ID-2", "GRV-2", "C001", "B", "9", "Cat", "Desc", "Loc", "Dept", 10, "HIGH", "Registered");
        cHigh.setCreatedAt("2026-09-01 10:00:00");

        Complaint cCrit = new Complaint("ID-1", "GRV-1", "C001", "A", "9", "Cat", "Desc", "Loc", "Dept", 10, "CRITICAL", "Registered");
        cCrit.setCreatedAt("2026-09-01 10:00:00");

        PriorityQueue<Complaint> queue = new PriorityQueue<>(Complaint.PRIORITY_COMPARATOR);
        // Add in reverse priority order
        queue.add(cLow);
        queue.add(cMed);
        queue.add(cHigh);
        queue.add(cCrit);

        Complaint p1 = queue.poll();
        Complaint p2 = queue.poll();
        Complaint p3 = queue.poll();
        Complaint p4 = queue.poll();

        if (p1 == cCrit && p2 == cHigh && p3 == cMed && p4 == cLow) {
            System.out.println("PASSED (Strict order: CRITICAL -> HIGH -> MEDIUM -> LOW)");
            return true;
        } else {
            System.out.println("FAILED: 4-level severity ordering failed.");
            return false;
        }
    }

    /**
     * Requirement 3: Constructor and registration severity preservation.
     * Verifies that severity is NOT silently defaulted to LOW when supplied.
     */
    public static boolean testConstructorAndRegistrationSeverityPreservation() {
        System.out.print("[TEST 7/7] Constructor and registration severity preservation (not defaulted to LOW)... ");

        // Constructor test with "HIGH" priority
        Complaint comp = new Complaint("ID-T", "GRV-T", "C001", "User", "9",
                "Water", "Burst", "Loc", "Water", 100, "HIGH", "Registered");

        boolean constructorPreserved = "HIGH".equalsIgnoreCase(comp.getSeverity());

        // Constructor test with "CRITICAL" priority
        Complaint compCrit = new Complaint("ID-TC", "GRV-TC", "C001", "User", "9",
                "Water", "Disaster", "Loc", "Water", 100, "CRITICAL", "Registered");

        boolean critPreserved = "CRITICAL".equalsIgnoreCase(compCrit.getSeverity());

        // Registration test via GrievanceManager: starts explicitly unassessed, not defaulted to LOW
        Database.setPersistenceDisabled(true);
        Database db = new Database();
        GrievanceManager gm = new GrievanceManager(db);
        User citizen = new Citizen("C-SEV", "Citizen Sev", "sev@test.gov", "9876543210", "hash", "2026-09-01 10:00:00");
        Complaint regComplaint = gm.createComplaint(citizen, "Roads", "Normal road crack", "Central Market", 10);

        boolean unassessedStartsNull = regComplaint.getSeverity() == null && !regComplaint.isAssessed();

        // Manager deliberate assessment sets severity to HIGH
        User manager = new model.Manager("M001", "manager@makkal.gov.in", "manager@makkal.gov.in", "9876543211", "hash", "Triage Manager", "2026-09-01 00:00:00");
        gm.assessComplaint(regComplaint.getComplaintId(), "HIGH", "Deliberate manager assessment", "Roads", "Officer", manager);
        boolean assessedHighPreserved = "HIGH".equalsIgnoreCase(regComplaint.getSeverity()) && regComplaint.isAssessed();

        if (constructorPreserved && critPreserved && unassessedStartsNull && assessedHighPreserved) {
            System.out.println("PASSED (Severity correctly initialized to supplied value, unassessed complaints not defaulted to LOW, manager assessment verified)");
            return true;
        } else {
            System.out.println("FAILED: Severity was silently overwritten or defaulted to LOW.");
            return false;
        }
    }
}
