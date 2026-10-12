package repository;

import model.Admin;
import model.Citizen;
import model.Department;
import model.DepartmentStaff;
import model.Manager;
import model.User;
import service.DatabaseConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * JDBC Repository for Users table.
 * Handles polymorphic mapping for Citizen, DepartmentStaff (Worker), Manager, and Admin.
 */
public class UserRepository {

    public static String resolveDepartmentId(String nameOrId) {
        if (nameOrId == null || nameOrId.trim().isEmpty()) return null;
        String s = nameOrId.trim().toUpperCase();
        if ("DEPT-WATER".equals(s) || s.contains("WATER")) return "DEPT-WATER";
        if ("DEPT-ROADS".equals(s) || s.contains("ROAD")) return "DEPT-ROADS";
        if ("DEPT-WASTE".equals(s) || s.contains("WASTE") || s.contains("SANIT")) return "DEPT-WASTE";
        if ("DEPT-LIGHT".equals(s) || s.contains("LIGHT") || s.contains("ELECT")) return "DEPT-LIGHT";
        if ("DEPT-GEN".equals(s) || s.contains("ADMIN") || s.contains("GEN")) return "DEPT-GEN";
        return null;
    }

    public void save(User user) throws SQLException {
        if (user == null) return;
        String sql = "INSERT INTO users (id, full_name, email, mobile, password_hash, role, department_id, active, created_at) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                     "ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), mobile = VALUES(mobile), " +
                     "password_hash = VALUES(password_hash), role = VALUES(role), department_id = VALUES(department_id), " +
                     "active = VALUES(active)";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, user.getId());
            ps.setString(2, user.getDisplayName());
            ps.setString(3, user.getEmail().toLowerCase());
            ps.setString(4, user.getMobile() != null && !user.getMobile().trim().isEmpty() ? user.getMobile().trim() : null);
            ps.setString(5, user.getPasswordHash());

            // Map role strictly to ENUM('CITIZEN', 'WORKER', 'MANAGER', 'ADMIN')
            String rawRole = user.getRole() != null ? user.getRole().toUpperCase().trim() : "CITIZEN";
            if ("STAFF".equals(rawRole)) rawRole = "WORKER";
            if (!rawRole.equals("WORKER") && !rawRole.equals("MANAGER") && !rawRole.equals("ADMIN")) {
                rawRole = "CITIZEN";
            }
            ps.setString(6, rawRole);

            // Department Foreign Key: Must be a valid department ID or NULL (citizens have NULL)
            String deptId = null;
            if (user instanceof DepartmentStaff) {
                DepartmentStaff staff = (DepartmentStaff) user;
                deptId = staff.getDepartmentId();
                if (deptId == null || deptId.trim().isEmpty()) {
                    deptId = resolveDepartmentId(staff.getDepartment());
                }
            } else if ("WORKER".equals(rawRole)) {
                deptId = resolveDepartmentId(user.getDepartment());
            } else if ("ADMIN".equals(rawRole) || "MANAGER".equals(rawRole)) {
                deptId = "DEPT-GEN";
            }

            if (deptId != null && !deptId.trim().isEmpty()) {
                ps.setString(7, deptId.trim());
            } else {
                ps.setNull(7, java.sql.Types.VARCHAR);
            }

            ps.setBoolean(8, user.isActive());

            String now = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            String createdAt = user.getCreatedAt();
            if (createdAt == null || createdAt.trim().isEmpty() || createdAt.contains("T")) {
                createdAt = now;
            }
            ps.setString(9, createdAt);
            ps.executeUpdate();
        }
    }

    public User findById(String id) throws SQLException {
        if (id == null || id.trim().isEmpty()) return null;
        String sql = "SELECT * FROM users WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapRowToUser(rs);
            }
        }
        return null;
    }

    public User findByEmail(String email) throws SQLException {
        if (email == null || email.trim().isEmpty()) return null;
        String sql = "SELECT * FROM users WHERE LOWER(email) = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, email.trim().toLowerCase());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapRowToUser(rs);
            }
        }
        return null;
    }

    public User findByUsernameOrEmail(String identifier) throws SQLException {
        if (identifier == null || identifier.trim().isEmpty()) return null;
        String clean = identifier.trim().toLowerCase();
        String sql = "SELECT * FROM users WHERE LOWER(email) = ? OR id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, clean);
            ps.setString(2, identifier.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapRowToUser(rs);
            }
        }
        return null;
    }

    public List<User> findAll() throws SQLException {
        List<User> list = new ArrayList<>();
        String sql = "SELECT * FROM users ORDER BY created_at ASC";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapRowToUser(rs));
            }
        }
        return list;
    }

    public List<User> findActiveWorkersByDepartment(String departmentId) throws SQLException {
        List<User> list = new ArrayList<>();
        String sql = "SELECT * FROM users WHERE role = 'WORKER' AND active = TRUE AND (department_id = ? OR department_id IN (SELECT id FROM departments WHERE name = ?))";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, departmentId);
            ps.setString(2, departmentId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRowToUser(rs));
                }
            }
        }
        return list;
    }

    public boolean deactivateUser(String userId) throws SQLException {
        String sql = "UPDATE users SET active = FALSE WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, userId);
            return ps.executeUpdate() > 0;
        }
    }

    private User mapRowToUser(ResultSet rs) throws SQLException {
        String id = rs.getString("id");
        String name = rs.getString("full_name");
        String email = rs.getString("email");
        String mobile = rs.getString("mobile");
        String passHash = rs.getString("password_hash");
        String role = rs.getString("role");
        String deptId = rs.getString("department_id");
        boolean active = rs.getBoolean("active");
        String createdAt = rs.getString("created_at");

        User u;
        if ("ADMIN".equalsIgnoreCase(role)) {
            u = new Admin(id, email, email, mobile, passHash, name, createdAt);
        } else if ("MANAGER".equalsIgnoreCase(role)) {
            u = new Manager(id, email, email, mobile, passHash, name, createdAt);
        } else if ("WORKER".equalsIgnoreCase(role) || "STAFF".equalsIgnoreCase(role)) {
            Department dept = new Department(deptId != null ? deptId : "DEPT-GEN", deptId != null ? deptId : "General", 10, 0);
            u = new DepartmentStaff(id, email, email, mobile, passHash, "WORKER", name, deptId, dept, createdAt);
        } else {
            u = new Citizen(id, name, email, mobile, passHash, createdAt);
        }
        u.setActive(active);
        return u;
    }
}
