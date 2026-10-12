package repository;

import service.DatabaseConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * JDBC Repository for Duplicate Links and Candidate Review.
 * Enforces prevention of self-linking and duplicates pair safety.
 */
public class DuplicateRepository {

    public void linkCandidate(String complaintId, String targetId) throws SQLException {
        if (complaintId == null || targetId == null || complaintId.equalsIgnoreCase(targetId)) {
            return;
        }
        String id = "DUP-" + UUID.randomUUID().toString().substring(0, 8);
        String sql = "INSERT INTO duplicate_links (id, complaint_id, possible_duplicate_id, review_status, created_at) " +
                     "VALUES (?, ?, ?, 'PENDING', NOW()) " +
                     "ON DUPLICATE KEY UPDATE review_status = review_status";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            ps.setString(2, complaintId);
            ps.setString(3, targetId);
            ps.executeUpdate();
        }
    }

    public boolean updateReview(String complaintId, String targetId, String reviewer, String status, String notes) throws SQLException {
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
        }
    }

    public List<Map<String, String>> findCandidates(String complaintId) throws SQLException {
        List<Map<String, String>> list = new ArrayList<>();
        if (complaintId == null) return list;
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
        }
        return list;
    }
}
