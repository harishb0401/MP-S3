# Full-Stack Government Grievance Management and Priority Resolution System
### Municipal Case Study: Makkal Nagar Municipal Office

[![Open in GitHub Codespaces](https://github.com/codespaces/badge.svg)](https://codespaces.new/harishb0401/MP-S3)
[![Java CI Build & Verification](https://github.com/harishb0401/MP-S3/actions/workflows/ci.yml/badge.svg)](https://github.com/harishb0401/MP-S3/actions/workflows/ci.yml)

A complete, working full-stack municipal grievance portal built using **Java (REST API + Data Structures)** and **Vanilla Web Technologies (HTML, CSS, JS)**.

---

## 1. Architecture & Overview

```text
       ┌────────────────────────┐         ┌────────────────────────┐
       │     Citizen Portal     │         │   Staff/Admin Portal   │
       └───────────┬────────────┘         └───────────┬────────────┘
                   │                                  │
                   └────────────────┬─────────────────┘
                                    │ HTTP REST API (fetch)
                                    ▼
       ┌───────────────────────────────────────────────────────────┐
       │            Java REST Backend (Port 8080)                  │
       │    - HttpServer (com.sun.net.httpserver)                 │
       │    - Auth & Token Session Manager                         │
       │    - Priority Calculation & Department Mapper             │
       │    - PriorityQueue<Complaint> & HashMap Lookups           │
       └────────────────────────────┬──────────────────────────────┘
                                    │
                                    ▼
       ┌───────────────────────────────────────────────────────────┐
       │             Persistent Database Storage                   │
       │             (backend/data/database.json)                  │
       └───────────────────────────────────────────────────────────┘
```

---

## 2. Key Features Implemented

1. **Role-Based Portals**:
   - **Citizen Portal**: Register, Login, Submit complaint, View personal complaints, Track case status step-by-step (`Submitted` $\rightarrow$ `Assigned` $\rightarrow$ `In Progress` $\rightarrow$ `Resolved`).
   - **Municipal Staff/Admin Portal**: Staff login, Real-time Database Stats (Total, Pending, In Progress, Resolved, Critical), Master Directory with Search & Filters, Priority Queue visualizer, Department & Staff assignment, Status updates, Resolution Notes entry.
2. **Real Database Synchronization**:
   - Zero hardcoded mock arrays! Both portals query the **same Java REST API** on `http://localhost:8080/api`.
   - Starts with **0 complaints**. Stats compute dynamically from the database.
3. **Automated Department Mapping**:
   - `Streetlight` $\rightarrow$ Electrical Department
   - `Waste Management` / `Public Sanitation` $\rightarrow$ Sanitation Department
   - `Road Damage` $\rightarrow$ Roads Department
   - `Water Supply` $\rightarrow$ Water Supply Department
   - `Drainage` $\rightarrow$ Drainage Department
   - `Other` $\rightarrow$ General Administration
4. **Backend Priority System & Data Structure**:
   - Priority levels: `CRITICAL`, `HIGH`, `MEDIUM`, `LOW`.
   - Backend priority calculator evaluates category & hazard keywords (`burst`, `flooding`, `hazardous`, `medical waste`, etc.).
   - Java `PriorityQueue<Complaint>` orders active complaints by severity, then by submission timestamp.

---

## 3. Database Architecture & MySQL Persistence

The application uses **MySQL 8.0+** as its primary persistent database engine via JDBC (`mysql-connector-j-8.3.0.jar`).

### Database Name:
`makkal_nagar_grievance`

### Relational Schema Tables (`database/schema.sql`):
1. **`departments`**: Core municipal departments (`DEPT-WATER`, `DEPT-ROADS`, `DEPT-WASTE`, `DEPT-LIGHT`), capacities, and operational status.
2. **`users`**: Role-based accounts (`CITIZEN`, `WORKER`, `MANAGER`, `ADMIN`) with SHA-256 hashed credentials.
3. **`complaints`**: Complete grievance records with GPS latitude/longitude/accuracy, arrival sequence, impact count, severity, category, municipal area, and resolution notes.
4. **`assignments`**: Complete historical audit trail of department and worker assignments and reassignments with handover reasons.
5. **`status_updates`**: Immutable state transition timeline (`Registered` $\rightarrow$ `Assigned` $\rightarrow$ `InProgress` $\rightarrow$ `Resolved`) with actor and remarks.
6. **`duplicate_links`**: Candidate duplicate links reviewed and confirmed/rejected by authorized managers.
7. **`audit_logs`**: Security and operations audit trail for administrative actions.

---

## 4. MySQL Setup & Configuration Guide

### Step 1: Install & Start MySQL 8.0
- **Windows**: Install MySQL Server via MySQL Installer or start the service:
  ```powershell
  net start MySQL80
  ```
- **Linux (Ubuntu/Debian)**:
  ```bash
  sudo systemctl start mysql
  ```
- **macOS (Homebrew)**:
  ```bash
  brew services start mysql
  ```

### Step 2: Create Database and Dedicated Application User
Log in to MySQL as root (`mysql -u root -p`) and execute:
```sql
CREATE DATABASE IF NOT EXISTS makkal_nagar_grievance
  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- Create least-privilege municipal application user
CREATE USER IF NOT EXISTS 'makkal_app'@'localhost' IDENTIFIED BY 'MakkalGov@2026';
GRANT ALL PRIVILEGES ON makkal_nagar_grievance.* TO 'makkal_app'@'localhost';
FLUSH PRIVILEGES;
```

### Step 3: Execute Schema DDL Script
Execute the database initialization script located at `database/schema.sql`:
```bash
mysql -u makkal_app -p makkal_nagar_grievance < database/schema.sql
```

### Step 4: Configure Database Credentials
Copy `.env.example` or `application.properties.example` to your active configuration:
```bash
# Option A: Environment variables file
cp .env.example .env

# Option B: Application properties file
cp application.properties.example application.properties
```
Edit `.env` or `application.properties`:
```properties
DB_HOST=localhost
DB_PORT=3306
DB_NAME=makkal_nagar_grievance
DB_USERNAME=makkal_app
DB_PASSWORD=MakkalGov@2026
```
*(Alternatively, export system environment variables: `export DB_HOST=localhost`, `export DB_USERNAME=makkal_app`, etc.)*

### Step 5: (Optional) Migrate Existing JSON Data Idempotently
If you have existing historical data in `backend/data/database.json`, run the migration utility:
```bash
# Windows
java -cp "backend/bin;backend/lib/*" service.DataMigrator

# Linux / macOS
java -cp "backend/bin:backend/lib/*" service.DataMigrator
```
The migrator creates a timestamped safety backup (`database.json.bak`) and reports the count of imported records.

---

## 5. Build, Test & Run

### Automated Verification Test Suite (All 17 Migration Requirements + 8 Core Verification Suites)
```bash
# Windows
javac -cp "backend/lib/*;backend/src" -d backend/bin backend/src/model/*.java backend/src/repository/*.java backend/src/service/*.java backend/src/test/*.java backend/src/Main.java
java -cp "backend/bin;backend/lib/*" test.SystemTest

# Linux / macOS
javac -cp "backend/lib/*:backend/src" -d backend/bin backend/src/model/*.java backend/src/repository/*.java backend/src/service/*.java backend/src/test/*.java backend/src/Main.java
java -cp "backend/bin:backend/lib/*" test.SystemTest
```

### Start the Application Server
```bash
# Windows 1-Click
.\start.bat

# Linux / macOS 1-Click
./start.sh
```
Open `frontend/index.html` or navigate to `http://localhost:8080/` in your browser. All complaint data persists across server restarts in MySQL.

---

## 6. End-to-End Live Demonstration Procedure

### Test 1: Citizen Registration & Login
1. Open `index.html`.
2. Click **Register Citizen**.
3. Register citizen: Name `Ramesh Kumar`, Email `ramesh@example.com`, Password `password123`.
4. Submit $\rightarrow$ Citizen Dashboard opens showing **0 Complaints**.

### Test 2: Submit Complaint & Verify Backend Priority Calculation
1. Click **Submit New Complaint**.
2. Select Category `Streetlight`, Location `5th Main Road`, Description `Streetlight bulb broken near government school`.
3. Submit $\rightarrow$ Receives generated Complaint ID (e.g. `GRV-2026-0001`). Priority is automatically set to `HIGH` and Department to `Electrical`.

### Test 3: Municipal Staff Login & Master Directory
1. Click **Logout**.
2. Switch to **Municipal Staff** tab.
3. Login using `STF1001` and password `admin123`.
4. Staff Dashboard opens showing **Total: 1**, **Pending: 1**, **High Priority: 1**.
5. `GRV-2026-0001` appears in the Master Register table.

### Test 4: Staff Department Assignment & Resolution Notes
1. Click **Manage** on `GRV-2026-0001`.
2. Assign Staff Officer `Officer Suresh` and click **Assign**.
3. Update Status to `IN_PROGRESS` and click **Update**.
4. Enter Resolution Notes: `"Faulty streetlight bulb replaced with LED unit and tested successfully."`
5. Click **Mark Resolved**. Status updates to `RESOLVED`.

### Test 5: Verify Live Sync on Citizen Portal
1. Logout of Staff Portal.
2. Login as Citizen `ramesh@example.com` / `password123`.
3. Citizen Dashboard shows **Total: 1**, **Resolved: 1**.
4. Click **Track Case** on `GRV-2026-0001`.
5. The timeline graph shows all steps completed (`Submitted` $\checkmark$, `Assigned` $\checkmark$, `In Progress` $\checkmark$, `Resolved` $\checkmark$) and displays the resolution description notes recorded by the staff officer!
