-- ==============================================================================
-- GOVERNMENT GRIEVANCE MANAGEMENT AND PRIORITY RESOLUTION SYSTEM
-- MYSQL 8.0 PRODUCTION DATABASE INITIALIZATION SCHEMA
-- Database: makkal_nagar_grievance
-- ==============================================================================

CREATE DATABASE IF NOT EXISTS makkal_nagar_grievance
CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE makkal_nagar_grievance;

-- Drop dependent tables in reverse foreign-key order
DROP TABLE IF EXISTS audit_logs;
DROP TABLE IF EXISTS duplicate_links;
DROP TABLE IF EXISTS status_updates;
DROP TABLE IF EXISTS assignments;
DROP TABLE IF EXISTS complaints;
DROP TABLE IF EXISTS users;
DROP TABLE IF EXISTS departments;

-- ------------------------------------------------------------------------------
-- 1. DEPARTMENTS TABLE
-- Four Core Municipal Departments: Water Supply, Roads, Waste Management, Streetlight Maintenance
-- ------------------------------------------------------------------------------
CREATE TABLE departments (
    id VARCHAR(50) PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    description TEXT NULL,
    capacity INT NOT NULL DEFAULT 10,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT chk_dept_capacity CHECK (capacity > 0)
) ENGINE=InnoDB;

-- ------------------------------------------------------------------------------
-- 2. USERS TABLE
-- Roles: CITIZEN, WORKER, MANAGER, ADMIN
-- ------------------------------------------------------------------------------
CREATE TABLE users (
    id VARCHAR(50) PRIMARY KEY,
    full_name VARCHAR(100) NOT NULL,
    email VARCHAR(100) NOT NULL UNIQUE,
    mobile VARCHAR(20) NULL,
    password_hash VARCHAR(255) NOT NULL,
    role ENUM('CITIZEN', 'WORKER', 'MANAGER', 'ADMIN') NOT NULL,
    department_id VARCHAR(50) NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_user_dept FOREIGN KEY (department_id)
        REFERENCES departments(id) ON UPDATE CASCADE ON DELETE SET NULL,
    INDEX idx_user_email (email),
    INDEX idx_user_role (role),
    INDEX idx_user_department (department_id)
) ENGINE=InnoDB;

-- ------------------------------------------------------------------------------
-- 3. COMPLAINTS TABLE
-- Complaints start explicitly unassessed (severity IS NULL until manager/admin assess)
-- ------------------------------------------------------------------------------
CREATE TABLE complaints (
    id VARCHAR(50) PRIMARY KEY,
    complaint_number VARCHAR(50) NOT NULL UNIQUE,
    citizen_id VARCHAR(50) NOT NULL,
    title VARCHAR(255) NULL,
    description TEXT NOT NULL,
    category VARCHAR(100) NOT NULL,
    location_area VARCHAR(100) NOT NULL,
    latitude DECIMAL(10, 8) NULL,
    longitude DECIMAL(11, 8) NULL,
    location_accuracy DECIMAL(8, 2) NULL,
    affected_people_count INT NOT NULL DEFAULT 1,
    severity ENUM('CRITICAL', 'HIGH', 'MEDIUM', 'LOW') NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'Registered',
    arrival_sequence BIGINT NOT NULL UNIQUE,
    assigned_department_id VARCHAR(50) NULL,
    assigned_worker_id VARCHAR(50) NULL,
    resolution_notes TEXT NULL,
    image_url MEDIUMTEXT NULL,
    assessed_by VARCHAR(100) NULL,
    assessed_at DATETIME NULL,
    assessment_reason TEXT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_complaint_citizen FOREIGN KEY (citizen_id)
        REFERENCES users(id) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_complaint_dept FOREIGN KEY (assigned_department_id)
        REFERENCES departments(id) ON UPDATE CASCADE ON DELETE SET NULL,
    CONSTRAINT fk_complaint_worker FOREIGN KEY (assigned_worker_id)
        REFERENCES users(id) ON UPDATE CASCADE ON DELETE SET NULL,
    CONSTRAINT chk_complaint_lat CHECK (latitude IS NULL OR (latitude >= -90.0 AND latitude <= 90.0)),
    CONSTRAINT chk_complaint_lng CHECK (longitude IS NULL OR (longitude >= -180.0 AND longitude <= 180.0)),
    CONSTRAINT chk_complaint_acc CHECK (location_accuracy IS NULL OR location_accuracy >= 0.0),
    CONSTRAINT chk_complaint_impact CHECK (affected_people_count > 0),
    INDEX idx_complaint_number (complaint_number),
    INDEX idx_complaint_citizen (citizen_id),
    INDEX idx_complaint_department (assigned_department_id),
    INDEX idx_complaint_worker (assigned_worker_id),
    INDEX idx_complaint_status (status),
    INDEX idx_complaint_severity (severity),
    INDEX idx_complaint_arrival (arrival_sequence),
    INDEX idx_complaint_created_at (created_at)
) ENGINE=InnoDB;

-- ------------------------------------------------------------------------------
-- 4. ASSIGNMENTS TABLE
-- Keeps full historical trail of worker & department assignments
-- ------------------------------------------------------------------------------
CREATE TABLE assignments (
    id VARCHAR(50) PRIMARY KEY,
    complaint_id VARCHAR(50) NOT NULL,
    department_id VARCHAR(50) NOT NULL,
    worker_id VARCHAR(50) NULL,
    assigned_by VARCHAR(100) NOT NULL,
    assigned_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    assignment_status VARCHAR(50) NOT NULL DEFAULT 'Active',
    reassigned_at DATETIME NULL,
    reassignment_reason TEXT NULL,
    CONSTRAINT fk_assignment_complaint FOREIGN KEY (complaint_id)
        REFERENCES complaints(id) ON UPDATE CASCADE ON DELETE CASCADE,
    CONSTRAINT fk_assignment_department FOREIGN KEY (department_id)
        REFERENCES departments(id) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_assignment_worker FOREIGN KEY (worker_id)
        REFERENCES users(id) ON UPDATE CASCADE ON DELETE SET NULL,
    INDEX idx_assignment_complaint (complaint_id),
    INDEX idx_assignment_dept (department_id),
    INDEX idx_assignment_worker (worker_id),
    INDEX idx_assignment_status (assignment_status)
) ENGINE=InnoDB;

-- ------------------------------------------------------------------------------
-- 5. STATUS_UPDATES TABLE
-- Tracks valid lifecycle transitions (Registered -> Assigned -> InProgress -> Resolved)
-- ------------------------------------------------------------------------------
CREATE TABLE status_updates (
    id VARCHAR(50) PRIMARY KEY,
    complaint_id VARCHAR(50) NOT NULL,
    previous_status VARCHAR(50) NOT NULL,
    new_status VARCHAR(50) NOT NULL,
    updated_by VARCHAR(100) NOT NULL,
    notes TEXT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_status_complaint FOREIGN KEY (complaint_id)
        REFERENCES complaints(id) ON UPDATE CASCADE ON DELETE CASCADE,
    INDEX idx_status_complaint (complaint_id),
    INDEX idx_status_created_at (created_at)
) ENGINE=InnoDB;

-- ------------------------------------------------------------------------------
-- 6. DUPLICATE_LINKS TABLE
-- Prevents self-referencing links; requires explicit manager/admin review
-- ------------------------------------------------------------------------------
CREATE TABLE duplicate_links (
    id VARCHAR(50) PRIMARY KEY,
    complaint_id VARCHAR(50) NOT NULL,
    possible_duplicate_id VARCHAR(50) NOT NULL,
    reviewed_by VARCHAR(100) NULL,
    review_status ENUM('PENDING', 'CONFIRMED', 'REJECTED') NOT NULL DEFAULT 'PENDING',
    review_notes TEXT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reviewed_at DATETIME NULL,
    CONSTRAINT fk_dup_complaint FOREIGN KEY (complaint_id)
        REFERENCES complaints(id) ON UPDATE CASCADE ON DELETE CASCADE,
    CONSTRAINT fk_dup_target FOREIGN KEY (possible_duplicate_id)
        REFERENCES complaints(id) ON UPDATE CASCADE ON DELETE CASCADE,
    CONSTRAINT chk_dup_not_self CHECK (complaint_id <> possible_duplicate_id),
    CONSTRAINT uq_dup_pair UNIQUE (complaint_id, possible_duplicate_id),
    INDEX idx_dup_complaint (complaint_id),
    INDEX idx_dup_possible (possible_duplicate_id),
    INDEX idx_dup_status (review_status)
) ENGINE=InnoDB;

-- ------------------------------------------------------------------------------
-- 7. AUDIT_LOGS TABLE
-- Immutable security audit log for triage actions, capacity changes, and worker CRUD
-- ------------------------------------------------------------------------------
CREATE TABLE audit_logs (
    id VARCHAR(50) PRIMARY KEY,
    actor_user_id VARCHAR(50) NULL,
    action VARCHAR(100) NOT NULL,
    entity_type VARCHAR(50) NOT NULL,
    entity_id VARCHAR(50) NOT NULL,
    details TEXT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_audit_actor (actor_user_id),
    INDEX idx_audit_entity (entity_type, entity_id),
    INDEX idx_audit_created_at (created_at)
) ENGINE=InnoDB;

-- ------------------------------------------------------------------------------
-- SEED INITIAL CORE DEPARTMENTS
-- ------------------------------------------------------------------------------
INSERT INTO departments (id, name, description, capacity, active) VALUES
('DEPT-WATER', 'Water Supply', 'Municipal Water Pipeline, Potable Water and Drainage Infrastructure', 10, TRUE),
('DEPT-ROADS', 'Roads', 'Road Infrastructure, Potholes, Tar Maintenance, and Stormwater Trenches', 10, TRUE),
('DEPT-WASTE', 'Waste Management', 'Solid Waste Collection, Public Sanitation and Garbage Management', 10, TRUE),
('DEPT-LIGHT', 'Streetlight Maintenance', 'Streetlight Poles, Electrical Safety and Illumination Grid', 10, TRUE),
('DEPT-GEN', 'General Administration', 'Cross-Departmental Governance and Public Grievance Operations', 15, TRUE)
ON DUPLICATE KEY UPDATE name=VALUES(name);

-- ------------------------------------------------------------------------------
-- SEED INITIAL ADMINISTRATIVE & CITIZEN ACCOUNTS
-- Passwords hashed using SHA-256:
-- admin123 -> 240be518fabd2724ddb6f04eeb1da5967448d7e831c08c8fa822809f74c720a9
-- 123456   -> 8d969eef6ecad3c29a3a629280e686cf0c3f5d5a86aff3ca12020c923adc6c92
-- ------------------------------------------------------------------------------
INSERT INTO users (id, full_name, email, mobile, password_hash, role, department_id, active, created_at) VALUES
('ADM1001', 'Chief Municipal Officer', 'admin@makkal.gov.in', '9876543210', '240be518fabd2724ddb6f04eeb1da5967448d7e831c08c8fa822809f74c720a9', 'ADMIN', 'DEPT-GEN', TRUE, '2026-09-14 00:00:00'),
('MGR1001', 'Triage Operations Manager', 'manager@makkal.gov.in', '9876543201', '240be518fabd2724ddb6f04eeb1da5967448d7e831c08c8fa822809f74c720a9', 'MANAGER', 'DEPT-GEN', TRUE, '2026-09-14 00:00:00'),
('STF-W01', 'Officer Suresh', 'water_staff@makkal.gov.in', '9876543211', '240be518fabd2724ddb6f04eeb1da5967448d7e831c08c8fa822809f74c720a9', 'WORKER', 'DEPT-WATER', TRUE, '2026-09-14 00:00:00'),
('STF-R01', 'Officer Karthik', 'roads_staff@makkal.gov.in', '9876543212', '240be518fabd2724ddb6f04eeb1da5967448d7e831c08c8fa822809f74c720a9', 'WORKER', 'DEPT-ROADS', TRUE, '2026-09-14 00:00:00'),
('STF-S01', 'Officer Meena', 'waste_staff@makkal.gov.in', '9876543213', '240be518fabd2724ddb6f04eeb1da5967448d7e831c08c8fa822809f74c720a9', 'WORKER', 'DEPT-WASTE', TRUE, '2026-09-14 00:00:00'),
('STF-L01', 'Officer Priya', 'light_staff@makkal.gov.in', '9876543214', '240be518fabd2724ddb6f04eeb1da5967448d7e831c08c8fa822809f74c720a9', 'WORKER', 'DEPT-LIGHT', TRUE, '2026-09-14 00:00:00'),
('C001', 'Ramesh', 'ramesh@test.com', '9876543210', '8d969eef6ecad3c29a3a629280e686cf0c3f5d5a86aff3ca12020c923adc6c92', 'CITIZEN', NULL, TRUE, '2026-09-14 10:00:00')
ON DUPLICATE KEY UPDATE email=VALUES(email);
