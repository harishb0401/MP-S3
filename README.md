# Full-Stack Government Grievance Management and Priority Resolution System
### Municipal Case Study: Makkal Nagar Municipal Office

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

## 3. Database & Default Admin Account

All records are persisted to `backend/data/database.json`.

### Initial Seeded Municipal Staff / Admin Account:
- **Staff ID / Email**: `STF1001` or `admin@makkal.gov.in`
- **Password**: `admin123`
- **Role**: `STAFF`
- **Department**: `General Administration`

---

## 4. Backend REST API Endpoints

| Method | Endpoint | Description | Auth Required |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/auth/register` | Register new Citizen account | No |
| `POST` | `/api/auth/login` | Login for Citizen or Staff | No |
| `GET` | `/api/auth/me` | Fetch authenticated user profile | Yes |
| `GET` | `/api/dashboard/stats` | Compute real database metrics | No |
| `POST` | `/api/complaints` | Submit new citizen grievance | Citizen |
| `GET` | `/api/my-complaints` | Fetch logged-in citizen's complaints | Citizen |
| `GET` | `/api/complaints` | Fetch master complaint directory | Staff |
| `GET` | `/api/complaints/priority-queue` | Fetch complaints ordered by PriorityQueue | Any |
| `GET` | `/api/complaints/:id` | Fetch specific complaint details | Any |
| `PUT` | `/api/complaints/:id/status` | Update complaint status | Staff |
| `POST` | `/api/complaints/:id/assign` | Assign department & staff officer | Staff |
| `POST` | `/api/complaints/:id/resolve` | Mark resolved & add resolution notes | Staff |

---

## 5. How to Run the Project Locally

### Step 1: Start the Java Backend REST API Server
Open Command Prompt / Terminal:

```cmd
# Navigate to backend source directory
cd c:\Project\MP-S3\01\backend\src

# Compile Java source code
javac -d ../bin model/*.java service/*.java Main.java

# Run Java Backend REST Server
java -cp ../bin Main
```

*(Server will start on `http://localhost:8080/api/`)*

### Step 2: Open the Web Application
Open your web browser (Chrome, Edge, Firefox) and open:
👉 [`c:/Project/MP-S3/01/frontend/index.html`](file:///c:/Project/MP-S3/01/frontend/index.html)

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
