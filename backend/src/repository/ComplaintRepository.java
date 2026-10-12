package repository;

import model.Complaint;
import model.GrievanceFactory;
import service.DatabaseConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

/**
 * JDBC Repository for Complaints table.
 * Manages persistent storage, updates, and polymorphic retrieval of grievances.
 */
public class ComplaintRepository {

    public synchronized void save(Complaint c) throws SQLException {
        if (c == null) return;
        String sql = "INSERT INTO complaints (" +
                "id, complaint_number, citizen_id, title, description, category, location_area, " +
                "latitude, longitude, location_accuracy, affected_people_count, severity, status, " +
                "arrival_sequence, assigned_department_id, assigned_worker_id, resolution_notes, " +
                "image_url, assessed_by, assessed_at, assessment_reason, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE " +
                "title = VALUES(title), description = VALUES(description), location_area = VALUES(location_area), " +
                "latitude = VALUES(latitude), longitude = VALUES(longitude), location_accuracy = VALUES(location_accuracy), " +
                "affected_people_count = VALUES(affected_people_count), severity = VALUES(severity), status = VALUES(status), " +
                "assigned_department_id = VALUES(assigned_department_id), assigned_worker_id = VALUES(assigned_worker_id), " +
                "resolution_notes = VALUES(resolution_notes), image_url = VALUES(image_url), " +
                "assessed_by = VALUES(assessed_by), assessed_at = VALUES(assessed_at), " +
                "assessment_reason = VALUES(assessment_reason), updated_at = VALUES(updated_at)";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            setComplaintParams(ps, c);
            ps.executeUpdate();
        }
    }

    public synchronized void update(Complaint c) throws SQLException {
        save(c);
    }

    public Complaint findById(String id) throws SQLException {
        if (id == null || id.trim().isEmpty()) return null;
        String sql = "SELECT c.*, u.full_name AS citizen_name, u.mobile AS citizen_mobile, d.name AS dept_name " +
                     "FROM complaints c " +
                     "LEFT JOIN users u ON c.citizen_id = u.id " +
                     "LEFT JOIN departments d ON c.assigned_department_id = d.id " +
                     "WHERE c.id = ? OR c.complaint_number = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id.trim());
            ps.setString(2, id.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapRowToComplaint(rs);
            }
        }
        return null;
    }

    public Complaint findByComplaintNumber(String num) throws SQLException {
        return findById(num);
    }

    public List<Complaint> findAll() throws SQLException {
        List<Complaint> list = new ArrayList<>();
        String sql = "SELECT c.*, u.full_name AS citizen_name, u.mobile AS citizen_mobile, d.name AS dept_name " +
                     "FROM complaints c " +
                     "LEFT JOIN users u ON c.citizen_id = u.id " +
                     "LEFT JOIN departments d ON c.assigned_department_id = d.id " +
                     "ORDER BY c.arrival_sequence ASC";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapRowToComplaint(rs));
            }
        }
        return list;
    }

    public List<Complaint> findByCitizenId(String citizenId) throws SQLException {
        List<Complaint> list = new ArrayList<>();
        if (citizenId == null) return list;
        String sql = "SELECT c.*, u.full_name AS citizen_name, u.mobile AS citizen_mobile, d.name AS dept_name " +
                     "FROM complaints c " +
                     "LEFT JOIN users u ON c.citizen_id = u.id " +
                     "LEFT JOIN departments d ON c.assigned_department_id = d.id " +
                     "WHERE c.citizen_id = ? " +
                     "ORDER BY c.arrival_sequence ASC";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, citizenId.trim());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRowToComplaint(rs));
                }
            }
        }
        return list;
    }

    public List<Complaint> findByDepartmentId(String deptIdOrName) throws SQLException {
        List<Complaint> list = new ArrayList<>();
        if (deptIdOrName == null) return list;
        String sql = "SELECT c.*, u.full_name AS citizen_name, u.mobile AS citizen_mobile, d.name AS dept_name " +
                     "FROM complaints c " +
                     "LEFT JOIN users u ON c.citizen_id = u.id " +
                     "LEFT JOIN departments d ON c.assigned_department_id = d.id " +
                     "WHERE c.assigned_department_id = ? OR d.name = ? OR c.category = ? " +
                     "ORDER BY c.arrival_sequence ASC";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, deptIdOrName.trim());
            ps.setString(2, deptIdOrName.trim());
            ps.setString(3, deptIdOrName.trim());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRowToComplaint(rs));
                }
            }
        }
        return list;
    }

    public List<Complaint> findByAssignedWorkerId(String workerId) throws SQLException {
        List<Complaint> list = new ArrayList<>();
        if (workerId == null) return list;
        String sql = "SELECT c.*, u.full_name AS citizen_name, u.mobile AS citizen_mobile, d.name AS dept_name " +
                     "FROM complaints c " +
                     "LEFT JOIN users u ON c.citizen_id = u.id " +
                     "LEFT JOIN departments d ON c.assigned_department_id = d.id " +
                     "WHERE c.assigned_worker_id = ? OR c.resolution_notes LIKE ? " +
                     "ORDER BY c.arrival_sequence ASC";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, workerId.trim());
            ps.setString(2, "%" + workerId.trim() + "%");
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRowToComplaint(rs));
                }
            }
        }
        return list;
    }

    public long getNextArrivalSequence() throws SQLException {
        String sql = "SELECT COALESCE(MAX(arrival_sequence), 0) + 1 FROM complaints";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) return rs.getLong(1);
        }
        return 1L;
    }

    private void setComplaintParams(PreparedStatement ps, Complaint c) throws SQLException {
        ps.setString(1, c.getId());
        ps.setString(2, c.getComplaintId());
        ps.setString(3, c.getCitizenId());
        ps.setString(4, c.getTitle());
        ps.setString(5, c.getDescription());
        ps.setString(6, c.getCategory());
        ps.setString(7, c.getLocation());

        if (c.getLatitude() != null) ps.setDouble(8, c.getLatitude());
        else ps.setNull(8, Types.DOUBLE);

        if (c.getLongitude() != null) ps.setDouble(9, c.getLongitude());
        else ps.setNull(9, Types.DOUBLE);

        if (c.getLocationAccuracyMeters() != null) ps.setDouble(10, c.getLocationAccuracyMeters());
        else ps.setNull(10, Types.DOUBLE);

        ps.setInt(11, c.getCitizenReportedImpact() > 0 ? c.getCitizenReportedImpact() : 1);

        if (c.getSeverity() != null && !c.getSeverity().trim().isEmpty() &&
            !"UNASSESSED".equalsIgnoreCase(c.getSeverity().trim())) {
            ps.setString(12, c.getSeverity().trim().toUpperCase());
        } else {
            ps.setNull(12, Types.VARCHAR);
        }

        ps.setString(13, c.getStatus());
        ps.setLong(14, c.getArrivalSequence());

        // 15. assigned_department_id (Foreign Key to departments.id)
        String deptId = UserRepository.resolveDepartmentId(c.getDepartmentId() != null ? c.getDepartmentId() : c.getDepartment());
        if (deptId != null && !deptId.trim().isEmpty()) {
            ps.setString(15, deptId.trim());
        } else {
            ps.setNull(15, Types.VARCHAR);
        }

        // 16. assigned_worker_id (Foreign Key to users.id)
        String workerRaw = c.getAssignedStaff();
        String workerId = null;
        if (workerRaw != null && !workerRaw.trim().isEmpty() && !workerRaw.equalsIgnoreCase("Unassigned") && !workerRaw.startsWith("Queued")) {
            String wUpper = workerRaw.trim().toUpperCase();
            if (wUpper.startsWith("STF-") || wUpper.startsWith("USR") || wUpper.startsWith("ADM") || wUpper.startsWith("MGR") || wUpper.startsWith("W")) {
                workerId = workerRaw.trim();
            } else if (wUpper.contains("SURESH")) {
                workerId = "STF-W01";
            } else if (wUpper.contains("KARTHIK")) {
                workerId = "STF-R01";
            } else if (wUpper.contains("MEENA")) {
                workerId = "STF-S01";
            } else if (wUpper.contains("PRIYA")) {
                workerId = "STF-L01";
            }
        }
        if (workerId != null) {
            ps.setString(16, workerId);
        } else {
            ps.setNull(16, Types.VARCHAR);
        }

        ps.setString(17, c.getResolutionNotes());
        ps.setString(18, c.getImageUrl());
        ps.setString(19, c.getAssessedBy());

        // 20. assessed_at (DATETIME NULL)
        String assessedAt = c.getAssessedAt();
        if (assessedAt != null && !assessedAt.trim().isEmpty() && !assessedAt.equalsIgnoreCase("null")) {
            String clean = assessedAt.replace('T', ' ').trim();
            if (clean.length() > 19) clean = clean.substring(0, 19);
            ps.setString(20, clean);
        } else {
            ps.setNull(20, Types.TIMESTAMP);
        }

        ps.setString(21, c.getAssessmentReason());

        String now = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String createdAt = c.getCreatedAt();
        if (createdAt == null || createdAt.trim().isEmpty() || createdAt.contains("T")) {
            createdAt = now;
        }
        String updatedAt = c.getUpdatedAt();
        if (updatedAt == null || updatedAt.trim().isEmpty() || updatedAt.contains("T")) {
            updatedAt = now;
        }
        ps.setString(22, createdAt);
        ps.setString(23, updatedAt);
    }

    private Complaint mapRowToComplaint(ResultSet rs) throws SQLException {
        String id = rs.getString("id");
        String complaintNumber = rs.getString("complaint_number");
        String citizenId = rs.getString("citizen_id");
        String citizenName = rs.getString("citizen_name");
        String citizenMobile = rs.getString("citizen_mobile");
        String category = rs.getString("category");
        String description = rs.getString("description");
        String locationArea = rs.getString("location_area");
        String deptName = rs.getString("dept_name");
        String deptId = rs.getString("assigned_department_id");
        int impact = rs.getInt("affected_people_count");
        String severity = rs.getString("severity");
        String status = rs.getString("status");

        Complaint c = GrievanceFactory.createGrievance(
                id, complaintNumber, citizenId,
                citizenName != null ? citizenName : "Citizen",
                citizenMobile != null ? citizenMobile : "N/A",
                category, description, locationArea,
                deptName != null ? deptName : (deptId != null ? deptId : "General Administration"),
                impact, severity, severity != null ? severity : "UNASSESSED", status
        );

        c.setTitle(rs.getString("title"));
        c.setDepartmentId(deptId);

        double lat = rs.getDouble("latitude");
        if (!rs.wasNull()) c.setLatitude(lat);

        double lng = rs.getDouble("longitude");
        if (!rs.wasNull()) c.setLongitude(lng);

        double acc = rs.getDouble("location_accuracy");
        if (!rs.wasNull()) c.setLocationAccuracyMeters(acc);

        c.setAssignedStaff(rs.getString("assigned_worker_id") != null ? rs.getString("assigned_worker_id") : "Unassigned");
        c.setResolutionNotes(rs.getString("resolution_notes"));
        c.setImageUrl(rs.getString("image_url"));
        c.setAssessedBy(rs.getString("assessed_by"));
        c.setAssessedAt(rs.getString("assessed_at"));
        c.setAssessmentReason(rs.getString("assessment_reason"));
        c.forceSetArrivalSequenceFromPersistence(rs.getLong("arrival_sequence"));
        c.setCreatedAt(rs.getString("created_at"));
        c.setUpdatedAt(rs.getString("updated_at"));

        if (severity != null && !severity.trim().isEmpty()) {
            c.setSeverity(severity);
            c.setPriority(severity);
        } else {
            c.setSeverity(null);
            c.setPriority("UNASSESSED");
        }

        return c;
    }
}
