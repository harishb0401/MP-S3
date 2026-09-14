# Government Grievance Management and Priority Resolution System
### Municipal Office Case Study: Makkal Nagar Municipal Office

---

## 1. Project Title
**Government Grievance Management and Priority Resolution System**

## 2. Problem Statement
Currently, municipal grievance reporting at **Makkal Nagar Municipal Office** is handled using paper registers, phone calls, and informal communications. This causes significant delays, lack of transparency, inability to track complaint statuses, and difficulty in identifying urgent civic emergencies (such as major water main bursts or hazardous waste leaks) over routine complaints.

## 3. Objectives
- Digitalize municipal grievance reporting into a centralized computerized system.
- Implement priority-driven resolution so high-severity/emergency complaints are addressed first.
- Provide real-time tracking for citizens and municipal administrative staff.
- Demonstrate core Object-Oriented Programming (OOP) and Data Structures concepts in a practical Java & Web solution.

## 4. Prototype Features Implemented (30% Scope)
1. **Administrative Dashboard**: Displays total, pending, in-progress, resolved, and emergency grievance metrics with a recent complaints table.
2. **Complaint Registration**: Form allowing citizens to file grievances with automatic Complaint ID generation (`CMP1001`, `CMP1002`, etc.).
3. **Master Complaint Directory**: Full table displaying all complaints with live Search and multi-criteria Filtering (Type, Priority, Status).
4. **Complaint Case Details & Actions**: Detailed modal allowing municipal staff to assign departments (Electrical, Sanitation, Roads, Water Supply, Drainage) and update complaint status.
5. **Priority Resolution Queue**: Dedicated view utilizing a **PriorityQueue** algorithm ordering complaints by priority (**Emergency &rarr; High &rarr; Medium &rarr; Low**).
6. **Department Workload Monitoring**: Overview cards displaying active complaint distribution across departments.

---

## 5. Technologies Used
- **Backend / Core Logic**: Java (JDK 8+)
- **Frontend / UI**: HTML5, CSS3 (Vanilla Custom System with Dark Mode/Glassmorphism), JavaScript (ES6+ with `localStorage` persistence)
- **Icons**: FontAwesome 6 (CDN)

---

## 6. Object-Oriented Programming (OOP) Concepts Used
- **Classes and Objects**: `Complaint`, `Citizen`, `Department`, and `Staff` objects representing domain entities.
- **Encapsulation**: Private member fields with getter and setter methods controlling access to object state.
- **Inheritance & Interfaces**: Implementation of `Comparable<Complaint>` interface to define custom natural ordering based on complaint priority weights.
- **Abstraction**: `GrievanceManager` service encapsulating complex data structure operations (`ArrayList`, `HashMap`, `PriorityQueue`) behind a simple API interface.

---

## 7. Data Structures Used
- **`ArrayList<Complaint>`**: Used for sequential storage and dynamic iteration of all registered complaints.
- **`HashMap<String, Complaint>`**: Provides fast $O(1)$ constant time lookup of complaints by `complaintId` (`CMP1001`).
- **`PriorityQueue<Complaint>`**: Maintains complaints in priority order (**Emergency**=1, **High**=2, **Medium**=3, **Low**=4) so that critical civic emergencies are dispatched first.

---

## 8. Current 30% Prototype Scope vs. 100% Roadmap

| Feature | 30% Prototype (Tomorrow's Demo) | 100% Final System |
| :--- | :--- | :--- |
| **Data Storage** | Preloaded Sample Data & `localStorage` / Java In-Memory | Relational Database (MySQL / PostgreSQL) |
| **Interface** | Admin Dashboard & Priority Queue Visualizer | Citizen & Staff Web & Mobile Apps |
| **Priority Queue** | PriorityQueue Comparator Logic (Emergency &rarr; Low) | AI Automated Priority Prediction |
| **Authentication** | Demo Mode | Role-Based Access Control (RBAC) |

---

## 9. Future Features Roadmap (100% Completion)
- Citizen Login & Profile Management Portal
- Municipal Staff & Officer Login Portal
- Persistent Database Integration (MySQL/PostgreSQL with Spring Boot REST API)
- Automated Email & SMS Notifications for status updates
- Automatic Complaint Escalation if pending beyond SLA time limits
- Interactive GIS Map / Location Marker Integration
- Automated Priority Prediction using NLP / Machine Learning
- Complete Complaint History Logs & Audit Trail
- Mobile Application (Flutter / React Native)

---

## 10. File Structure Explanation

```text
Government-Grievance-Management/
├── backend/
│   └── src/
│       ├── model/
│       │   ├── Citizen.java          # Model representing citizen contact details
│       │   ├── Department.java       # Model representing municipal departments
│       │   ├── Staff.java            # Model representing department staff members
│       │   └── Complaint.java        # Core model implementing Comparable for PriorityQueue
│       ├── service/
│       │   └── GrievanceManager.java # Data Structure manager (ArrayList, HashMap, PriorityQueue)
│       └── Main.java                 # Preloads sample data & runs CLI demonstration
├── frontend/
│   ├── index.html                    # Single Page Web App container (Dashboard, Queue, Form, Directory)
│   ├── css/
│   │   └── style.css                 # Modern municipal dark system styling
│   └── js/
│       └── app.js                    # State management, search/filters, modal logic & local storage
└── README.md                         # Project documentation and demonstration guide
```

---

## 11. How to Run the Prototype

### Running the Frontend Prototype (Web Dashboard)
1. Open your web browser (Chrome, Edge, Firefox).
2. Open the file directly:
   `c:/Project/MP-S3/01/frontend/index.html`
3. All features (Dashboard, Registration, Priority Queue, Department Assignment, Status Updates) work interactively out of the box!

### Compiling and Running the Java Backend
Open Command Prompt or Terminal and execute:

```bash
# Navigate to backend source directory
cd c:\Project\MP-S3\01\backend\src

# Compile all Java files into the bin directory
javac -d ../bin model/*.java service/*.java Main.java

# Run the compiled Java program
java -cp ../bin Main
```

---

## 12. 2–3 Minute Demonstration Script (For Tomorrow's Presentation)

> **"Good morning/afternoon respected staff/professors. Today I am demonstrating the 30% prototype of the Government Grievance Management and Priority Resolution System for Makkal Nagar Municipal Office."**

### Step 1: Explain Problem & Dashboard (30 Seconds)
- *"Currently, municipal offices record complaints in manual registers, leading to delays and missed emergencies."*
- *"Here on the **Dashboard**, we have real-time metrics showing Total Complaints, Pending Cases, Resolved Cases, and High/Emergency Priority alerts."*

### Step 2: Register a New Complaint (45 Seconds)
- *"Let's go to **Register Complaint**. Imagine citizen 'Ravi' submits a 'Streetlight' complaint with priority 'High'."*
- *"When submitted, the system automatically generates a unique ID `CMP1007` and adds it to our master directory."*

### Step 3: Demonstrate Priority Queue & Data Structures (45 Seconds)
- *"Now let's switch to the **Priority Queue** view. This is the core Data Structure feature of our project."*
- *"In Java, we use a `PriorityQueue<Complaint>` with custom `Comparable` weights. As you can see, **Emergency** complaints like water main bursts and medical waste dumping automatically rise to Rank #1 and #2 ahead of Low priority road complaints."*
- *"In addition, we use `ArrayList` for sequential storage and a `HashMap<String, Complaint>` to perform instant $O(1)$ fast lookups by Complaint ID."*

### Step 4: Complaint Details, Assigning & Updating (30 Seconds)
- *"If we click **View** on any complaint, we open the Case File. Here municipal staff can assign departments (e.g., 'Electrical' or 'Water Supply') and update the status from 'Pending' to 'In Progress' or 'Resolved'."*
- *"The dashboard metrics and priority queue update instantly to reflect these changes."*
- *"This completes our functional 30% prototype demonstration for today. Thank you!"*
