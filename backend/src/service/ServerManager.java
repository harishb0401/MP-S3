package service;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import model.Complaint;
import model.ComplaintHistory;
import model.Department;
import model.Notification;
import model.User;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
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
            server.setExecutor(null);
            server.start();
            System.out.println("[SERVER SUCCESS] REST API Server running on http://localhost:" + port + "/api/");
        } catch (Exception e) {
            System.err.println("[SERVER ERROR] Could not start HTTP Server on port " + port + ": " + e.getMessage());
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

                if (path.equals("/api/auth/me") && "GET".equalsIgnoreCase(method)) {
                    handleGetMe(exchange);
                    return;
                }

                // Dashboard Stats
                if (path.equals("/api/dashboard/stats") && "GET".equalsIgnoreCase(method)) {
                    handleDashboardStats(exchange);
                    return;
                }

                // Department Capacity Info
                if (path.equals("/api/departments/capacity") && "GET".equalsIgnoreCase(method)) {
                    handleDepartmentCapacity(exchange);
                    return;
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

                    // Duplicate management: /api/complaints/:id/duplicate
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

                    // Department Assignment: /api/complaints/:id/assign
                    if (subPath.endsWith("/assign") && "POST".equalsIgnoreCase(method)) {
                        String id = subPath.replace("/assign", "");
                        handleAssignDepartment(exchange, id, body);
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

        String token = database.createSession(user);
        sendJsonResponse(exchange, 200, String.format(
                "{\"message\":\"Login successful\",\"token\":\"%s\",\"user\":%s}",
                token, formatUserJson(user)
        ));
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

        String category = extractVal(body, "category");
        String description = extractVal(body, "description");
        String location = extractVal(body, "location");
        String impactStr = extractVal(body, "citizenReportedImpact");
        if (impactStr.isEmpty()) impactStr = extractVal(body, "impact");

        int impact = 1;
        try {
            if (!impactStr.isEmpty()) impact = Integer.parseInt(impactStr);
        } catch (Exception ignored) {}

        if (category.isEmpty() || description.isEmpty() || location.isEmpty()) {
            sendJsonResponse(exchange, 400, "{\"error\":\"Category, description, and location are required.\"}");
            return;
        }

        Complaint created = manager.createComplaint(user, category, description, location, impact);
        sendJsonResponse(exchange, 201, formatComplaintJson(created));
    }

    private void handleGetAllComplaints(HttpExchange exchange) throws Exception {
        User user = getAuthenticatedUser(exchange);
        if (user == null) {
            sendJsonResponse(exchange, 401, "{\"error\":\"Unauthorized access.\"}");
            return;
        }

        List<Complaint> complaints = manager.getAllComplaints();
        sendJsonResponse(exchange, 200, formatComplaintsListJson(complaints));
    }

    private void handleGetMyComplaints(HttpExchange exchange) throws Exception {
        User user = getAuthenticatedUser(exchange);
        if (user == null) {
            sendJsonResponse(exchange, 401, "{\"error\":\"Unauthorized access.\"}");
            return;
        }

        List<Complaint> complaints = manager.getCitizenComplaints(user.getId());
        sendJsonResponse(exchange, 200, formatComplaintsListJson(complaints));
    }

    private void handleGetPriorityQueue(HttpExchange exchange) throws Exception {
        List<Complaint> queueList = manager.getPriorityQueueList();
        sendJsonResponse(exchange, 200, formatComplaintsListJson(queueList));
    }

    private void handleGetComplaintById(HttpExchange exchange, String id) throws Exception {
        Complaint c = manager.getComplaintById(id);
        if (c == null) {
            sendJsonResponse(exchange, 404, "{\"error\":\"Complaint not found.\"}");
            return;
        }
        sendJsonResponse(exchange, 200, formatComplaintJson(c));
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
            sendJsonResponse(exchange, 200, formatComplaintJson(updated));
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
                sb.append(formatComplaintJson(c));
                count++;
            }
        }
        sb.append("]");
        sendJsonResponse(exchange, 200, sb.toString());
    }

    private void handleDuplicateAction(HttpExchange exchange, String id, String body) throws Exception {
        User user = getAuthenticatedUser(exchange);
        if (user == null || !user.canVerifyComplaints()) {
            sendJsonResponse(exchange, 403, "{\"error\":\"Forbidden: Admin privileges required.\"}");
            return;
        }

        String action = extractVal(body, "action"); // "merge" or "separate"
        if ("merge".equalsIgnoreCase(action)) {
            String primaryId = extractVal(body, "primaryComplaintId");
            String reason = extractVal(body, "reason");
            boolean success = manager.mergeDuplicate(id, primaryId, user, reason);
            if (!success) {
                sendJsonResponse(exchange, 400, "{\"error\":\"Could not merge duplicate complaint.\"}");
                return;
            }
            sendJsonResponse(exchange, 200, "{\"message\":\"Duplicate merged successfully into " + primaryId + "\"}");
        } else if ("separate".equalsIgnoreCase(action)) {
            boolean success = manager.keepSeparate(id, user);
            if (!success) {
                sendJsonResponse(exchange, 400, "{\"error\":\"Could not mark complaint as separate.\"}");
                return;
            }
            sendJsonResponse(exchange, 200, "{\"message\":\"Complaint marked as independent separate case.\"}");
        } else {
            sendJsonResponse(exchange, 400, "{\"error\":\"Invalid duplicate action. Use 'merge' or 'separate'.\"}");
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

        String status = extractVal(body, "status");
        String remarks = extractVal(body, "remarks");
        if (status.isEmpty()) {
            sendJsonResponse(exchange, 400, "{\"error\":\"Status field is required.\"}");
            return;
        }

        boolean success = manager.updateStatus(id, status, remarks, user);
        if (!success) {
            sendJsonResponse(exchange, 404, "{\"error\":\"Complaint not found.\"}");
            return;
        }

        Complaint updated = manager.getComplaintById(id);
        sendJsonResponse(exchange, 200, formatComplaintJson(updated));
    }

    private void handleAssignDepartment(HttpExchange exchange, String id, String body) throws Exception {
        User user = getAuthenticatedUser(exchange);
        if (user == null || !user.canAssignDepartment()) {
            sendJsonResponse(exchange, 403, "{\"error\":\"Forbidden: Only Municipal Admin can assign departments.\"}");
            return;
        }

        String department = extractVal(body, "department");
        String staffName = extractVal(body, "staffName");

        boolean success = manager.assignDepartmentAndStaff(id, department, staffName, user);
        if (!success) {
            sendJsonResponse(exchange, 404, "{\"error\":\"Complaint not found.\"}");
            return;
        }

        Complaint updated = manager.getComplaintById(id);
        sendJsonResponse(exchange, 200, formatComplaintJson(updated));
    }

    private void handleResolveComplaint(HttpExchange exchange, String id, String body) throws Exception {
        User user = getAuthenticatedUser(exchange);
        if (user == null || !user.canUpdateStatus()) {
            sendJsonResponse(exchange, 403, "{\"error\":\"Forbidden: Staff access required.\"}");
            return;
        }

        String resolutionNotes = extractVal(body, "resolutionNotes");
        boolean success = manager.resolveComplaint(id, resolutionNotes, user);
        if (!success) {
            sendJsonResponse(exchange, 404, "{\"error\":\"Complaint not found.\"}");
            return;
        }

        Complaint updated = manager.getComplaintById(id);
        sendJsonResponse(exchange, 200, formatComplaintJson(updated));
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
                "{\"id\":\"%s\",\"name\":\"%s\",\"email\":\"%s\",\"mobile\":\"%s\",\"role\":\"%s\",\"department\":\"%s\"}",
                esc(u.getId()), esc(u.getDisplayName()), esc(u.getEmail()), esc(u.getMobile()), esc(u.getRole()), esc(u.getDepartment())
        );
    }

    private String formatComplaintJson(Complaint c) {
        return String.format(
                "{\"id\":\"%s\",\"complaintId\":\"%s\",\"citizenId\":\"%s\",\"citizenName\":\"%s\",\"citizenMobile\":\"%s\",\"category\":\"%s\",\"description\":\"%s\",\"location\":\"%s\",\"departmentId\":\"%s\",\"department\":\"%s\",\"citizenReportedImpact\":%d,\"adminVerifiedImpact\":%s,\"verificationReason\":\"%s\",\"isVerifiedLocked\":%b,\"severity\":\"%s\",\"priority\":\"%s\",\"status\":\"%s\",\"assignedStaff\":\"%s\",\"resolutionNotes\":\"%s\",\"isDuplicate\":%b,\"duplicateOfId\":\"%s\",\"createdAt\":\"%s\",\"updatedAt\":\"%s\",\"resolvedAt\":\"%s\"}",
                esc(c.getId()), esc(c.getComplaintId()), esc(c.getCitizenId()), esc(c.getCitizenName()),
                esc(c.getCitizenMobile()), esc(c.getCategory()), esc(c.getDescription()), esc(c.getLocation()),
                esc(c.getDepartmentId()), esc(c.getDepartment()), c.getCitizenReportedImpact(),
                (c.getAdminVerifiedImpact() == null ? "null" : c.getAdminVerifiedImpact()),
                esc(c.getVerificationReason()), c.isVerifiedLocked(),
                esc(c.getSeverity()), esc(c.getPriority()), esc(c.getStatus()), esc(c.getAssignedStaff()),
                esc(c.getResolutionNotes()), c.isDuplicate(), esc(c.getDuplicateOfId()),
                esc(c.getCreatedAt()), esc(c.getUpdatedAt()), esc(c.getResolvedAt())
        );
    }

    private String formatComplaintsListJson(List<Complaint> list) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            sb.append(formatComplaintJson(list.get(i)));
            if (i < list.size() - 1) sb.append(",");
        }
        sb.append("]");
        return sb.toString();
    }

    private String esc(String str) {
        if (str == null) return "";
        return str.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "");
    }
}
