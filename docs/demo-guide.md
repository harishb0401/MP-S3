# DELIVERABLE E: DEMONSTRATION GUIDE

## 1. System Requirements & Starting the Application

### 1.1 Prerequisites
- **Java SE Development Kit (JDK)**: Version 17 or higher (`javac` and `java` available on PATH).
- **Web Browser**: Google Chrome, Mozilla Firefox, or Microsoft Edge.
- **Port**: Ensure port `8080` is free.

### 1.2 Start Commands
From the project root directory (`c:\Project\MP-S3\2.0\MP-S3`):

#### On Windows (PowerShell or Command Prompt)
Run the automated build and start script:
```powershell
.\start.bat
```
Or run manually step-by-step:
```powershell
# 1. Compile backend
javac -d backend/bin backend/src/model/*.java backend/src/service/*.java backend/src/test/*.java backend/src/Main.java

# 2. Run system test verification (optional sanity check)
java -cp backend/bin test.SystemTest

# 3. Start the combined Web Server and REST API
java -cp backend/bin Main
```

#### On Linux / macOS
```bash
chmod +x start.sh
./start.sh
```

Upon successful startup, the console displays:
```
===============================================================================
     MAKKAL NAGAR MUNICIPAL OFFICE - FULL-STACK BACKEND REST API SERVER
===============================================================================
[SYSTEM] Initializing persistent database storage...
[DATA HEALTH] PASS: Synthetic demonstration dataset is clean, complete, and verified.
[DEMO DATA STATUS] Preloaded synthetic demonstration complaints active.
[DEMO LOCATIONS] Five canonical Makkal Nagar zones: [Central Market, North Street, South Colony, Lake Road, Government Hospital Area]
[SERVER SUCCESS] REST API Server running on http://localhost:8080/api/
[SERVER SUCCESS] Web Portal running at http://localhost:8080/
```

Open your browser and navigate to:
```
http://localhost:8080/
```

---

## 2. Discovered Official Demonstration Credentials

Use only the actual credentials present in the verified database:

| Role | Name | Email / Login Identifier | Password | Primary Scope / Portal View |
| :--- | :--- | :--- | :--- | :--- |
| **Administrator** | Municipal Officer | `admin@makkal.gov.in` | `admin123` | Full Administrative Dashboard & Triage |
| **Chief Admin** | Chief Municipal Officer | `commissioner@makkal.gov.in` | `admin123` | Executive Oversight & Triage |
| **Water Staff** | Officer Suresh | `water_staff@makkal.gov.in` | `admin123` | Water Supply Operations (`DEPT-WATER`) |
| **Roads Staff** | Officer Karthik | `roads_staff@makkal.gov.in` | `admin123` | Roads Department Operations (`DEPT-ROADS`) |
| **Waste Staff** | Officer Priya | `waste_staff@makkal.gov.in` | `admin123` | Waste Management (`DEPT-WASTE`) |
| **Streetlight Staff**| Officer Ramesh | `light_staff@makkal.gov.in` | `admin123` | Streetlight Department (`DEPT-LIGHT`) |
| **Citizen 1** | Ramesh | `ramesh@test.com` | `123456` | Citizen Dashboard & Grievance History |
| **Citizen 2** | Kalai | `kalai@gmail.com` | `123456` | Citizen Dashboard & Grievance Filing |

---

## 3. Step-by-Step Demonstration Script

### Step 1: Citizen Login & Complaint Submission
1. Navigate to `http://localhost:8080/`.
2. On the login screen, select **Citizen Portal** or enter:
   - Email: `ramesh@test.com`
   - Password: `123456`
3. Click **Sign In as Citizen**. You are redirected to the **Citizen Dashboard**, displaying active complaints previously submitted by Ramesh.
4. Click **Submit Complaint** in the sidebar.
5. In the guided multi-step form:
   - **Category**: Select `Road Damage`.
   - **Location**: Select one of the 5 canonical locations: `Central Market`.
   - **Affected Persons (Impact)**: Enter `250`.
   - **Severity**: Select `CRITICAL`.
   - **Description**: Enter `Dangerous deep cavity near central bus terminal crossing`.
6. Click **Submit Grievance**.
7. Confirm that a generated complaint ID appears (e.g., `GRV-2026-0033`), status is set to `Registered`, and the record appears in your dashboard timeline.

---

### Step 2: Staff / Admin Login & Priority Resolution Queue
1. Log out by clicking the user avatar in the top-right corner and clicking **Sign Out**.
2. Log in using administrative credentials:
   - Email: `admin@makkal.gov.in`
   - Password: `admin123`
3. Click **Priority Queue** in the sidebar (`/api/complaints/priority-queue`).
4. **Observe Priority Ranking**:
   - Notice that complaints are strictly ordered:
     1. All `CRITICAL` complaints appear at the top.
     2. Within `CRITICAL`, complaints with highest affected counts (e.g., impact `500`, `350`, `250`) rank higher.
     3. Identical severity and impact complaints are ordered strictly by earliest arrival time (FIFO).
5. Locate the newly registered complaint `GRV-2026-0033`.

---

### Step 3: Impact Verification & Citizen Notification
1. In the Staff Dashboard or Priority Queue, click **Process** on a complaint (e.g., `GRV-2026-0033`).
2. In the modal, locate the **Impact Verification** panel.
3. Observe that **Citizen Reported Impact** (`250`) is displayed as locked and immutable.
4. Enter an **Admin Verified Impact**: `200`.
5. Enter a mandatory **Verification Reason**: `Site inspection verified 2 affected retail rows.`
6. Click **Save Verification**.
7. The system updates the effective priority, locks the verification, logs an audit entry, and dispatches a notification to the citizen.

---

### Step 4: Status Transition State Machine
1. Open the **Process** modal for a `Registered` complaint.
2. Select **Status Update**:
   - Try selecting `InProgress` or `Resolved` directly without assignment. Notice the system blocks invalid skips or warns that assignment is required.
3. Assign to department (e.g., `Roads Department` - `Officer Karthik`).
4. Click **Update Status** to transition to `Assigned`.
5. Now transition from `Assigned` $\rightarrow$ `InProgress`.
6. Provide resolution notes: `Bitumen patching completed and road opened for traffic.`
7. Transition from `InProgress` $\rightarrow$ `Resolved`.
8. Attempt to transition from `Resolved` back to `InProgress`. Observe that the state machine rejects reopening of resolved complaints.

---

### Step 5: Triggering Department Capacity & Waiting Queue
1. Navigate to **Departments** (`/api/departments/capacity`) in the sidebar.
2. Observe the current load of each department (e.g., `Roads Department Load: 7/10`).
3. To demonstrate capacity saturation:
   - Make multiple assignments to `Roads Department` until active load reaches capacity (`10/10`).
   - Assign an 11th complaint to `Roads Department`.
4. Observe the system response:
   - The 11th complaint is marked `isWaiting = true`.
   - It is enqueued in the department's `waitingQueue` (a `PriorityQueue<Complaint>`).
   - Active capacity remains strictly capped at `10/10`.
5. Resolve one of the active complaints in `Roads Department`.
6. Observe that the freed slot triggers automatic dequeue of the highest-priority waiting complaint, transitioning it into active capacity.

---

### Step 6: Duplicate Candidate Detection & Operator Confirmation / Rejection
1. Register or locate a complaint with the same category and canonical location as an open complaint:
   - Primary: `Road Damage` at `Central Market`.
   - Candidate: Another open complaint with `Road Damage` at `Central Market`.
2. As Admin/Staff, open the complaint details and click **Duplicate Detection** / **View Candidates**.
3. **Candidate Detection**: The system displays the candidate with a match score (e.g., `85% Match - Same Category and Normalized Location: Central Market`).
4. **Demonstrate Rejection**:
   - Click **Reject Candidate**.
   - The candidate is dismissed, added to `rejectedCandidateIds`, and will never be presented again for this pairing.
5. **Demonstrate Confirmation**:
   - For another candidate, click **Confirm Duplicate**.
   - Enter merge reason: `Confirmed identical pothole on Central Market road`.
   - Click **Confirm Link**.
   - Observe: The candidate is linked (`duplicateOfId = primaryId`), transitioned to `Resolved`, and preserved in the database without being deleted. Both primary and candidate receive audit history entries.

---

### Step 7: Viewing Analytics & Department Reports
1. In the sidebar, click **Analytics & Reports**.
2. Review the generated visualizations:
   - **Category Distribution**: Breakdown across `Road Damage`, `Water Supply`, `Waste Management`, and `Streetlight`.
   - **Department Workload Chart**: Real-time load across the 4 core departments.
   - **Priority Ratio**: Proportions of `CRITICAL`, `HIGH`, `MEDIUM`, and `LOW` grievances.
   - **Status Breakdown**: Visual representation of `Registered`, `Assigned`, `InProgress`, and `Resolved` cases.
3. Click **Departments** view to inspect individual department workload cards and active vs. pending tasks.
