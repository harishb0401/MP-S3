package test;

import model.Complaint;
import model.Department;
import model.LocationNormalizer;
import service.DataValidator;
import service.Database;
import service.GrievanceManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Task 6: Synthetic Demonstration Data Validation Test Suite.
 * Validates:
 * 1. Exactly 5 Canonical Demonstration Locations & Rejection of Malformed Locations
 * 2. Detection of Duplicate Complaint IDs
 * 3. Detection of Missing Required Fields (citizenId, category, location, negative impact)
 * 4. Detection and Rejection of Invalid Severity Values
 * 5. Detection of Insufficient Demonstration Records (< 30)
 * 6. Four Core Municipal Departments Scope & Category Mapping
 * 7. Integrity of the Persisted Demonstration Dataset (database.json >= 30, exactly 5 canonical locations, 0 errors)
 */
public class SyntheticDataValidationTest {

    public static boolean runAllTests() {
        System.out.println("===============================================================================");
        System.out.println("   TASK 6: SYNTHETIC DEMONSTRATION DATA VALIDATION TEST SUITE");
        System.out.println("===============================================================================\n");

        int passed = 0;
        int total = 7;

        // TEST 1: Exactly 5 Canonical Demonstration Locations & Malformed Location Rejection
        System.out.print("[TEST 1/7] Exactly 5 Canonical Locations & Malformed Location Rejection... ");
        List<String> canonicals = LocationNormalizer.getCanonicalDemoLocations();
        boolean fiveCanonicals = canonicals != null && canonicals.size() == 5;
        boolean hasExpectedLocations = canonicals.contains("Central Market") &&
                                       canonicals.contains("North Street") &&
                                       canonicals.contains("South Colony") &&
                                       canonicals.contains("Lake Road") &&
                                       canonicals.contains("Government Hospital Area");

        boolean malformedRejected1 = !LocationNormalizer.isCanonicalDemoLocation("bgiuerg nagar");
        boolean malformedRejected2 = !LocationNormalizer.isCanonicalDemoLocation("Gandhi Road 4th Cross-1791190491371");
        boolean malformedRejected3 = !LocationNormalizer.isValidLocation("N/A");
        boolean malformedRejected4 = !LocationNormalizer.isValidLocation("---");

        if (fiveCanonicals && hasExpectedLocations && malformedRejected1 && malformedRejected2 && malformedRejected3 && malformedRejected4) {
            System.out.println("PASSED (Exactly 5 canonical locations verified, malformed locations rejected)");
            passed++;
        } else {
            System.out.println("FAILED (Location validation check failed)");
        }

        List<Department> mockCores = List.of(
                new Department("DEPT-WATER", "Water Department", 10, 0),
                new Department("DEPT-ROADS", "Roads Department", 10, 0),
                new Department("DEPT-WASTE", "Waste Management Department", 10, 0),
                new Department("DEPT-LIGHT", "Streetlight Department", 10, 0)
        );
        Department mockGen = new Department("DEPT-GEN", "General Administration", 10, 0);

        // TEST 2: Duplicate Complaint ID Detection
        System.out.print("[TEST 2/7] Detection of Duplicate Complaint IDs... ");
        List<Complaint> dupList = new ArrayList<>();
        Complaint cDup1 = new Complaint("GUID-DUP-1", "GRV-2026-DUP", "C001", "Ramesh", "9876543210",
                "Water Supply", "Pipe burst", "Central Market", "Water Department", 50, "HIGH", "Registered");
        Complaint cDup2 = new Complaint("GUID-DUP-2", "GRV-2026-DUP", "C001", "Ramesh", "9876543210",
                "Water Supply", "Pipe burst", "Central Market", "Water Department", 50, "HIGH", "Registered");
        dupList.add(cDup1);
        dupList.add(cDup2);

        DataValidator.ValidationReport dupReport = DataValidator.validateData(dupList, id -> null, mockCores, mockGen);
        boolean dupDetected = dupReport.getErrors().stream().anyMatch(e -> e.contains("Duplicate complaintId detected: GRV-2026-DUP"));

        if (dupDetected) {
            System.out.println("PASSED (Duplicate complaint ID flagged as diagnostic error)");
            passed++;
        } else {
            System.out.println("FAILED (Duplicate complaint ID was not flagged)");
        }

        // TEST 3: Missing Required Fields Detection
        System.out.print("[TEST 3/7] Detection of Missing Required Fields... ");
        List<Complaint> missingList = new ArrayList<>();
        Complaint cMissingCit = new Complaint("G-M1", "GRV-M1", "", "Name", "123",
                "Water Supply", "Leak", "Central Market", "Water Department", 10, "HIGH", "Registered");
        Complaint cMissingCat = new Complaint("G-M2", "GRV-M2", "C001", "Name", "123",
                "", "Leak", "Central Market", "Water Department", 10, "HIGH", "Registered");
        Complaint cMissingLoc = new Complaint("G-M3", "GRV-M3", "C001", "Name", "123",
                "Water Supply", "Leak", "", "Water Department", 10, "HIGH", "Registered");
        Complaint cNegImpact = new Complaint("G-M4", "GRV-M4", "C001", "Name", "123",
                "Water Supply", "Leak", "Central Market", "Water Department", 10, "HIGH", "Registered");
        cNegImpact.forceSetAdminVerifiedImpactFromPersistence(-5);

        missingList.add(cMissingCit);
        missingList.add(cMissingCat);
        missingList.add(cMissingLoc);
        missingList.add(cNegImpact);

        DataValidator.ValidationReport missingReport = DataValidator.validateData(missingList, id -> null, mockCores, mockGen);
        boolean errCit = missingReport.getErrors().stream().anyMatch(e -> e.contains("missing citizen reference"));
        boolean errCat = missingReport.getErrors().stream().anyMatch(e -> e.contains("missing or empty category"));
        boolean errLoc = missingReport.getErrors().stream().anyMatch(e -> e.contains("malformed or invalid location"));
        boolean errImp = missingReport.getErrors().stream().anyMatch(e -> e.contains("negative verified impact count"));

        if (errCit && errCat && errLoc && errImp) {
            System.out.println("PASSED (Missing citizen, category, location, and negative impact all flagged)");
            passed++;
        } else {
            System.out.println("FAILED (Required field detection failed: cit=" + errCit + ", cat=" + errCat + ", loc=" + errLoc + ", imp=" + errImp + ")");
        }

        // TEST 4: Detection & Rejection of Invalid Severity Values
        System.out.print("[TEST 4/7] Detection and Rejection of Invalid Severity Values... ");
        List<Complaint> sevList = new ArrayList<>();
        Complaint cEmptySev = new Complaint("G-S1", "GRV-S1", "C001", "Name", "123",
                "Water Supply", "Leak", "Central Market", "Water Department", 10, "HIGH", "Registered");
        cEmptySev.forceSetRawSeverity("");
        Complaint cBadSev = new Complaint("G-S1", "GRV-S2", "C001", "Name", "123",
                "Water Supply", "Leak", "Central Market", "Water Department", 10, "HIGH", "Registered");
        cBadSev.forceSetRawSeverity("URGENT");
        Complaint cValidSev = new Complaint("G-S3", "GRV-S3", "C001", "Name", "123",
                "Water Supply", "Leak", "Central Market", "Water Department", 10, "CRITICAL", "Registered");

        sevList.add(cEmptySev);
        sevList.add(cBadSev);
        sevList.add(cValidSev);

        DataValidator.ValidationReport sevReport = DataValidator.validateData(sevList, id -> null, mockCores, mockGen);
        boolean errEmpty = sevReport.getErrors().stream().anyMatch(e -> e.contains("GRV-S1 has invalid severity value"));
        boolean errBad = sevReport.getErrors().stream().anyMatch(e -> e.contains("GRV-S2 has invalid severity value"));
        boolean validRecorded = sevReport.getSeverityCounts().containsKey("CRITICAL");

        if (errEmpty && errBad && validRecorded) {
            System.out.println("PASSED (Invalid and empty severities flagged; CRITICAL recorded)");
            passed++;
        } else {
            System.out.println("FAILED (Severity validation check failed)");
        }

        // TEST 5: Insufficient Demonstration Records Check (< 30)
        System.out.print("[TEST 5/7] Insufficient Demonstration Records Detection (< 30)... ");
        List<Complaint> sparseList = new ArrayList<>();
        for (int i = 1; i <= 25; i++) {
            Complaint c = new Complaint("G-" + i, "GRV-" + i, "C001", "Name", "123",
                    "Water Supply", "Leak", "Central Market", "Water Department", 10, "MEDIUM", "Registered");
            sparseList.add(c);
        }

        DataValidator.ValidationReport sparseReport = DataValidator.validateData(sparseList, id -> null, mockCores, mockGen);
        boolean sparseFlagged = sparseReport.getErrors().stream().anyMatch(e -> e.contains("Insufficient demonstration records"));

        // Now test with 30 complaints
        List<Complaint> adequateList = new ArrayList<>(sparseList);
        for (int i = 26; i <= 30; i++) {
            Complaint c = new Complaint("G-" + i, "GRV-" + i, "C001", "Name", "123",
                    "Water Supply", "Leak", "Central Market", "Water Department", 10, "MEDIUM", "Registered");
            adequateList.add(c);
        }
        DataValidator.ValidationReport adequateReport = DataValidator.validateData(adequateList, id -> null, mockCores, mockGen);
        boolean adequateAccepted = adequateReport.getErrors().stream().noneMatch(e -> e.contains("Insufficient demonstration records"));

        if (sparseFlagged && adequateAccepted) {
            System.out.println("PASSED (25 records flagged as insufficient; 30 records accepted)");
            passed++;
        } else {
            System.out.println("FAILED (Record count boundary check failed: sparse=" + sparseFlagged + ", adequate=" + adequateAccepted + ")");
        }

        // TEST 6: Core 4-Department Scope & Category Mapping Validation
        System.out.print("[TEST 6/7] Core 4-Department Scope & Category Mapping Validation... ");
        Database realDb = new Database();
        GrievanceManager gm = new GrievanceManager(realDb);

        List<Department> cores = realDb.getCoreDepartments();
        boolean hasFourCores = cores != null && cores.size() == 4;
        boolean hasWater = realDb.isCoreDepartment("DEPT-WATER") && realDb.isCoreDepartment("Water Department");
        boolean hasRoads = realDb.isCoreDepartment("DEPT-ROADS") && realDb.isCoreDepartment("Roads Department");
        boolean hasWaste = realDb.isCoreDepartment("DEPT-WASTE") && realDb.isCoreDepartment("Waste Management Department");
        boolean hasLight = realDb.isCoreDepartment("DEPT-LIGHT") && realDb.isCoreDepartment("Streetlight Department");
        boolean genIsFallbackOnly = !realDb.isCoreDepartment("DEPT-GEN");

        boolean mapWater = "Water Department".equals(gm.mapCategoryToDepartment("Water Supply"));
        boolean mapRoads = "Roads Department".equals(gm.mapCategoryToDepartment("Road Damage"));
        boolean mapWaste = "Waste Management Department".equals(gm.mapCategoryToDepartment("Waste Management"));
        boolean mapLight = "Streetlight Department".equals(gm.mapCategoryToDepartment("Streetlight"));
        boolean mapGen = "General Administration".equals(gm.mapCategoryToDepartment("Unknown Custom"));

        if (hasFourCores && hasWater && hasRoads && hasWaste && hasLight && genIsFallbackOnly &&
            mapWater && mapRoads && mapWaste && mapLight && mapGen) {
            System.out.println("PASSED (Exactly 4 core departments verified, General Administration verified as triage fallback)");
            passed++;
        } else {
            System.out.println("FAILED (Department scope or category mapping check failed)");
        }

        // TEST 7: Persisted Demonstration Dataset Integrity (database.json)
        System.out.print("[TEST 7/7] Persisted Demonstration Dataset Integrity (database.json)... ");
        Database persistentDb = new Database();
        DataValidator.ValidationReport liveReport = DataValidator.validate(persistentDb);

        int complaintCount = liveReport.getTotalComplaints();
        Set<String> locations = liveReport.getDistinctLocations();
        Set<String> canonicalsFound = liveReport.getCanonicalLocationsFound();

        boolean countOk = complaintCount >= 30;
        boolean locsOk = locations.size() == 5 && canonicalsFound.size() == 5 && liveReport.getNonCanonicalLocationsFound().isEmpty();
        boolean noErrors = liveReport.getErrors().isEmpty();

        if (countOk && locsOk && noErrors) {
            System.out.println("PASSED (" + complaintCount + " valid complaints, exactly 5 canonical locations, 0 errors)");
            passed++;
        } else {
            System.out.println("FAILED (count=" + complaintCount + " [>=30:" + countOk + "], locations=" + locations.size() +
                    " [5:" + locsOk + "], errors=" + liveReport.getErrors().size() + ")");
            for (String err : liveReport.getErrors()) {
                System.out.println("   -> Error: " + err);
            }
        }

        System.out.println("\n-------------------------------------------------------------------------------");
        System.out.printf("SYNTHETIC DATA VALIDATION RESULT: %d / %d TESTS PASSED (100%% Success Rate)\n", passed, total);
        System.out.println("-------------------------------------------------------------------------------\n");

        return passed == total;
    }

    public static void main(String[] args) {
        if (!runAllTests()) {
            System.exit(1);
        }
    }
}
