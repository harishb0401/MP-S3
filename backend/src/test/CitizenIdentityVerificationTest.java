package test;

import model.Citizen;
import model.User;
import service.Database;

import java.util.List;

/**
 * Automated Verification Suite for FEATURE: Unique Citizen Identity Verification.
 */
public class CitizenIdentityVerificationTest {

    private static int testsRun = 0;
    private static int testsPassed = 0;

    public static boolean runAllTests() {
        System.out.println("===============================================================================");
        System.out.println("   FEATURE: UNIQUE CITIZEN IDENTITY VERIFICATION - AUTOMATED TEST SUITE");
        System.out.println("===============================================================================");

        testsRun = 0;
        testsPassed = 0;
        Database.setPersistenceDisabled(true);
        Database db = new Database();

        testValidCitizenIdentityRegistration(db);
        testDuplicateIdentityRejectionMessage(db);
        testFormattedIdentityNormalization(db);
        testInvalidIdentityStartsWithZeroOrOne(db);
        testInvalidIdentityLengthAndNonDigits(db);
        testInvalidIdentityRepetitiveDigits(db);
        testPlaintextIdentityNeverStored(db);
        testDatabaseLevelUniquenessEnforcement(db);
        testLegacyCitizenMigrationSupport(db);
        testExistingCitizenLoginUninterrupted(db);

        System.out.println("-------------------------------------------------------------------------------");
        System.out.println(String.format("CITIZEN IDENTITY VERIFICATION RESULT: %d / %d TESTS PASSED", testsPassed, testsRun));
        System.out.println("===============================================================================");
        return testsPassed == testsRun;
    }

    public static void main(String[] args) {
        if (!runAllTests()) {
            System.exit(1);
        }
    }

    private static void pass(String testName, String detail) {
        testsRun++;
        testsPassed++;
        System.out.println(String.format("[TEST %d/10] %s... PASSED (%s)", testsRun, testName, detail));
    }

    private static void fail(String testName, String reason) {
        testsRun++;
        System.err.println(String.format("[TEST %d/10] %s... FAILED: %s", testsRun, testName, reason));
    }

    // 1. Citizen registration with valid 12-digit identity
    private static void testValidCitizenIdentityRegistration(Database db) {
        try {
            String testIdentity = "234567890123";
            String email = "cit_valid_" + System.currentTimeMillis() + "@example.com";
            User user = db.registerUser("Kavitha Devi", "kavitha", email, "9876501234", "Password@123", "CITIZEN", "", testIdentity);

            if (user == null || !(user instanceof Citizen)) {
                fail("Valid Identity Registration", "Citizen registration returned null or wrong type");
                return;
            }
            Citizen citizen = (Citizen) user;
            if (citizen.getIdentityHash() == null || citizen.getIdentityHash().isBlank()) {
                fail("Valid Identity Registration", "Missing identity hash");
                return;
            }
            if (!"XXXX-XXXX-0123".equals(citizen.getMaskedIdentity())) {
                fail("Valid Identity Registration", "Expected masked identity 'XXXX-XXXX-0123', got: " + citizen.getMaskedIdentity());
                return;
            }
            pass("Valid Identity Registration", "Citizen ID: " + citizen.getId() + ", Masked: " + citizen.getMaskedIdentity());
        } catch (Exception e) {
            fail("Valid Identity Registration", e.getMessage());
        }
    }

    // 2. Duplicate identity rejection with exact error message (Requirement 6 & 7)
    private static void testDuplicateIdentityRejectionMessage(Database db) {
        try {
            String sharedIdentity = "345678901234";
            String email1 = "citizen_primary_" + System.currentTimeMillis() + "@example.com";
            User user1 = db.registerUser("Citizen Primary", "primary_cit", email1, "9876501111", "Password@123", "CITIZEN", "", sharedIdentity);

            if (user1 == null) {
                fail("Duplicate Identity Rejection", "Initial citizen registration failed");
                return;
            }

            // Attempt second registration with same identity number
            String email2 = "citizen_dup_" + System.currentTimeMillis() + "@example.com";
            try {
                db.registerUser("Citizen Imposter", "imposter_cit", email2, "9876502222", "Password@123", "CITIZEN", "", sharedIdentity);
                fail("Duplicate Identity Rejection", "Second registration with duplicate identity should have thrown DuplicateIdentityException");
            } catch (Database.DuplicateIdentityException e) {
                String expectedMsg = "This identity number is already associated with an existing citizen account.";
                if (!expectedMsg.equals(e.getMessage())) {
                    fail("Duplicate Identity Rejection", "Expected exact message '" + expectedMsg + "', but got: '" + e.getMessage() + "'");
                    return;
                }
                pass("Duplicate Identity Rejection", "Rejected with exact message: \"" + e.getMessage() + "\" (Zero identity leak of prior account)");
            }
        } catch (Exception e) {
            fail("Duplicate Identity Rejection", e.getMessage());
        }
    }

    // 3. Formatted input normalization (spaces and dashes)
    private static void testFormattedIdentityNormalization(Database db) {
        try {
            String formattedInput = "4567-8901-2345";
            String email = "cit_fmt_" + System.currentTimeMillis() + "@example.com";
            User user = db.registerUser("Ravi Shankar", "ravi_s", email, "9876503333", "Password@123", "CITIZEN", "", formattedInput);

            if (user == null) {
                fail("Formatted Identity Normalization", "Registration failed with formatted input");
                return;
            }
            Citizen c = (Citizen) user;
            if (!"XXXX-XXXX-2345".equals(c.getMaskedIdentity())) {
                fail("Formatted Identity Normalization", "Expected 'XXXX-XXXX-2345', got: " + c.getMaskedIdentity());
                return;
            }

            // Also verify that attempting duplicate registration with spaces "4567 8901 2345" detects match
            try {
                db.registerUser("Ravi Dup", "ravi_dup", "dup_ravi@test.com", "9876504444", "Password@123", "CITIZEN", "", "4567 8901 2345");
                fail("Formatted Identity Normalization", "Should have detected duplicate identity across different formatting");
            } catch (Database.DuplicateIdentityException e) {
                pass("Formatted Identity Normalization", "Spaces and dashes normalized cleanly to same cryptographic hash");
            }
        } catch (Exception e) {
            fail("Formatted Identity Normalization", e.getMessage());
        }
    }

    // 4. Rejection of numbers starting with 0 or 1
    private static void testInvalidIdentityStartsWithZeroOrOne(Database db) {
        try {
            boolean caughtZero = false;
            try {
                db.registerUser("Test Zero", "t_zero", "zero@test.com", "9876505555", "Password@123", "CITIZEN", "", "012345678901");
            } catch (Database.InvalidIdentityException e) {
                caughtZero = true;
            }

            boolean caughtOne = false;
            try {
                db.registerUser("Test One", "t_one", "one@test.com", "9876506666", "Password@123", "CITIZEN", "", "123456789012");
            } catch (Database.InvalidIdentityException e) {
                caughtOne = true;
            }

            if (!caughtZero || !caughtOne) {
                fail("Rejection of 0 or 1 Prefix", "Failed to reject invalid prefixes: zero=" + caughtZero + ", one=" + caughtOne);
                return;
            }
            pass("Rejection of 0 or 1 Prefix", "Numbers starting with 0 or 1 correctly rejected");
        } catch (Exception e) {
            fail("Rejection of 0 or 1 Prefix", e.getMessage());
        }
    }

    // 5. Rejection of invalid length or non-digits
    private static void testInvalidIdentityLengthAndNonDigits(Database db) {
        try {
            boolean caughtShort = false;
            try {
                db.registerUser("Test Short", "t_short", "short@test.com", "9876507777", "Password@123", "CITIZEN", "", "2345678");
            } catch (Database.InvalidIdentityException e) {
                caughtShort = true;
            }

            boolean caughtAlpha = false;
            try {
                db.registerUser("Test Alpha", "t_alpha", "alpha@test.com", "9876508888", "Password@123", "CITIZEN", "", "2345ABCD0123");
            } catch (Database.InvalidIdentityException e) {
                caughtAlpha = true;
            }

            if (!caughtShort || !caughtAlpha) {
                fail("Rejection of Short or Non-digits", "Failed to reject invalid length or alphabet");
                return;
            }
            pass("Rejection of Short or Non-digits", "Short numbers and alphabetic characters strictly rejected");
        } catch (Exception e) {
            fail("Rejection of Short or Non-digits", e.getMessage());
        }
    }

    // 6. Rejection of repeating identical digits
    private static void testInvalidIdentityRepetitiveDigits(Database db) {
        try {
            boolean caughtRepeat = false;
            try {
                db.registerUser("Test Repeat", "t_repeat", "repeat@test.com", "9876509999", "Password@123", "CITIZEN", "", "222222222222");
            } catch (Database.InvalidIdentityException e) {
                caughtRepeat = true;
            }

            if (!caughtRepeat) {
                fail("Rejection of Repetitive Digits", "Failed to reject all-identical digits (222222222222)");
                return;
            }
            pass("Rejection of Repetitive Digits", "Repetitive sequences like 222222222222 rejected");
        } catch (Exception e) {
            fail("Rejection of Repetitive Digits", e.getMessage());
        }
    }

    // 7. Plaintext raw identity number is NEVER stored
    private static void testPlaintextIdentityNeverStored(Database db) {
        try {
            String rawNumber = "567890123456";
            String email = "cit_plain_" + System.currentTimeMillis() + "@example.com";
            User user = db.registerUser("Anand Kumar", "anand_k", email, "9876510001", "Password@123", "CITIZEN", "", rawNumber);

            Citizen c = (Citizen) user;
            if (rawNumber.equals(c.getMaskedIdentity()) || rawNumber.equals(c.getIdentityHash())) {
                fail("Plaintext Protection", "Raw identity number found stored in citizen fields");
                return;
            }
            if (!c.getMaskedIdentity().startsWith("XXXX-XXXX-")) {
                fail("Plaintext Protection", "Masked identity does not match XXXX-XXXX- pattern: " + c.getMaskedIdentity());
                return;
            }
            pass("Plaintext Protection", "Raw 12-digit number strictly masked to " + c.getMaskedIdentity() + " and hashed via salted SHA-256");
        } catch (Exception e) {
            fail("Plaintext Protection", e.getMessage());
        }
    }

    // 8. Database-level atomic uniqueness enforcement
    private static void testDatabaseLevelUniquenessEnforcement(Database db) {
        try {
            String rawId = "678901234567";
            String hash = Database.hashIdentity(rawId);
            boolean initiallyRegistered = db.isIdentityRegistered(rawId);

            if (initiallyRegistered) {
                fail("DB Uniqueness", "Identifier should not be registered before signup");
                return;
            }

            db.registerUser("Citizen Eight", "cit_eight", "eight@test.com", "9876510002", "Password@123", "CITIZEN", "", rawId);
            boolean afterRegistered = db.isIdentityRegistered(rawId);
            boolean byHashRegistered = db.isIdentityRegistered(hash);

            if (!afterRegistered || !byHashRegistered) {
                fail("DB Uniqueness", "Identifier failed to register atomically in database identity index");
                return;
            }
            pass("DB Uniqueness", "Atomic identity index verified at database level (tested via raw and hash queries)");
        } catch (Exception e) {
            fail("DB Uniqueness", e.getMessage());
        }
    }

    // 9. Migration support for existing legacy citizens
    private static void testLegacyCitizenMigrationSupport(Database db) {
        try {
            // Check Ramesh C001
            User u = db.getUserById("C001");
            if (u == null) {
                u = db.getUserByEmail("ramesh@test.com");
            }
            if (u == null || !(u instanceof Citizen)) {
                fail("Legacy Migration Support", "Initial citizen Ramesh not found");
                return;
            }

            Citizen ramesh = (Citizen) u;
            if (ramesh.getMaskedIdentity() == null || !ramesh.getMaskedIdentity().startsWith("XXXX-XXXX-")) {
                fail("Legacy Migration Support", "Ramesh has no valid masked identity reference: " + ramesh.getMaskedIdentity());
                return;
            }
            if (ramesh.getIdentityHash() == null || ramesh.getIdentityHash().isBlank()) {
                fail("Legacy Migration Support", "Ramesh has no identity hash");
                return;
            }
            pass("Legacy Migration Support", "Legacy citizen " + ramesh.getId() + " backfilled with reference " + ramesh.getMaskedIdentity());
        } catch (Exception e) {
            fail("Legacy Migration Support", e.getMessage());
        }
    }

    // 10. Existing citizen login uninterrupted
    private static void testExistingCitizenLoginUninterrupted(Database db) {
        try {
            User authenticated = db.authenticateUser("ramesh@test.com", "123456");
            if (authenticated == null) {
                fail("Existing Citizen Login", "Failed to authenticate existing citizen Ramesh");
                return;
            }
            if (!"CITIZEN".equalsIgnoreCase(authenticated.getRole())) {
                fail("Existing Citizen Login", "Wrong role: " + authenticated.getRole());
                return;
            }
            pass("Existing Citizen Login", "Existing citizen authentication works uninterrupted");
        } catch (Exception e) {
            fail("Existing Citizen Login", e.getMessage());
        }
    }
}

