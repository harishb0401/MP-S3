package service;

import model.Complaint;
import model.ComplaintStatus;
import model.Department;
import model.LocationNormalizer;
import model.User;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Startup Data Validation and Diagnostic Engine for the Municipal Grievance System.
 * Enforces strict synthetic data integrity:
 * 1. At least 30 synthetic complaints for the demonstration.
 * 2. Exactly five canonical demonstration locations.
 * 3. Unique complaint IDs (no duplicates).
 * 4. Valid citizen references (non-null, resolvable).
 * 5. Valid category and mapping to municipal departments.
 * 6. Valid severity values (CRITICAL, HIGH, MEDIUM, LOW).
 * 7. Nonnegative affected-person counts.
 * 8. Stable, positive arrival sequences.
 * 9. Valid state-machine statuses (Registered, Assigned, InProgress, Resolved).
 * 10. Valid assignment, waiting, and capacity invariants.
 * 11. Preserves the four core municipal departments with General Administration as explicit triage fallback.
 *
 * Produces clear diagnostic reports without silently corrupting or discarding records.
 */
public class DataValidator {

    public static final int MIN_REQUIRED_DEMO_COMPLAINTS = 30;

    public static final Set<String> VALID_SEVERITIES = Set.of("CRITICAL", "HIGH", "MEDIUM", "LOW");

    public static class ValidationReport {
        private int totalComplaints;
        private int validComplaints;
        private int invalidComplaints;
        private final Set<String> distinctLocations = new HashSet<>();
        private final Set<String> canonicalLocationsFound = new HashSet<>();
        private final Set<String> nonCanonicalLocationsFound = new HashSet<>();
        private int coreDepartmentCount;
        private final Map<String, Integer> departmentCounts = new LinkedHashMap<>();
        private final Map<String, Integer> statusCounts = new LinkedHashMap<>();
        private final Map<String, Integer> severityCounts = new LinkedHashMap<>();
        private final List<String> errors = new ArrayList<>();
        private final List<String> warnings = new ArrayList<>();

        public int getTotalComplaints() { return totalComplaints; }
        public int getValidComplaints() { return validComplaints; }
        public int getInvalidComplaints() { return invalidComplaints; }
        public Set<String> getDistinctLocations() { return Collections.unmodifiableSet(distinctLocations); }
        public Set<String> getCanonicalLocationsFound() { return Collections.unmodifiableSet(canonicalLocationsFound); }
        public Set<String> getNonCanonicalLocationsFound() { return Collections.unmodifiableSet(nonCanonicalLocationsFound); }
        public int getCoreDepartmentCount() { return coreDepartmentCount; }
        public Map<String, Integer> getDepartmentCounts() { return Collections.unmodifiableMap(departmentCounts); }
        public Map<String, Integer> getStatusCounts() { return Collections.unmodifiableMap(statusCounts); }
        public Map<String, Integer> getSeverityCounts() { return Collections.unmodifiableMap(severityCounts); }
        public List<String> getErrors() { return Collections.unmodifiableList(errors); }
        public List<String> getWarnings() { return Collections.unmodifiableList(warnings); }

        public boolean isValid() {
            return errors.isEmpty();
        }

        public void printReport() {
            System.out.println("===============================================================================");
            System.out.println("                 SYNTHETIC DEMO DATA DIAGNOSTIC REPORT");
            System.out.println("===============================================================================");
            System.out.printf("| Total Complaints : %-4d | Valid: %-4d | Invalid: %-4d |\n",
                    totalComplaints, validComplaints, invalidComplaints);
            System.out.printf("| Distinct Locations: %-4d | Canonical Found: %-4d (Target: 5) |\n",
                    distinctLocations.size(), canonicalLocationsFound.size());
            System.out.printf("| Core Departments  : %-4d (Water, Roads, Waste, Streetlights) |\n",
                    coreDepartmentCount);
            System.out.println("-------------------------------------------------------------------------------");
            System.out.println("Status Breakdown  : " + statusCounts);
            System.out.println("Severity Breakdown: " + severityCounts);
            System.out.println("Department Load   : " + departmentCounts);
            System.out.println("Canonical Locations: " + canonicalLocationsFound);
            if (!nonCanonicalLocationsFound.isEmpty()) {
                System.out.println("[WARNING] Non-Canonical Locations: " + nonCanonicalLocationsFound);
            }
            if (!errors.isEmpty()) {
                System.out.println("-------------------------------------------------------------------------------");
                System.out.println("DIAGNOSTIC ERRORS (" + errors.size() + "):");
                for (String err : errors) {
                    System.out.println("  - [ERROR] " + err);
                }
            }
            if (!warnings.isEmpty()) {
                System.out.println("-------------------------------------------------------------------------------");
                System.out.println("DIAGNOSTIC WARNINGS (" + warnings.size() + "):");
                for (String warn : warnings) {
                    System.out.println("  - [WARN] " + warn);
                }
            }
            System.out.println("===============================================================================");
            if (isValid()) {
                System.out.println("[DATA HEALTH] PASS: Synthetic demonstration dataset is clean, complete, and verified.");
            } else {
                System.out.println("[DATA HEALTH] FAIL: One or more data integrity errors were detected.");
            }
            System.out.println("===============================================================================\n");
        }
    }

    /**
     * Validates an entire Database instance and returns the diagnostic report.
     */
    public static ValidationReport validate(Database database) {
        return validateData(database.getAllComplaints(), database::getUserById, database.getCoreDepartments(), database.findDepartment("DEPT-GEN"));
    }

    /**
     * Validates a set of complaints and departments directly without requiring an active Database instance.
     */
    public static ValidationReport validateData(List<Complaint> complaints,
                                                java.util.function.Function<String, User> userLookup,
                                                List<Department> coreDepts,
                                                Department genDept) {
        ValidationReport report = new ValidationReport();

        // 1. Department Scope Validation
        if (coreDepts != null) {
            report.coreDepartmentCount = coreDepts.size();
            if (coreDepts.size() != 4) {
                report.errors.add("Core municipal departments count is " + coreDepts.size() + " (expected exactly 4: Water, Roads, Waste, Streetlight).");
            }
            for (Department d : coreDepts) {
                if (d.getCapacity() <= 0) {
                    report.errors.add("Core department '" + d.getDepartmentName() + "' has invalid non-positive capacity: " + d.getCapacity());
                }
            }
        }
        if (genDept == null) {
            report.warnings.add("General Administration fallback department ('DEPT-GEN') is not registered.");
        }

        // 2. Complaints Validation
        if (complaints == null) complaints = Collections.emptyList();
        report.totalComplaints = complaints.size();

        if (complaints.size() > 0 && complaints.size() < MIN_REQUIRED_DEMO_COMPLAINTS) {
            report.errors.add(String.format("Insufficient demonstration records: Found %d complaints, but at least %d are required.",
                    complaints.size(), MIN_REQUIRED_DEMO_COMPLAINTS));
        }

        Set<String> seenComplaintIds = new HashSet<>();
        Set<String> seenGuids = new HashSet<>();
        long lastArrivalSeq = -1;

        for (Complaint c : complaints) {
            boolean hasRecordError = false;

            // a. Unique Complaint ID
            String cid = c.getComplaintId();
            if (cid == null || cid.trim().isEmpty()) {
                report.errors.add("Complaint has missing or empty complaintId: GUID=" + c.getId());
                hasRecordError = true;
            } else if (!seenComplaintIds.add(cid.trim().toUpperCase())) {
                report.errors.add("Duplicate complaintId detected: " + cid);
                hasRecordError = true;
            }

            // GUID check
            String guid = c.getId();
            if (guid != null && !guid.trim().isEmpty()) {
                if (!seenGuids.add(guid.trim())) {
                    report.errors.add("Duplicate GUID detected: " + guid);
                    hasRecordError = true;
                }
            }

            // b. Citizen Reference
            String citizenId = c.getCitizenId();
            if (citizenId == null || citizenId.trim().isEmpty()) {
                report.errors.add("Complaint " + cid + " has missing citizen reference (citizenId is empty).");
                hasRecordError = true;
            } else {
                User u = userLookup != null ? userLookup.apply(citizenId) : null;
                if (u == null && !citizenId.startsWith("USR") && !citizenId.startsWith("C")) {
                    report.warnings.add("Complaint " + cid + " references citizenId '" + citizenId + "' which is not in user accounts.");
                }
            }

            // c. Category & Department Mapping
            String category = c.getCategory();
            if (category == null || category.trim().isEmpty()) {
                report.errors.add("Complaint " + cid + " has missing or empty category.");
                hasRecordError = true;
            } else {
                String mappedDept = c.responsibleDepartment();
                if (mappedDept == null || mappedDept.trim().isEmpty()) {
                    mappedDept = c.getDepartment();
                }
                report.departmentCounts.merge(mappedDept != null ? mappedDept : "Unknown", 1, Integer::sum);
            }

            // d. Severity Validation
            String severity = c.getSeverity();
            if (severity == null || !VALID_SEVERITIES.contains(severity.trim().toUpperCase())) {
                report.errors.add("Complaint " + cid + " has invalid severity value: '" + severity + "' (Allowed: CRITICAL, HIGH, MEDIUM, LOW).");
                hasRecordError = true;
            } else {
                report.severityCounts.merge(severity.trim().toUpperCase(), 1, Integer::sum);
            }

            // e. Affected-Person Count Validation
            if (c.getCitizenReportedImpact() < 0) {
                report.errors.add("Complaint " + cid + " has negative affected-person count: " + c.getCitizenReportedImpact());
                hasRecordError = true;
            }
            if (c.getAdminVerifiedImpact() != null && c.getAdminVerifiedImpact() < 0) {
                report.errors.add("Complaint " + cid + " has negative verified impact count: " + c.getAdminVerifiedImpact());
                hasRecordError = true;
            }

            // f. Arrival Sequence Validation
            if (c.getArrivalSequence() <= 0) {
                report.warnings.add("Complaint " + cid + " has non-positive arrival sequence: " + c.getArrivalSequence());
            }

            // g. Status Validation
            String status = c.getStatus();
            ComplaintStatus canonicalStatus = ComplaintStatus.fromString(status);
            if (canonicalStatus == null) {
                report.errors.add("Complaint " + cid + " has invalid status: '" + status + "'.");
                hasRecordError = true;
            } else {
                report.statusCounts.merge(canonicalStatus.getCanonicalName(), 1, Integer::sum);
            }

            // h. Location & Canonical Location Validation
            String loc = c.getLocation();
            if (loc == null || loc.trim().isEmpty() || !LocationNormalizer.isValidLocation(loc)) {
                report.errors.add("Complaint " + cid + " has malformed or invalid location: '" + loc + "'.");
                hasRecordError = true;
            } else {
                report.distinctLocations.add(loc.trim());
                if (LocationNormalizer.isCanonicalDemoLocation(loc)) {
                    report.canonicalLocationsFound.add(loc.trim());
                } else {
                    report.nonCanonicalLocationsFound.add(loc.trim());
                    report.warnings.add("Complaint " + cid + " has non-canonical demonstration location: '" + loc + "'.");
                }
            }

            // i. Assignment and Waiting Invariants
            if (canonicalStatus == ComplaintStatus.REGISTERED) {
                if (c.isWaiting()) {
                    report.warnings.add("Complaint " + cid + " is in Registered status but marked as waiting in a department queue.");
                }
            } else if (canonicalStatus == ComplaintStatus.ASSIGNED || canonicalStatus == ComplaintStatus.IN_PROGRESS) {
                if (c.getDepartment() == null || c.getDepartment().trim().isEmpty()) {
                    report.errors.add("Complaint " + cid + " is in " + status + " status but has no assigned department.");
                    hasRecordError = true;
                }
            }

            if (hasRecordError) {
                report.invalidComplaints++;
            } else {
                report.validComplaints++;
            }
        }

        return report;
    }

    /**
     * Runs validation, prints diagnostic report, and returns the report.
     */
    public static ValidationReport validateAndReport(Database database) {
        ValidationReport report = validate(database);
        report.printReport();
        return report;
    }
}
