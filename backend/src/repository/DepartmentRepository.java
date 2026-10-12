package repository;

import model.Department;
import service.DatabaseConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * JDBC Repository for Departments table and capacity metrics.
 */
public class DepartmentRepository {

    public void save(Department dept) throws SQLException {
        if (dept == null) return;
        String sql = "INSERT INTO departments (id, name, description, capacity, active) " +
                     "VALUES (?, ?, ?, ?, ?) " +
                     "ON DUPLICATE KEY UPDATE name = VALUES(name), description = VALUES(description), " +
                     "capacity = VALUES(capacity), active = VALUES(active)";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, dept.getDepartmentId());
            ps.setString(2, dept.getDepartmentName());
            ps.setString(3, dept.getDepartmentName() + " Operations");
            ps.setInt(4, dept.getCapacity());
            ps.setBoolean(5, true);
            ps.executeUpdate();
        }
    }

    public Department findById(String id) throws SQLException {
        if (id == null || id.trim().isEmpty()) return null;
        String sql = "SELECT * FROM departments WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapRowToDepartment(rs);
            }
        }
        return null;
    }

    public Department findByName(String name) throws SQLException {
        if (name == null || name.trim().isEmpty()) return null;
        String sql = "SELECT * FROM departments WHERE LOWER(name) = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name.trim().toLowerCase());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapRowToDepartment(rs);
            }
        }
        return null;
    }

    public Department findByAnyIdentifier(String identifier) throws SQLException {
        if (identifier == null || identifier.trim().isEmpty()) return null;
        String clean = identifier.trim().toLowerCase();
        String sql = "SELECT * FROM departments WHERE LOWER(id) = ? OR LOWER(name) = ? " +
                     "OR LOWER(name) LIKE ? OR ? LIKE CONCAT('%', LOWER(name), '%')";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, clean);
            ps.setString(2, clean);
            ps.setString(3, "%" + clean + "%");
            ps.setString(4, clean);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapRowToDepartment(rs);
            }
        }
        return null;
    }

    public List<Department> findAll() throws SQLException {
        List<Department> list = new ArrayList<>();
        String sql = "SELECT * FROM departments ORDER BY id ASC";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapRowToDepartment(rs));
            }
        }
        return list;
    }

    public boolean updateCapacity(String idOrName, int newCapacity) throws SQLException {
        String sql = "UPDATE departments SET capacity = ? WHERE id = ? OR name = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, newCapacity);
            ps.setString(2, idOrName);
            ps.setString(3, idOrName);
            return ps.executeUpdate() > 0;
        }
    }

    public int getActiveLoad(String departmentId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM complaints WHERE (assigned_department_id = ? OR assigned_department_id IN (SELECT id FROM departments WHERE name = ?)) " +
                     "AND status IN ('Assigned', 'InProgress') AND resolution_notes NOT LIKE '%Queued%'";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, departmentId);
            ps.setString(2, departmentId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        }
        return 0;
    }

    private Department mapRowToDepartment(ResultSet rs) throws SQLException {
        String id = rs.getString("id");
        String name = rs.getString("name");
        int capacity = rs.getInt("capacity");
        return new Department(id, name, capacity, 0);
    }
}
