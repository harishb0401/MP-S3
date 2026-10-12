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

/**
 * JDBC Repository for Analytics and Management Reporting.
 * Computes live operational metrics directly from MySQL tables.
 */
public class ReportRepository {

    public Map<String, Object> getOverallSystemMetrics() throws SQLException {
        Map<String, Object> metrics = new HashMap<>();
        String sql = "SELECT " +
                "COUNT(*) AS total, " +
                "SUM(CASE WHEN severity IS NULL OR severity = '' THEN 1 ELSE 0 END) AS unassessed, " +
                "SUM(CASE WHEN status = 'Registered' THEN 1 ELSE 0 END) AS registered, " +
                "SUM(CASE WHEN status = 'Assigned' THEN 1 ELSE 0 END) AS assigned, " +
                "SUM(CASE WHEN status = 'InProgress' THEN 1 ELSE 0 END) AS in_progress, " +
                "SUM(CASE WHEN status = 'Resolved' THEN 1 ELSE 0 END) AS resolved, " +
                "SUM(CASE WHEN severity = 'CRITICAL' THEN 1 ELSE 0 END) AS critical, " +
                "SUM(CASE WHEN severity = 'HIGH' THEN 1 ELSE 0 END) AS high, " +
                "SUM(CASE WHEN severity = 'MEDIUM' THEN 1 ELSE 0 END) AS medium, " +
                "SUM(CASE WHEN severity = 'LOW' THEN 1 ELSE 0 END) AS low " +
                "FROM complaints";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                metrics.put("total", rs.getInt("total"));
                metrics.put("unassessed", rs.getInt("unassessed"));
                metrics.put("registered", rs.getInt("registered"));
                metrics.put("assigned", rs.getInt("assigned"));
                metrics.put("inProgress", rs.getInt("in_progress"));
                metrics.put("resolved", rs.getInt("resolved"));
                metrics.put("critical", rs.getInt("critical"));
                metrics.put("high", rs.getInt("high"));
                metrics.put("medium", rs.getInt("medium"));
                metrics.put("low", rs.getInt("low"));
            }
        }
        return metrics;
    }

    public List<Map<String, Object>> getDepartmentSummaryReport() throws SQLException {
        List<Map<String, Object>> list = new ArrayList<>();
        String sql = "SELECT " +
                "d.id AS dept_id, d.name AS dept_name, d.capacity, " +
                "COUNT(c.id) AS total_complaints, " +
                "SUM(CASE WHEN c.status IN ('Assigned', 'InProgress') THEN 1 ELSE 0 END) AS current_load, " +
                "SUM(CASE WHEN c.status = 'Resolved' THEN 1 ELSE 0 END) AS resolved_count, " +
                "SUM(CASE WHEN c.severity = 'CRITICAL' THEN 1 ELSE 0 END) AS critical_count " +
                "FROM departments d " +
                "LEFT JOIN complaints c ON c.assigned_department_id = d.id OR c.category = d.name " +
                "GROUP BY d.id, d.name, d.capacity " +
                "ORDER BY d.id ASC";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Map<String, Object> map = new HashMap<>();
                map.put("departmentId", rs.getString("dept_id"));
                map.put("departmentName", rs.getString("dept_name"));
                map.put("capacity", rs.getInt("capacity"));
                map.put("currentLoad", rs.getInt("current_load"));
                map.put("totalComplaints", rs.getInt("total_complaints"));
                map.put("resolvedCount", rs.getInt("resolved_count"));
                map.put("criticalCount", rs.getInt("critical_count"));
                list.add(map);
            }
        }
        return list;
    }

    public List<Map<String, Object>> getAreaAndCategoryDistribution() throws SQLException {
        List<Map<String, Object>> list = new ArrayList<>();
        String sql = "SELECT location_area, category, COUNT(*) AS count, " +
                     "SUM(CASE WHEN severity = 'CRITICAL' THEN 1 ELSE 0 END) AS critical_count " +
                     "FROM complaints " +
                     "GROUP BY location_area, category " +
                     "ORDER BY location_area, count DESC";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Map<String, Object> map = new HashMap<>();
                map.put("locationArea", rs.getString("location_area"));
                map.put("category", rs.getString("category"));
                map.put("count", rs.getInt("count"));
                map.put("criticalCount", rs.getInt("critical_count"));
                list.add(map);
            }
        }
        return list;
    }

    public double getAverageResolutionTimeHours() throws SQLException {
        String sql = "SELECT AVG(TIMESTAMPDIFF(MINUTE, created_at, updated_at)) / 60.0 AS avg_hours " +
                     "FROM complaints WHERE status = 'Resolved'";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return rs.getDouble("avg_hours");
            }
        }
        return 0.0;
    }
}
