package service;

import model.Admin;
import model.Citizen;
import model.Complaint;
import model.ComplaintHistory;
import model.Department;
import model.DepartmentStaff;
import model.Grievance;
import model.GrievanceFactory;
import model.Manager;
import model.Notification;
import model.User;
import repository.AssignmentRepository;
import repository.AuditLogRepository;
import repository.ComplaintRepository;
import repository.DepartmentRepository;
import repository.DuplicateRepository;
import repository.ReportRepository;
import repository.StatusUpdateRepository;
import repository.UserRepository;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.sql.SQLException;
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
    public static String getDbDir() {
        if (new File("backend/data").exists() || new File("backend").exists()) {
            return "backend/data";
        }
        if (new File("data").exists()) {
            return "data";
        }
        return "../data";
    }

    public static String getDbFile() {
        if (new File("backend/data/database.json").exists()) {
            return "backend/data/database.json";
        }
        if (new File("data/database.json").exists()) {
            return "data/database.json";
        }
        if (new File("../data/database.json").exists()) {
            return "../data/database.json";
        }
        return new File("backend").exists() ? "backend/data/database.json" : "data/database.json";
    }

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

    private String customDbFile = null;
    private boolean useMySql = false;

    private UserRepository userRepository = new UserRepository();
    private DepartmentRepository departmentRepository = new DepartmentRepository();
    private ComplaintRepository complaintRepository = new ComplaintRepository();
    private AssignmentRepository assignmentRepository = new AssignmentRepository();
    private StatusUpdateRepository statusUpdateRepository = new StatusUpdateRepository();
    private DuplicateRepository duplicateRepository = new DuplicateRepository();
    private AuditLogRepository auditLogRepository = new AuditLogRepository();
    private ReportRepository reportRepository = new ReportRepository();

    public Database() {
        this(null);
    }

    public Database(String customDbFilePath) {
        this.customDbFile = customDbFilePath;
        this.userMap = new ConcurrentHashMap<>();
        this.complaintList = new ArrayList<>();
        this.complaintMap = new ConcurrentHashMap<>();
        this.priorityQueue = new PriorityQueue<>(Complaint.PRIORITY_COMPARATOR);
        this.departmentMap = new ConcurrentHashMap<>();
        this.historyMap = new ConcurrentHashMap<>();
        this.notificationList = new ArrayList<>();
        this.activeCategoryLocationSet = new HashSet<>();
        this.activeSessions = new ConcurrentHashMap<>();

        initializeDepartments();

        if (customDbFilePath == null && !persistenceDisabled && DatabaseConnection.testConnection()) {
            this.useMySql = true;
            System.out.println("[DATABASE PERSISTENCE] Primary Source of Truth: MySQL 8.0 (" + DatabaseConfig.getDatabaseName() + ")");
            loadFromMySql();
        } else {
            this.useMySql = false;
            ensureDatabaseFile();
            loadDatabase();
        }
        seedInitialAccounts();
    }

    public String getEffectiveDbFile() {
        return (customDbFile != null && !customDbFile.trim().isEmpty()) ? customDbFile : getDbFile();
    }

    public synchronized void loadFromMySql() {
        try {
            // 1. Departments
            List<Department> depts = departmentRepository.findAll();
            if (depts != null && !depts.isEmpty()) {
                departmentMap.clear();
                for (Department d : depts) {
                    departmentMap.put(d.getDepartmentId(), d);
                    departmentMap.put(d.getDepartmentName(), d);
                }
            }
            // 2. Users
            List<User> users = userRepository.findAll();
            if (users != null) {
                userMap.clear();
                for (User u : users) {
                    userMap.put(u.getId(), u);
                    userMap.put(u.getEmail().toLowerCase(), u);
                    userMap.put(u.getUsername().toLowerCase(), u);
                }
            }
            // 3. Complaints
            List<Complaint> complaints = complaintRepository.findAll();
            if (complaints != null) {
                complaintList.clear();
                complaintMap.clear();
                priorityQueue.clear();
                activeCategoryLocationSet.clear();
                int maxCounter = 0;
                for (Complaint c : complaints) {
                    complaintList.add(c);
                    complaintMap.put(c.getComplaintId(), c);
                    complaintMap.put(c.getId(), c);
                    if (!"RESOLVED".equalsIgnoreCase(c.getStatus()) && !"CLOSED".equalsIgnoreCase(c.getStatus())) {
                        priorityQueue.add(c);
                        activeCategoryLocationSet.add(buildDuplicateKey(c.getCategory(), c.getLocation()));
                    }
                    if (c.getComplaintId() != null && c.getComplaintId().startsWith("GRV-2026-")) {
                        try {
                            int num = Integer.parseInt(c.getComplaintId().substring("GRV-2026-".length()));
                            if (num > maxCounter) maxCounter = num;
                        } catch (Exception ignored) {}
                    }
                }
                this.complaintCounter = maxCounter;
            }
            rebuildPriorityQueueAndCapacity();
            System.out.println("[MYSQL DATABASE LOADED] Users: " + getUniqueUsers().size() +
                    " | Complaints: " + complaintList.size() +
                    " | PriorityQueue size: " + priorityQueue.size());
        } catch (SQLException e) {
            System.err.println("[MYSQL ERROR] Could not load data from MySQL: " + e.getMessage());
            throw new RuntimeException("MySQL Initialization Error: " + e.getMessage(), e);
        }
    }

    private void initializeDepartments() {
        // Seed 4 Core Municipal Departments with default capacities
        Department water = new Department("DEPT-WATER", "Water Department", 10, 0);
        Department roads = new Department("DEPT-ROADS", "Roads Department", 10, 0);
        Department waste = new Department("DEPT-WASTE", "Waste Management Department", 10, 0);
        Department light = new Department("DEPT-LIGHT", "Streetlight Department", 10, 0);
        Department general = new Department("DEPT-GEN", "General Administration", 15, 0);

        departmentMap.put(water.getDepartmentId(), water);
        departmentMap.put(water.getDepartmentName(), water);
        departmentMap.put("Water Supply", water);
        departmentMap.put("Water", water);

        departmentMap.put(roads.getDepartmentId(), roads);
        departmentMap.put(roads.getDepartmentName(), roads);
        departmentMap.put("Roads", roads);
        departmentMap.put("Road Damage", roads);

        departmentMap.put(waste.getDepartmentId(), waste);
        departmentMap.put(waste.getDepartmentName(), waste);
        departmentMap.put("Waste Management", waste);
        departmentMap.put("Sanitation", waste);
        departmentMap.put("Public Sanitation", waste);

        departmentMap.put(light.getDepartmentId(), light);
        departmentMap.put(light.getDepartmentName(), light);
        departmentMap.put("Streetlight", light);
        departmentMap.put("Streetlights", light);
        departmentMap.put("Electrical", light);

        departmentMap.put(general.getDepartmentId(), general);
        departmentMap.put(general.getDepartmentName(), general);
        departmentMap.put("General Administration", general);
    }

    private void ensureDatabaseFile() {
        File file = new File(getEffectiveDbFile());
        File dir = file.getParentFile();
        if (dir != null && !dir.exists()) {
            dir.mkdirs();
        }
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

        // 1b. Initial Manager
        if (getUserById("MGR1001") == null && getUserByEmail("manager@makkal.gov.in") == null) {
            Manager manager = new Manager(
                    "MGR1001",
                    "manager@makkal.gov.in",
                    "manager@makkal.gov.in",
                    "9876543201",
                    hashPassword("admin123"),
                    "Triage Operations Manager",
                    "2026-09-14 00:00:00"
            );
            userMap.put(manager.getEmail().toLowerCase(), manager);
            userMap.put(manager.getId(), manager);
            userMap.put(manager.getUsername().toLowerCase(), manager);
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

        // 3. Initial Citizen
        if (getUserById("C001") == null && getUserByEmail("ramesh@test.com") == null) {
            Citizen citizen = new Citizen(
                    "C001", "Ramesh", "ramesh@test.com", "9876543210",
                    hashPassword("123456"), "2026-09-14 10:00:00"
            );
            userMap.put(citizen.getEmail().toLowerCase(), citizen);
            userMap.put(citizen.getId(), citizen);
            userMap.put(citizen.getUsername().toLowerCase(), citizen);
            if (useMySql) {
                try { userRepository.save(citizen); } catch (Exception ignored) {}
            }
        }

        saveDatabase();
    }

    private static boolean persistenceDisabled = false;

    public static void setPersistenceDisabled(boolean disabled) {
        persistenceDisabled = disabled;
    }

    public static boolean isPersistenceDisabled() {
        return persistenceDisabled;
    }

    // Save full system state to JSON (only active in standalone file/test mode)
    public synchronized void saveDatabase() {
        if (useMySql) {
            // MySQL 8.0 is the primary source of truth.
            // All operations are committed directly to MySQL via PreparedStatements.
            // Do not silently fall back or duplicate writes to database.json.
            return;
        }
        if (persistenceDisabled && customDbFile == null) {
            return;
        }
        try (FileWriter writer = new FileWriter(getEffectiveDbFile(), false)) {
            StringBuilder json = new StringBuilder();
            json.append("{\n");

            // 1. Users
            json.append("  \"users\": [\n");
            List<User> uniqueUsers = getUniqueUsers();
            for (int i = 0; i < uniqueUsers.size(); i++) {
                User u = uniqueUsers.get(i);
                json.append(String.format(
                        "    {\"id\":\"%s\",\"name\":\"%s\",\"email\":\"%s\",\"mobile\":\"%s\",\"passwordHash\":\"%s\",\"role\":\"%s\",\"department\":\"%s\",\"active\":%b,\"createdAt\":\"%s\"}",
                        escapeJson(u.getId()), escapeJson(u.getDisplayName()), escapeJson(u.getEmail()),
                        escapeJson(u.getMobile()), escapeJson(u.getPasswordHash()), escapeJson(u.getRole()),
                        escapeJson(u.getDepartment()), u.isActive(), escapeJson(u.getCreatedAt())
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
                        "    {\"id\":\"%s\",\"complaintId\":\"%s\",\"citizenId\":\"%s\",\"citizenName\":\"%s\",\"citizenMobile\":\"%s\",\"category\":\"%s\",\"description\":\"%s\",\"location\":\"%s\",\"latitude\":%s,\"longitude\":%s,\"locationAccuracyMeters\":%s,\"departmentId\":\"%s\",\"department\":\"%s\",\"citizenReportedImpact\":%d,\"adminVerifiedImpact\":%s,\"verificationReason\":\"%s\",\"isVerifiedLocked\":%b,\"severity\":\"%s\",\"priority\":\"%s\",\"status\":\"%s\",\"assignedStaff\":\"%s\",\"resolutionNotes\":\"%s\",\"imageUrl\":\"%s\",\"assessedBy\":\"%s\",\"assessedAt\":\"%s\",\"assessmentReason\":\"%s\",\"isDuplicate\":%b,\"duplicateOfId\":\"%s\",\"rejectedCandidateIds\":\"%s\",\"isWaiting\":%b,\"arrivalSequence\":%d,\"createdAt\":\"%s\",\"updatedAt\":\"%s\",\"resolvedAt\":\"%s\"}",
                        escapeJson(c.getId()), escapeJson(c.getComplaintId()), escapeJson(c.getCitizenId()),
                        escapeJson(c.getCitizenName()), escapeJson(c.getCitizenMobile()), escapeJson(c.getCategory()),
                        escapeJson(c.getDescription()), escapeJson(c.getLocation()),
                        (c.getLatitude() == null ? "null" : c.getLatitude().toString()),
                        (c.getLongitude() == null ? "null" : c.getLongitude().toString()),
                        (c.getLocationAccuracyMeters() == null ? "null" : c.getLocationAccuracyMeters().toString()),
                        escapeJson(c.getDepartmentId()),
                        escapeJson(c.getDepartment()), c.getCitizenReportedImpact(),
                        (c.getAdminVerifiedImpact() == null ? "null" : c.getAdminVerifiedImpact()),
                        escapeJson(c.getVerificationReason()), c.isVerifiedLocked(),
                        (c.getSeverity() == null ? "UNASSESSED" : escapeJson(c.getSeverity())),
                        (c.getPriority() == null ? "UNASSESSED" : escapeJson(c.getPriority())),
                        escapeJson(c.getStatus()),
                        escapeJson(c.getAssignedStaff()), escapeJson(c.getResolutionNotes()),
                        (c.getImageUrl() == null ? "" : escapeJson(c.getImageUrl())),
                        escapeJson(c.getAssessedBy()), escapeJson(c.getAssessedAt()), escapeJson(c.getAssessmentReason()),
                        c.isDuplicate(), escapeJson(c.getDuplicateOfId()),
                        escapeJson(c.getRejectedCandidateIdsCsv()),
                        c.isWaiting(),
                        c.getArrivalSequence(),
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
        File file = new File(getEffectiveDbFile());
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

            // Startup demonstration data validation and diagnostic report
            if (customDbFile == null) {
                DataValidator.validateAndReport(this);
            }
        } catch (Exception e) {
            System.err.println("[DATABASE WARNING] Could not parse existing database. Initializing fresh: " + e.getMessage());
        }
    }

    public List<User> getUniqueUsers() {
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

            String activeVal = extractJsonVal(objStr, "active");
            boolean active = activeVal.isEmpty() || !"false".equalsIgnoreCase(activeVal);

            User u;
            if ("ADMIN".equalsIgnoreCase(role)) {
                u = new Admin(id, email, email, mobile, passwordHash, name, createdAt);
            } else if ("MANAGER".equalsIgnoreCase(role)) {
                u = new Manager(id, email, email, mobile, passwordHash, name, createdAt);
            } else if ("STAFF".equalsIgnoreCase(role) || "WORKER".equalsIgnoreCase(role)) {
                Department dept = getDepartmentByAnyIdentifier(department);
                u = new DepartmentStaff(id, email, email, mobile, passwordHash, role.toUpperCase(), name,
                        dept != null ? dept.getDepartmentId() : "DEPT-GEN", dept, createdAt);
            } else {
                u = new Citizen(id, name, email, mobile, passwordHash, createdAt);
            }
            u.setActive(active);

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

            // GPS Location fields
            c.setLatitude(extractJsonDoubleVal(objStr, "latitude"));
            c.setLongitude(extractJsonDoubleVal(objStr, "longitude"));
            c.setLocationAccuracyMeters(extractJsonDoubleVal(objStr, "locationAccuracyMeters"));

            // Assessment audit fields
            c.setAssessedBy(extractJsonVal(objStr, "assessedBy"));
            c.setAssessedAt(extractJsonVal(objStr, "assessedAt"));
            c.setAssessmentReason(extractJsonVal(objStr, "assessmentReason"));

            String sevRaw = extractJsonVal(objStr, "severity");
            if (sevRaw.isEmpty() || "null".equalsIgnoreCase(sevRaw) || "UNASSESSED".equalsIgnoreCase(sevRaw)) {
                c.setSeverity(null);
            } else {
                c.setSeverity(sevRaw);
            }

            String prioRaw = extractJsonVal(objStr, "priority");
            if (prioRaw.isEmpty() || "null".equalsIgnoreCase(prioRaw) || "UNASSESSED".equalsIgnoreCase(prioRaw)) {
                c.setPriority(c.getSeverity() != null ? c.getSeverity() : "UNASSESSED");
            } else {
                c.setPriority(prioRaw);
            }

            c.setStatus(extractJsonVal(objStr, "status"));
            c.setAssignedStaff(extractJsonVal(objStr, "assignedStaff"));
            c.setResolutionNotes(extractJsonVal(objStr, "resolutionNotes"));
            c.setImageUrl(extractJsonVal(objStr, "imageUrl"));

            c.setDuplicate("true".equalsIgnoreCase(extractJsonVal(objStr, "isDuplicate")));
            c.setDuplicateOfId(extractJsonVal(objStr, "duplicateOfId"));
            c.setRejectedCandidateIdsFromCsv(extractJsonVal(objStr, "rejectedCandidateIds"));
            c.setWaiting("true".equalsIgnoreCase(extractJsonVal(objStr, "isWaiting")));

            String arrSeq = extractJsonIntVal(objStr, "arrivalSequence");
            if (!arrSeq.isEmpty()) {
                c.forceSetArrivalSequenceFromPersistence(Long.parseLong(arrSeq));
            } else if (c.getComplaintId() != null && c.getComplaintId().startsWith("GRV-2026-")) {
                try {
                    long seq = Long.parseLong(c.getComplaintId().replace("GRV-2026-", ""));
                    c.forceSetArrivalSequenceFromPersistence(seq);
                } catch (Exception ignored) {}
            }

            c.setCreatedAt(extractJsonVal(objStr, "createdAt"));
            c.setUpdatedAt(extractJsonVal(objStr, "updatedAt"));
            c.setResolvedAt(extractJsonVal(objStr, "resolvedAt"));

            Complaint adapted = GrievanceFactory.adapt(c);
            complaintList.add(adapted);
            complaintMap.put(adapted.getComplaintId(), adapted);
            complaintMap.put(adapted.getId(), adapted);

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

    private Double extractJsonDoubleVal(String objStr, String key) {
        String pattern = "\"" + key + "\":";
        int start = objStr.indexOf(pattern);
        if (start == -1) return null;
        start += pattern.length();
        int end = objStr.indexOf(",", start);
        if (end == -1) end = objStr.indexOf("}", start);
        if (end == -1) return null;
        String val = objStr.substring(start, end).replaceAll("[\"\\s]", "").trim();
        if (val.isEmpty() || "null".equalsIgnoreCase(val)) return null;
        try {
            return Double.parseDouble(val);
        } catch (Exception e) {
            return null;
        }
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

        // Reset department loads and waiting queues across unique departments
        for (Department d : getAllUniqueDepartments()) {
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
            }
        }

        // Capacity reconstruction:
        // Pass 1: InProgress complaints consume capacity first
        for (Complaint c : complaintList) {
            boolean isResolved = "RESOLVED".equalsIgnoreCase(c.getStatus()) || "CLOSED".equalsIgnoreCase(c.getStatus());
            if (isResolved) continue;

            if ("IN_PROGRESS".equalsIgnoreCase(c.getStatus())) {
                Department dept = findDepartment(c.getDepartmentId() != null ? c.getDepartmentId() : c.getDepartment());
                if (dept == null) dept = getDepartmentByAnyIdentifier(c.getDepartment());
                if (dept != null) {
                    if (dept.getCurrentLoad() < dept.getCapacity()) {
                        dept.incrementLoad();
                        c.setWaiting(false);
                    } else {
                        dept.addToWaitingQueue(c);
                    }
                }
            }
        }

        // Pass 2: Assigned complaints, prioritized by PRIORITY_COMPARATOR
        List<Complaint> assignedComplaints = new ArrayList<>();
        for (Complaint c : complaintList) {
            boolean isResolved = "RESOLVED".equalsIgnoreCase(c.getStatus()) || "CLOSED".equalsIgnoreCase(c.getStatus());
            if (!isResolved && "ASSIGNED".equalsIgnoreCase(c.getStatus())) {
                assignedComplaints.add(c);
            }
        }
        assignedComplaints.sort(Complaint.PRIORITY_COMPARATOR);

        for (Complaint c : assignedComplaints) {
            Department dept = findDepartment(c.getDepartmentId() != null ? c.getDepartmentId() : c.getDepartment());
            if (dept == null) dept = getDepartmentByAnyIdentifier(c.getDepartment());
            if (dept != null) {
                if (c.isWaiting()) {
                    dept.addToWaitingQueue(c);
                } else if (dept.getCurrentLoad() < dept.getCapacity()) {
                    dept.incrementLoad();
                    c.setWaiting(false);
                } else {
                    dept.addToWaitingQueue(c);
                }
            }
        }
    }

    public String buildDuplicateKey(String category, String location) {
        String normCat = model.LocationNormalizer.normalizeCategory(category);
        String normLoc = model.LocationNormalizer.normalizeLocation(location);
        if (normCat.isEmpty() || normLoc == null) {
            return (category != null ? category.trim().toLowerCase() : "") + ":::" +
                   (location != null ? location.trim().toLowerCase() : "");
        }
        return normCat + ":::" + normLoc;
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
    public synchronized User registerUser(String name, String email, String mobile, String rawPassword) {
        return registerUser(name, email, mobile, rawPassword, "CITIZEN", null);
    }

    public User authenticateUser(String emailOrId, String rawPassword) {
        if (emailOrId == null || rawPassword == null) return null;
        User user = getUserByEmail(emailOrId);
        if (user == null) {
            user = getUserById(emailOrId);
        }
        if (user == null) return null;
        if (!user.isActive()) return null;
        if (user.getPasswordHash() != null && user.getPasswordHash().equals(hashPassword(rawPassword))) {
            return user;
        }
        return null;
    }

    public synchronized User registerUser(String name, String email, String mobile, String rawPassword, String role, String department) {
        String lowerEmail = email.toLowerCase().trim();
        if (getUserByEmail(lowerEmail) != null) {
            return null; // Already exists
        }

        String userId = "USR" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        User user;
        if ("ADMIN".equalsIgnoreCase(role)) {
            user = new Admin(userId, lowerEmail, lowerEmail, mobile, hashPassword(rawPassword), name, "2026-09-14 12:00:00");
        } else if ("MANAGER".equalsIgnoreCase(role)) {
            user = new Manager(userId, lowerEmail, lowerEmail, mobile, hashPassword(rawPassword), name, "2026-09-14 12:00:00");
        } else if ("STAFF".equalsIgnoreCase(role) || "WORKER".equalsIgnoreCase(role)) {
            Department dept = getDepartmentByAnyIdentifier(department);
            user = new DepartmentStaff(userId, lowerEmail, lowerEmail, mobile, hashPassword(rawPassword), role.toUpperCase(), name,
                    dept != null ? dept.getDepartmentId() : "DEPT-GEN", dept, "2026-09-14 12:00:00");
        } else {
            user = new Citizen(userId, name, lowerEmail, mobile, hashPassword(rawPassword), "2026-09-14 12:00:00");
        }

        userMap.put(lowerEmail, user);
        userMap.put(userId, user);
        if (user.getUsername() != null && !user.getUsername().trim().isEmpty()) {
            userMap.put(user.getUsername().toLowerCase().trim(), user);
        }

        if (useMySql) {
            try {
                userRepository.save(user);
                auditLogRepository.log(userId, "REGISTER_USER", "USER", userId, "Registered " + user.getRole() + ": " + user.getEmail());
            } catch (SQLException e) {
                System.err.println("[MYSQL USER ERROR] " + e.getMessage());
                throw new RuntimeException("MySQL Persistence Error: " + e.getMessage(), e);
            }
        } else {
            saveDatabase();
        }
        return user;
    }

    public User getUserByEmail(String email) {
        if (email == null) return null;
        String cleanEmail = email.toLowerCase().trim();
        if ("admin".equals(cleanEmail) || "stf1001".equals(cleanEmail)) {
            cleanEmail = "admin@makkal.gov.in";
        } else if ("manager".equals(cleanEmail) || "mgr1001".equals(cleanEmail)) {
            cleanEmail = "manager@makkal.gov.in";
        } else if ("suresh".equals(cleanEmail) || "water".equals(cleanEmail) || "stf-w01".equals(cleanEmail)) {
            cleanEmail = "water_staff@makkal.gov.in";
        } else if ("ramesh".equals(cleanEmail) || "c001".equals(cleanEmail)) {
            cleanEmail = "ramesh@test.com";
        }
        User u = userMap.get(cleanEmail);
        if (u == null && useMySql) {
            try {
                u = userRepository.findByEmail(cleanEmail);
                if (u != null) {
                    userMap.put(u.getId(), u);
                    userMap.put(u.getEmail().toLowerCase().trim(), u);
                    if (u.getUsername() != null && !u.getUsername().trim().isEmpty()) {
                        userMap.put(u.getUsername().toLowerCase().trim(), u);
                    }
                }
            } catch (SQLException ignored) {}
        }
        return u;
    }

    public User getUserById(String id) {
        if (id == null) return null;
        String cleanId = id.trim();
        if ("STF1001".equalsIgnoreCase(cleanId)) {
            cleanId = "ADM1001";
        }
        User u = userMap.get(cleanId);
        if (u == null && useMySql) {
            try {
                u = userRepository.findById(cleanId);
                if (u != null) {
                    userMap.put(u.getId(), u);
                    userMap.put(u.getEmail().toLowerCase().trim(), u);
                    if (u.getUsername() != null && !u.getUsername().trim().isEmpty()) {
                        userMap.put(u.getUsername().toLowerCase().trim(), u);
                    }
                }
            } catch (SQLException ignored) {}
        }
        return u;
    }

    public String createSession(User user) {
        String token = "TOKEN-" + UUID.randomUUID().toString();
        activeSessions.put(token, user.getId());
        return token;
    }

    public void invalidateSession(String token) {
        if (token != null) {
            String cleanToken = token.replace("Bearer ", "").trim();
            activeSessions.remove(cleanToken);
        }
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

    // ------------------- WORKER MANAGEMENT -------------------
    public synchronized List<DepartmentStaff> getAllWorkers() {
        List<DepartmentStaff> workers = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (User u : userMap.values()) {
            if (u instanceof DepartmentStaff && seen.add(u.getId())) {
                workers.add((DepartmentStaff) u);
            }
        }
        return workers;
    }

    public synchronized DepartmentStaff createWorker(String name, String email, String mobile, String rawPassword, String departmentName, User adminActor) {
        if (adminActor == null || !adminActor.isAdmin()) {
            throw new SecurityException("Unauthorized: Only administrators can create workers.");
        }
        if (name == null || name.trim().isEmpty() || email == null || email.trim().isEmpty() || rawPassword == null || rawPassword.trim().isEmpty()) {
            throw new IllegalArgumentException("Worker name, email, and password are required.");
        }
        String lowerEmail = email.toLowerCase().trim();
        if (userMap.containsKey(lowerEmail)) {
            throw new IllegalArgumentException("An account with email " + email + " already exists.");
        }

        Department dept = getDepartmentByAnyIdentifier(departmentName);
        if (dept == null) {
            dept = departmentMap.get("DEPT-GEN");
        }
        String workerId = "WRK" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        DepartmentStaff worker = new DepartmentStaff(
                workerId, lowerEmail, lowerEmail, mobile, hashPassword(rawPassword), "WORKER",
                name.trim(), dept != null ? dept.getDepartmentId() : "DEPT-GEN", dept,
                java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        );
        userMap.put(lowerEmail, worker);
        userMap.put(workerId, worker);
        userMap.put(worker.getUsername().toLowerCase(), worker);

        if (useMySql) {
            try {
                userRepository.save(worker);
                auditLogRepository.log(adminActor.getId(), "CREATE_WORKER", "USER", workerId,
                        "Created worker: " + worker.getStaffName() + " for " + worker.getDepartment());
            } catch (SQLException e) {
                System.err.println("[MYSQL WORKER ERROR] " + e.getMessage());
                throw new RuntimeException("MySQL Persistence Error: " + e.getMessage(), e);
            }
        } else {
            saveDatabase();
        }
        return worker;
    }

    public synchronized DepartmentStaff updateWorker(String workerId, String name, String mobile, String departmentName, Boolean active, User adminActor) {
        return updateWorker(workerId, name, null, mobile, departmentName, active, adminActor);
    }

    public synchronized DepartmentStaff updateWorker(String workerId, String name, String email, String mobile, String departmentName, Boolean active, User adminActor) {
        if (adminActor == null || !adminActor.isAdmin()) {
            throw new SecurityException("Unauthorized: Only administrators can update workers.");
        }
        User u = getUserById(workerId);
        if (u == null || !(u instanceof DepartmentStaff)) {
            throw new IllegalArgumentException("Worker not found with ID: " + workerId);
        }
        DepartmentStaff worker = (DepartmentStaff) u;
        String prevDept = worker.getDepartment();

        if (name != null && !name.trim().isEmpty()) {
            worker.setStaffName(name.trim());
            worker.setUsername(name.trim());
        }
        if (mobile != null) {
            worker.setMobile(mobile.trim());
        }
        if (active != null) {
            worker.setActive(active);
        }

        boolean deptChanged = false;
        if (departmentName != null && !departmentName.trim().isEmpty()) {
            Department newDept = getDepartmentByAnyIdentifier(departmentName);
            if (newDept != null && !newDept.getDepartmentName().equalsIgnoreCase(prevDept)) {
                worker.setDepartmentObj(newDept);
                deptChanged = true;
            }
        }

        // Handover: If worker is deactivated or transferred, reassign/unassign active complaints
        if (!worker.isActive() || deptChanged) {
            handoverWorkerComplaints(worker, adminActor, deptChanged ? "Worker transferred from " + prevDept + " to " + worker.getDepartment() : "Worker deactivated by administrator");
        }

        if (useMySql) {
            try {
                userRepository.save(worker);
                auditLogRepository.log(adminActor.getId(), "UPDATE_WORKER", "USER", workerId,
                        "Updated worker details for " + worker.getStaffName());
            } catch (SQLException e) {
                System.err.println("[MYSQL WORKER ERROR] " + e.getMessage());
                throw new RuntimeException("MySQL Persistence Error: " + e.getMessage(), e);
            }
        } else {
            saveDatabase();
        }
        return worker;
    }

    public synchronized boolean resetWorkerPassword(String workerId, String newPassword, User adminActor) {
        if (adminActor == null || !adminActor.isAdmin()) {
            throw new SecurityException("Unauthorized: Only administrators can reset worker passwords.");
        }
        if (newPassword == null || newPassword.trim().isEmpty()) {
            throw new IllegalArgumentException("New password cannot be empty.");
        }
        User u = getUserById(workerId);
        if (u == null || !(u instanceof DepartmentStaff)) {
            throw new IllegalArgumentException("Worker not found with ID: " + workerId);
        }
        u.setPasswordHash(hashPassword(newPassword.trim()));

        if (useMySql) {
            try {
                userRepository.save(u);
                auditLogRepository.log(adminActor.getId(), "PASSWORD_RESET", "USER", workerId,
                        "Password reset by administrator for worker ID " + workerId);
            } catch (SQLException e) {
                System.err.println("[MYSQL WORKER ERROR] " + e.getMessage());
                throw new RuntimeException("MySQL Persistence Error: " + e.getMessage(), e);
            }
        } else {
            saveDatabase();
        }
        return true;
    }

    private void handoverWorkerComplaints(DepartmentStaff worker, User adminActor, String handoverReason) {
        String workerName = worker.getStaffName();
        String workerDisp = worker.getDisplayName();
        for (Complaint c : complaintList) {
            if ("Assigned".equalsIgnoreCase(c.getStatus()) || "InProgress".equalsIgnoreCase(c.getStatus())) {
                boolean matches = (c.getAssignedStaff() != null &&
                        (c.getAssignedStaff().equalsIgnoreCase(workerName) ||
                         c.getAssignedStaff().equalsIgnoreCase(workerDisp) ||
                         c.getAssignedStaff().toLowerCase().contains(workerName.toLowerCase()) ||
                         c.getAssignedStaff().toLowerCase().contains(workerDisp.toLowerCase())));
                if (matches) {
                    c.setAssignedStaff("Unassigned");
                    c.setUpdatedAt(java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
                    addHistory(new ComplaintHistory(
                            generateHistoryId(),
                            c.getComplaintId(),
                            c.getStatus(),
                            c.getStatus(),
                            adminActor != null ? adminActor.getDisplayName() : "Administrator",
                            "Staff Handover: " + handoverReason + ". Assigned staff reset to Unassigned for department reallocation."
                    ));
                }
            }
        }
    }

    public synchronized boolean removeComplaint(String complaintId) {
        if (complaintId == null) return false;
        Complaint c = complaintMap.remove(complaintId);
        if (c != null) {
            if (c.getId() != null) complaintMap.remove(c.getId());
            complaintList.remove(c);
            priorityQueue.remove(c);
            return true;
        }
        return false;
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

        if (useMySql) {
            try {
                complaintRepository.save(complaint);
                if (complaint.getDepartmentId() != null) {
                    assignmentRepository.recordAssignment(complaint.getId(), complaint.getDepartmentId(),
                            complaint.getAssignedStaff(), "System", "Active", "Initial Registration");
                }
                auditLogRepository.log(complaint.getCitizenId(), "REGISTER_COMPLAINT", "COMPLAINT",
                        complaint.getComplaintId(), "Registered at " + complaint.getLocation() + " (Impact: " + complaint.getCitizenReportedImpact() + ")");
            } catch (SQLException e) {
                System.err.println("[MYSQL PERSISTENCE ERROR] " + e.getMessage());
                throw new RuntimeException("MySQL Persistence Error: " + e.getMessage(), e);
            }
        } else {
            saveDatabase();
        }
    }

    public synchronized void updateComplaint(Complaint complaint) {
        if (complaint == null) return;
        complaintMap.put(complaint.getComplaintId(), complaint);
        if (complaint.getId() != null) complaintMap.put(complaint.getId(), complaint);

        if (useMySql) {
            try {
                complaintRepository.update(complaint);
            } catch (SQLException e) {
                System.err.println("[MYSQL UPDATE ERROR] " + e.getMessage());
                throw new RuntimeException("MySQL Persistence Error: " + e.getMessage(), e);
            }
        } else {
            saveDatabase();
        }
    }

    public UserRepository getUserRepository() { return userRepository; }
    public DepartmentRepository getDepartmentRepository() { return departmentRepository; }
    public ComplaintRepository getComplaintRepository() { return complaintRepository; }
    public AssignmentRepository getAssignmentRepository() { return assignmentRepository; }
    public StatusUpdateRepository getStatusUpdateRepository() { return statusUpdateRepository; }
    public DuplicateRepository getDuplicateRepository() { return duplicateRepository; }
    public AuditLogRepository getAuditLogRepository() { return auditLogRepository; }
    public ReportRepository getReportRepository() { return reportRepository; }
    public boolean isUsingMySql() { return useMySql; }
    public void setUseMySql(boolean flag) { this.useMySql = flag; }

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

    public PriorityQueue<Complaint> getPriorityQueue() {
        return priorityQueue;
    }

    /**
     * Removes and re-inserts a waiting complaint to maintain correct heap ordering.
     * Does not mutate or reorder resolved complaints.
     */
    public synchronized boolean updateComplaintPriorityInQueues(Complaint complaint) {
        if (complaint == null) return false;
        boolean isResolved = "RESOLVED".equalsIgnoreCase(complaint.getStatus()) || "CLOSED".equalsIgnoreCase(complaint.getStatus());
        if (isResolved) {
            priorityQueue.remove(complaint);
            Department dept = getDepartmentByAnyIdentifier(complaint.getDepartment());
            if (dept != null) {
                dept.removeFromWaitingQueue(complaint);
            }
            return false;
        }

        priorityQueue.remove(complaint);
        priorityQueue.add(complaint);

        Department dept = getDepartmentByAnyIdentifier(complaint.getDepartment());
        if (dept != null) {
            dept.updateComplaintPriority(complaint);
        }
        return true;
    }

    public List<Complaint> getOrderedPriorityQueueList() {
        PriorityQueue<Complaint> tempQueue = new PriorityQueue<>(Complaint.PRIORITY_COMPARATOR);
        tempQueue.addAll(priorityQueue);
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

    /**
     * Strict department lookup without falling back to General Administration.
     * Returns null if department identifier or name is unknown.
     */
    public Department findDepartment(String idOrName) {
        if (idOrName == null || idOrName.trim().isEmpty()) return null;
        String trimmed = idOrName.trim();
        Department d = departmentMap.get(trimmed);
        if (d != null) return d;

        for (Department dept : departmentMap.values()) {
            if (dept.getDepartmentName().equalsIgnoreCase(trimmed) ||
                dept.getDepartmentId().equalsIgnoreCase(trimmed)) {
                return dept;
            }
        }
        return null;
    }

    public boolean isValidDepartment(String idOrName) {
        return findDepartment(idOrName) != null;
    }

    public List<Department> getAllUniqueDepartments() {
        Map<String, Department> map = new HashMap<>();
        for (Department d : departmentMap.values()) {
            map.put(d.getDepartmentId(), d);
        }
        return new ArrayList<>(map.values());
    }

    public List<Department> getAllDepartments() {
        return getAllUniqueDepartments();
    }

    /**
     * Strict list of the four core municipal departments required by municipal mandate.
     */
    public static final List<String> CORE_DEPARTMENT_IDS = java.util.Collections.unmodifiableList(
            java.util.Arrays.asList("DEPT-WATER", "DEPT-ROADS", "DEPT-WASTE", "DEPT-LIGHT")
    );

    /**
     * Returns strictly the four core operational municipal departments.
     * General Administration is excluded as it is strictly an administrative triage fallback.
     */
    public List<Department> getCoreDepartments() {
        List<Department> cores = new ArrayList<>();
        for (String id : CORE_DEPARTMENT_IDS) {
            Department d = departmentMap.get(id);
            if (d != null) cores.add(d);
        }
        return cores;
    }

    /**
     * Checks if a department identifier or name belongs to one of the 4 core municipal departments.
     */
    public boolean isCoreDepartment(String idOrName) {
        Department d = findDepartment(idOrName);
        if (d == null) return false;
        return CORE_DEPARTMENT_IDS.contains(d.getDepartmentId());
    }

    // History & Audit Trail Operations
    public synchronized void addHistory(ComplaintHistory history) {
        historyMap.computeIfAbsent(history.getComplaintId(), k -> new ArrayList<>()).add(history);
        if (useMySql) {
            try {
                statusUpdateRepository.recordUpdate(history.getComplaintId(), history.getOldStatus(),
                        history.getNewStatus(), history.getUpdatedBy(), history.getRemarks());
                auditLogRepository.log(history.getUpdatedBy(), "STATUS_UPDATE", "COMPLAINT",
                        history.getComplaintId(), "Transition: " + history.getOldStatus() + " -> " + history.getNewStatus() + ". Remarks: " + history.getRemarks());
            } catch (SQLException e) {
                System.err.println("[MYSQL STATUS ERROR] Failed to record status update: " + e.getMessage());
            }
        } else {
            saveDatabase();
        }
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
