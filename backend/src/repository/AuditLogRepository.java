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
 * JDBC Repository for Audit Logs table.
 * Records administrative actions, severity assessments, assignments, and capacity modifications.
 */
public class AuditLogRepository {

    public void log(String actorUserId, String action, String entityType, String entityId, String details) throws SQLException {
        String id = "AUD-" + UUID.randomUUID().toString().substring(0, 8);
        String sql = "INSERT INTO audit_logs (id, actor_user_id, action, entity_type, entity_id, details, created_at) " +
                     "VALUES (?, ?, ?, ?, ?, ?, NOW())";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            ps.setString(2, actorUserId);
            ps.setString(3, action);
            ps.setString(4, entityType);
            ps.setString(5, entityId);
            ps.setString(6, details);
            ps.executeUpdate();
        }
    }

    public List<Map<String, String>> findRecent(int limit) throws SQLException {
        List<Map<String, String>> list = new ArrayList<>();
        String sql = "SELECT * FROM audit_logs ORDER BY created_at DESC LIMIT ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, limit > 0 ? limit : 50);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, String> row = new HashMap<>();
                    row.put("id", rs.getString("id"));
                    row.put("actor_user_id", rs.getString("actor_user_id"));
                    row.put("action", rs.getString("action"));
                    row.put("entity_type", rs.getString("entity_type"));
                    row.put("entity_id", rs.getString("entity_id"));
                    row.put("details", rs.getString("details"));
                    row.put("created_at", rs.getString("created_at"));
                    list.add(row);
                }
            }
        }
        return list;
    }
}
