package test;

import model.Admin;
import model.Citizen;
import model.Complaint;
import model.Department;
import model.DepartmentQueueManager;
import model.Grievance;
import model.GrievanceFactory;
import model.GrievancePriorityPolicy;
import model.RoadGrievance;
import model.StandardGrievancePriorityPolicy;
import model.User;
import model.WaterGrievance;
import service.Database;
import service.GrievanceManager;

import java.util.ArrayList;
import java.util.List;
import java.util.PriorityQueue;

/**
 * Automated Verification Test Suite for Task 5:
 * Complete OOP Design (Abstraction, Inheritance, Polymorphism, Encapsulation, Composition).
 *
 * Requirements Verified:
 * 1. Base grievance abstraction (Grievance base type with shared fields & responsibleDepartment()).
 * 2. Inheritance & Subclasses (WaterGrievance and RoadGrievance return correct department).
 * 3. Runtime Polymorphism (Base-type references invoke overridden responsibleDepartment()).
 * 4. Priority-Policy Abstraction (GrievancePriorityPolicy controls real PriorityQueue ordering).
 * 5. Encapsulation (Protected fields, immutable arrival sequence, validated state transitions & department assignment).
 * 6. Composition (Department composes DepartmentQueueManager managing waiting and assigned complaints).
 * 7. Factory & Backward-Compatible Mapping (GrievanceFactory creates & adapts concrete subclasses seamlessly).
 */
public class OOPDesignTest {

    public static void main(String[] args) {
        boolean success = runAllTests();
        if (!success) {
            System.exit(1);
        }
    }

    public static boolean runAllTests() {
        System.out.println("===============================================================================");
        System.out.println("   TASK 5: COMPLETE OOP DESIGN VERIFICATION SUITE");
        System.out.println("===============================================================================\n");

        int passed = 0;
        int total = 7;

        // -----------------------------------------------------------------------------------
        // TEST 1: INHERITANCE - WATERGRIEVANCE & ROADGRIEVANCE RETURN CORRECT DEPARTMENTS
        // -----------------------------------------------------------------------------------
        System.out.print("[TEST 1/7] WaterGrievance & RoadGrievance Return Correct Responsible Departments... ");
        WaterGrievance waterG = new WaterGrievance(
                "ID-W-1", "GRV-2026-9001", "CIT-1", "Citizen One", "9876543201",
                "Burst pipeline on 1st cross", "Ward 5", 50, "HIGH", "Registered"
        );
        RoadGrievance roadG = new RoadGrievance(
                "ID-R-1", "GRV-2026-9002", "CIT-2", "Citizen Two", "9876543202",
                "Deep crater pothole", "Ward 12", 20, "HIGH", "Registered"
        );
        Complaint generalG = new Complaint(
                "ID-G-1", "GRV-2026-9003", "CIT-3", "Citizen Three", "9876543203",
                "General Issue", "Administrative question", "Ward 1", "General Administration", 5, "LOW", "Registered"
        );

        boolean waterCorrect = "Water Department".equals(waterG.responsibleDepartment());
        boolean roadCorrect = "Roads Department".equals(roadG.responsibleDepartment());
        boolean generalCorrect = "General Administration".equals(generalG.responsibleDepartment());

        if (waterCorrect && roadCorrect && generalCorrect) {
            System.out.println("PASSED (Water -> 'Water Department', Road -> 'Roads Department')");
            passed++;
        } else {
            System.out.printf("FAILED (water=%b, road=%b, general=%b)\n", waterCorrect, roadCorrect, generalCorrect);
        }

        // -----------------------------------------------------------------------------------
        // TEST 2: RUNTIME POLYMORPHISM VIA BASE-TYPE GRIEVANCE REFERENCES
        // -----------------------------------------------------------------------------------
        System.out.print("[TEST 2/7] Runtime Polymorphism via Base Grievance Abstraction References... ");
        List<Grievance> grievances = new ArrayList<>();
        grievances.add(waterG);
        grievances.add(roadG);
        grievances.add(generalG);

        List<String> polymorphicResults = new ArrayList<>();
        for (Grievance g : grievances) {
            // Polymorphic dispatch through base-type Grievance reference
            polymorphicResults.add(g.responsibleDepartment());
        }

        boolean polyWater = "Water Department".equals(polymorphicResults.get(0));
        boolean polyRoad = "Roads Department".equals(polymorphicResults.get(1));
        boolean polyGen = "General Administration".equals(polymorphicResults.get(2));

        if (polyWater && polyRoad && polyGen) {
            System.out.println("PASSED (Base-type references dispatched dynamically to subclass overrides)");
            passed++;
        } else {
            System.out.println("FAILED (Dynamic method dispatch failed)");
        }

        // -----------------------------------------------------------------------------------
        // TEST 3: PRIORITY-POLICY ABSTRACTION CONTROLS REAL PRIORITYQUEUE ORDERING
        // -----------------------------------------------------------------------------------
        System.out.print("[TEST 3/7] GrievancePriorityPolicy Controls Real PriorityQueue Ordering... ");
        GrievancePriorityPolicy policy = new StandardGrievancePriorityPolicy();
        PriorityQueue<Complaint> testQueue = new PriorityQueue<>(policy);

        Complaint lowPrio = new Complaint("C-L", "GRV-LOW", "U1", "Name", "123", "Roads", "Minor crack", "Loc", "Roads", 5, "LOW", "Registered");
        Complaint highPrio = new Complaint("C-H", "GRV-HIGH", "U2", "Name", "123", "Water Supply", "Pipe leak", "Loc", "Water", 10, "HIGH", "Registered");
        Complaint criticalPrio = new Complaint("C-C", "GRV-CRIT", "U3", "Name", "123", "Water Supply", "Major main burst", "Loc", "Water", 2, "CRITICAL", "Registered");

        testQueue.add(lowPrio);
        testQueue.add(criticalPrio);
        testQueue.add(highPrio);

        Complaint poll1 = testQueue.poll();
        Complaint poll2 = testQueue.poll();
        Complaint poll3 = testQueue.poll();

        boolean orderCorrect = (poll1 == criticalPrio) && (poll2 == highPrio) && (poll3 == lowPrio);
        boolean policyNamed = policy.getPolicyName() != null && !policy.getPolicyName().isEmpty();

        if (orderCorrect && policyNamed) {
            System.out.println("PASSED (Queue strictly ordered: CRITICAL -> HIGH -> LOW via GrievancePriorityPolicy)");
            passed++;
        } else {
            System.out.println("FAILED (Queue ordering did not match policy specification)");
        }

        // -----------------------------------------------------------------------------------
        // TEST 4: ENCAPSULATION PREVENTS INVALID STATE CHANGES & OVERWRITES
        // -----------------------------------------------------------------------------------
        System.out.print("[TEST 4/7] Encapsulation Validates State Transitions & Department Assignment... ");
        Complaint testEncaps = new Complaint("C-E", "GRV-E", "U4", "Name", "123", "Roads", "Desc", "Loc", "Roads", 10, "MEDIUM", "Registered");

        // 4a. Invalid direct transition Registered -> InProgress must be rejected
        boolean invalidTransitionBlocked = false;
        try {
            testEncaps.transitionStatus("InProgress", false, false);
        } catch (IllegalStateException e) {
            invalidTransitionBlocked = e.getMessage().contains("Invalid status transition");
        }

        // 4b. Valid transition Registered -> Assigned must succeed
        boolean validAssignedOk = testEncaps.transitionStatus("Assigned", false, false);

        // 4c. Immutability: Attempting to overwrite Citizen Reported Impact must be rejected
        boolean impactOverwriteBlocked = false;
        try {
            testEncaps.setCitizenReportedImpact(999);
        } catch (IllegalStateException e) {
            impactOverwriteBlocked = e.getMessage().contains("immutable");
        }

        // 4d. Transition to Resolved
        testEncaps.transitionStatus("InProgress", false, false);
        testEncaps.transitionStatus("Resolved", false, false);

        // 4e. Encapsulation: Department assignment on resolved complaint must be rejected
        boolean assignOnResolvedBlocked = false;
        try {
            testEncaps.assignDepartment("Water Department", "Officer Suresh");
        } catch (IllegalStateException e) {
            assignOnResolvedBlocked = e.getMessage().contains("resolved");
        }

        if (invalidTransitionBlocked && validAssignedOk && impactOverwriteBlocked && assignOnResolvedBlocked) {
            System.out.println("PASSED (Encapsulation verified: State machine, immutable fields & assignment guarded)");
            passed++;
        } else {
            System.out.printf("FAILED (invTrans=%b, validAssigned=%b, impactBlk=%b, assignBlk=%b)\n",
                    invalidTransitionBlocked, validAssignedOk, impactOverwriteBlocked, assignOnResolvedBlocked);
        }

        // -----------------------------------------------------------------------------------
        // TEST 5: COMPOSITION - DEPARTMENT COMPOSES DEPARTMENTQUEUEMANAGER
        // -----------------------------------------------------------------------------------
        System.out.print("[TEST 5/7] Department Composes DepartmentQueueManager & Invariants... ");
        Department dept = new Department("DEPT-TEST", "Test Sanitation Department", 2, 0);
        DepartmentQueueManager qm = dept.getQueueManager();

        boolean hasQueueManager = (qm != null);
        boolean usesPolicy = dept.getPriorityPolicy() != null;

        Complaint compA = new Complaint("C-A", "GRV-A", "U1", "N1", "123", "Sanitation", "Bin full", "Loc1", "Sanitation", 10, "HIGH", "Registered");
        Complaint compB = new Complaint("C-B", "GRV-B", "U2", "N2", "123", "Sanitation", "Litter", "Loc2", "Sanitation", 10, "CRITICAL", "Registered");

        // Add to waiting queue through composed manager
        dept.addToWaitingQueue(compA);
        dept.addToWaitingQueue(compB);

        // Composed queue should order CRITICAL ahead of HIGH
        Complaint topWaiting = dept.pollWaitingQueue();
        boolean queueComposedCorrectly = (topWaiting == compB) && dept.getWaitingQueueSize() == 1;

        // Active complaints tracking through composition
        dept.addAssignedComplaint(compB);
        boolean tracksAssigned = dept.getAssignedComplaints().contains(compB);
        dept.removeAssignedComplaint(compB);
        boolean removedAssigned = !dept.getAssignedComplaints().contains(compB);

        if (hasQueueManager && usesPolicy && queueComposedCorrectly && tracksAssigned && removedAssigned) {
            System.out.println("PASSED (Department composes DepartmentQueueManager with verified priority ordering)");
            passed++;
        } else {
            System.out.println("FAILED (Department composition failed)");
        }

        // -----------------------------------------------------------------------------------
        // TEST 6: FACTORY CREATES CORRECT POLYMORPHIC SUBCLASSES
        // -----------------------------------------------------------------------------------
        System.out.print("[TEST 6/7] GrievanceFactory Polymorphic Creation & Subclass Adaptation... ");
        Complaint facWater = GrievanceFactory.createGrievance(
                "ID-FW", "GRV-FW", "CIT-W", "Water Citizen", "9876543210",
                "Water Supply", "Pipe burst", "Main road", "Water Department", 50, "HIGH", "Registered"
        );
        Complaint facRoad = GrievanceFactory.createGrievance(
                "ID-FR", "GRV-FR", "CIT-R", "Road Citizen", "9876543211",
                "Roads", "Pothole repair", "Cross road", "Roads Department", 20, "MEDIUM", "Registered"
        );
        Complaint facOther = GrievanceFactory.createGrievance(
                "ID-FO", "GRV-FO", "CIT-O", "General Citizen", "9876543212",
                "Streetlights", "Dark corner", "Park avenue", "Streetlight Department", 10, "LOW", "Registered"
        );

        boolean isWaterInstance = facWater instanceof WaterGrievance;
        boolean isRoadInstance = facRoad instanceof RoadGrievance;
        boolean isOtherStandard = !(facOther instanceof WaterGrievance) && !(facOther instanceof RoadGrievance);

        // Adaptation of legacy complaint instance
        Complaint legacy = new Complaint("ID-LEG", "GRV-LEG", "CIT-L", "Legacy", "123", "Water Supply", "Leak", "Loc", "Water", 10, "MEDIUM", "Registered");
        Complaint adapted = GrievanceFactory.adapt(legacy);
        boolean adaptedIsWater = adapted instanceof WaterGrievance && "Water Department".equals(adapted.responsibleDepartment());

        if (isWaterInstance && isRoadInstance && isOtherStandard && adaptedIsWater) {
            System.out.println("PASSED (Factory correctly instantiates and adapts concrete subclasses)");
            passed++;
        } else {
            System.out.printf("FAILED (waterInst=%b, roadInst=%b, otherStd=%b, adapt=%b)\n",
                    isWaterInstance, isRoadInstance, isOtherStandard, adaptedIsWater);
        }

        // -----------------------------------------------------------------------------------
        // TEST 7: END-TO-END SERVICE INTEGRATION WITH POLYMORPHIC MODELS
        // -----------------------------------------------------------------------------------
        System.out.print("[TEST 7/7] End-to-End GrievanceManager Integration with Subclasses... ");
        Database db = new Database();
        GrievanceManager manager = new GrievanceManager(db);
        User citizenUser = new Citizen("C_OOP_1", "Geetha", "geetha.oop@test.com", "9876543250", "hash", "2026-10-11 10:00:00");
        User adminUser = new Admin("A_OOP_1", "admin.oop@makkal.gov.in", "admin.oop@makkal.gov.in", "9876543200", "hash", "Admin Officer", "2026-10-11 00:00:00");

        Complaint createdWater = manager.createComplaint(citizenUser, "Water Supply", "Valve leakage", "Sector 3", 15);
        Complaint createdRoad = manager.createComplaint(citizenUser, "Road Damage", "Tar broken", "Sector 4", 25);

        boolean mgrWaterSubclass = createdWater instanceof WaterGrievance;
        boolean mgrRoadSubclass = createdRoad instanceof RoadGrievance;
        boolean mgrWaterDept = "Water Department".equals(createdWater.responsibleDepartment());
        boolean mgrRoadDept = "Roads Department".equals(createdRoad.responsibleDepartment());

        // Process through status workflow
        manager.assignDepartmentAndStaff(createdWater.getComplaintId(), "Water Department", "Officer Rajesh", adminUser);
        manager.updateStatus(createdWater.getComplaintId(), "InProgress", "Field team active", adminUser);
        manager.resolveComplaint(createdWater.getComplaintId(), "Leakage fixed");

        boolean flowSuccess = "Resolved".equalsIgnoreCase(createdWater.getStatus());

        if (mgrWaterSubclass && mgrRoadSubclass && mgrWaterDept && mgrRoadDept && flowSuccess) {
            System.out.println("PASSED (Created complaints are typed subclasses, lifecycle executed seamlessly)");
            passed++;
        } else {
            System.out.printf("FAILED (waterSub=%b, roadSub=%b, waterDept=%b, roadDept=%b, flow=%b)\n",
                    mgrWaterSubclass, mgrRoadSubclass, mgrWaterDept, mgrRoadDept, flowSuccess);
        }

        System.out.println("\n-------------------------------------------------------------------------------");
        System.out.printf("OOP DESIGN TEST RESULTS: %d / %d PASSED (%s)\n", passed, total, (passed == total ? "100% Success Rate" : "Failures Present"));
        System.out.println("-------------------------------------------------------------------------------\n");

        return passed == total;
    }
}
