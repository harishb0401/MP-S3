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
 * JDBC Repository for Assignments table.
 * Preserves historical trail of staff and department assignments.
 */
public class AssignmentRepository {

    public void recordAssignment(String complaintId, String departmentId, String workerId,
                                 String assignedBy, String status, String reason) throws SQLException {
        String id = "ASN-" + UUID.randomUUID().toString().substring(0, 8);
        String sql = "INSERT INTO assignments (id, complaint_id, department_id, worker_id, assigned_by, assigned_at, assignment_status, reassignment_reason) " +
                     "VALUES (?, COALESCE((SELECT id FROM complaints WHERE id = ? OR complaint_number = ? LIMIT 1), ?), ?, ?, ?, NOW(), ?, ?)";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            ps.setString(2, complaintId.trim());
            ps.setString(3, complaintId.trim());
            ps.setString(4, complaintId.trim());

            String cleanDept = UserRepository.resolveDepartmentId(departmentId);
            if (cleanDept != null) {
                ps.setString(5, cleanDept);
            } else {
                ps.setString(5, "DEPT-GEN");
            }

            String cleanWorker = null;
            if (workerId != null && !workerId.trim().isEmpty() && !workerId.equalsIgnoreCase("Unassigned") && !workerId.startsWith("Queued")) {
                String wUpper = workerId.trim().toUpperCase();
                if (wUpper.startsWith("STF-") || wUpper.startsWith("USR") || wUpper.startsWith("ADM") || wUpper.startsWith("MGR") || wUpper.startsWith("W")) {
                    cleanWorker = workerId.trim();
                } else if (wUpper.contains("SURESH")) {
                    cleanWorker = "STF-W01";
                } else if (wUpper.contains("KARTHIK")) {
                    cleanWorker = "STF-R01";
                } else if (wUpper.contains("MEENA")) {
                    cleanWorker = "STF-S01";
                } else if (wUpper.contains("PRIYA")) {
                    cleanWorker = "STF-L01";
                }
            }
            if (cleanWorker != null) {
                ps.setString(6, cleanWorker);
            } else {
                ps.setNull(6, java.sql.Types.VARCHAR);
            }

            ps.setString(7, assignedBy);
            ps.setString(8, status != null ? status : "Active");
            ps.setString(9, reason);
            ps.executeUpdate();
        }
    }

    public List<Map<String, Object>> findByComplaintId(String complaintId) throws SQLException {
        List<Map<String, Object>> list = new ArrayList<>();
        if (complaintId == null) return list;
        String sql = "SELECT * FROM assignments WHERE complaint_id = ? ORDER BY assigned_at DESC";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, complaintId.trim());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> map = new HashMap<>();
                    map.put("id", rs.getString("id"));
                    map.put("complaint_id", rs.getString("complaint_id"));
                    map.put("department_id", rs.getString("department_id"));
                    map.put("worker_id", rs.getString("worker_id"));
                    map.put("assigned_by", rs.getString("assigned_by"));
                    map.put("assigned_at", rs.getString("assigned_at"));
                    map.put("assignment_status", rs.getString("assignment_status"));
                    map.put("reassignment_reason", rs.getString("reassignment_reason"));
                    list.add(map);
                }
            }
        }
        return list;
    }
}
