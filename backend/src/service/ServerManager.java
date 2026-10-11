package service;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import model.Complaint;
import model.ComplaintHistory;
import model.Department;
import model.DuplicateCandidate;
import model.Notification;
import model.User;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;

public class ServerManager {
    private int port;
    private Database database;
    private GrievanceManager manager;
    private HttpServer server;

    public ServerManager(int port, Database database, GrievanceManager manager) {
        this.port = port;
        this.database = database;
        this.manager = manager;
    }

    public void start() {
        try {
            server = HttpServer.create(new InetSocketAddress(port), 0);
            server.createContext("/api/", new ApiRouterHandler());

            File frontendDir = resolveFrontendDir();
            if (frontendDir != null && frontendDir.exists()) {
                server.createContext("/", new StaticFileHandler(frontendDir));
            }

            server.setExecutor(null);
            server.start();
            System.out.println("[SERVER SUCCESS] REST API Server running on http://localhost:" + port + "/api/");
            if (frontendDir != null && frontendDir.exists()) {
                System.out.println("[SERVER SUCCESS] Web Portal running at http://localhost:" + port + "/");
            }
        } catch (Exception e) {
            System.err.println("[SERVER ERROR] Could not start HTTP Server on port " + port + ": " + e.getMessage());
        }
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    private class ApiRouterHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) {
            try {
                // CORS Headers
                exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
                exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, PUT, PATCH, DELETE, OPTIONS");
                exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type, Authorization");

                String method = exchange.getRequestMethod();
                if ("OPTIONS".equalsIgnoreCase(method)) {
                    exchange.sendResponseHeaders(204, -1);
                    return;
                }

                String path = exchange.getRequestURI().getPath();
                String body = readRequestBody(exchange);

                // Authentication
                if (path.equals("/api/auth/register") && "POST".equalsIgnoreCase(method)) {
                    handleRegister(exchange, body);
                    return;
                }

                if (path.equals("/api/auth/login") && "POST".equalsIgnoreCase(method)) {
                    handleLogin(exchange, body);
                    return;
                }

                if (path.equals("/api/auth/logout") && "POST".equalsIgnoreCase(method)) {
                    handleLogout(exchange);
                    return;
                }

                if (path.equals("/api/auth/me") && "GET".equalsIgnoreCase(method)) {
                    handleGetMe(exchange);
                    return;
                }

                // Dashboard Stats & Reports
                if (path.equals("/api/dashboard/stats") && "GET".equalsIgnoreCase(method)) {
                    handleDashboardStats(exchange);
                    return;
                }

                if (path.equals("/api/reports/manager") && "GET".equalsIgnoreCase(method)) {
                    handleGetManagerReport(exchange);
                    return;
                }

                if (path.equals("/api/reports/admin") && "GET".equalsIgnoreCase(method)) {
                    handleGetAdminReport(exchange);
                    return;
                }

                // Department Capacity Info & Management
                if (path.equals("/api/departments/capacity") && "GET".equalsIgnoreCase(method)) {
                    handleDepartmentCapacity(exchange);
                    return;
                }

                if (path.equals("/api/departments/capacity") && ("PUT".equalsIgnoreCase(method) || "POST".equalsIgnoreCase(method))) {
                    handleUpdateDepartmentCapacity(exchange, body);
                    return;
                }

                // Admin Worker Management
                if (path.equals("/api/admin/workers") && "GET".equalsIgnoreCase(method)) {
                    handleGetWorkers(exchange);
                    return;
                }

                if (path.equals("/api/admin/workers") && "POST".equalsIgnoreCase(method)) {
                    handleCreateWorker(exchange, body);
                    return;
                }

                if (path.startsWith("/api/admin/workers/")) {
                    String workerSub = path.substring("/api/admin/workers/".length());
                    if (workerSub.endsWith("/reset-password") && "POST".equalsIgnoreCase(method)) {
                        String workerId = workerSub.replace("/reset-password", "");
                        handleResetWorkerPassword(exchange, workerId, body);
                        return;
                    }
                    if ("PUT".equalsIgnoreCase(method)) {
                        handleUpdateWorker(exchange, workerSub, body);
                        return;
                    }
                }

                // Notifications
                if ((path.equals("/api/notifications") || path.equals("/api/notifications/my")) && "GET".equalsIgnoreCase(method)) {
                    handleGetNotifications(exchange);
                    return;
                }

                // Complaints
                if (path.equals("/api/complaints") && "POST".equalsIgnoreCase(method)) {
                    handleCreateComplaint(exchange, body);
                    return;
                }

                if (path.equals("/api/complaints") && "GET".equalsIgnoreCase(method)) {
                    handleGetAllComplaints(exchange);
                    return;
                }

                if (path.equals("/api/my-complaints") && "GET".equalsIgnoreCase(method)) {
                    handleGetMyComplaints(exchange);
                    return;
                }

                if (path.equals("/api/worker/complaints") && "GET".equalsIgnoreCase(method)) {
                    handleGetWorkerComplaints(exchange);
                    return;
                }

                if (path.equals("/api/complaints/priority-queue") && "GET".equalsIgnoreCase(method)) {
                    handleGetPriorityQueue(exchange);
                    return;
                }

                if (path.equals("/api/complaints/duplicates") && "GET".equalsIgnoreCase(method)) {
                    handleGetDuplicates(exchange);
                    return;
                }

                // Sub-routes for specific complaint ID
                if (path.startsWith("/api/complaints/")) {
                    String subPath = path.substring("/api/complaints/".length());

                    // Verification: /api/complaints/:id/verify
                    if (subPath.endsWith("/verify") && "POST".equalsIgnoreCase(method)) {
                        String id = subPath.replace("/verify", "");
                        handleVerifyComplaint(exchange, id, body);
                        return;
                    }

                    // Duplicate candidates: /api/complaints/:id/duplicate-candidates
                    if (subPath.endsWith("/duplicate-candidates") && "GET".equalsIgnoreCase(method)) {
                        String id = subPath.replace("/duplicate-candidates", "");
                        handleGetDuplicateCandidates(exchange, id);
                        return;
                    }

                    // Duplicate confirm link: /api/complaints/:id/duplicate/confirm
                    if (subPath.endsWith("/duplicate/confirm") && "POST".equalsIgnoreCase(method)) {
                        String id = subPath.replace("/duplicate/confirm", "");
                        handleConfirmDuplicateLink(exchange, id, body);
                        return;
                    }

                    // Duplicate reject candidate: /api/complaints/:id/duplicate/reject
                    if (subPath.endsWith("/duplicate/reject") && "POST".equalsIgnoreCase(method)) {
                        String id = subPath.replace("/duplicate/reject", "");
                        handleRejectDuplicateCandidate(exchange, id, body);
                        return;
                    }

                    // Duplicate delete: DELETE /api/complaints/:id/duplicate
                    if (subPath.endsWith("/duplicate") && "DELETE".equalsIgnoreCase(method)) {
                        String id = subPath.replace("/duplicate", "");
                        handleDeleteDuplicate(exchange, id, body);
                        return;
                    }

                    // Duplicate management: POST /api/complaints/:id/duplicate
                    if (subPath.endsWith("/duplicate") && "POST".equalsIgnoreCase(method)) {
                        String id = subPath.replace("/duplicate", "");
                        handleDuplicateAction(exchange, id, body);
                        return;
                    }

                    // Audit Timeline History: /api/complaints/:id/history
                    if (subPath.endsWith("/history") && "GET".equalsIgnoreCase(method)) {
                        String id = subPath.replace("/history", "");
                        handleGetHistory(exchange, id);
                        return;
                    }

                    // Status update: /api/complaints/:id/status
                    if (subPath.endsWith("/status") && ("PUT".equalsIgnoreCase(method) || "PATCH".equalsIgnoreCase(method))) {
                        String id = subPath.replace("/status", "");
                        handleUpdateStatus(exchange, id, body);
                        return;
                    }

                    // Risk Assessment: /api/complaints/:id/assess
                    if (subPath.endsWith("/assess") && "POST".equalsIgnoreCase(method)) {
                        String id = subPath.replace("/assess", "");
                        handleAssessComplaint(exchange, id, body);
                        return;
                    }

                    // Department Assignment: /api/complaints/:id/assign
                    if (subPath.endsWith("/assign") && "POST".equalsIgnoreCase(method)) {
                        String id = subPath.replace("/assign", "");
                        handleAssignDepartment(exchange, id, body);
                        return;
                    }

                    // Department Reassignment: /api/complaints/:id/reassign
                    if (subPath.endsWith("/reassign") && "POST".equalsIgnoreCase(method)) {
                        String id = subPath.replace("/reassign", "");
                        handleReassignDepartment(exchange, id, body);
                        return;
                    }

                    // Resolve: /api/complaints/:id/resolve
                    if (subPath.endsWith("/resolve") && "POST".equalsIgnoreCase(method)) {
                        String id = subPath.replace("/resolve", "");
                        handleResolveComplaint(exchange, id, body);
                        return;
                    }

                    // Get single complaint: /api/complaints/:id
                    if ("GET".equalsIgnoreCase(method)) {
                        handleGetComplaintById(exchange, subPath);
                        return;
                    }
                }

                sendJsonResponse(exchange, 404, "{\"error\":\"Route not found: " + path + "\"}");

            } catch (Exception e) {
                System.err.println("[API ERROR] Error handling request: " + e.getMessage());
                try {
                    sendJsonResponse(exchange, 500, "{\"error\":\"Internal Server Error: " + e.getMessage() + "\"}");
                } catch (Exception ignored) {}
            }
        }
    }

    // ------------------- AUTH HANDLERS -------------------
    private void handleRegister(HttpExchange exchange, String body) throws Exception {
        String name = extractVal(body, "name");
        String email = extractVal(body, "email");
        String mobile = extractVal(body, "mobile");
        String password = extractVal(body, "password");

        if (name.isEmpty() || email.isEmpty() || password.isEmpty()) {
            sendJsonResponse(exchange, 400, "{\"error\":\"Name, email, and password are required.\"}");
            return;
        }

        User user = database.registerUser(name, email, mobile, password, "CITIZEN", "");
        if (user == null) {
            sendJsonResponse(exchange, 409, "{\"error\":\"Email is already registered.\"}");
            return;
        }

        String token = database.createSession(user);
        sendJsonResponse(exchange, 201, String.format(
                "{\"message\":\"Registration successful\",\"token\":\"%s\",\"user\":%s}",
                token, formatUserJson(user)
        ));
    }

    private void handleLogin(HttpExchange exchange, String body) throws Exception {
        String emailOrId = extractVal(body, "email");
        if (emailOrId.isEmpty()) {
            emailOrId = extractVal(body, "staffId");
        }
        String password = extractVal(body, "password");

        if (emailOrId.isEmpty() || password.isEmpty()) {
            sendJsonResponse(exchange, 400, "{\"error\":\"Email/Staff ID and password are required.\"}");
            return;
        }

        User user = database.getUserByEmail(emailOrId);
        if (user == null) {
            user = database.getUserById(emailOrId);
        }

        if (user == null || !user.getPasswordHash().equals(Database.hashPassword(password))) {
            sendJsonResponse(exchange, 401, "{\"error\":\"Invalid credentials. Check your ID/Email and password.\"}");
            return;
        }

        if (!user.isActive()) {
            sendJsonResponse(exchange, 403, "{\"error\":\"Account has been deactivated. Please contact your municipal administrator.\"}");
            return;
        }

        String token = database.createSession(user);
        sendJsonResponse(exchange, 200, String.format(
                "{\"message\":\"Login successful\",\"token\":\"%s\",\"user\":%s}",
                token, formatUserJson(user)
        ));
    }

    private void handleLogout(HttpExchange exchange) throws Exception {
        String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
        if (authHeader != null && !authHeader.isEmpty()) {
            database.invalidateSession(authHeader);
        }
        sendJsonResponse(exchange, 200, "{\"message\":\"Logged out successfully.\"}");
    }

    private void handleGetMe(HttpExchange exchange) throws Exception {
        User user = getAuthenticatedUser(exchange);
        if (user == null) {
            sendJsonResponse(exchange, 401, "{\"error\":\"Unauthorized token\"}");
            return;
        }
        sendJsonResponse(exchange, 200, formatUserJson(user));
    }

    // ------------------- COMPLAINT HANDLERS -------------------
    private void handleDashboardStats(HttpExchange exchange) throws Exception {
        Map<String, Integer> stats = manager.getDashboardStats();
        sendJsonResponse(exchange, 200, String.format(
                "{\"total\":%d,\"pending\":%d,\"inProgress\":%d,\"resolved\":%d,\"critical\":%d}",
                stats.get("total"), stats.get("pending"), stats.get("inProgress"),
                stats.get("resolved"), stats.get("critical")
        ));
    }

    private void handleDepartmentCapacity(HttpExchange exchange) throws Exception {
        List<Department> departments = manager.getAllDepartments();
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < departments.size(); i++) {
            Department d = departments.get(i);
            sb.append(String.format(
                    "{\"departmentId\":\"%s\",\"departmentName\":\"%s\",\"capacity\":%d,\"currentLoad\":%d,\"waitingCount\":%d}",
                    esc(d.getDepartmentId()), esc(d.getDepartmentName()), d.getCapacity(), d.getCurrentLoad(), d.getWaitingQueueSize()
            ));
            if (i < departments.size() - 1) sb.append(",");
        }
        sb.append("]");
        sendJsonResponse(exchange, 200, sb.toString());
    }

    private void handleCreateComplaint(HttpExchange exchange, String body) throws Exception {
        User user = getAuthenticatedUser(exchange);
        if (user == null) {
            sendJsonResponse(exchange, 401, "{\"error\":\"Unauthorized. Please log in to submit a complaint.\"}");
            return;
        }

        String title = extractVal(body, "title");
        String category = extractVal(body, "category");
        String description = extractVal(body, "description");
        String location = extractVal(body, "location");
        String impactStr = extractVal(body, "citizenReportedImpact");
        if (impactStr.isEmpty()) impactStr = extractVal(body, "impact");
        if (impactStr.isEmpty()) impactStr = extractVal(body, "affectedPeople");

        int impact = 1;
        try {
            if (!impactStr.isEmpty()) impact = Integer.parseInt(impactStr);
        } catch (Exception ignored) {}

        if (category.isEmpty() || description.isEmpty() || location.isEmpty()) {
            sendJsonResponse(exchange, 400, "{\"error\":\"Category, description, and location are required.\"}");
            return;
        }

        if (!title.isEmpty() && !description.startsWith("[" + title + "]")) {
            description = "[" + title + "] " + description;
        }

        Double latitude = null;
        Double longitude = null;
        Double accuracy = null;

        String latStr = extractVal(body, "latitude");
        String lngStr = extractVal(body, "longitude");
        String accStr = extractVal(body, "locationAccuracyMeters");
        if (accStr.isEmpty()) accStr = extractVal(body, "accuracy");

        if ("null".equalsIgnoreCase(latStr)) latStr = "";
        if ("null".equalsIgnoreCase(lngStr)) lngStr = "";
        if ("null".equalsIgnoreCase(accStr)) accStr = "";

        if (!latStr.isEmpty() || !lngStr.isEmpty()) {
            if (latStr.isEmpty() || lngStr.isEmpty()) {
                sendJsonResponse(exchange, 400, "{\"error\":\"Both latitude and longitude must be provided together.\"}");
                return;
            }
            try {
                latitude = Double.parseDouble(latStr);
                longitude = Double.parseDouble(lngStr);
            } catch (Exception e) {
                sendJsonResponse(exchange, 400, "{\"error\":\"Latitude and longitude must be valid decimal numbers.\"}");
                return;
            }
            if (!accStr.isEmpty()) {
                try {
                    accuracy = Double.parseDouble(accStr);
                } catch (Exception e) {
                    sendJsonResponse(exchange, 400, "{\"error\":\"Location accuracy must be a valid number.\"}");
                    return;
                }
            }
            try {
                Complaint.validateCoordinates(latitude, longitude, accuracy);
            } catch (IllegalArgumentException iae) {
                sendJsonResponse(exchange, 400, "{\"error\":\"" + esc(iae.getMessage()) + "\"}");
                return;
            }
        }

        // Newly registered complaints are explicitly unassessed. Client-supplied severity/priority is ignored.
        Complaint created = manager.createComplaint(user, category, description, location, impact, latitude, longitude, accuracy, title);
        sendJsonResponse(exchange, 201, formatComplaintJson(created, user));
    }

    private void handleGetAllComplaints(HttpExchange exchange) throws Exception {
        User user = getAuthenticatedUser(exchange);
        if (user == null) {
            sendJsonResponse(exchange, 401, "{\"error\":\"Unauthorized access.\"}");
            return;
        }

        List<Complaint> complaints;
        if (user.isCitizen()) {
            complaints = manager.getCitizenComplaints(user.getId());
        } else if (user.isWorker()) {
            complaints = manager.getDepartmentComplaints(user.getDepartment());
        } else {
            complaints = manager.getAllComplaints();
        }
        sendJsonResponse(exchange, 200, formatComplaintsListJson(complaints, user));
    }

    private void handleGetMyComplaints(HttpExchange exchange) throws Exception {
        User user = getAuthenticatedUser(exchange);
        if (user == null) {
            sendJsonResponse(exchange, 401, "{\"error\":\"Unauthorized access.\"}");
            return;
        }

        List<Complaint> complaints = manager.getCitizenComplaints(user.getId());
        sendJsonResponse(exchange, 200, formatComplaintsListJson(complaints, user));
    }

    private void handleGetWorkerComplaints(HttpExchange exchange) throws Exception {
        User user = getAuthenticatedUser(exchange);
        if (user == null) {
            sendJsonResponse(exchange, 401, "{\"error\":\"Unauthorized access.\"}");
            return;
        }
        if (user.isCitizen()) {
            sendJsonResponse(exchange, 403, "{\"error\":\"Forbidden: Worker or administrative role required.\"}");
            return;
        }

        String dept = user.getDepartment();
        List<Complaint> complaints = manager.getDepartmentComplaints(dept);
        sendJsonResponse(exchange, 200, formatComplaintsListJson(complaints, user));
    }

    private void handleGetPriorityQueue(HttpExchange exchange) throws Exception {
        User user = getAuthenticatedUser(exchange);
        List<Complaint> queueList = manager.getPriorityQueueList();
        sendJsonResponse(exchange, 200, formatComplaintsListJson(queueList, user));
    }

    private void handleGetComplaintById(HttpExchange exchange, String id) throws Exception {
        User user = getAuthenticatedUser(exchange);
        if (user == null) {
            sendJsonResponse(exchange, 401, "{\"error\":\"Unauthorized access.\"}");
            return;
        }

        Complaint c = manager.getComplaintById(id);
        if (c == null) {
            sendJsonResponse(exchange, 404, "{\"error\":\"Complaint not found.\"}");
            return;
        }

        // Enforce IDOR protection:
        if (user.isCitizen() && !c.getCitizenId().equals(user.getId())) {
            sendJsonResponse(exchange, 403, "{\"error\":\"Forbidden: Citizens can only access their own complaints.\"}");
            return;
        }

        if (user.isWorker() && !c.getDepartment().equalsIgnoreCase(user.getDepartment())) {
            sendJsonResponse(exchange, 403, "{\"error\":\"Forbidden: Workers cannot access complaints from other departments.\"}");
            return;
        }

        sendJsonResponse(exchange, 200, formatComplaintJson(c, user));
    }

    // ------------------- ADMIN VERIFICATION HANDLER -------------------
    private void handleVerifyComplaint(HttpExchange exchange, String id, String body) throws Exception {
        User user = getAuthenticatedUser(exchange);
        if (user == null) {
            sendJsonResponse(exchange, 401, "{\"error\":\"Unauthorized access.\"}");
            return;
        }

        if (!user.canVerifyComplaints()) {
            sendJsonResponse(exchange, 403, "{\"error\":\"Forbidden: Only Municipal Administrators can verify complaints.\"}");
            return;
        }

        String impactStr = extractVal(body, "adminVerifiedImpact");
        String reason = extractVal(body, "verificationReason");
        String severity = extractVal(body, "severity");

        if (reason.isEmpty()) {
            sendJsonResponse(exchange, 400, "{\"error\":\"Verification Reason is mandatory.\"}");
            return;
        }

        int verifiedImpact;
        try {
            verifiedImpact = Integer.parseInt(impactStr);
            if (verifiedImpact <= 0) throw new Exception();
        } catch (Exception e) {
            sendJsonResponse(exchange, 400, "{\"error\":\"Admin Verified Impact must be a valid positive integer.\"}");
            return;
        }

        try {
            boolean success = manager.verifyComplaint(id, verifiedImpact, reason, severity, user);
            if (!success) {
                sendJsonResponse(exchange, 404, "{\"error\":\"Complaint not found.\"}");
                return;
            }

            Complaint updated = manager.getComplaintById(id);
            sendJsonResponse(exchange, 200, formatComplaintJson(updated, user));
        } catch (Exception e) {
            sendJsonResponse(exchange, 400, "{\"error\":\"" + e.getMessage() + "\"}");
        }
    }

    // ------------------- DUPLICATE MANAGEMENT HANDLER -------------------
    private void handleGetDuplicates(HttpExchange exchange) throws Exception {
        User user = getAuthenticatedUser(exchange);
        if (user == null) {
            sendJsonResponse(exchange, 401, "{\"error\":\"Unauthorized access.\"}");
            return;
        }

        List<Complaint> all = manager.getAllComplaints();
        StringBuilder sb = new StringBuilder("[");
        int count = 0;
        for (Complaint c : all) {
            if (c.isDuplicate()) {
                if (count > 0) sb.append(",");
                sb.append(formatComplaintJson(c, user));
                count++;
            }
        }
        sb.append("]");
        sendJsonResponse(exchange, 200, sb.toString());
    }

    private void handleGetDuplicateCandidates(HttpExchange exchange, String id) throws Exception {
        User user = getAuthenticatedUser(exchange);
        if (user == null || !user.canManageDuplicates()) {
            sendJsonResponse(exchange, 403, "{\"error\":\"Forbidden: Staff or Admin privileges required to view duplicate candidates.\"}");
            return;
        }

        List<DuplicateCandidate> candidates = manager.findDuplicateCandidates(id);
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < candidates.size(); i++) {
            DuplicateCandidate dc = candidates.get(i);
            sb.append(String.format(
                    "{\"sourceComplaintId\":\"%s\",\"candidateComplaintId\":\"%s\",\"reason\":\"%s\",\"category\":\"%s\",\"location\":\"%s\",\"status\":\"%s\",\"citizenName\":\"%s\",\"createdAt\":\"%s\"}",
                    esc(dc.getSourceComplaintId()), esc(dc.getCandidateComplaintId()), esc(dc.getReason()),
                    esc(dc.getCategory()), esc(dc.getLocation()), esc(dc.getStatus()),
                    esc(dc.getCitizenName()), esc(dc.getCreatedAt())
            ));
            if (i < candidates.size() - 1) sb.append(",");
        }
        sb.append("]");
        sendJsonResponse(exchange, 200, sb.toString());
    }

    private void handleConfirmDuplicateLink(HttpExchange exchange, String id, String body) throws Exception {
        User user = getAuthenticatedUser(exchange);
        if (user == null || !user.canConfirmDuplicateLink()) {
            sendJsonResponse(exchange, 403, "{\"error\":\"Forbidden: Manager or Admin privileges required to confirm duplicate link.\"}");
            return;
        }

        String primaryId = extractVal(body, "primaryComplaintId");
        if (primaryId.isEmpty()) primaryId = extractVal(body, "candidateId");
        String remarks = extractVal(body, "remarks");
        if (remarks.isEmpty()) remarks = extractVal(body, "reason");

        if (primaryId.isEmpty()) {
            sendJsonResponse(exchange, 400, "{\"error\":\"primaryComplaintId is required.\"}");
            return;
        }

        try {
            boolean success = manager.confirmDuplicateLink(id, primaryId, user, remarks);
            if (!success) {
                sendJsonResponse(exchange, 400, "{\"error\":\"Could not confirm duplicate link.\"}");
                return;
            }
            sendJsonResponse(exchange, 200, "{\"message\":\"Duplicate link confirmed successfully to " + esc(primaryId) + "\"}");
        } catch (IllegalStateException | IllegalArgumentException | SecurityException e) {
            sendJsonResponse(exchange, 400, "{\"error\":\"" + esc(e.getMessage()) + "\"}");
        }
    }

    private void handleRejectDuplicateCandidate(HttpExchange exchange, String id, String body) throws Exception {
        User user = getAuthenticatedUser(exchange);
        if (user == null || !user.canManageDuplicates()) {
            sendJsonResponse(exchange, 403, "{\"error\":\"Forbidden: Staff, Manager, or Admin privileges required to reject duplicate candidate.\"}");
            return;
        }

        String candidateId = extractVal(body, "candidateComplaintId");
        if (candidateId.isEmpty()) candidateId = extractVal(body, "candidateId");
        String remarks = extractVal(body, "remarks");
        if (remarks.isEmpty()) remarks = extractVal(body, "reason");

        if (candidateId.isEmpty()) {
            sendJsonResponse(exchange, 400, "{\"error\":\"candidateComplaintId is required.\"}");
            return;
        }

        try {
            boolean success = manager.rejectDuplicateCandidate(id, candidateId, user, remarks);
            if (!success) {
                sendJsonResponse(exchange, 400, "{\"error\":\"Could not reject duplicate candidate.\"}");
                return;
            }
            sendJsonResponse(exchange, 200, "{\"message\":\"Duplicate candidate rejected successfully.\"}");
        } catch (IllegalStateException | IllegalArgumentException | SecurityException e) {
            sendJsonResponse(exchange, 400, "{\"error\":\"" + esc(e.getMessage()) + "\"}");
        }
    }

    private void handleDuplicateAction(HttpExchange exchange, String id, String body) throws Exception {
        User user = getAuthenticatedUser(exchange);
        if (user == null || !user.canManageDuplicates()) {
            sendJsonResponse(exchange, 403, "{\"error\":\"Forbidden: Staff, Manager, or Admin privileges required.\"}");
            return;
        }

        String action = extractVal(body, "action"); // "confirm", "merge", "reject", "separate", or "delete"
        if ("delete".equalsIgnoreCase(action)) {
            handleDeleteDuplicate(exchange, id, body);
            return;
        }

        if ("confirm".equalsIgnoreCase(action)) {
            if (!user.canConfirmDuplicateLink()) {
                sendJsonResponse(exchange, 403, "{\"error\":\"Forbidden: Manager or Admin privileges required to confirm duplicate link.\"}");
                return;
            }
            String primaryId = extractVal(body, "primaryComplaintId");
            if (primaryId.isEmpty()) primaryId = extractVal(body, "candidateId");
            String remarks = extractVal(body, "remarks");
            if (remarks.isEmpty()) remarks = extractVal(body, "reason");
            try {
                boolean success = manager.confirmDuplicateLink(id, primaryId, user, remarks);
                if (!success) {
                    sendJsonResponse(exchange, 400, "{\"error\":\"Could not confirm duplicate link.\"}");
                    return;
                }
                sendJsonResponse(exchange, 200, "{\"message\":\"Duplicate link confirmed successfully to " + esc(primaryId) + "\"}");
            } catch (IllegalStateException | IllegalArgumentException | SecurityException e) {
                sendJsonResponse(exchange, 400, "{\"error\":\"" + esc(e.getMessage()) + "\"}");
            }
        } else if ("reject".equalsIgnoreCase(action)) {
            String candidateId = extractVal(body, "candidateComplaintId");
            if (candidateId.isEmpty()) candidateId = extractVal(body, "candidateId");
            String remarks = extractVal(body, "remarks");
            if (remarks.isEmpty()) remarks = extractVal(body, "reason");
            try {
                boolean success = manager.rejectDuplicateCandidate(id, candidateId, user, remarks);
                if (!success) {
                    sendJsonResponse(exchange, 400, "{\"error\":\"Could not reject duplicate candidate.\"}");
                    return;
                }
                sendJsonResponse(exchange, 200, "{\"message\":\"Duplicate candidate rejected successfully.\"}");
            } catch (IllegalStateException | IllegalArgumentException | SecurityException e) {
                sendJsonResponse(exchange, 400, "{\"error\":\"" + esc(e.getMessage()) + "\"}");
            }
        } else if ("merge".equalsIgnoreCase(action)) {
            String primaryId = extractVal(body, "primaryComplaintId");
            if (primaryId.isEmpty()) primaryId = extractVal(body, "candidateId");
            String reason = extractVal(body, "reason");
            if (reason.isEmpty()) reason = extractVal(body, "remarks");
            try {
                boolean success = manager.mergeDuplicate(id, primaryId, user, reason);
                if (!success) {
                    sendJsonResponse(exchange, 400, "{\"error\":\"Could not merge duplicate complaint.\"}");
                    return;
                }
                sendJsonResponse(exchange, 200, "{\"message\":\"Duplicate merged successfully into " + esc(primaryId) + "\"}");
            } catch (IllegalStateException | IllegalArgumentException | SecurityException e) {
                sendJsonResponse(exchange, 400, "{\"error\":\"" + esc(e.getMessage()) + "\"}");
            }
        } else if ("separate".equalsIgnoreCase(action)) {
            try {
                boolean success = manager.keepSeparate(id, user);
                if (!success) {
                    sendJsonResponse(exchange, 400, "{\"error\":\"Could not mark complaint as separate.\"}");
                    return;
                }
                sendJsonResponse(exchange, 200, "{\"message\":\"Complaint marked as independent separate case.\"}");
            } catch (IllegalStateException | IllegalArgumentException | SecurityException e) {
                sendJsonResponse(exchange, 400, "{\"error\":\"" + esc(e.getMessage()) + "\"}");
            }
        } else {
            sendJsonResponse(exchange, 400, "{\"error\":\"Invalid duplicate action. Use 'confirm', 'reject', 'merge', 'separate', or 'delete'.\"}");
        }
    }

    private void handleDeleteDuplicate(HttpExchange exchange, String id, String body) throws Exception {
        User user = getAuthenticatedUser(exchange);
        if (user == null || !user.canDeleteDuplicate()) {
            sendJsonResponse(exchange, 403, "{\"error\":\"Forbidden: Only administrators or managers can delete confirmed duplicates.\"}");
            return;
        }

        String reason = extractVal(body, "confirmationReason");
        if (reason.isEmpty()) reason = extractVal(body, "reason");
        if (reason.isEmpty()) reason = extractVal(body, "remarks");

        if (reason.isEmpty()) {
            sendJsonResponse(exchange, 400, "{\"error\":\"A formal confirmation reason is required to delete a confirmed duplicate.\"}");
            return;
        }

        try {
            boolean success = manager.deleteConfirmedDuplicate(id, user, reason);
            if (!success) {
                sendJsonResponse(exchange, 404, "{\"error\":\"Complaint not found or could not be deleted.\"}");
                return;
            }
            sendJsonResponse(exchange, 200, "{\"message\":\"Confirmed duplicate complaint " + esc(id) + " deleted successfully.\"}");
        } catch (IllegalStateException | IllegalArgumentException | SecurityException e) {
            sendJsonResponse(exchange, 400, "{\"error\":\"" + esc(e.getMessage()) + "\"}");
        }
    }

    // ------------------- AUDIT HISTORY HANDLER -------------------
    private void handleGetHistory(HttpExchange exchange, String id) throws Exception {
        List<ComplaintHistory> history = manager.getComplaintTimeline(id);
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < history.size(); i++) {
            ComplaintHistory h = history.get(i);
            sb.append(String.format(
                    "{\"historyId\":\"%s\",\"complaintId\":\"%s\",\"oldStatus\":\"%s\",\"newStatus\":\"%s\",\"updatedBy\":\"%s\",\"updateTime\":\"%s\",\"remarks\":\"%s\"}",
                    esc(h.getHistoryId()), esc(h.getComplaintId()), esc(h.getOldStatus()),
                    esc(h.getNewStatus()), esc(h.getUpdatedBy()), esc(h.getUpdateTime()), esc(h.getRemarks())
            ));
            if (i < history.size() - 1) sb.append(",");
        }
        sb.append("]");
        sendJsonResponse(exchange, 200, sb.toString());
    }

    // ------------------- NOTIFICATIONS HANDLER -------------------
    private void handleGetNotifications(HttpExchange exchange) throws Exception {
        User user = getAuthenticatedUser(exchange);
        if (user == null) {
            sendJsonResponse(exchange, 401, "{\"error\":\"Unauthorized access.\"}");
            return;
        }

        List<Notification> list = manager.getCitizenNotifications(user.getId());
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            Notification n = list.get(i);
            sb.append(String.format(
                    "{\"notificationId\":\"%s\",\"complaintId\":\"%s\",\"originalImpact\":%d,\"verifiedImpact\":%d,\"verificationReason\":\"%s\",\"title\":\"%s\",\"message\":\"%s\",\"createdAt\":\"%s\",\"isRead\":%b}",
                    esc(n.getNotificationId()), esc(n.getComplaintId()), n.getOriginalImpact(),
                    n.getVerifiedImpact(), esc(n.getVerificationReason()), esc(n.getTitle()),
                    esc(n.getMessage()), esc(n.getCreatedAt()), n.isRead()
            ));
            if (i < list.size() - 1) sb.append(",");
        }
        sb.append("]");
        sendJsonResponse(exchange, 200, sb.toString());
    }

    // ------------------- STATUS & ASSIGN HANDLERS -------------------
    private void handleUpdateStatus(HttpExchange exchange, String id, String body) throws Exception {
        User user = getAuthenticatedUser(exchange);
        if (user == null || !user.canUpdateStatus()) {
            sendJsonResponse(exchange, 403, "{\"error\":\"Forbidden. Staff or Admin access required.\"}");
            return;
        }

        Complaint existing = manager.getComplaintById(id);
        if (existing == null) {
            sendJsonResponse(exchange, 404, "{\"error\":\"Complaint not found.\"}");
            return;
        }

        // Workers can only update complaints within their assigned department
        if (user.isWorker() && !existing.getDepartment().equalsIgnoreCase(user.getDepartment())) {
            sendJsonResponse(exchange, 403, "{\"error\":\"Forbidden: Workers can only update complaints within their department.\"}");
            return;
        }

        boolean explicitCancellation = "true".equalsIgnoreCase(extractVal(body, "explicitCancellation"));
        boolean explicitReassignment = "true".equalsIgnoreCase(extractVal(body, "explicitReassignment"));

        if (user.isWorker() && (explicitCancellation || explicitReassignment)) {
            sendJsonResponse(exchange, 403, "{\"error\":\"Forbidden: Workers cannot cancel assignments or reassign complaints.\"}");
            return;
        }

        String status = extractVal(body, "status");
        String remarks = extractVal(body, "remarks");

        if (status.isEmpty()) {
            sendJsonResponse(exchange, 400, "{\"error\":\"Status field is required.\"}");
            return;
        }

        try {
            boolean success = manager.updateStatus(id, status, remarks, user, explicitCancellation, explicitReassignment);
            if (!success) {
                sendJsonResponse(exchange, 404, "{\"error\":\"Complaint not found.\"}");
                return;
            }

            Complaint updated = manager.getComplaintById(id);
            sendJsonResponse(exchange, 200, formatComplaintJson(updated, user));
        } catch (IllegalStateException | IllegalArgumentException e) {
            sendJsonResponse(exchange, 400, "{\"error\":\"" + esc(e.getMessage()) + "\"}");
        }
    }

    private void handleAssessComplaint(HttpExchange exchange, String id, String body) throws Exception {
        User user = getAuthenticatedUser(exchange);
        if (user == null || !user.canAssessSeverity()) {
            sendJsonResponse(exchange, 403, "{\"error\":\"Forbidden: Only Managers or Administrators can assess complaint severity.\"}");
            return;
        }

        String severity = extractVal(body, "severity");
        String reason = extractVal(body, "reason");
        if (reason.isEmpty()) reason = extractVal(body, "assessmentReason");
        if (reason.isEmpty()) reason = extractVal(body, "remarks");

        String department = extractVal(body, "department");
        if (department.isEmpty()) department = extractVal(body, "departmentName");
        String staffName = extractVal(body, "staffName");

        try {
            boolean success = manager.assessComplaint(id, severity, reason, department, staffName, user);
            if (!success) {
                sendJsonResponse(exchange, 404, "{\"error\":\"Complaint not found.\"}");
                return;
            }

            Complaint updated = manager.getComplaintById(id);
            sendJsonResponse(exchange, 200, formatComplaintJson(updated, user));
        } catch (IllegalArgumentException | IllegalStateException | SecurityException e) {
            sendJsonResponse(exchange, 400, "{\"error\":\"" + esc(e.getMessage()) + "\"}");
        }
    }

    private void handleAssignDepartment(HttpExchange exchange, String id, String body) throws Exception {
        User user = getAuthenticatedUser(exchange);
        if (user == null || !user.canAssignDepartment()) {
            sendJsonResponse(exchange, 403, "{\"error\":\"Forbidden: Manager or Admin access required to assign departments.\"}");
            return;
        }

        String severity = extractVal(body, "severity");
        String department = extractVal(body, "department");
        String staffName = extractVal(body, "staffName");
        String reason = extractVal(body, "reason");
        if (reason.isEmpty()) reason = extractVal(body, "assessmentReason");
        if (reason.isEmpty()) reason = extractVal(body, "remarks");

        // If severity is supplied, route through risk assessment & assignment
        if (!severity.isEmpty()) {
            if (reason.isEmpty()) reason = "Assigned by " + user.getDisplayName();
            try {
                boolean success = manager.assessComplaint(id, severity, reason, department, staffName, user);
                if (!success) {
                    sendJsonResponse(exchange, 404, "{\"error\":\"Complaint not found.\"}");
                    return;
                }

                Complaint updated = manager.getComplaintById(id);
                sendJsonResponse(exchange, 200, formatComplaintJson(updated, user));
                return;
            } catch (IllegalArgumentException | IllegalStateException | SecurityException e) {
                sendJsonResponse(exchange, 400, "{\"error\":\"" + esc(e.getMessage()) + "\"}");
                return;
            }
        }

        try {
            boolean success = manager.assignDepartmentAndStaff(id, department, staffName, user);
            if (!success) {
                sendJsonResponse(exchange, 404, "{\"error\":\"Complaint not found.\"}");
                return;
            }

            Complaint updated = manager.getComplaintById(id);
            sendJsonResponse(exchange, 200, formatComplaintJson(updated, user));
        } catch (IllegalStateException | IllegalArgumentException e) {
            sendJsonResponse(exchange, 400, "{\"error\":\"" + esc(e.getMessage()) + "\"}");
        }
    }

    private void handleReassignDepartment(HttpExchange exchange, String id, String body) throws Exception {
        User user = getAuthenticatedUser(exchange);
        if (user == null || !user.canAssignDepartment()) {
            sendJsonResponse(exchange, 403, "{\"error\":\"Forbidden: Manager or Admin access required to reassign departments.\"}");
            return;
        }

        String department = extractVal(body, "department");
        String staffName = extractVal(body, "staffName");
        String reason = extractVal(body, "reason");

        try {
            boolean success = manager.reassignComplaint(id, department, staffName, reason, user);
            if (!success) {
                sendJsonResponse(exchange, 404, "{\"error\":\"Complaint not found.\"}");
                return;
            }

            Complaint updated = manager.getComplaintById(id);
            sendJsonResponse(exchange, 200, formatComplaintJson(updated, user));
        } catch (IllegalStateException | IllegalArgumentException e) {
            sendJsonResponse(exchange, 400, "{\"error\":\"" + esc(e.getMessage()) + "\"}");
        }
    }

    private void handleResolveComplaint(HttpExchange exchange, String id, String body) throws Exception {
        User user = getAuthenticatedUser(exchange);
        if (user == null || !user.canUpdateStatus()) {
            sendJsonResponse(exchange, 403, "{\"error\":\"Forbidden: Staff access required.\"}");
            return;
        }

        Complaint existing = manager.getComplaintById(id);
        if (existing == null) {
            sendJsonResponse(exchange, 404, "{\"error\":\"Complaint not found.\"}");
            return;
        }

        if (user.isWorker() && !existing.getDepartment().equalsIgnoreCase(user.getDepartment())) {
            sendJsonResponse(exchange, 403, "{\"error\":\"Forbidden: Workers can only resolve complaints within their department.\"}");
            return;
        }

        String resolutionNotes = extractVal(body, "resolutionNotes");
        try {
            boolean success = manager.resolveComplaint(id, resolutionNotes, user);
            if (!success) {
                sendJsonResponse(exchange, 404, "{\"error\":\"Complaint not found.\"}");
                return;
            }

            Complaint updated = manager.getComplaintById(id);
            sendJsonResponse(exchange, 200, formatComplaintJson(updated, user));
        } catch (IllegalStateException | IllegalArgumentException e) {
            sendJsonResponse(exchange, 400, "{\"error\":\"" + esc(e.getMessage()) + "\"}");
        }
    }

    // ------------------- ADMIN WORKER MANAGEMENT HANDLERS -------------------
    private void handleGetWorkers(HttpExchange exchange) throws Exception {
        User user = getAuthenticatedUser(exchange);
        if (user == null) {
            sendJsonResponse(exchange, 401, "{\"error\":\"Unauthorized: Please log in.\"}");
            return;
        }
        if (!user.canManageWorkers()) {
            sendJsonResponse(exchange, 403, "{\"error\":\"Forbidden: Administrator privileges required to manage workers.\"}");
            return;
        }

        List<model.DepartmentStaff> workers = database.getAllWorkers();
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < workers.size(); i++) {
            model.DepartmentStaff w = workers.get(i);
            sb.append(String.format(
                    "{\"id\":\"%s\",\"name\":\"%s\",\"email\":\"%s\",\"mobile\":\"%s\",\"department\":\"%s\",\"role\":\"%s\",\"active\":%b,\"createdAt\":\"%s\"}",
                    esc(w.getId()), esc(w.getDisplayName()), esc(w.getEmail()), esc(w.getMobile()),
                    esc(w.getDepartment()), esc(w.getRole()), w.isActive(), esc(w.getCreatedAt())
            ));
            if (i < workers.size() - 1) sb.append(",");
        }
        sb.append("]");
        sendJsonResponse(exchange, 200, sb.toString());
    }

    private void handleCreateWorker(HttpExchange exchange, String body) throws Exception {
        User user = getAuthenticatedUser(exchange);
        if (user == null) {
            sendJsonResponse(exchange, 401, "{\"error\":\"Unauthorized: Please log in.\"}");
            return;
        }
        if (!user.canManageWorkers()) {
            sendJsonResponse(exchange, 403, "{\"error\":\"Forbidden: Administrator privileges required.\"}");
            return;
        }

        String name = extractVal(body, "name");
        String email = extractVal(body, "email");
        String mobile = extractVal(body, "mobile");
        String password = extractVal(body, "password");
        String department = extractVal(body, "department");

        if (name.isEmpty() || email.isEmpty() || password.isEmpty() || department.isEmpty()) {
            sendJsonResponse(exchange, 400, "{\"error\":\"Name, email, password, and department are required.\"}");
            return;
        }

        try {
            User worker = database.createWorker(name, email, mobile, password, department, user);
            sendJsonResponse(exchange, 201, String.format(
                    "{\"message\":\"Worker created successfully\",\"worker\":{\"id\":\"%s\",\"name\":\"%s\",\"email\":\"%s\",\"department\":\"%s\",\"role\":\"%s\",\"active\":%b}}",
                    esc(worker.getId()), esc(worker.getDisplayName()), esc(worker.getEmail()), esc(worker.getDepartment()), esc(worker.getRole()), worker.isActive()
            ));
        } catch (IllegalArgumentException e) {
            sendJsonResponse(exchange, 400, "{\"error\":\"" + esc(e.getMessage()) + "\"}");
        }
    }

    private void handleUpdateWorker(HttpExchange exchange, String workerId, String body) throws Exception {
        User user = getAuthenticatedUser(exchange);
        if (user == null) {
            sendJsonResponse(exchange, 401, "{\"error\":\"Unauthorized: Please log in.\"}");
            return;
        }
        if (!user.canManageWorkers()) {
            sendJsonResponse(exchange, 403, "{\"error\":\"Forbidden: Administrator privileges required.\"}");
            return;
        }

        String name = extractVal(body, "name");
        String mobile = extractVal(body, "mobile");
        String department = extractVal(body, "department");
        String activeStr = extractVal(body, "active");
        Boolean active = activeStr.isEmpty() ? null : "true".equalsIgnoreCase(activeStr);

        try {
            User updated = database.updateWorker(workerId, name, mobile, department, active, user);
            sendJsonResponse(exchange, 200, String.format(
                    "{\"message\":\"Worker updated successfully\",\"worker\":{\"id\":\"%s\",\"name\":\"%s\",\"email\":\"%s\",\"department\":\"%s\",\"role\":\"%s\",\"active\":%b}}",
                    esc(updated.getId()), esc(updated.getDisplayName()), esc(updated.getEmail()), esc(updated.getDepartment()), esc(updated.getRole()), updated.isActive()
            ));
        } catch (IllegalArgumentException e) {
            sendJsonResponse(exchange, 400, "{\"error\":\"" + esc(e.getMessage()) + "\"}");
        }
    }

    private void handleResetWorkerPassword(HttpExchange exchange, String workerId, String body) throws Exception {
        User user = getAuthenticatedUser(exchange);
        if (user == null) {
            sendJsonResponse(exchange, 401, "{\"error\":\"Unauthorized: Please log in.\"}");
            return;
        }
        if (!user.canManageWorkers()) {
            sendJsonResponse(exchange, 403, "{\"error\":\"Forbidden: Administrator privileges required.\"}");
            return;
        }

        String newPassword = extractVal(body, "password");
        if (newPassword.isEmpty()) newPassword = extractVal(body, "newPassword");

        if (newPassword.isEmpty()) {
            sendJsonResponse(exchange, 400, "{\"error\":\"New password is required.\"}");
            return;
        }

        try {
            boolean success = database.resetWorkerPassword(workerId, newPassword, user);
            if (!success) {
                sendJsonResponse(exchange, 404, "{\"error\":\"Worker not found.\"}");
                return;
            }
            sendJsonResponse(exchange, 200, "{\"message\":\"Worker password reset successfully.\"}");
        } catch (IllegalArgumentException e) {
            sendJsonResponse(exchange, 400, "{\"error\":\"" + esc(e.getMessage()) + "\"}");
        }
    }

    // ------------------- CAPACITY & REPORTS HANDLERS -------------------
    private void handleUpdateDepartmentCapacity(HttpExchange exchange, String body) throws Exception {
        User user = getAuthenticatedUser(exchange);
        if (user == null || !user.isAdmin()) {
            sendJsonResponse(exchange, 403, "{\"error\":\"Forbidden: Administrator privileges required.\"}");
            return;
        }

        String dept = extractVal(body, "department");
        if (dept.isEmpty()) dept = extractVal(body, "departmentId");
        String capStr = extractVal(body, "capacity");

        if (dept.isEmpty() || capStr.isEmpty()) {
            sendJsonResponse(exchange, 400, "{\"error\":\"Department and capacity are required.\"}");
            return;
        }

        int cap;
        try {
            cap = Integer.parseInt(capStr);
        } catch (Exception e) {
            sendJsonResponse(exchange, 400, "{\"error\":\"Capacity must be a positive integer.\"}");
            return;
        }

        try {
            boolean success = manager.updateDepartmentCapacity(dept, cap, user);
            if (!success) {
                sendJsonResponse(exchange, 404, "{\"error\":\"Department not found: " + esc(dept) + "\"}");
                return;
            }
            sendJsonResponse(exchange, 200, "{\"message\":\"Department capacity updated successfully.\"}");
        } catch (IllegalArgumentException | SecurityException e) {
            sendJsonResponse(exchange, 400, "{\"error\":\"" + esc(e.getMessage()) + "\"}");
        }
    }

    private void handleGetManagerReport(HttpExchange exchange) throws Exception {
        User user = getAuthenticatedUser(exchange);
        if (user == null || (!user.isManager() && !user.isAdmin())) {
            sendJsonResponse(exchange, 403, "{\"error\":\"Forbidden: Manager or Administrator privileges required.\"}");
            return;
        }

        String reportJson = manager.getManagerReportJson();
        sendJsonResponse(exchange, 200, reportJson);
    }

    private void handleGetAdminReport(HttpExchange exchange) throws Exception {
        User user = getAuthenticatedUser(exchange);
        if (user == null || !user.isAdmin()) {
            sendJsonResponse(exchange, 403, "{\"error\":\"Forbidden: Administrator privileges required.\"}");
            return;
        }

        String reportJson = manager.getAdminReportJson();
        sendJsonResponse(exchange, 200, reportJson);
    }

    // ------------------- HELPERS & SERIALIZERS -------------------
    private User getAuthenticatedUser(HttpExchange exchange) {
        String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
        if (authHeader != null && !authHeader.isEmpty()) {
            return database.getUserByToken(authHeader);
        }
        return null;
    }

    private String readRequestBody(HttpExchange exchange) throws Exception {
        InputStream is = exchange.getRequestBody();
        StringBuilder sb = new StringBuilder();
        byte[] buffer = new byte[1024];
        int bytesRead;
        while ((bytesRead = is.read(buffer)) != -1) {
            sb.append(new String(buffer, 0, bytesRead, StandardCharsets.UTF_8));
        }
        return sb.toString();
    }

    private void sendJsonResponse(HttpExchange exchange, int statusCode, String jsonResponse) throws Exception {
        byte[] responseBytes = jsonResponse.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(statusCode, responseBytes.length);
        OutputStream os = exchange.getResponseBody();
        os.write(responseBytes);
        os.close();
    }

    private String extractVal(String json, String key) {
        String pattern = "\"" + key + "\":\"";
        int start = json.indexOf(pattern);
        if (start == -1) {
            pattern = "\"" + key + "\":";
            start = json.indexOf(pattern);
            if (start == -1) return "";
            start += pattern.length();
            int end = json.indexOf(",", start);
            if (end == -1) end = json.indexOf("}", start);
            if (end == -1) return "";
            return json.substring(start, end).replaceAll("[\"\\}]", "").trim();
        }
        start += pattern.length();
        int end = json.indexOf("\"", start);
        if (end == -1) return "";
        return json.substring(start, end).replace("\\\"", "\"").replace("\\n", "\n");
    }

    private String formatUserJson(User u) {
        return String.format(
                "{\"id\":\"%s\",\"name\":\"%s\",\"email\":\"%s\",\"mobile\":\"%s\",\"role\":\"%s\",\"department\":\"%s\",\"active\":%b,\"createdAt\":\"%s\"}",
                esc(u.getId()), esc(u.getDisplayName()), esc(u.getEmail()), esc(u.getMobile()), esc(u.getRole()), esc(u.getDepartment()), u.isActive(), esc(u.getCreatedAt())
        );
    }

    private String formatComplaintJson(Complaint c) {
        return formatComplaintJson(c, null);
    }

    private String formatComplaintJson(Complaint c, User requestingUser) {
        if (c == null) return "null";
        StringBuilder rejs = new StringBuilder("[");
        int rIdx = 0;
        if (c.getRejectedCandidateIds() != null) {
            for (String rId : c.getRejectedCandidateIds()) {
                if (rIdx > 0) rejs.append(",");
                rejs.append("\"").append(esc(rId)).append("\"");
                rIdx++;
            }
        }
        rejs.append("]");

        boolean authorizedForGps = false;
        if (requestingUser != null) {
            if (requestingUser.isAdmin() || requestingUser.isManager()) {
                authorizedForGps = true;
            } else if (requestingUser.isWorker()) {
                if (c.getDepartment() == null || c.getDepartment().equalsIgnoreCase(requestingUser.getDepartment()) ||
                    c.getDepartmentId() == null || c.getDepartmentId().equalsIgnoreCase(requestingUser.getDepartment())) {
                    authorizedForGps = true;
                }
            } else if (requestingUser.isCitizen()) {
                if (c.getCitizenId() != null && c.getCitizenId().equals(requestingUser.getId())) {
                    authorizedForGps = true;
                }
            }
        }

        String latVal = (authorizedForGps && c.getLatitude() != null) ? String.valueOf(c.getLatitude()) : "null";
        String lngVal = (authorizedForGps && c.getLongitude() != null) ? String.valueOf(c.getLongitude()) : "null";
        String accVal = (authorizedForGps && c.getLocationAccuracyMeters() != null) ? String.valueOf(c.getLocationAccuracyMeters()) : "null";
        String mapsUrl = (authorizedForGps && c.getGoogleMapsUrl() != null) ? c.getGoogleMapsUrl() : "";
        boolean hasGps = authorizedForGps && c.hasGpsCoordinates();

        return String.format(
                "{\"id\":\"%s\",\"complaintId\":\"%s\",\"citizenId\":\"%s\",\"citizenName\":\"%s\",\"citizenMobile\":\"%s\",\"category\":\"%s\",\"description\":\"%s\",\"location\":\"%s\",\"latitude\":%s,\"longitude\":%s,\"locationAccuracyMeters\":%s,\"googleMapsUrl\":\"%s\",\"hasGpsCoordinates\":%b,\"departmentId\":\"%s\",\"department\":\"%s\",\"citizenReportedImpact\":%d,\"adminVerifiedImpact\":%s,\"verificationReason\":\"%s\",\"isVerifiedLocked\":%b,\"severity\":\"%s\",\"priority\":\"%s\",\"isAssessed\":%b,\"assessedBy\":\"%s\",\"assessedAt\":\"%s\",\"assessmentReason\":\"%s\",\"status\":\"%s\",\"assignedStaff\":\"%s\",\"resolutionNotes\":\"%s\",\"isDuplicate\":%b,\"duplicateOfId\":\"%s\",\"rejectedCandidateIds\":%s,\"isWaiting\":%b,\"arrivalSequence\":%d,\"createdAt\":\"%s\",\"updatedAt\":\"%s\",\"resolvedAt\":\"%s\"}",
                esc(c.getId()), esc(c.getComplaintId()), esc(c.getCitizenId()), esc(c.getCitizenName()),
                esc(c.getCitizenMobile()), esc(c.getCategory()), esc(c.getDescription()), esc(c.getLocation()),
                latVal, lngVal, accVal, esc(mapsUrl), hasGps,
                esc(c.getDepartmentId()), esc(c.getDepartment()), c.getCitizenReportedImpact(),
                (c.getAdminVerifiedImpact() == null ? "null" : c.getAdminVerifiedImpact()),
                esc(c.getVerificationReason()), c.isVerifiedLocked(),
                esc(c.getSeverity() == null ? "UNASSESSED" : c.getSeverity()),
                esc(c.getPriority() == null ? "UNASSESSED" : c.getPriority()),
                c.isAssessed(),
                esc(c.getAssessedBy()),
                esc(c.getAssessedAt()),
                esc(c.getAssessmentReason()),
                esc(c.getStatus()), esc(c.getAssignedStaff()),
                esc(c.getResolutionNotes()), c.isDuplicate(), esc(c.getDuplicateOfId()),
                rejs.toString(),
                c.isWaiting(),
                c.getArrivalSequence(),
                esc(c.getCreatedAt()), esc(c.getUpdatedAt()), esc(c.getResolvedAt())
        );
    }

    private String formatComplaintsListJson(List<Complaint> list) {
        return formatComplaintsListJson(list, null);
    }

    private String formatComplaintsListJson(List<Complaint> list, User user) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            sb.append(formatComplaintJson(list.get(i), user));
            if (i < list.size() - 1) sb.append(",");
        }
        sb.append("]");
        return sb.toString();
    }

    private String esc(String str) {
        if (str == null) return "";
        return str.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "");
    }

    private static File resolveFrontendDir() {
        if (new File("frontend").isDirectory()) {
            return new File("frontend");
        }
        if (new File("../frontend").isDirectory()) {
            return new File("../frontend");
        }
        return null;
    }

    private static class StaticFileHandler implements HttpHandler {
        private final File baseDir;

        public StaticFileHandler(File baseDir) {
            this.baseDir = baseDir;
        }

        @Override
        public void handle(HttpExchange exchange) {
            try {
                String path = exchange.getRequestURI().getPath();
                if (path == null || path.equals("/") || path.isEmpty()) {
                    path = "/index.html";
                }

                File targetFile = new File(baseDir, path.replace("/", File.separator)).getCanonicalFile();
                if (!targetFile.getPath().startsWith(baseDir.getCanonicalPath()) || !targetFile.exists() || targetFile.isDirectory()) {
                    String notFound = "404 Not Found";
                    exchange.sendResponseHeaders(404, notFound.length());
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(notFound.getBytes(StandardCharsets.UTF_8));
                    }
                    return;
                }

                String contentType = "application/octet-stream";
                String fileName = targetFile.getName().toLowerCase();
                if (fileName.endsWith(".html")) contentType = "text/html; charset=UTF-8";
                else if (fileName.endsWith(".css")) contentType = "text/css; charset=UTF-8";
                else if (fileName.endsWith(".js")) contentType = "application/javascript; charset=UTF-8";
                else if (fileName.endsWith(".json")) contentType = "application/json; charset=UTF-8";
                else if (fileName.endsWith(".png")) contentType = "image/png";
                else if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg")) contentType = "image/jpeg";
                else if (fileName.endsWith(".svg")) contentType = "image/svg+xml";

                byte[] bytes = Files.readAllBytes(targetFile.toPath());
                exchange.getResponseHeaders().set("Content-Type", contentType);
                exchange.sendResponseHeaders(200, bytes.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(bytes);
                }
            } catch (Exception e) {
                try {
                    exchange.sendResponseHeaders(500, -1);
                } catch (Exception ignored) {}
            }
        }
    }
}
