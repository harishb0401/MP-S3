package test;

import model.*;
import service.Database;
import service.GrievanceManager;
import service.ServerManager;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Automated Test Suite for Complaint Assignment Visibility & Validation:
 * - Registered unassigned complaints can be assigned only by authorized roles (Admin/Manager).
 * - Assigned complaints hide initial assign option; duplicate /assign rejected.
 * - In Progress complaints hide initial assign option; /assign rejected.
 * - Resolved complaints completely hide/disable assign & reassign; /assign and /reassign rejected.
 * - Workers have no assignment/reassignment permissions.
 * - Reassignment requires explicit confirmation and mandatory reason.
 */
public class ComplaintAssignmentVisibilityTest {

    private static final int TEST_PORT = 8897;

    public static void main(String[] args) {
        System.out.println("===============================================================================");
        System.out.println("   COMPLAINT ASSIGNMENT VISIBILITY & VALIDATION AUTOMATED TEST SUITE");
        System.out.println("===============================================================================");

        int passed = 0;
        int total = 10;

        Database db = new Database();
        GrievanceManager gm = new GrievanceManager(db);
        ServerManager server = new ServerManager(TEST_PORT, db, gm);

        User admin = db.getUserById("ADM1001");
        if (admin == null) admin = db.getUserByUsername("admin");
        if (admin == null) admin = db.getUserByEmail("admin@makkal.gov.in");

        User manager = db.getUserById("MGR1001");
        if (manager == null) manager = db.getUserByUsername("manager");
        if (manager == null) manager = db.getUserByEmail("manager@makkal.gov.in");

        User worker = db.getUserById("STF-W01");
        if (worker == null) worker = db.getUserByUsername("water_staff");
        if (worker == null) worker = db.getUserByEmail("water_staff@makkal.gov.in");

        User citizen = db.getUserById("C001");
        if (citizen == null) citizen = db.getUserByUsername("ramesh");
        if (citizen == null && !db.getAllCitizens().isEmpty()) citizen = db.getAllCitizens().get(0);

        try {
            server.start();

            String adminToken = db.createSession(admin);
            String adminAuth = "Bearer " + adminToken;

            String workerToken = db.createSession(worker);
            String workerAuth = "Bearer " + workerToken;

            // TEST 1: Role-Based Assignment Permissions (RBAC)
            System.out.print("[TEST 1/10] Role-Based Assignment Permissions: Admin/Manager permitted, Worker/Citizen blocked... ");
            boolean adminPermitted = admin != null && admin.canAssignDepartment();
            boolean managerPermitted = manager != null && manager.canAssignDepartment();
            boolean workerBlocked = worker != null && !worker.canAssignDepartment();
            boolean citizenBlocked = citizen != null && !citizen.canAssignDepartment();

            if (adminPermitted && managerPermitted && workerBlocked && citizenBlocked) {
                System.out.println("PASSED");
                passed++;
            } else {
                System.out.println("FAILED (Permission mismatch: admin=" + adminPermitted + ", mgr=" + managerPermitted + ", worker=" + !workerBlocked + ")");
            }

            // TEST 2: Initial Assignment of Registered Complaint by Authorized Admin
            System.out.print("[TEST 2/10] Initial assignment of Registered complaint by Admin... ");
            Complaint c1 = gm.createComplaint(citizen, "Water Supply", "Pipe burst near market square", "North Street", 120);
            boolean initialAssignOk = gm.assignDepartmentAndStaff(c1.getComplaintId(), "Water Department", worker.getName(), admin);
            Complaint c1Updated = gm.getComplaintById(c1.getComplaintId());

            if (initialAssignOk && "Assigned".equalsIgnoreCase(c1Updated.getStatus()) && worker.getName().equalsIgnoreCase(c1Updated.getAssignedStaff())) {
                System.out.println("PASSED (Status: Assigned, Staff: " + c1Updated.getAssignedStaff() + ")");
                passed++;
            } else {
                System.out.println("FAILED");
            }

            // TEST 3: Worker Permission Enforcement (Worker cannot assign complaints)
            System.out.print("[TEST 3/10] Worker attempt to assign complaint is strictly rejected... ");
            Complaint c2 = gm.createComplaint(citizen, "Road Damage", "Pothole on main avenue", "South Colony", 45);
            boolean workerAssignRejected = false;
            try {
                gm.assignDepartmentAndStaff(c2.getComplaintId(), "Roads Department", "Worker Bob", worker);
            } catch (SecurityException e) {
                workerAssignRejected = true;
            }

            if (workerAssignRejected) {
                System.out.println("PASSED (SecurityException cleanly raised)");
                passed++;
            } else {
                System.out.println("FAILED (Worker was permitted to assign)");
            }

            // TEST 4: Backend Rejection of Duplicate Assignment on Already Assigned Complaint via HTTP API
            System.out.print("[TEST 4/10] HTTP POST /api/complaints/:id/assign on already ASSIGNED complaint returns 400... ");
            HttpResponse resAssignAgain = sendHttpRequest("POST", "http://localhost:" + TEST_PORT + "/api/complaints/" + c1.getComplaintId() + "/assign",
                    "{\"department\":\"Water Supply\",\"staffName\":\"" + worker.getName() + "\"}", adminAuth);

            if (resAssignAgain.statusCode == 400 && resAssignAgain.body.contains("already assigned")) {
                System.out.println("PASSED (HTTP 400: " + resAssignAgain.body + ")");
                passed++;
            } else {
                System.out.println("FAILED (Status=" + resAssignAgain.statusCode + ", body=" + resAssignAgain.body + ")");
            }

            // TEST 5: Backend Rejection of Initial Assignment on IN_PROGRESS Complaint via HTTP API
            System.out.print("[TEST 5/10] HTTP POST /api/complaints/:id/assign on IN_PROGRESS complaint returns 400... ");
            gm.updateStatus(c1.getComplaintId(), "InProgress", "Field repairs underway", worker);
            HttpResponse resAssignProgress = sendHttpRequest("POST", "http://localhost:" + TEST_PORT + "/api/complaints/" + c1.getComplaintId() + "/assign",
                    "{\"department\":\"Water Supply\",\"staffName\":\"" + worker.getName() + "\"}", adminAuth);

            if (resAssignProgress.statusCode == 400 && resAssignProgress.body.contains("In Progress")) {
                System.out.println("PASSED (HTTP 400: " + resAssignProgress.body + ")");
                passed++;
            } else {
                System.out.println("FAILED (Status=" + resAssignProgress.statusCode + ", body=" + resAssignProgress.body + ")");
            }

            // TEST 6: Backend Rejection of Initial Assignment on RESOLVED Complaint via HTTP API
            System.out.print("[TEST 6/10] HTTP POST /api/complaints/:id/assign on RESOLVED complaint returns 400... ");
            gm.resolveComplaint(c1.getComplaintId(), "Pipe replaced with ductile iron fitting and tested", worker);
            HttpResponse resAssignResolved = sendHttpRequest("POST", "http://localhost:" + TEST_PORT + "/api/complaints/" + c1.getComplaintId() + "/assign",
                    "{\"department\":\"Water Supply\",\"staffName\":\"" + worker.getName() + "\"}", adminAuth);

            if (resAssignResolved.statusCode == 400 && resAssignResolved.body.contains("Resolved complaints")) {
                System.out.println("PASSED (HTTP 400: " + resAssignResolved.body + ")");
                passed++;
            } else {
                System.out.println("FAILED (Status=" + resAssignResolved.statusCode + ", body=" + resAssignResolved.body + ")");
            }

            // TEST 7: Backend Rejection of Reassignment on RESOLVED Complaint via HTTP API
            System.out.print("[TEST 7/10] HTTP POST /api/complaints/:id/reassign on RESOLVED complaint returns 400... ");
            HttpResponse resReassignResolved = sendHttpRequest("POST", "http://localhost:" + TEST_PORT + "/api/complaints/" + c1.getComplaintId() + "/reassign",
                    "{\"department\":\"Roads\",\"staffName\":\"Officer R\",\"reason\":\"Emergency handover\"}", adminAuth);

            if (resReassignResolved.statusCode == 400 && resReassignResolved.body.contains("Resolved complaints")) {
                System.out.println("PASSED (HTTP 400: " + resReassignResolved.body + ")");
                passed++;
            } else {
                System.out.println("FAILED (Status=" + resReassignResolved.statusCode + ", body=" + resReassignResolved.body + ")");
            }

            // TEST 8: Reassignment Requires Mandatory Reason via Service and HTTP
            System.out.print("[TEST 8/10] Reassignment without reason is strictly rejected... ");
            Complaint c3 = gm.createComplaint(citizen, "Waste Management", "Garbage overflow", "Central Market", 80);
            gm.assignDepartmentAndStaff(c3.getComplaintId(), "Waste Management Department", "Worker Waste", admin);

            boolean serviceReasonRejected = false;
            try {
                gm.reassignComplaint(c3.getComplaintId(), "Roads Department", "Worker Roads", "", admin);
            } catch (IllegalArgumentException e) {
                serviceReasonRejected = true;
            }

            HttpResponse resHttpNoReason = sendHttpRequest("POST", "http://localhost:" + TEST_PORT + "/api/complaints/" + c3.getComplaintId() + "/reassign",
                    "{\"department\":\"Roads\",\"staffName\":\"Worker Roads\",\"reason\":\"   \"}", adminAuth);
            boolean httpReasonRejected = (resHttpNoReason.statusCode == 400 && resHttpNoReason.body.contains("reason is mandatory"));

            if (serviceReasonRejected && httpReasonRejected) {
                System.out.println("PASSED (Both service and API reject empty reason)");
                passed++;
            } else {
                System.out.println("FAILED (service=" + serviceReasonRejected + ", http=" + httpReasonRejected + ")");
            }

            // TEST 9: Worker Attempt to Reassign via HTTP API returns 403 Forbidden
            System.out.print("[TEST 9/10] Worker attempt to call /reassign via HTTP API returns 403 Forbidden... ");
            HttpResponse resWorkerReassign = sendHttpRequest("POST", "http://localhost:" + TEST_PORT + "/api/complaints/" + c3.getComplaintId() + "/reassign",
                    "{\"department\":\"Roads\",\"staffName\":\"Worker Self\",\"reason\":\"Self reassignment\"}", workerAuth);

            if (resWorkerReassign.statusCode == 403) {
                System.out.println("PASSED (HTTP 403 Forbidden)");
                passed++;
            } else {
                System.out.println("FAILED (Status=" + resWorkerReassign.statusCode + ")");
            }

            // TEST 10: Authorized Explicit Reassignment with Reason Updates Assignment and Audit
            System.out.print("[TEST 10/10] Authorized explicit reassignment with confirmation reason succeeds... ");
            HttpResponse resValidReassign = sendHttpRequest("POST", "http://localhost:" + TEST_PORT + "/api/complaints/" + c3.getComplaintId() + "/reassign",
                    "{\"department\":\"Roads\",\"staffName\":\"Officer Raman\",\"reason\":\"Department jurisdiction realignment\"}", adminAuth);

            Complaint c3Reassigned = gm.getComplaintById(c3.getComplaintId());
            boolean reassignedDept = "Roads Department".equalsIgnoreCase(c3Reassigned.getDepartment()) || "Roads".equalsIgnoreCase(c3Reassigned.getDepartment());
            boolean reassignedStaff = "Officer Raman".equalsIgnoreCase(c3Reassigned.getAssignedStaff());

            if (resValidReassign.statusCode == 200 && reassignedDept && reassignedStaff) {
                System.out.println("PASSED (HTTP 200, Department: " + c3Reassigned.getDepartment() + ", Staff: " + c3Reassigned.getAssignedStaff() + ")");
                passed++;
            } else {
                System.out.println("FAILED (Status=" + resValidReassign.statusCode + ", dept=" + reassignedDept + ", staff=" + reassignedStaff + ")");
            }

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            server.stop();
        }

        System.out.println("-------------------------------------------------------------------------------");
        System.out.println("RESULT: " + passed + " / " + total + " ASSIGNMENT VISIBILITY TESTS PASSED (" + (passed * 100 / total) + "% Success Rate)");
        System.out.println("===============================================================================");
    }

    private static class HttpResponse {
        int statusCode;
        String body;

        HttpResponse(int statusCode, String body) {
            this.statusCode = statusCode;
            this.body = body;
        }
    }

    private static HttpResponse sendHttpRequest(String method, String urlStr, String body, String authHeader) {
        try {
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod(method);
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            if (authHeader != null && !authHeader.isEmpty()) {
                conn.setRequestProperty("Authorization", authHeader);
            }
            conn.setRequestProperty("Content-Type", "application/json");

            if (body != null && !body.isEmpty()) {
                conn.setDoOutput(true);
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(body.getBytes(StandardCharsets.UTF_8));
                }
            }

            int code = conn.getResponseCode();
            InputStreamReader isr;
            if (code >= 200 && code < 400) {
                isr = new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8);
            } else if (conn.getErrorStream() != null) {
                isr = new InputStreamReader(conn.getErrorStream(), StandardCharsets.UTF_8);
            } else {
                return new HttpResponse(code, "");
            }

            BufferedReader reader = new BufferedReader(isr);
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            reader.close();
            return new HttpResponse(code, sb.toString());
        } catch (Exception e) {
            return new HttpResponse(500, e.getMessage());
        }
    }
}
