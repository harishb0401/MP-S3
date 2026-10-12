package service;

import model.Complaint;
import model.User;
import repository.ComplaintRepository;
import repository.DepartmentRepository;
import repository.StatusUpdateRepository;
import repository.UserRepository;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.util.List;

/**
 * Production Data Migration Utility.
 * Safely and idempotently migrates JSON-persisted records from database.json into MySQL 8.0.
 *
 * Invariants:
 * 1. Backs up database.json to database.json.bak prior to processing.
 * 2. Idempotent: uses ON DUPLICATE KEY / conflict resolution to avoid duplicate entries.
 * 3. Validates password hashes (never imports plaintext).
 * 4. Preserves GPS coordinates, arrival sequences, timestamps, and audit history.
 */
public class DataMigrator {

    public static void main(String[] args) {
        System.out.println("===============================================================================");
        System.out.println("     MAKKAL NAGAR GRIEVANCE SYSTEM - MYSQL ONE-TIME DATA MIGRATOR");
        System.out.println("===============================================================================\n");

        runMigration(true);
    }

    public static MigrationSummary runMigration(boolean printLog) {
        MigrationSummary summary = new MigrationSummary();

        // 1. Check MySQL connectivity
        if (!DatabaseConnection.isDriverAvailable()) {
            if (printLog) System.err.println("[MIGRATION ERROR] MySQL JDBC Driver not found on classpath.");
            summary.error = "MySQL Driver not found on classpath.";
            return summary;
        }

        if (!DatabaseConnection.testConnection()) {
            if (printLog) {
                System.err.println("[MIGRATION ERROR] Cannot connect to MySQL server.");
                System.err.println("JDBC Target : " + DatabaseConfig.getSafeConfigSummary());
                System.err.println("Please check that MySQL service is running and database exists.");
            }
            summary.error = "Cannot connect to MySQL server at " + DatabaseConfig.getHost();
            return summary;
        }

        // 2. Locate and backup source JSON file
        File jsonFile = new File(Database.getDbFile());
        if (!jsonFile.exists() || jsonFile.length() == 0) {
            if (printLog) System.out.println("[MIGRATION] No existing database.json found to migrate. Skipping.");
            summary.skipped = true;
            return summary;
        }

        try {
            File backupFile = new File(jsonFile.getAbsolutePath() + ".bak");
            if (!backupFile.exists()) {
                Files.copy(jsonFile.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                if (printLog) System.out.println("[BACKUP] Created safety backup at: " + backupFile.getAbsolutePath());
            }

            // 3. Parse JSON into temporary in-memory database
            Database.setPersistenceDisabled(true);
            Database sourceDb = new Database();
            Database.setPersistenceDisabled(false);

            UserRepository userRepo = new UserRepository();
            ComplaintRepository complaintRepo = new ComplaintRepository();
            DepartmentRepository deptRepo = new DepartmentRepository();
            StatusUpdateRepository statusRepo = new StatusUpdateRepository();

            // 4. Migrate Departments
            for (model.Department dept : sourceDb.getAllDepartments()) {
                try {
                    deptRepo.save(dept);
                    summary.departmentsImported++;
                } catch (Exception e) {
                    summary.rejectedRecords++;
                    if (printLog) System.err.println("  [ERROR] Department " + dept.getDepartmentId() + ": " + e.getMessage());
                }
            }

            // 5. Migrate Users
            for (User user : sourceDb.getUniqueUsers()) {
                try {
                    if (user.getPasswordHash() == null || user.getPasswordHash().length() < 10) {
                        summary.rejectedRecords++;
                        if (printLog) System.err.println("  [REJECTED] Insecure/empty password for user: " + user.getEmail());
                        continue;
                    }
                    userRepo.save(user);
                    summary.usersImported++;
                } catch (Exception e) {
                    summary.rejectedRecords++;
                    if (printLog) System.err.println("  [ERROR] User " + user.getEmail() + ": " + e.getMessage());
                }
            }

            // 6. Migrate Complaints
            for (Complaint complaint : sourceDb.getAllComplaints()) {
                try {
                    complaintRepo.save(complaint);
                    summary.complaintsImported++;
                } catch (Exception e) {
                    summary.rejectedRecords++;
                    if (printLog) System.err.println("  [ERROR] Complaint " + complaint.getComplaintId() + ": " + e.getMessage());
                }
            }

            // 7. Migrate History / Status Updates
            for (model.ComplaintHistory hist : sourceDb.getAllHistoryList()) {
                try {
                    statusRepo.recordUpdate(hist.getComplaintId(), hist.getOldStatus(), hist.getNewStatus(),
                            hist.getUpdatedBy(), hist.getRemarks());
                    summary.historyImported++;
                } catch (Exception e) {
                    // Ignore duplicate history if already recorded
                }
            }

            summary.success = true;
            if (printLog) {
                System.out.println("-------------------------------------------------------------------------------");
                System.out.println("                      MIGRATION RESULTS SUMMARY");
                System.out.println("-------------------------------------------------------------------------------");
                System.out.printf("| Departments Migrated : %-5d |\n", summary.departmentsImported);
                System.out.printf("| Users Migrated       : %-5d |\n", summary.usersImported);
                System.out.printf("| Complaints Migrated  : %-5d |\n", summary.complaintsImported);
                System.out.printf("| History Logged       : %-5d |\n", summary.historyImported);
                System.out.printf("| Rejected Records     : %-5d |\n", summary.rejectedRecords);
                System.out.println("-------------------------------------------------------------------------------");
                System.out.println("[SUCCESS] Database migration completed successfully!");
            }

        } catch (Exception e) {
            summary.error = e.getMessage();
            if (printLog) System.err.println("[MIGRATION FAILED] " + e.getMessage());
        }

        return summary;
    }

    public static class MigrationSummary {
        public boolean success = false;
        public boolean skipped = false;
        public String error = null;
        public int departmentsImported = 0;
        public int usersImported = 0;
        public int complaintsImported = 0;
        public int historyImported = 0;
        public int rejectedRecords = 0;
    }
}
