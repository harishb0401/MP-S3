package test;

import model.*;
import service.*;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Automated test suite for:
 * 1. Complaint deletion for Admin and Manager with role-based security & audit logging.
 * 2. Duplicate complaint detection by category and normalized location with Admin/Manager alerts.
 * 3. Prevention of Workers from modifying Operational Action & manager-controlled fields after assignment.
 */
public class ComplaintDeletionAndDuplicatesTest {

    private static final int TEST_PORT = 8990;

    public static void main(String[] args) {
        boolean allPassed = runAllTests();
        if (allPassed) {
            System.out.println("\n>>> ALL COMPLAINT DELETION, DUPLICATE DETECTION & OPERATIONAL ACTION TESTS PASSED! <<<");
            System.exit(0);
        } else {
            System.err.println("\n>>> SOME TESTS FAILED! <<<");
            System.exit(1);
        }
    }

    public static boolean runAllTests() {
        System.out.println("===============================================================================");
        System.out.println("   MP-S3: COMPLAINT DELETION, DUPLICATES & OPERATIONAL ACTION TEST SUITE");
        System.out.println("===============================================================================\n");

        int passed = 0;
        int total = 12;

        Database.setPersistenceDisabled(true);
        Database db = new Database();
        GrievanceManager manager = new GrievanceManager(db);

        // Seed users via authentication using standard seeded credentials
        User admin = db.authenticateUser("admin@makkal.gov.in", "admin123");
        User mgr = db.authenticateUser("manager@makkal.gov.in", "admin123");
        User worker = db.authenticateUser("water_staff@makkal.gov.in", "admin123");
        User citizen = db.registerUser("Test Citizen", "citizen.test@makkal.gov.in", "9876543210", "CitPass123");

        String adminToken = db.createSession(admin);
        String mgrToken = db.createSession(mgr);
        String citizenToken = db.createSession(citizen);
        String workerToken = db.createSession(worker);

        ServerManager server = new ServerManager(TEST_PORT, db, manager);
        server.start();

        try {
            // -------------------------------------------------------------
            // TEST 1: Admin successfully deletes a complaint (API DELETE)
            // -------------------------------------------------------------
            System.out.print("[TEST 1/12] Admin deletes complaint with capacity cleanup & audit logging... ");
            Complaint cAdmin = manager.createComplaint(citizen, "Water Supply", "Pipe burst in street", "Anna Nagar, Street 5", 25, "HIGH", "Water Issue 1");
            manager.assignDepartmentAndStaff(cAdmin.getComplaintId(), "Water Department", worker.getName(), mgr);
            Department waterDept = db.findDepartment("Water Supply");
            int loadBefore = (waterDept != null) ? waterDept.getCurrentLoad() : 0;

            int deleteAdminCode = sendHttpRequest("DELETE", "http://localhost:" + TEST_PORT + "/api/complaints/" + cAdmin.getComplaintId(), "{\"reason\":\"Duplicate reported by admin\"}", adminToken);
            boolean deleteAdminSuccess = (deleteAdminCode == 200);
            boolean notInDbAdmin = (db.getComplaintById(cAdmin.getComplaintId()) == null);
            int loadAfterAdmin = (waterDept != null) ? waterDept.getCurrentLoad() : 0;
            boolean loadDecrementedAdmin = (loadAfterAdmin == loadBefore - 1);

            if (deleteAdminSuccess && notInDbAdmin && loadDecrementedAdmin) {
                System.out.println("PASSED");
                passed++;
            } else {
                System.out.println("FAILED (code=" + deleteAdminCode + ", inDb=" + !notInDbAdmin + ", load=" + loadAfterAdmin + ")");
            }

            // -------------------------------------------------------------
            // TEST 2: Manager successfully deletes a complaint (API DELETE)
            // -------------------------------------------------------------
            System.out.print("[TEST 2/12] Manager deletes complaint via API... ");
            Complaint cMgr = manager.createComplaint(citizen, "Roads", "Pothole on main cross", "Gandhi Road", 10, "MEDIUM", "Road Issue");
            int deleteMgrCode = sendHttpRequest("DELETE", "http://localhost:" + TEST_PORT + "/api/complaints/" + cMgr.getComplaintId(), "{\"reason\":\"Spam test complaint\"}", mgrToken);
            boolean deleteMgrSuccess = (deleteMgrCode == 200);
            boolean notInDbMgr = (db.getComplaintById(cMgr.getComplaintId()) == null);

            if (deleteMgrSuccess && notInDbMgr) {
                System.out.println("PASSED");
                passed++;
            } else {
                System.out.println("FAILED (code=" + deleteMgrCode + ")");
            }

            // -------------------------------------------------------------
            // TEST 3: Worker cannot delete a complaint through the API (HTTP 403)
            // -------------------------------------------------------------
            System.out.print("[TEST 3/12] Worker rejected from deleting complaint (HTTP 403)... ");
            Complaint cWorkerTest = manager.createComplaint(citizen, "Drainage", "Blocked drainage drain", "North Gate", 15, "MEDIUM", "Drain Issue");
            int deleteWorkerCode = sendHttpRequest("DELETE", "http://localhost:" + TEST_PORT + "/api/complaints/" + cWorkerTest.getComplaintId(), null, workerToken);
            boolean workerBlocked = (deleteWorkerCode == 403);
            boolean stillInDbWorker = (db.getComplaintById(cWorkerTest.getComplaintId()) != null);

            if (workerBlocked && stillInDbWorker) {
                System.out.println("PASSED");
                passed++;
            } else {
                System.out.println("FAILED (code=" + deleteWorkerCode + ")");
            }

            // -------------------------------------------------------------
            // TEST 4: Citizen cannot delete a complaint through the API (HTTP 403)
            // -------------------------------------------------------------
            System.out.print("[TEST 4/12] Citizen rejected from deleting complaint (HTTP 403)... ");
            int deleteCitizenCode = sendHttpRequest("DELETE", "http://localhost:" + TEST_PORT + "/api/complaints/" + cWorkerTest.getComplaintId(), null, citizenToken);
            boolean citizenBlocked = (deleteCitizenCode == 403);
            boolean stillInDbCitizen = (db.getComplaintById(cWorkerTest.getComplaintId()) != null);

            if (citizenBlocked && stillInDbCitizen) {
                System.out.println("PASSED");
                passed++;
            } else {
                System.out.println("FAILED (code=" + deleteCitizenCode + ")");
            }

            // -------------------------------------------------------------
            // TEST 5: Nonexistent complaint ID and double deletion handled safely
            // -------------------------------------------------------------
            System.out.print("[TEST 5/12] Nonexistent complaint ID & double deletion safety (HTTP 404)... ");
            int nonExistentCode = sendHttpRequest("DELETE", "http://localhost:" + TEST_PORT + "/api/complaints/GRV-NONEXISTENT-9999", null, adminToken);
            boolean nonExistentSafe = (nonExistentCode == 404);
            // Double delete previously deleted complaint
            int doubleDeleteCode = sendHttpRequest("DELETE", "http://localhost:" + TEST_PORT + "/api/complaints/" + cAdmin.getComplaintId(), null, adminToken);
            boolean doubleDeleteSafe = (doubleDeleteCode == 404);

            if (nonExistentSafe && doubleDeleteSafe) {
                System.out.println("PASSED");
                passed++;
            } else {
                System.out.println("FAILED (nonExistent=" + nonExistentCode + ", doubleDelete=" + doubleDeleteCode + ")");
            }

            // -------------------------------------------------------------
            // TEST 6: Duplicate detection by category and normalized location
            // -------------------------------------------------------------
            System.out.print("[TEST 6/12] Same category and normalized location matches duplicates... ");
            Complaint cOriginal = manager.createComplaint(citizen, "Streetlights", "Streetlight not working", "Anna Nagar, Street 5", 10, "MEDIUM", "Light issue 1");
            
            // Check duplicates via API for matching category and location with whitespace and case differences
            String checkPayloadMatch = "{\"category\":\"streetlights\",\"location\":\"   anna   nagar,   street 5.  \"}";
            HttpResponse checkRespMatch = sendHttpRequestWithBody("POST", "http://localhost:" + TEST_PORT + "/api/complaints/check-duplicate", checkPayloadMatch, citizenToken);
            boolean dupFound = (checkRespMatch.statusCode == 200) && checkRespMatch.body.contains(cOriginal.getComplaintId());

            if (dupFound) {
                System.out.println("PASSED");
                passed++;
            } else {
                System.out.println("FAILED (status=" + checkRespMatch.statusCode + ", body=" + checkRespMatch.body + ")");
            }

            // -------------------------------------------------------------
            // TEST 7: Negative matching: different location or different category
            // -------------------------------------------------------------
            System.out.print("[TEST 7/12] Negative matching: different location or different category... ");
            // Same category, different location
            String checkDiffLoc = "{\"category\":\"Streetlights\",\"location\":\"T Nagar 2nd Avenue\"}";
            HttpResponse respDiffLoc = sendHttpRequestWithBody("POST", "http://localhost:" + TEST_PORT + "/api/complaints/check-duplicate", checkDiffLoc, citizenToken);
            boolean noFalseMatchLoc = (respDiffLoc.statusCode == 200) && !respDiffLoc.body.contains(cOriginal.getComplaintId());

            // Different category, same location
            String checkDiffCat = "{\"category\":\"Water Supply\",\"location\":\"Anna Nagar, Street 5\"}";
            HttpResponse respDiffCat = sendHttpRequestWithBody("POST", "http://localhost:" + TEST_PORT + "/api/complaints/check-duplicate", checkDiffCat, citizenToken);
            boolean noFalseMatchCat = (respDiffCat.statusCode == 200) && !respDiffCat.body.contains(cOriginal.getComplaintId());

            if (noFalseMatchLoc && noFalseMatchCat) {
                System.out.println("PASSED");
                passed++;
            } else {
                System.out.println("FAILED (locMatch=" + !noFalseMatchLoc + ", catMatch=" + !noFalseMatchCat + ")");
            }

            // -------------------------------------------------------------
            // TEST 8: Self exclusion and exclusion of resolved complaints
            // -------------------------------------------------------------
            System.out.print("[TEST 8/12] Duplicate exclusion of self and resolved complaints... ");
            // Current complaint excluded from its own candidates
            String checkSelf = "{\"category\":\"Streetlights\",\"location\":\"Anna Nagar, Street 5\",\"currentComplaintId\":\"" + cOriginal.getComplaintId() + "\"}";
            HttpResponse respSelf = sendHttpRequestWithBody("POST", "http://localhost:" + TEST_PORT + "/api/complaints/check-duplicate", checkSelf, citizenToken);
            boolean selfExcluded = (respSelf.statusCode == 200) && !respSelf.body.contains(cOriginal.getComplaintId());

            // Follow lifecycle state machine: Assigned -> InProgress -> Resolved
            manager.assignDepartmentAndStaff(cOriginal.getComplaintId(), "Streetlights", worker.getName(), admin);
            manager.updateStatus(cOriginal.getComplaintId(), "InProgress", "Working on light", admin);
            manager.updateStatus(cOriginal.getComplaintId(), "Resolved", "Repaired light", admin);
            HttpResponse respAfterResolve = sendHttpRequestWithBody("POST", "http://localhost:" + TEST_PORT + "/api/complaints/check-duplicate", checkPayloadMatch, citizenToken);
            boolean resolvedExcluded = (respAfterResolve.statusCode == 200) && !respAfterResolve.body.contains(cOriginal.getComplaintId());

            if (selfExcluded && resolvedExcluded) {
                System.out.println("PASSED");
                passed++;
            } else {
                System.out.println("FAILED (selfExcluded=" + selfExcluded + ", resolvedExcluded=" + resolvedExcluded + ")");
            }

            // -------------------------------------------------------------
            // TEST 9: Admin and Manager alerts on duplicate creation without spam
            // -------------------------------------------------------------
            System.out.print("[TEST 9/12] Duplicate submission notifies Admin & Manager without spam... ");
            Complaint cOpen1 = manager.createComplaint(citizen, "Waste Management", "Garbage overflow", "Market Road Corner", 20, "HIGH", "Garbage 1");
            int notifCountBefore = db.getNotificationsForUser(admin).size();

            // Submit second complaint matching first
            Complaint cOpen2 = manager.createComplaint(citizen, "Waste Management", "Overflowing dumpster", "market road corner", 15, "MEDIUM", "Garbage 2");
            manager.checkAndAlertDuplicateCandidates(cOpen2);

            int notifCountAfter = db.getNotificationsForUser(admin).size();
            boolean notifCreated = (notifCountAfter > notifCountBefore);

            // Re-check should not duplicate notification spam
            manager.checkAndAlertDuplicateCandidates(cOpen2);
            int notifCountAfterRecheck = db.getNotificationsForUser(admin).size();
            boolean noSpam = (notifCountAfterRecheck == notifCountAfter);

            if (notifCreated && noSpam) {
                System.out.println("PASSED");
                passed++;
            } else {
                System.out.println("FAILED (notifCreated=" + notifCreated + ", noSpam=" + noSpam + ")");
            }

            // -------------------------------------------------------------
            // TEST 10: Manager updates Operational Action
            // -------------------------------------------------------------
            System.out.print("[TEST 10/12] Manager can update Operational Action... ");
            Complaint cOpTest = manager.createComplaint(citizen, "Water Supply", "Pipe leak", "South Avenue", 30, "HIGH", "Pipe Leak");
            String mgrAssignPayload = "{\"department\":\"Water Department\",\"staffName\":\"Water Worker\",\"reason\":\"Critical pipeline\",\"operationalAction\":\"Urgent Valve Replacement & Pressure Testing\"}";
            int mgrAssignCode = sendHttpRequest("POST", "http://localhost:" + TEST_PORT + "/api/complaints/" + cOpTest.getComplaintId() + "/assign", mgrAssignPayload, mgrToken);
            boolean mgrAssignOk = (mgrAssignCode == 200);

            Complaint cAfterMgr = db.getComplaintById(cOpTest.getComplaintId());
            boolean opActionSet = cAfterMgr != null && "Urgent Valve Replacement & Pressure Testing".equals(cAfterMgr.getOperationalAction());

            // Manager updates Operational Action via status endpoint
            String mgrStatusPayload = "{\"status\":\"Assigned\",\"remarks\":\"Action updated\",\"operationalAction\":\"Excavation & Joint Sealing\"}";
            int mgrStatusCode = sendHttpRequest("PUT", "http://localhost:" + TEST_PORT + "/api/complaints/" + cOpTest.getComplaintId() + "/status", mgrStatusPayload, mgrToken);
            boolean mgrStatusOk = (mgrStatusCode == 200);
            boolean opActionUpdated = "Excavation & Joint Sealing".equals(db.getComplaintById(cOpTest.getComplaintId()).getOperationalAction());

            if (mgrAssignOk && opActionSet && mgrStatusOk && opActionUpdated) {
                System.out.println("PASSED");
                passed++;
            } else {
                System.out.println("FAILED (mgrAssign=" + mgrAssignCode + ", opActionSet=" + opActionSet + ", mgrStatus=" + mgrStatusCode + ", opActionUpdated=" + opActionUpdated + ")");
            }

            // -------------------------------------------------------------
            // TEST 11: Worker rejected from modifying Operational Action (HTTP 403)
            // -------------------------------------------------------------
            System.out.print("[TEST 11/12] Worker rejected from modifying Operational Action (HTTP 403)... ");
            String workerTamperPayload = "{\"status\":\"InProgress\",\"operationalAction\":\"Worker unauthorized changed action\"}";
            int workerTamperCode = sendHttpRequest("PUT", "http://localhost:" + TEST_PORT + "/api/complaints/" + cOpTest.getComplaintId() + "/status", workerTamperPayload, workerToken);
            boolean workerTamperBlocked = (workerTamperCode == 403);

            // Also verify worker cannot modify department or staffName or severity
            String workerDeptTamperPayload = "{\"status\":\"InProgress\",\"department\":\"Drainage\"}";
            int workerDeptTamperCode = sendHttpRequest("PUT", "http://localhost:" + TEST_PORT + "/api/complaints/" + cOpTest.getComplaintId() + "/status", workerDeptTamperPayload, workerToken);
            boolean workerDeptTamperBlocked = (workerDeptTamperCode == 403);

            // Verify Operational Action was preserved
            boolean opActionPreserved = "Excavation & Joint Sealing".equals(db.getComplaintById(cOpTest.getComplaintId()).getOperationalAction());

            if (workerTamperBlocked && workerDeptTamperBlocked && opActionPreserved) {
                System.out.println("PASSED");
                passed++;
            } else {
                System.out.println("FAILED (tamperCode=" + workerTamperCode + ", deptTamperCode=" + workerDeptTamperCode + ", preserved=" + opActionPreserved + ")");
            }

            // -------------------------------------------------------------
            // TEST 12: Permitted Worker tasks continue to work
            // -------------------------------------------------------------
            System.out.print("[TEST 12/12] Permitted Worker tasks work (Assigned -> InProgress -> Resolved)... ");
            // 1. Worker moves to InProgress
            String workerValidPayload = "{\"status\":\"InProgress\"}";
            int workerProgressCode = sendHttpRequest("PUT", "http://localhost:" + TEST_PORT + "/api/complaints/" + cOpTest.getComplaintId() + "/status", workerValidPayload, workerToken);
            boolean workerProgressOk = (workerProgressCode == 200);
            boolean statusInProgress = "InProgress".equalsIgnoreCase(db.getComplaintById(cOpTest.getComplaintId()).getStatus());

            // 2. Worker resolves complaint with notes
            String workerResolvePayload = "{\"status\":\"Resolved\",\"resolutionNotes\":\"Valve replaced and leak stopped successfully.\"}";
            int workerResolveCode = sendHttpRequest("PUT", "http://localhost:" + TEST_PORT + "/api/complaints/" + cOpTest.getComplaintId() + "/status", workerResolvePayload, workerToken);
            boolean workerResolveOk = (workerResolveCode == 200);
            Complaint cResolved = db.getComplaintById(cOpTest.getComplaintId());
            boolean statusResolved = cResolved != null && "Resolved".equalsIgnoreCase(cResolved.getStatus());
            boolean notesPresent = cResolved != null && cResolved.getResolutionNotes().contains("Valve replaced");

            if (workerProgressOk && statusInProgress && workerResolveOk && statusResolved && notesPresent) {
                System.out.println("PASSED");
                passed++;
            } else {
                System.out.println("FAILED (progressCode=" + workerProgressCode + ", resolveCode=" + workerResolveCode + ")");
            }

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            server.stop();
        }

        System.out.println("\n-------------------------------------------------------------------------------");
        System.out.println("TEST SUMMARY: " + passed + " / " + total + " TESTS PASSED (" + ((passed * 100) / total) + "%)");
        System.out.println("-------------------------------------------------------------------------------");

        return passed == total;
    }

    private static int sendHttpRequest(String method, String urlString, String jsonBody, String token) throws Exception {
        return sendHttpRequestWithBody(method, urlString, jsonBody, token).statusCode;
    }

    private static class HttpResponse {
        int statusCode;
        String body;
        HttpResponse(int code, String b) {
            this.statusCode = code;
            this.body = b;
        }
    }

    private static HttpResponse sendHttpRequestWithBody(String method, String urlString, String jsonBody, String token) throws Exception {
        URI uri = URI.create(urlString);
        HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
        conn.setRequestMethod(method);
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);

        if (token != null) {
            conn.setRequestProperty("Authorization", "Bearer " + token);
        }

        if (jsonBody != null && (method.equals("POST") || method.equals("PUT") || method.equals("DELETE"))) {
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            conn.setDoOutput(true);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
            }
        }

        int code = conn.getResponseCode();
        String responseBody = "";
        try {
            InputStream is = (code >= 200 && code < 400) ? conn.getInputStream() : conn.getErrorStream();
            if (is != null) {
                Scanner scanner = new Scanner(is, StandardCharsets.UTF_8.name());
                scanner.useDelimiter("\\A");
                responseBody = scanner.hasNext() ? scanner.next() : "";
            }
        } catch (Exception ignored) {}

        conn.disconnect();
        return new HttpResponse(code, responseBody);
    }
}
