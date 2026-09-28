package service;

import model.Complaint;
import model.User;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class Database {
    private static final String DB_DIR = "../data";
    private static final String DB_FILE = "../data/database.json";

    private Map<String, User> userMap;            // email/id -> User (HashMap)
    private List<Complaint> complaintList;        // ArrayList of complaints
    private Map<String, Complaint> complaintMap;  // complaintId -> Complaint (HashMap)
    private PriorityQueue<Complaint> priorityQueue;// PriorityQueue for priority sorting
    private Map<String, String> activeSessions;   // token -> userId

    private int complaintCounter = 0;

    public Database() {
        this.userMap = new ConcurrentHashMap<>();
        this.complaintList = new ArrayList<>();
        this.complaintMap = new ConcurrentHashMap<>();
        this.priorityQueue = new PriorityQueue<>();
        this.activeSessions = new ConcurrentHashMap<>();

        ensureDatabaseFile();
        loadDatabase();
        seedInitialStaffAccount();
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

    // Seed Initial Admin/Staff Account if none exists
    private void seedInitialStaffAccount() {
        boolean staffExists = false;
        for (User u : userMap.values()) {
            if ("STAFF".equalsIgnoreCase(u.getRole())) {
                staffExists = true;
                break;
            }
        }

        if (!staffExists) {
            System.out.println("[DATABASE] Seeding initial Municipal Staff/Admin account...");
            User admin = new User(
                    "STF1001",
                    "Municipal Officer",
                    "admin@makkal.gov.in",
                    "9876543210",
                    hashPassword("admin123"),
                    "STAFF",
                    "General Administration",
                    "2026-09-14 00:00:00"
            );
            userMap.put(admin.getEmail().toLowerCase(), admin);
            userMap.put(admin.getId(), admin);
            saveDatabase();
            System.out.println("[DATABASE SUCCESS] Initial Admin Account created: STF1001 / admin@makkal.gov.in (Password: admin123)");
        }
    }

    // Save state to disk
    public synchronized void saveDatabase() {
        try (FileWriter writer = new FileWriter(DB_FILE, false)) {
            StringBuilder json = new StringBuilder();
            json.append("{\n");
            
            // Users
            json.append("  \"users\": [\n");
            List<User> uniqueUsers = getUniqueUsers();
            for (int i = 0; i < uniqueUsers.size(); i++) {
                User u = uniqueUsers.get(i);
                json.append(String.format(
                        "    {\"id\":\"%s\",\"name\":\"%s\",\"email\":\"%s\",\"mobile\":\"%s\",\"passwordHash\":\"%s\",\"role\":\"%s\",\"department\":\"%s\",\"createdAt\":\"%s\"}",
                        escapeJson(u.getId()), escapeJson(u.getName()), escapeJson(u.getEmail()),
                        escapeJson(u.getMobile()), escapeJson(u.getPasswordHash()), escapeJson(u.getRole()),
                        escapeJson(u.getDepartment()), escapeJson(u.getCreatedAt())
                ));
                if (i < uniqueUsers.size() - 1) json.append(",");
                json.append("\n");
            }
            json.append("  ],\n");

            // Complaints
            json.append("  \"complaints\": [\n");
            for (int i = 0; i < complaintList.size(); i++) {
                Complaint c = complaintList.get(i);
                json.append(String.format(
                        "    {\"id\":\"%s\",\"complaintId\":\"%s\",\"citizenId\":\"%s\",\"citizenName\":\"%s\",\"citizenMobile\":\"%s\",\"category\":\"%s\",\"description\":\"%s\",\"location\":\"%s\",\"department\":\"%s\",\"priority\":\"%s\",\"status\":\"%s\",\"assignedStaff\":\"%s\",\"resolutionNotes\":\"%s\",\"createdAt\":\"%s\",\"updatedAt\":\"%s\",\"resolvedAt\":\"%s\"}",
                        escapeJson(c.getId()), escapeJson(c.getComplaintId()), escapeJson(c.getCitizenId()),
                        escapeJson(c.getCitizenName()), escapeJson(c.getCitizenMobile()), escapeJson(c.getCategory()),
                        escapeJson(c.getDescription()), escapeJson(c.getLocation()), escapeJson(c.getDepartment()),
                        escapeJson(c.getPriority()), escapeJson(c.getStatus()), escapeJson(c.getAssignedStaff()),
                        escapeJson(c.getResolutionNotes()), escapeJson(c.getCreatedAt()), escapeJson(c.getUpdatedAt()),
                        escapeJson(c.getResolvedAt())
                ));
                if (i < complaintList.size() - 1) json.append(",");
                json.append("\n");
            }
            json.append("  ],\n");
            json.append("  \"complaintCounter\": ").append(complaintCounter).append("\n");
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

            // Parse Users
            int usersStart = content.indexOf("\"users\":");
            int complaintsStart = content.indexOf("\"complaints\":");
            int counterStart = content.indexOf("\"complaintCounter\":");

            if (usersStart != -1 && complaintsStart != -1) {
                String usersJson = content.substring(usersStart, complaintsStart);
                parseUsers(usersJson);
            }

            if (complaintsStart != -1) {
                String complaintsJson = (counterStart != -1) ? 
                        content.substring(complaintsStart, counterStart) : content.substring(complaintsStart);
                parseComplaints(complaintsJson);
            }

            if (counterStart != -1) {
                String counterStr = content.substring(counterStart + "\"complaintCounter\":".length()).replaceAll("[^0-9]", "").trim();
                if (!counterStr.isEmpty()) {
                    this.complaintCounter = Integer.parseInt(counterStr);
                }
            }

            rebuildPriorityQueue();
            System.out.println("[DATABASE LOADED] Total Users: " + getUniqueUsers().size() + " | Total Complaints: " + complaintList.size());
        } catch (Exception e) {
            System.err.println("[DATABASE WARNING] Could not parse existing database. Starting fresh: " + e.getMessage());
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
            User u = new User(
                    extractJsonVal(objStr, "id"),
                    extractJsonVal(objStr, "name"),
                    extractJsonVal(objStr, "email"),
                    extractJsonVal(objStr, "mobile"),
                    extractJsonVal(objStr, "passwordHash"),
                    extractJsonVal(objStr, "role"),
                    extractJsonVal(objStr, "department"),
                    extractJsonVal(objStr, "createdAt")
            );
            if (u.getEmail() != null) {
                userMap.put(u.getEmail().toLowerCase(), u);
            }
            if (u.getId() != null) {
                userMap.put(u.getId(), u);
            }
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
            c.setDepartment(extractJsonVal(objStr, "department"));
            c.setPriority(extractJsonVal(objStr, "priority"));
            c.setStatus(extractJsonVal(objStr, "status"));
            c.setAssignedStaff(extractJsonVal(objStr, "assignedStaff"));
            c.setResolutionNotes(extractJsonVal(objStr, "resolutionNotes"));
            c.setCreatedAt(extractJsonVal(objStr, "createdAt"));
            c.setUpdatedAt(extractJsonVal(objStr, "updatedAt"));
            c.setResolvedAt(extractJsonVal(objStr, "resolvedAt"));

            complaintList.add(c);
            complaintMap.put(c.getComplaintId(), c);
            complaintMap.put(c.getId(), c);

            // Update complaint counter
            if (c.getComplaintId() != null && c.getComplaintId().startsWith("GRV-2026-")) {
                try {
                    int num = Integer.parseInt(c.getComplaintId().replace("GRV-2026-", ""));
                    if (num > complaintCounter) complaintCounter = num;
                } catch (Exception ignored) {}
            }

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
            return objStr.substring(start, end).replaceAll("\"", "").trim();
        }
        start += pattern.length();
        int end = objStr.indexOf("\"", start);
        if (end == -1) return "";
        return unescapeJson(objStr.substring(start, end));
    }

    private String escapeJson(String str) {
        if (str == null) return "";
        return str.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "");
    }

    private String unescapeJson(String str) {
        if (str == null) return "";
        return str.replace("\\n", "\n").replace("\\\"", "\"").replace("\\\\", "\\");
    }

    // Rebuild PriorityQueue after updates
    public void rebuildPriorityQueue() {
        priorityQueue.clear();
        for (Complaint c : complaintList) {
            if (!"RESOLVED".equalsIgnoreCase(c.getStatus()) && !"CLOSED".equalsIgnoreCase(c.getStatus())) {
                priorityQueue.add(c);
            }
        }
    }

    // Generate Auto Complaint ID (e.g. GRV-2026-0001)
    public synchronized String generateComplaintId() {
        complaintCounter++;
        return String.format("GRV-2026-%04d", complaintCounter);
    }

    // Auth & Users Operations
    public synchronized User registerUser(String name, String email, String mobile, String rawPassword, String role, String department) {
        String lowerEmail = email.toLowerCase().trim();
        if (userMap.containsKey(lowerEmail)) {
            return null; // User already exists
        }

        String userId = "USR" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        User user = new User(userId, name, lowerEmail, mobile, hashPassword(rawPassword), role, department, "2026-09-14 12:00:00");
        userMap.put(lowerEmail, user);
        userMap.put(userId, user);
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

    // Session Management
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

    public PriorityQueue<Complaint> getRawPriorityQueue() {
        return priorityQueue;
    }
}
