package repository;

import model.ComplaintHistory;
import service.DatabaseConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * JDBC Repository for Status Updates and Audit Timeline history.
 */
public class StatusUpdateRepository {

    public void recordUpdate(String complaintId, String oldStatus, String newStatus, String updatedBy, String notes) throws SQLException {
        if (complaintId == null || complaintId.trim().isEmpty()) return;
        String id = "HIST-" + UUID.randomUUID().toString().substring(0, 8);
        String sql = "INSERT INTO status_updates (id, complaint_id, previous_status, new_status, updated_by, notes, created_at) " +
                     "VALUES (?, COALESCE((SELECT id FROM complaints WHERE id = ? OR complaint_number = ? LIMIT 1), ?), ?, ?, ?, ?, NOW())";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            ps.setString(2, complaintId.trim());
            ps.setString(3, complaintId.trim());
            ps.setString(4, complaintId.trim());
            ps.setString(5, oldStatus != null ? oldStatus : "Registered");
            ps.setString(6, newStatus != null ? newStatus : "Registered");
            ps.setString(7, updatedBy != null ? updatedBy : "System");
            ps.setString(8, notes != null ? notes : "");
            ps.executeUpdate();
        }
    }

    public List<ComplaintHistory> findByComplaintId(String complaintId) throws SQLException {
        List<ComplaintHistory> list = new ArrayList<>();
        if (complaintId == null) return list;
        String sql = "SELECT * FROM status_updates WHERE complaint_id = ? ORDER BY created_at ASC";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, complaintId.trim());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new ComplaintHistory(
                            rs.getString("id"),
                            rs.getString("complaint_id"),
                            rs.getString("previous_status"),
                            rs.getString("new_status"),
                            rs.getString("updated_by"),
                            rs.getString("created_at"),
                            rs.getString("notes")
                    ));
                }
            }
        }
        return list;
    }
}
