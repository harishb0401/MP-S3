package repository;

import service.DatabaseConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Repository for Duplicate Links, Candidate Review, and Duplicate Notification Tracking.
 * Supports both JDBC execution when MySQL is connected and robust synchronized memory/file persistence.
 * Enforces prevention of self-linking, duplicate link safety, and persistent anti-spam tracking.
 */
public class DuplicateRepository {

    // Persistent tracking of notified duplicate relationships and their citizen counts
    private final Map<String, Integer> duplicateNotificationTracking = Collections.synchronizedMap(new LinkedHashMap<>());

    // Resilient fallback storage when JDBC connection is not active
    private final Map<String, Map<String, String>> fallbackLinks = new ConcurrentHashMap<>();

    private boolean isJdbcActive() {
        try {
            return DatabaseConnection.isDriverAvailable() && DatabaseConnection.testConnection();
        } catch (Throwable t) {
            return false;
        }
    }

    public void linkCandidate(String complaintId, String targetId) throws SQLException {
        if (complaintId == null || targetId == null || complaintId.equalsIgnoreCase(targetId)) {
            return;
        }

        String id = "DUP-" + UUID.randomUUID().toString().substring(0, 8);

        if (isJdbcActive()) {
            String sql = "INSERT INTO duplicate_links (id, complaint_id, possible_duplicate_id, review_status, created_at) " +
                         "VALUES (?, ?, ?, 'PENDING', NOW()) " +
                         "ON DUPLICATE KEY UPDATE review_status = review_status";

            try (Connection conn = DatabaseConnection.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, id);
                ps.setString(2, complaintId);
                ps.setString(3, targetId);
                ps.executeUpdate();
                return;
            } catch (SQLException e) {
                // Fall back to local store on DB error
            }
        }

        // Fallback local memory store
        Map<String, String> link = new HashMap<>();
        link.put("id", id);
        link.put("complaint_id", complaintId);
        link.put("possible_duplicate_id", targetId);
        link.put("review_status", "PENDING");
        link.put("created_at", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        fallbackLinks.put(complaintId + ":::" + targetId, link);
    }

    public boolean updateReview(String complaintId, String targetId, String reviewer, String status, String notes) throws SQLException {
        if (isJdbcActive()) {
            String sql = "UPDATE duplicate_links SET review_status = ?, reviewed_by = ?, review_notes = ?, reviewed_at = NOW() " +
                         "WHERE (complaint_id = ? AND possible_duplicate_id = ?) OR (complaint_id = ? AND possible_duplicate_id = ?)";

            try (Connection conn = DatabaseConnection.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, status.toUpperCase());
                ps.setString(2, reviewer);
                ps.setString(3, notes);
                ps.setString(4, complaintId);
                ps.setString(5, targetId);
                ps.setString(6, targetId);
                ps.setString(7, complaintId);
                return ps.executeUpdate() > 0;
            } catch (SQLException e) {
                // Fall back to local store
            }
        }

        // Fallback
        boolean found = false;
        String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        for (Map<String, String> link : fallbackLinks.values()) {
            String cId = link.get("complaint_id");
            String pId = link.get("possible_duplicate_id");
            if ((complaintId.equalsIgnoreCase(cId) && targetId.equalsIgnoreCase(pId)) ||
                (targetId.equalsIgnoreCase(cId) && complaintId.equalsIgnoreCase(pId))) {
                link.put("review_status", status.toUpperCase());
                link.put("reviewed_by", reviewer);
                link.put("review_notes", notes);
                link.put("reviewed_at", now);
                found = true;
            }
        }
        return found;
    }

    public List<Map<String, String>> findCandidates(String complaintId) throws SQLException {
        List<Map<String, String>> list = new ArrayList<>();
        if (complaintId == null) return list;

        if (isJdbcActive()) {
            String sql = "SELECT * FROM duplicate_links WHERE (complaint_id = ? OR possible_duplicate_id = ?) AND review_status = 'PENDING'";
            try (Connection conn = DatabaseConnection.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, complaintId.trim());
                ps.setString(2, complaintId.trim());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, String> map = new HashMap<>();
                        map.put("id", rs.getString("id"));
                        map.put("complaint_id", rs.getString("complaint_id"));
                        map.put("possible_duplicate_id", rs.getString("possible_duplicate_id"));
                        map.put("review_status", rs.getString("review_status"));
                        list.add(map);
                    }
                }
                return list;
            } catch (SQLException e) {
                // Fall back to local store
            }
        }

        for (Map<String, String> link : fallbackLinks.values()) {
            String cId = link.get("complaint_id");
            String pId = link.get("possible_duplicate_id");
            String rev = link.get("review_status");
            if ((complaintId.equalsIgnoreCase(cId) || complaintId.equalsIgnoreCase(pId)) && "PENDING".equalsIgnoreCase(rev)) {
                list.add(new HashMap<>(link));
            }
        }
        return list;
    }

    // -----------------------------------------------------------------------------------
    // Duplicate Notification Anti-Spam Tracking Methods (survives restarts)
    // -----------------------------------------------------------------------------------

    public synchronized void recordNotificationSent(String pairKey, int citizenCount) {
        if (pairKey == null || pairKey.trim().isEmpty()) return;
        duplicateNotificationTracking.put(pairKey.trim(), citizenCount);
    }

    public synchronized int getNotifiedCitizenCount(String pairKey) {
        if (pairKey == null) return 0;
        Integer count = duplicateNotificationTracking.get(pairKey.trim());
        return count != null ? count : 0;
    }

    public synchronized boolean hasNotificationBeenSent(String pairKey, int citizenCount) {
        if (pairKey == null) return false;
        Integer prevCount = duplicateNotificationTracking.get(pairKey.trim());
        if (prevCount == null) return false;
        // Already notified if the recorded count is >= current citizenCount
        return prevCount >= citizenCount;
    }

    public synchronized Map<String, Integer> getTrackingData() {
        return new LinkedHashMap<>(duplicateNotificationTracking);
    }

    public synchronized void loadTrackingData(Map<String, Integer> data) {
        if (data != null) {
            duplicateNotificationTracking.putAll(data);
        }
    }

    public synchronized void clearTracking() {
        duplicateNotificationTracking.clear();
        fallbackLinks.clear();
    }
}
