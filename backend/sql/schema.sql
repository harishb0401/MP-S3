-- ==============================================================================
-- GOVERNMENT GRIEVANCE MANAGEMENT AND PRIORITY RESOLUTION SYSTEM
-- DATABASE SCHEMA DESIGN & PRODUCTION MYSQL QUERIES
-- Municipal Case Study: Makkal Nagar Municipal Office
-- ==============================================================================

CREATE DATABASE IF NOT EXISTS municipal_grievance_db
CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE municipal_grievance_db;

-- ------------------------------------------------------------------------------
-- 1. CITIZEN TABLE
-- ------------------------------------------------------------------------------
DROP TABLE IF EXISTS notification;
DROP TABLE IF EXISTS complaint_history;
DROP TABLE IF EXISTS duplicate_mapping;
DROP TABLE IF EXISTS complaint;
DROP TABLE IF EXISTS department_staff;
DROP TABLE IF EXISTS department;
DROP TABLE IF EXISTS admin;
DROP TABLE IF EXISTS citizen;

CREATE TABLE citizen (
    citizen_id VARCHAR(50) PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    mobile VARCHAR(20) NOT NULL,
    username VARCHAR(100) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;

-- ------------------------------------------------------------------------------
-- 2. ADMIN TABLE
-- ------------------------------------------------------------------------------
CREATE TABLE admin (
    admin_id VARCHAR(50) PRIMARY KEY,
    username VARCHAR(100) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    full_name VARCHAR(100) NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;

-- ------------------------------------------------------------------------------
-- 3. DEPARTMENT TABLE
-- ------------------------------------------------------------------------------
CREATE TABLE department (
    department_id VARCHAR(50) PRIMARY KEY,
    department_name VARCHAR(100) NOT NULL UNIQUE,
    capacity INT NOT NULL DEFAULT 10,
    current_load INT NOT NULL DEFAULT 0,
    CONSTRAINT chk_capacity CHECK (capacity > 0),
    CONSTRAINT chk_load CHECK (current_load >= 0)
) ENGINE=InnoDB;

-- ------------------------------------------------------------------------------
-- 4. DEPARTMENT STAFF TABLE
-- ------------------------------------------------------------------------------
CREATE TABLE department_staff (
    staff_id VARCHAR(50) PRIMARY KEY,
    username VARCHAR(100) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    staff_name VARCHAR(100) NOT NULL,
    department_id VARCHAR(50) NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_staff_dept FOREIGN KEY (department_id) 
        REFERENCES department(department_id) ON UPDATE CASCADE ON DELETE RESTRICT
) ENGINE=InnoDB;

-- ------------------------------------------------------------------------------
-- 5. COMPLAINT TABLE
-- ------------------------------------------------------------------------------
CREATE TABLE complaint (
    complaint_id VARCHAR(50) PRIMARY KEY,
    citizen_id VARCHAR(50) NOT NULL,
    category ENUM('Water Supply', 'Road Damage', 'Waste Management', 'Streetlight') NOT NULL,
    location VARCHAR(255) NOT NULL,
    description TEXT NOT NULL,
    
    -- IMPACT FIELDS:
    -- citizen_reported_impact is the original value entered by the citizen.
    -- Protected by Trigger against any future overwrite or mutation!
    citizen_reported_impact INT NOT NULL DEFAULT 1,
    admin_verified_impact INT NULL,
    verification_reason TEXT NULL,
    is_verified_locked BOOLEAN DEFAULT FALSE,
    
    severity ENUM('HIGH', 'MEDIUM', 'LOW') DEFAULT 'LOW',
    priority VARCHAR(20) DEFAULT 'MEDIUM',
    status ENUM('Registered', 'Under Review', 'Assigned', 'In Progress', 'Resolved', 'Closed') DEFAULT 'Registered',
    department_id VARCHAR(50) NULL,
    assigned_staff VARCHAR(100) DEFAULT 'Unassigned',
    resolution_notes TEXT NULL,
    
    -- Duplicate detection links
    is_duplicate BOOLEAN DEFAULT FALSE,
    duplicate_of_id VARCHAR(50) NULL,
    
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    resolved_at DATETIME NULL,
    
    CONSTRAINT fk_complaint_citizen FOREIGN KEY (citizen_id) 
        REFERENCES citizen(citizen_id) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_complaint_dept FOREIGN KEY (department_id) 
        REFERENCES department(department_id) ON UPDATE CASCADE ON DELETE SET NULL,
    CONSTRAINT fk_complaint_dup FOREIGN KEY (duplicate_of_id) 
        REFERENCES complaint(complaint_id) ON UPDATE CASCADE ON DELETE SET NULL,
    CONSTRAINT chk_citizen_impact CHECK (citizen_reported_impact > 0)
) ENGINE=InnoDB;

-- INDEXES FOR HIGH-THROUGHPUT SEARCH & PRIORITY RETRIEVAL
CREATE INDEX idx_complaint_cat_loc ON complaint (category, location);
CREATE INDEX idx_complaint_status_dept ON complaint (status, department_id);
CREATE INDEX idx_complaint_priority_order ON complaint (severity, admin_verified_impact, created_at);

-- ------------------------------------------------------------------------------
-- 6. IMMUTABILITY TRIGGER FOR CITIZEN REPORTED IMPACT
-- Enforces that citizen_reported_impact can NEVER be modified once inserted!
-- ------------------------------------------------------------------------------
DELIMITER $$
CREATE TRIGGER trg_protect_citizen_impact
BEFORE UPDATE ON complaint
FOR EACH ROW
BEGIN
    IF OLD.citizen_reported_impact <> NEW.citizen_reported_impact THEN
        SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'POLICY VIOLATION: Citizen Reported Impact is immutable and cannot be modified or replaced.';
    END IF;
    
    -- Prevent altering verified impact after it has been locked
    IF OLD.is_verified_locked = TRUE AND (OLD.admin_verified_impact <> NEW.admin_verified_impact OR OLD.verification_reason <> NEW.verification_reason) THEN
        SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'POLICY VIOLATION: Verification data is locked. Create a new audit record instead of modifying locked data.';
    END IF;
END$$
DELIMITER ;

-- ------------------------------------------------------------------------------
-- 7. COMPLAINT HISTORY (AUDIT TRAIL) TABLE
-- ------------------------------------------------------------------------------
CREATE TABLE complaint_history (
    history_id INT AUTO_INCREMENT PRIMARY KEY,
    complaint_id VARCHAR(50) NOT NULL,
    old_status VARCHAR(50) NOT NULL,
    new_status VARCHAR(50) NOT NULL,
    updated_by VARCHAR(100) NOT NULL,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    remarks TEXT NOT NULL,
    CONSTRAINT fk_history_complaint FOREIGN KEY (complaint_id) 
        REFERENCES complaint(complaint_id) ON UPDATE CASCADE ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE INDEX idx_history_complaint ON complaint_history (complaint_id, update_time);

-- ------------------------------------------------------------------------------
-- 8. NOTIFICATION TABLE
-- ------------------------------------------------------------------------------
CREATE TABLE notification (
    notification_id INT AUTO_INCREMENT PRIMARY KEY,
    citizen_id VARCHAR(50) NOT NULL,
    complaint_id VARCHAR(50) NOT NULL,
    original_impact INT NOT NULL,
    verified_impact INT NOT NULL,
    verification_reason TEXT NOT NULL,
    title VARCHAR(255) NOT NULL,
    message TEXT NOT NULL,
    is_read BOOLEAN DEFAULT FALSE,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_notification_citizen FOREIGN KEY (citizen_id) 
        REFERENCES citizen(citizen_id) ON UPDATE CASCADE ON DELETE CASCADE,
    CONSTRAINT fk_notification_complaint FOREIGN KEY (complaint_id) 
        REFERENCES complaint(complaint_id) ON UPDATE CASCADE ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE INDEX idx_notification_citizen ON notification (citizen_id, is_read);

-- ------------------------------------------------------------------------------
-- 9. DUPLICATE MAPPING TABLE
-- ------------------------------------------------------------------------------
CREATE TABLE duplicate_mapping (
    mapping_id INT AUTO_INCREMENT PRIMARY KEY,
    primary_complaint_id VARCHAR(50) NOT NULL,
    duplicate_complaint_id VARCHAR(50) NOT NULL,
    decision ENUM('MERGED', 'SEPARATE') NOT NULL,
    decision_reason VARCHAR(255) NULL,
    decided_by VARCHAR(100) NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_dup_primary FOREIGN KEY (primary_complaint_id) 
        REFERENCES complaint(complaint_id) ON UPDATE CASCADE ON DELETE CASCADE,
    CONSTRAINT fk_dup_child FOREIGN KEY (duplicate_complaint_id) 
        REFERENCES complaint(complaint_id) ON UPDATE CASCADE ON DELETE CASCADE
) ENGINE=InnoDB;

-- ==============================================================================
-- INITIAL SEED DATA
-- ==============================================================================

-- 4 Municipal Departments with Initial Capacities
INSERT INTO department (department_id, department_name, capacity, current_load) VALUES
('DEPT-WATER', 'Water Department', 10, 0),
('DEPT-ROADS', 'Roads Department', 10, 0),
('DEPT-WASTE', 'Waste Management Department', 10, 0),
('DEPT-LIGHT', 'Streetlight Department', 10, 0)
ON DUPLICATE KEY UPDATE capacity=VALUES(capacity);

-- Initial Administrator Account (password: admin123 -> SHA-256)
INSERT INTO admin (admin_id, username, password, full_name) VALUES
('ADM1001', 'admin@makkal.gov.in', '240be518fabd2724ddb6f04eeb1da5967448d7e831c08c8fa822809f74c720a9', 'Chief Municipal Officer')
ON DUPLICATE KEY UPDATE full_name=VALUES(full_name);

-- Initial Department Staff Accounts (password: admin123)
INSERT INTO department_staff (staff_id, username, password, staff_name, department_id) VALUES
('STF-W01', 'water_staff@makkal.gov.in', '240be518fabd2724ddb6f04eeb1da5967448d7e831c08c8fa822809f74c720a9', 'Officer Suresh (Water)', 'DEPT-WATER'),
('STF-R01', 'roads_staff@makkal.gov.in', '240be518fabd2724ddb6f04eeb1da5967448d7e831c08c8fa822809f74c720a9', 'Officer Karthik (Roads)', 'DEPT-ROADS'),
('STF-M01', 'waste_staff@makkal.gov.in', '240be518fabd2724ddb6f04eeb1da5967448d7e831c08c8fa822809f74c720a9', 'Officer Priya (Waste)', 'DEPT-WASTE'),
('STF-L01', 'light_staff@makkal.gov.in', '240be518fabd2724ddb6f04eeb1da5967448d7e831c08c8fa822809f74c720a9', 'Officer Ramesh (Streetlight)', 'DEPT-LIGHT')
ON DUPLICATE KEY UPDATE staff_name=VALUES(staff_name);


-- ==============================================================================
-- PRODUCTION MYSQL QUERIES FOR CORE BUSINESS OPERATIONS
-- ==============================================================================

-- ------------------------------------------------------------------------------
-- QUERY 1: PRIORITY QUEUE RESOLUTION ORDER
-- Orders active complaints by:
-- 1. Severity: HIGH (1) -> MEDIUM (2) -> LOW (3)
-- 2. Impact: COALESCE(admin_verified_impact, citizen_reported_impact) DESC
-- 3. Arrival Order: created_at ASC (FIFO)
-- ------------------------------------------------------------------------------
-- EXPLAIN:
SELECT 
    c.complaint_id,
    c.category,
    c.location,
    c.severity,
    COALESCE(c.admin_verified_impact, c.citizen_reported_impact) AS effective_impact,
    c.citizen_reported_impact AS original_impact,
    c.admin_verified_impact AS verified_impact,
    c.status,
    c.department_id,
    d.department_name,
    c.created_at
FROM complaint c
LEFT JOIN department d ON c.department_id = d.department_id
WHERE c.status NOT IN ('Resolved', 'Closed')
  AND c.is_duplicate = FALSE
ORDER BY 
    CASE c.severity
        WHEN 'HIGH' THEN 1
        WHEN 'MEDIUM' THEN 2
        WHEN 'LOW' THEN 3
        ELSE 4
    END ASC,
    effective_impact DESC,
    c.created_at ASC;

-- ------------------------------------------------------------------------------
-- QUERY 2: DUPLICATE COMPLAINT DETECTION
-- Detects active complaints that share the exact same category and location.
-- ------------------------------------------------------------------------------
SELECT 
    c1.complaint_id AS duplicate_candidate_id,
    c1.citizen_id AS duplicate_citizen_id,
    c2.complaint_id AS existing_primary_id,
    c1.category,
    c1.location,
    c1.created_at AS candidate_created_at,
    c2.created_at AS primary_created_at
FROM complaint c1
JOIN complaint c2 ON LOWER(TRIM(c1.category)) = LOWER(TRIM(c2.category))
                AND LOWER(TRIM(c1.location)) = LOWER(TRIM(c2.location))
                AND c1.complaint_id <> c2.complaint_id
                AND c1.created_at > c2.created_at
WHERE c1.status NOT IN ('Resolved', 'Closed')
  AND c2.status NOT IN ('Resolved', 'Closed')
  AND c1.is_duplicate = FALSE;

-- ------------------------------------------------------------------------------
-- QUERY 3: DEPARTMENT CAPACITY MANAGEMENT & WORKLOAD UTILIZATION REPORT
-- Shows capacity, current load, and waiting complaints per department.
-- ------------------------------------------------------------------------------
SELECT 
    d.department_id,
    d.department_name,
    d.capacity,
    d.current_load,
    (d.capacity - d.current_load) AS available_capacity,
    ROUND((d.current_load / d.capacity) * 100, 2) AS capacity_utilization_pct,
    COUNT(CASE WHEN c.status = 'Assigned' AND d.current_load >= d.capacity THEN 1 END) AS waiting_in_queue_count
FROM department d
LEFT JOIN complaint c ON d.department_id = c.department_id
GROUP BY d.department_id, d.department_name, d.capacity, d.current_load;

-- ------------------------------------------------------------------------------
-- QUERY 4: COMPLETE COMPLAINT TIMELINE & AUDIT HISTORY
-- Retrieves complete sequential history of all actions performed on a case.
-- ------------------------------------------------------------------------------
SELECT 
    h.history_id,
    h.complaint_id,
    h.old_status,
    h.new_status,
    h.updated_by,
    h.update_time,
    h.remarks
FROM complaint_history h
WHERE h.complaint_id = 'GRV-2026-0001'
ORDER BY h.update_time ASC, h.history_id ASC;

-- ------------------------------------------------------------------------------
-- QUERY 5: CITIZEN NOTIFICATION FEED
-- Retrieves unread verification notifications for a specific citizen.
-- ------------------------------------------------------------------------------
SELECT 
    n.notification_id,
    n.complaint_id,
    n.original_impact,
    n.verified_impact,
    n.verification_reason,
    n.message,
    n.created_at,
    n.is_read
FROM notification n
WHERE n.citizen_id = 'USR848B560C'
ORDER BY n.created_at DESC;

-- ------------------------------------------------------------------------------
-- QUERY 6: ATOMIC ADMIN VERIFICATION TRANSACTION
-- Sets verified impact, mandatory reason, severity, locks verified data,
-- appends audit history, and generates citizen notification.
-- ------------------------------------------------------------------------------
/*
START TRANSACTION;

-- 1. Lock and update complaint verification data
UPDATE complaint
SET admin_verified_impact = 300,
    verification_reason = 'Field inspection confirmed only 3 streets are affected.',
    severity = 'HIGH',
    status = 'Under Review',
    is_verified_locked = TRUE,
    updated_at = NOW()
WHERE complaint_id = 'GRV-2026-0001'
  AND is_verified_locked = FALSE;

-- 2. Insert into complaint audit history
INSERT INTO complaint_history (complaint_id, old_status, new_status, updated_by, remarks)
VALUES (
    'GRV-2026-0001',
    'Registered',
    'Under Review',
    'admin@makkal.gov.in',
    'Admin verified impact: 300 (Original: 500). Reason: Field inspection confirmed only 3 streets are affected.'
);

-- 3. Generate Citizen Notification
INSERT INTO notification (citizen_id, complaint_id, original_impact, verified_impact, verification_reason, title, message)
VALUES (
    'USR848B560C',
    'GRV-2026-0001',
    500,
    300,
    'Field inspection confirmed only 3 streets are affected.',
    'Complaint GRV-2026-0001 Verified',
    'Complaint GRV-2026-0001 has been reviewed. Citizen Reported Impact: 500 | Admin Verified Impact: 300 | Reason: Field inspection confirmed only 3 streets are affected. Your original submission remains preserved in the system.'
);

COMMIT;
*/
