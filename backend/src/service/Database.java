package service;

import model.Admin;
import model.Citizen;
import model.Complaint;
import model.ComplaintHistory;
import model.Department;
import model.DepartmentStaff;
import model.Notification;
import model.User;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Persistence & Data Structure Manager for the Municipal Grievance System.
 * Uses:
 * - PriorityQueue<Complaint> for multi-criteria complaint prioritization.
 * - HashMap<String, Complaint> for O(1) complaint lookup.
 * - HashMap<String, Department> for department capacity and load management.
 * - HashSet<String> for duplicate complaint detection by Category + Location.
 * - ArrayList<ComplaintHistory> for chronological audit timeline history.
 * - Thread-safe ConcurrentHashMap for sessions and in-memory caches.
 */
public class Database {
    private static final String DB_DIR = "../data";
    private static final String DB_FILE = "../data/database.json";

    private Map<String, User> userMap;                           // email/id -> User (HashMap)
    private List<Complaint> complaintList;                       // ArrayList of complaints
    private Map<String, Complaint> complaintMap;                 // complaintId -> Complaint (HashMap)
    private PriorityQueue<Complaint> priorityQueue;             // PriorityQueue for global priority ordering
    private Map<String, Department> departmentMap;               // departmentId -> Department (HashMap)
    private Map<String, List<ComplaintHistory>> historyMap;      // complaintId -> ArrayList<ComplaintHistory>
    private List<Notification> notificationList;                 // ArrayList<Notification>
    private Set<String> activeCategoryLocationSet;               // HashSet for duplicate detection
    private Map<String, String> activeSessions;                  // token -> userId

    private int complaintCounter = 0;
    private int historyCounter = 0;
    private int notificationCounter = 0;

    public Database() {
        this.userMap = new ConcurrentHashMap<>();
        this.complaintList = new ArrayList<>();
        this.complaintMap = new ConcurrentHashMap<>();
        this.priorityQueue = new PriorityQueue<>();
        this.departmentMap = new ConcurrentHashMap<>();
        this.historyMap = new ConcurrentHashMap<>();
        this.notificationList = new ArrayList<>();
        this.activeCategoryLocationSet = new HashSet<>();
        this.activeSessions = new ConcurrentHashMap<>();

        initializeDepartments();
        ensureDatabaseFile();
        loadDatabase();
        seedInitialAccounts();
    }

    private void initializeDepartments() {
        // Seed 4 Core Municipal Departments with default capacities
        Department water = new Department("DEPT-WATER", "Water Department", 10, 0);
        Department roads = new Department("DEPT-ROADS", "Roads Department", 10, 0);
        Department waste = new Department("DEPT-WASTE", "Waste Management Department", 10, 0);
        Department light = new Department("DEPT-LIGHT", "Streetlight Department", 10, 0);
        Department general = new Department("DEPT-GEN", "General Administration", 15, 0);

        departmentMap.put(water.getDepartmentId(), water);
        departmentMap.put("Water Supply", water);
        departmentMap.put("Water", water);

        departmentMap.put(roads.getDepartmentId(), roads);
        departmentMap.put("Roads", roads);
        departmentMap.put("Road Damage", roads);

        departmentMap.put(waste.getDepartmentId(), waste);
        departmentMap.put("Waste Management", waste);
        departmentMap.put("Sanitation", waste);

        departmentMap.put(light.getDepartmentId(), light);
        departmentMap.put("Streetlight", light);
        departmentMap.put("Electrical", light);

        departmentMap.put(general.getDepartmentId(), general);
        departmentMap.put("General Administration", general);
    }

    private void ensureDatabaseFile() {
        File dir = new File(DB_DIR);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        File file = new File(DB_FILE);
        if (!file.exists()) {
            try {
                file.createNewFile();
                saveDatabase();
            } catch (IOException e) {
                System.err.println("[DATABASE ERROR] Could not create database file: " + e.getMessage());
            }
        }
    }

    // Hash password using SHA-256
    public static String hashPassword(String rawPassword) {
        if (rawPassword == null) return "";
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(rawPassword.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            return rawPassword;
        }
    }

    // Seed Initial Admin & Department Staff Accounts
    private void seedInitialAccounts() {
        // 1. Initial Admin
        if (getUserById("ADM1001") == null && getUserByEmail("admin@makkal.gov.in") == null) {
            Admin admin = new Admin(
                    "ADM1001",
                    "admin@makkal.gov.in",
                    "admin@makkal.gov.in",
                    "9876543210",
                    hashPassword("admin123"),
                    "Chief Municipal Officer",
                    "2026-09-14 00:00:00"
            );
            userMap.put(admin.getEmail().toLowerCase(), admin);
            userMap.put(admin.getId(), admin);
            userMap.put(admin.getUsername().toLowerCase(), admin);

            // Legacy Staff ID alias for backward compatibility
            userMap.put("STF1001", admin);
        }

        // 2. Department Staff accounts
        Department waterDept = departmentMap.get("DEPT-WATER");
        if (getUserById("STF-W01") == null) {
            DepartmentStaff staffWater = new DepartmentStaff(
                    "STF-W01", "water_staff@makkal.gov.in", "water_staff@makkal.gov.in", "9876543211",
                    hashPassword("admin123"), "Officer Suresh", "DEPT-WATER", waterDept, "2026-09-14 00:00:00"
            );
            userMap.put(staffWater.getEmail().toLowerCase(), staffWater);
            userMap.put(staffWater.getId(), staffWater);
        }

        Department roadsDept = departmentMap.get("DEPT-ROADS");
        if (getUserById("STF-R01") == null) {
            DepartmentStaff staffRoads = new DepartmentStaff(
                    "STF-R01", "roads_staff@makkal.gov.in", "roads_staff@makkal.gov.in", "9876543212",
                    hashPassword("admin123"), "Officer Karthik", "DEPT-ROADS", roadsDept, "2026-09-14 00:00:00"
            );
            userMap.put(staffRoads.getEmail().toLowerCase(), staffRoads);
            userMap.put(staffRoads.getId(), staffRoads);
        }

        Department wasteDept = departmentMap.get("DEPT-WASTE");
        if (getUserById("STF-M01") == null) {
            DepartmentStaff staffWaste = new DepartmentStaff(
                    "STF-M01", "waste_staff@makkal.gov.in", "waste_staff@makkal.gov.in", "9876543213",
                    hashPassword("admin123"), "Officer Priya", "DEPT-WASTE", wasteDept, "2026-09-14 00:00:00"
            );
            userMap.put(staffWaste.getEmail().toLowerCase(), staffWaste);
            userMap.put(staffWaste.getId(), staffWaste);
        }

        Department lightDept = departmentMap.get("DEPT-LIGHT");
        if (getUserById("STF-L01") == null) {
            DepartmentStaff staffLight = new DepartmentStaff(
                    "STF-L01", "light_staff@makkal.gov.in", "light_staff@makkal.gov.in", "9876543214",
                    hashPassword("admin123"), "Officer Ramesh", "DEPT-LIGHT", lightDept, "2026-09-14 00:00:00"
            );
            userMap.put(staffLight.getEmail().toLowerCase(), staffLight);
            userMap.put(staffLight.getId(), staffLight);
        }

        saveDatabase();
    }

    // Save full system state to JSON
    public synchronized void saveDatabase() {
        try (FileWriter writer = new FileWriter(DB_FILE, false)) {
            StringBuilder json = new StringBuilder();
            json.append("{\n");

            // 1. Users
            json.append("  \"users\": [\n");
            List<User> uniqueUsers = getUniqueUsers();
            for (int i = 0; i < uniqueUsers.size(); i++) {
                User u = uniqueUsers.get(i);
                json.append(String.format(
                        "    {\"id\":\"%s\",\"name\":\"%s\",\"email\":\"%s\",\"mobile\":\"%s\",\"passwordHash\":\"%s\",\"role\":\"%s\",\"department\":\"%s\",\"createdAt\":\"%s\"}",
                        escapeJson(u.getId()), escapeJson(u.getDisplayName()), escapeJson(u.getEmail()),
                        escapeJson(u.getMobile()), escapeJson(u.getPasswordHash()), escapeJson(u.getRole()),
                        escapeJson(u.getDepartment()), escapeJson(u.getCreatedAt())
                ));
                if (i < uniqueUsers.size() - 1) json.append(",");
                json.append("\n");
            }
            json.append("  ],\n");

            // 2. Complaints
            json.append("  \"complaints\": [\n");
            for (int i = 0; i < complaintList.size(); i++) {
                Complaint c = complaintList.get(i);
                json.append(String.format(
                        "    {\"id\":\"%s\",\"complaintId\":\"%s\",\"citizenId\":\"%s\",\"citizenName\":\"%s\",\"citizenMobile\":\"%s\",\"category\":\"%s\",\"description\":\"%s\",\"location\":\"%s\",\"departmentId\":\"%s\",\"department\":\"%s\",\"citizenReportedImpact\":%d,\"adminVerifiedImpact\":%s,\"verificationReason\":\"%s\",\"isVerifiedLocked\":%b,\"severity\":\"%s\",\"priority\":\"%s\",\"status\":\"%s\",\"assignedStaff\":\"%s\",\"resolutionNotes\":\"%s\",\"isDuplicate\":%b,\"duplicateOfId\":\"%s\",\"createdAt\":\"%s\",\"updatedAt\":\"%s\",\"resolvedAt\":\"%s\"}",
                        escapeJson(c.getId()), escapeJson(c.getComplaintId()), escapeJson(c.getCitizenId()),
                        escapeJson(c.getCitizenName()), escapeJson(c.getCitizenMobile()), escapeJson(c.getCategory()),
                        escapeJson(c.getDescription()), escapeJson(c.getLocation()), escapeJson(c.getDepartmentId()),
                        escapeJson(c.getDepartment()), c.getCitizenReportedImpact(),
                        (c.getAdminVerifiedImpact() == null ? "null" : c.getAdminVerifiedImpact()),
                        escapeJson(c.getVerificationReason()), c.isVerifiedLocked(),
                        escapeJson(c.getSeverity()), escapeJson(c.getPriority()), escapeJson(c.getStatus()),
                        escapeJson(c.getAssignedStaff()), escapeJson(c.getResolutionNotes()),
                        c.isDuplicate(), escapeJson(c.getDuplicateOfId()),
                        escapeJson(c.getCreatedAt()), escapeJson(c.getUpdatedAt()), escapeJson(c.getResolvedAt())
                ));
                if (i < complaintList.size() - 1) json.append(",");
                json.append("\n");
            }
            json.append("  ],\n");

            // 3. Complaint History (Audit Trail)
            json.append("  \"history\": [\n");
            List<ComplaintHistory> allHistory = getAllHistoryList();
            for (int i = 0; i < allHistory.size(); i++) {
                ComplaintHistory h = allHistory.get(i);
                json.append(String.format(
                        "    {\"historyId\":\"%s\",\"complaintId\":\"%s\",\"oldStatus\":\"%s\",\"newStatus\":\"%s\",\"updatedBy\":\"%s\",\"updateTime\":\"%s\",\"remarks\":\"%s\"}",
                        escapeJson(h.getHistoryId()), escapeJson(h.getComplaintId()), escapeJson(h.getOldStatus()),
                        escapeJson(h.getNewStatus()), escapeJson(h.getUpdatedBy()), escapeJson(h.getUpdateTime()),
                        escapeJson(h.getRemarks())
                ));
                if (i < allHistory.size() - 1) json.append(",");
                json.append("\n");
            }
            json.append("  ],\n");

            // 4. Notifications
            json.append("  \"notifications\": [\n");
            for (int i = 0; i < notificationList.size(); i++) {
                Notification n = notificationList.get(i);
                json.append(String.format(
                        "    {\"notificationId\":\"%s\",\"citizenId\":\"%s\",\"complaintId\":\"%s\",\"originalImpact\":%d,\"verifiedImpact\":%d,\"verificationReason\":\"%s\",\"title\":\"%s\",\"message\":\"%s\",\"createdAt\":\"%s\",\"isRead\":%b}",
                        escapeJson(n.getNotificationId()), escapeJson(n.getCitizenId()), escapeJson(n.getComplaintId()),
                        n.getOriginalImpact(), n.getVerifiedImpact(), escapeJson(n.getVerificationReason()),
                        escapeJson(n.getTitle()), escapeJson(n.getMessage()), escapeJson(n.getCreatedAt()), n.isRead()
                ));
                if (i < notificationList.size() - 1) json.append(",");
                json.append("\n");
            }
            json.append("  ],\n");

            // Counters
            json.append("  \"complaintCounter\": ").append(complaintCounter).append(",\n");
            json.append("  \"historyCounter\": ").append(historyCounter).append(",\n");
            json.append("  \"notificationCounter\": ").append(notificationCounter).append("\n");
            json.append("}\n");

            writer.write(json.toString());
        } catch (IOException e) {
            System.err.println("[DATABASE ERROR] Failed to save database: " + e.getMessage());
        }
    }

    // Load state from disk
    public synchronized void loadDatabase() {
        File file = new File(DB_FILE);
        if (!file.exists() || file.length() == 0) return;

        try {
            String content = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            if (content.trim().isEmpty()) return;

            int usersStart = content.indexOf("\"users\":");
            int complaintsStart = content.indexOf("\"complaints\":");
            int historyStart = content.indexOf("\"history\":");
            int notiStart = content.indexOf("\"notifications\":");
            int counterStart = content.indexOf("\"complaintCounter\":");

            if (usersStart != -1) {
                int end = (complaintsStart != -1) ? complaintsStart : content.length();
                parseUsers(content.substring(usersStart, end));
            }

            if (complaintsStart != -1) {
                int end = (historyStart != -1) ? historyStart : (notiStart != -1 ? notiStart : (counterStart != -1 ? counterStart : content.length()));
                parseComplaints(content.substring(complaintsStart, end));
            }

            if (historyStart != -1) {
                int end = (notiStart != -1) ? notiStart : (counterStart != -1 ? counterStart : content.length());
                parseHistory(content.substring(historyStart, end));
            }

            if (notiStart != -1) {
                int end = (counterStart != -1) ? counterStart : content.length();
                parseNotifications(content.substring(notiStart, end));
            }

            if (counterStart != -1) {
                String counterStr = extractJsonIntVal(content, "complaintCounter");
                if (!counterStr.isEmpty()) this.complaintCounter = Integer.parseInt(counterStr);
                String histCounterStr = extractJsonIntVal(content, "historyCounter");
                if (!histCounterStr.isEmpty()) this.historyCounter = Integer.parseInt(histCounterStr);
                String notiCounterStr = extractJsonIntVal(content, "notificationCounter");
                if (!notiCounterStr.isEmpty()) this.notificationCounter = Integer.parseInt(notiCounterStr);
            }

            rebuildPriorityQueueAndCapacity();
            System.out.println("[DATABASE LOADED] Total Users: " + getUniqueUsers().size() +
                    " | Total Complaints: " + complaintList.size() +
                    " | History Records: " + getAllHistoryList().size() +
                    " | Notifications: " + notificationList.size());
        } catch (Exception e) {
            System.err.println("[DATABASE WARNING] Could not parse existing database. Initializing fresh: " + e.getMessage());
        }
    }

    private List<User> getUniqueUsers() {
        Map<String, User> uniqueMap = new HashMap<>();
        for (User u : userMap.values()) {
            uniqueMap.put(u.getId(), u);
        }
        return new ArrayList<>(uniqueMap.values());
    }

    private void parseUsers(String json) {
        int idx = 0;
        while ((idx = json.indexOf("{\"id\":", idx)) != -1) {
            int endIdx = json.indexOf("}", idx);
            if (endIdx == -1) break;
            String objStr = json.substring(idx, endIdx + 1);

            String id = extractJsonVal(objStr, "id");
            String name = extractJsonVal(objStr, "name");
            String email = extractJsonVal(objStr, "email");
            String mobile = extractJsonVal(objStr, "mobile");
            String passwordHash = extractJsonVal(objStr, "passwordHash");
            String role = extractJsonVal(objStr, "role");
            String department = extractJsonVal(objStr, "department");
            String createdAt = extractJsonVal(objStr, "createdAt");

            User u;
            if ("ADMIN".equalsIgnoreCase(role)) {
                u = new Admin(id, email, email, mobile, passwordHash, name, createdAt);
            } else if ("STAFF".equalsIgnoreCase(role)) {
                Department dept = departmentMap.get(department);
                u = new DepartmentStaff(id, email, email, mobile, passwordHash, name,
                        dept != null ? dept.getDepartmentId() : "DEPT-GEN", dept, createdAt);
            } else {
                u = new Citizen(id, name, email, mobile, passwordHash, createdAt);
            }

            if (u.getEmail() != null) userMap.put(u.getEmail().toLowerCase(), u);
            if (u.getId() != null) userMap.put(u.getId(), u);
            if (u.getUsername() != null) userMap.put(u.getUsername().toLowerCase(), u);

            idx = endIdx + 1;
        }
    }

    private void parseComplaints(String json) {
        int idx = 0;
        while ((idx = json.indexOf("{\"id\":", idx)) != -1) {
            int endIdx = json.indexOf("}", idx);
            if (endIdx == -1) break;
            String objStr = json.substring(idx, endIdx + 1);

            Complaint c = new Complaint();
            c.setId(extractJsonVal(objStr, "id"));
            c.setComplaintId(extractJsonVal(objStr, "complaintId"));
            c.setCitizenId(extractJsonVal(objStr, "citizenId"));
            c.setCitizenName(extractJsonVal(objStr, "citizenName"));
            c.setCitizenMobile(extractJsonVal(objStr, "citizenMobile"));
            c.setCategory(extractJsonVal(objStr, "category"));
            c.setDescription(extractJsonVal(objStr, "description"));
            c.setLocation(extractJsonVal(objStr, "location"));
            c.setDepartmentId(extractJsonVal(objStr, "departmentId"));
            c.setDepartment(extractJsonVal(objStr, "department"));

            // Impact & Verification fields
            String citImpact = extractJsonIntVal(objStr, "citizenReportedImpact");
            c.forceSetCitizenReportedImpactFromPersistence(!citImpact.isEmpty() ? Integer.parseInt(citImpact) : 1);

            String admImpact = extractJsonIntVal(objStr, "adminVerifiedImpact");
            if (!admImpact.isEmpty()) {
                c.forceSetAdminVerifiedImpactFromPersistence(Integer.parseInt(admImpact));
            }

            c.forceSetVerificationReasonFromPersistence(extractJsonVal(objStr, "verificationReason"));
            c.setVerifiedLocked("true".equalsIgnoreCase(extractJsonVal(objStr, "isVerifiedLocked")));

            c.setSeverity(extractJsonVal(objStr, "severity"));
            c.setPriority(extractJsonVal(objStr, "priority"));
            c.setStatus(extractJsonVal(objStr, "status"));
            c.setAssignedStaff(extractJsonVal(objStr, "assignedStaff"));
            c.setResolutionNotes(extractJsonVal(objStr, "resolutionNotes"));

            c.setDuplicate("true".equalsIgnoreCase(extractJsonVal(objStr, "isDuplicate")));
            c.setDuplicateOfId(extractJsonVal(objStr, "duplicateOfId"));

            c.setCreatedAt(extractJsonVal(objStr, "createdAt"));
            c.setUpdatedAt(extractJsonVal(objStr, "updatedAt"));
            c.setResolvedAt(extractJsonVal(objStr, "resolvedAt"));

            complaintList.add(c);
            complaintMap.put(c.getComplaintId(), c);
            complaintMap.put(c.getId(), c);

            // Update counter
            if (c.getComplaintId() != null && c.getComplaintId().startsWith("GRV-2026-")) {
                try {
                    int num = Integer.parseInt(c.getComplaintId().replace("GRV-2026-", ""));
                    if (num > complaintCounter) complaintCounter = num;
                } catch (Exception ignored) {}
            }

            idx = endIdx + 1;
        }
    }

    private void parseHistory(String json) {
        int idx = 0;
        while ((idx = json.indexOf("{\"historyId\":", idx)) != -1) {
            int endIdx = json.indexOf("}", idx);
            if (endIdx == -1) break;
            String objStr = json.substring(idx, endIdx + 1);

            ComplaintHistory h = new ComplaintHistory(
                    extractJsonVal(objStr, "historyId"),
                    extractJsonVal(objStr, "complaintId"),
                    extractJsonVal(objStr, "oldStatus"),
                    extractJsonVal(objStr, "newStatus"),
                    extractJsonVal(objStr, "updatedBy"),
                    extractJsonVal(objStr, "updateTime"),
                    extractJsonVal(objStr, "remarks")
            );

            addHistory(h);
            idx = endIdx + 1;
        }
    }

    private void parseNotifications(String json) {
        int idx = 0;
        while ((idx = json.indexOf("{\"notificationId\":", idx)) != -1) {
            int endIdx = json.indexOf("}", idx);
            if (endIdx == -1) break;
            String objStr = json.substring(idx, endIdx + 1);

            String orig = extractJsonIntVal(objStr, "originalImpact");
            String veri = extractJsonIntVal(objStr, "verifiedImpact");

            Notification n = new Notification(
                    extractJsonVal(objStr, "notificationId"),
                    extractJsonVal(objStr, "citizenId"),
                    extractJsonVal(objStr, "complaintId"),
                    !orig.isEmpty() ? Integer.parseInt(orig) : 1,
                    !veri.isEmpty() ? Integer.parseInt(veri) : 1,
                    extractJsonVal(objStr, "verificationReason"),
                    extractJsonVal(objStr, "title"),
                    extractJsonVal(objStr, "message"),
                    extractJsonVal(objStr, "createdAt"),
                    "true".equalsIgnoreCase(extractJsonVal(objStr, "isRead"))
            );

            notificationList.add(n);
            idx = endIdx + 1;
        }
    }

    private String extractJsonVal(String objStr, String key) {
        String pattern = "\"" + key + "\":\"";
        int start = objStr.indexOf(pattern);
        if (start == -1) {
            pattern = "\"" + key + "\":";
            start = objStr.indexOf(pattern);
            if (start == -1) return "";
            start += pattern.length();
            int end = objStr.indexOf(",", start);
            if (end == -1) end = objStr.indexOf("}", start);
            if (end == -1) return "";
            return objStr.substring(start, end).replaceAll("\"", "").trim();
        }
        start += pattern.length();
        int end = objStr.indexOf("\"", start);
        if (end == -1) return "";
        return unescapeJson(objStr.substring(start, end));
    }

    private String extractJsonIntVal(String objStr, String key) {
        String pattern = "\"" + key + "\":";
        int start = objStr.indexOf(pattern);
        if (start == -1) return "";
        start += pattern.length();
        int end = objStr.indexOf(",", start);
        if (end == -1) end = objStr.indexOf("}", start);
        if (end == -1) return "";
        String val = objStr.substring(start, end).replaceAll("[\"\\s]", "").trim();
        if ("null".equalsIgnoreCase(val)) return "";
        return val.replaceAll("[^0-9]", "");
    }

    private String escapeJson(String str) {
        if (str == null) return "";
        return str.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "");
    }

    private String unescapeJson(String str) {
        if (str == null) return "";
        return str.replace("\\n", "\n").replace("\\\"", "\"").replace("\\\\", "\\");
    }

    // Rebuild PriorityQueue, Duplicate HashSet, and Department Load
    public synchronized void rebuildPriorityQueueAndCapacity() {
        priorityQueue.clear();
        activeCategoryLocationSet.clear();

        // Reset department loads
        for (Department d : departmentMap.values()) {
            d.setCurrentLoad(0);
            d.getWaitingQueue().clear();
        }

        for (Complaint c : complaintList) {
            boolean isResolved = "RESOLVED".equalsIgnoreCase(c.getStatus()) || "CLOSED".equalsIgnoreCase(c.getStatus());

            if (!isResolved) {
                // Add to global PriorityQueue
                priorityQueue.add(c);

                // Add to duplicate detection HashSet
                if (c.getCategory() != null && c.getLocation() != null) {
                    activeCategoryLocationSet.add(buildDuplicateKey(c.getCategory(), c.getLocation()));
                }

                // Department Capacity allocation
                Department dept = getDepartmentByAnyIdentifier(c.getDepartment());
                if (dept != null) {
                    if ("IN_PROGRESS".equalsIgnoreCase(c.getStatus()) || "ASSIGNED".equalsIgnoreCase(c.getStatus())) {
                        if (dept.getCurrentLoad() < dept.getCapacity()) {
                            dept.incrementLoad();
                        } else {
                            // Exceeded capacity -> waiting queue
                            dept.addToWaitingQueue(c);
                        }
                    }
                }
            }
        }
    }

    public String buildDuplicateKey(String category, String location) {
        return (category != null ? category.trim().toLowerCase() : "") + ":::" +
               (location != null ? location.trim().toLowerCase() : "");
    }

    public boolean isCategoryLocationActive(String category, String location) {
        return activeCategoryLocationSet.contains(buildDuplicateKey(category, location));
    }

    // ID Generators
    public synchronized String generateComplaintId() {
        complaintCounter++;
        return String.format("GRV-2026-%04d", complaintCounter);
    }

    public synchronized String generateHistoryId() {
        historyCounter++;
        return String.format("HIST-%05d", historyCounter);
    }

    public synchronized String generateNotificationId() {
        notificationCounter++;
        return String.format("NOTI-%05d", notificationCounter);
    }

    // Auth Operations
    public synchronized User registerUser(String name, String email, String mobile, String rawPassword, String role, String department) {
        String lowerEmail = email.toLowerCase().trim();
        if (userMap.containsKey(lowerEmail)) {
            return null; // Already exists
        }

        String userId = "USR" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        User user;
        if ("ADMIN".equalsIgnoreCase(role)) {
            user = new Admin(userId, lowerEmail, lowerEmail, mobile, hashPassword(rawPassword), name, "2026-09-14 12:00:00");
        } else if ("STAFF".equalsIgnoreCase(role)) {
            Department dept = getDepartmentByAnyIdentifier(department);
            user = new DepartmentStaff(userId, lowerEmail, lowerEmail, mobile, hashPassword(rawPassword), name,
                    dept != null ? dept.getDepartmentId() : "DEPT-GEN", dept, "2026-09-14 12:00:00");
        } else {
            user = new Citizen(userId, name, lowerEmail, mobile, hashPassword(rawPassword), "2026-09-14 12:00:00");
        }

        userMap.put(lowerEmail, user);
        userMap.put(userId, user);
        userMap.put(user.getUsername().toLowerCase(), user);
        saveDatabase();
        return user;
    }

    public User getUserByEmail(String email) {
        if (email == null) return null;
        return userMap.get(email.toLowerCase().trim());
    }

    public User getUserById(String id) {
        if (id == null) return null;
        return userMap.get(id);
    }

    public String createSession(User user) {
        String token = "TOKEN-" + UUID.randomUUID().toString();
        activeSessions.put(token, user.getId());
        return token;
    }

    public User getUserByToken(String token) {
        if (token == null) return null;
        String cleanToken = token.replace("Bearer ", "").trim();
        String userId = activeSessions.get(cleanToken);
        if (userId != null) {
            return getUserById(userId);
        }
        return null;
    }

    // Complaint Operations
    public synchronized void addComplaint(Complaint complaint) {
        complaintList.add(complaint);
        complaintMap.put(complaint.getComplaintId(), complaint);
        complaintMap.put(complaint.getId(), complaint);

        if (!"RESOLVED".equalsIgnoreCase(complaint.getStatus()) && !"CLOSED".equalsIgnoreCase(complaint.getStatus())) {
            priorityQueue.add(complaint);
            activeCategoryLocationSet.add(buildDuplicateKey(complaint.getCategory(), complaint.getLocation()));
        }

        saveDatabase();
    }

    public Complaint getComplaintById(String id) {
        if (id == null) return null;
        return complaintMap.get(id);
    }

    public List<Complaint> getAllComplaints() {
        return new ArrayList<>(complaintList);
    }

    public List<Complaint> getComplaintsByCitizenId(String citizenId) {
        List<Complaint> result = new ArrayList<>();
        for (Complaint c : complaintList) {
            if (c.getCitizenId() != null && c.getCitizenId().equalsIgnoreCase(citizenId)) {
                result.add(c);
            }
        }
        return result;
    }

    public List<Complaint> getOrderedPriorityQueueList() {
        PriorityQueue<Complaint> tempQueue = new PriorityQueue<>(priorityQueue);
        List<Complaint> list = new ArrayList<>();
        while (!tempQueue.isEmpty()) {
            list.add(tempQueue.poll());
        }
        return list;
    }

    // Department Operations
    public Department getDepartmentById(String deptId) {
        if (deptId == null) return null;
        return departmentMap.get(deptId);
    }

    public Department getDepartmentByAnyIdentifier(String idOrName) {
        if (idOrName == null) return departmentMap.get("DEPT-GEN");
        Department d = departmentMap.get(idOrName);
        if (d != null) return d;

        for (Department dept : departmentMap.values()) {
            if (dept.getDepartmentName().equalsIgnoreCase(idOrName.trim()) ||
                dept.getDepartmentId().equalsIgnoreCase(idOrName.trim())) {
                return dept;
            }
        }
        return departmentMap.get("DEPT-GEN");
    }

    public List<Department> getAllUniqueDepartments() {
        Map<String, Department> map = new HashMap<>();
        for (Department d : departmentMap.values()) {
            map.put(d.getDepartmentId(), d);
        }
        return new ArrayList<>(map.values());
    }

    // History & Audit Trail Operations
    public synchronized void addHistory(ComplaintHistory history) {
        historyMap.computeIfAbsent(history.getComplaintId(), k -> new ArrayList<>()).add(history);
        saveDatabase();
    }

    public List<ComplaintHistory> getComplaintHistory(String complaintId) {
        return historyMap.getOrDefault(complaintId, new ArrayList<>());
    }

    public List<ComplaintHistory> getAllHistoryList() {
        List<ComplaintHistory> all = new ArrayList<>();
        for (List<ComplaintHistory> list : historyMap.values()) {
            all.addAll(list);
        }
        return all;
    }

    // Notification Operations
    public synchronized void addNotification(Notification notification) {
        notificationList.add(notification);
        saveDatabase();
    }

    public List<Notification> getNotificationsByCitizenId(String citizenId) {
        List<Notification> list = new ArrayList<>();
        for (Notification n : notificationList) {
            if (n.getCitizenId() != null && n.getCitizenId().equalsIgnoreCase(citizenId)) {
                list.add(n);
            }
        }
        return list;
    }

    public List<Notification> getAllNotifications() {
        return new ArrayList<>(notificationList);
    }
}
