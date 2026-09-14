// Makkal Nagar Municipal Office - Grievance Management System App Logic

// Initial Preloaded Sample Complaints
const initialComplaints = [
    {
        complaintId: "CMP1001",
        citizenName: "Ravi",
        citizenMobile: "9876543210",
        complaintType: "Streetlight",
        description: "Streetlight not working for 3 days near 2nd Street corner",
        location: "Makkal Nagar 2nd Street",
        priority: "High",
        status: "Pending",
        assignedDepartment: "Electrical",
        createdDate: "2026-09-14 09:30"
    },
    {
        complaintId: "CMP1002",
        citizenName: "Priya",
        citizenMobile: "9876543211",
        complaintType: "Water Supply",
        description: "Major pipe burst causing flooding in main street and water shortage",
        location: "Makkal Nagar 5th Street",
        priority: "Emergency",
        status: "Pending",
        assignedDepartment: "Water Supply",
        createdDate: "2026-09-14 10:15"
    },
    {
        complaintId: "CMP1003",
        citizenName: "Kumar",
        citizenMobile: "9876543212",
        complaintType: "Waste Management",
        description: "Uncollected solid waste overflowing community bins for past 2 days",
        location: "Makkal Nagar Main Road",
        priority: "Medium",
        status: "In Progress",
        assignedDepartment: "Sanitation",
        createdDate: "2026-09-14 11:00"
    },
    {
        complaintId: "CMP1004",
        citizenName: "Anitha",
        citizenMobile: "9876543213",
        complaintType: "Drainage",
        description: "Drainage blockage causing foul smell and health hazard",
        location: "Makkal Nagar 8th Cross",
        priority: "High",
        status: "Pending",
        assignedDepartment: "Drainage",
        createdDate: "2026-09-14 11:45"
    },
    {
        complaintId: "CMP1005",
        citizenName: "Suresh",
        citizenMobile: "9876543214",
        complaintType: "Road",
        description: "Deep pothole causing vehicle damage and traffic slowdown",
        location: "Bus Stand Road",
        priority: "Low",
        status: "In Progress",
        assignedDepartment: "Roads",
        createdDate: "2026-09-14 12:30"
    },
    {
        complaintId: "CMP1006",
        citizenName: "Rajesh",
        citizenMobile: "9876543215",
        complaintType: "Sanitation",
        description: "Hazardous medical waste dumped near public park play area",
        location: "Makkal Nagar Park",
        priority: "Emergency",
        status: "Pending",
        assignedDepartment: "Sanitation",
        createdDate: "2026-09-14 13:10"
    }
];

// App State
let complaints = [];
let activeComplaintId = null;

// Initialize App
document.addEventListener("DOMContentLoaded", () => {
    loadState();
    setupNavigation();
    setupFilters();
    renderAll();
});

// Load state from localStorage or initialize with sample data
function loadState() {
    const saved = localStorage.getItem("makkal_nagar_complaints");
    if (saved) {
        try {
            complaints = JSON.parse(saved);
        } catch (e) {
            complaints = [...initialComplaints];
        }
    } else {
        complaints = [...initialComplaints];
        saveState();
    }
}

// Save state to localStorage
function saveState() {
    localStorage.setItem("makkal_nagar_complaints", JSON.stringify(complaints));
}

// Navigation View Switching
function setupNavigation() {
    document.querySelectorAll(".nav-link").forEach(link => {
        link.addEventListener("click", (e) => {
            e.preventDefault();
            const viewId = link.getAttribute("data-view");
            switchView(viewId);
        });
    });
}

function switchView(viewId) {
    document.querySelectorAll(".nav-link").forEach(l => l.classList.remove("active"));
    const activeLink = document.querySelector(`.nav-link[data-view="${viewId}"]`);
    if (activeLink) activeLink.classList.add("active");

    document.querySelectorAll(".page-view").forEach(v => v.classList.remove("active"));
    const targetView = document.getElementById(viewId);
    if (targetView) targetView.classList.add("active");

    // Refresh views on switch
    renderAll();
}

// Render All Components
function renderAll() {
    renderStats();
    renderRecentTable();
    renderFullTable();
    renderPriorityQueue();
    renderDepartments();
}

// 1. Stats Dashboard
function renderStats() {
    const total = complaints.length;
    const pending = complaints.filter(c => c.status === "Pending").length;
    const progress = complaints.filter(c => c.status === "In Progress").length;
    const resolved = complaints.filter(c => c.status === "Resolved").length;
    const highEmergency = complaints.filter(c => c.priority === "High" || c.priority === "Emergency").length;

    document.getElementById("stat-total").innerText = total;
    document.getElementById("stat-pending").innerText = pending;
    document.getElementById("stat-progress").innerText = progress;
    document.getElementById("stat-resolved").innerText = resolved;
    document.getElementById("stat-high").innerText = highEmergency;
}

// Priority Badge Helper
function getPriorityBadge(priority) {
    const p = (priority || "").toLowerCase();
    return `<span class="badge badge-${p}"><i class="fa-solid fa-circle" style="font-size:6px;"></i> ${priority}</span>`;
}

// Status Badge Helper
function getStatusBadge(status) {
    let sClass = "pending";
    if (status === "In Progress") sClass = "progress";
    if (status === "Resolved") sClass = "resolved";
    if (status === "Rejected") sClass = "rejected";
    return `<span class="badge badge-${sClass}">${status}</span>`;
}

// 2. Recent Complaints Table (Dashboard)
function renderRecentTable() {
    const tbody = document.getElementById("recent-complaints-tbody");
    if (!tbody) return;
    
    // Recent 5 complaints
    const recent = [...complaints].reverse().slice(0, 5);
    
    if (recent.length === 0) {
        tbody.innerHTML = `<tr><td colspan="7" style="text-align:center; color:var(--text-muted);">No complaints recorded.</td></tr>`;
        return;
    }

    tbody.innerHTML = recent.map(c => `
        <tr>
            <td><strong>${c.complaintId}</strong></td>
            <td>${escapeHtml(c.citizenName)}</td>
            <td>${escapeHtml(c.complaintType)}</td>
            <td>${escapeHtml(c.location)}</td>
            <td>${getPriorityBadge(c.priority)}</td>
            <td>${getStatusBadge(c.status)}</td>
            <td>
                <button class="btn btn-secondary btn-sm" onclick="openModal('${c.complaintId}')">
                    <i class="fa-solid fa-eye"></i> View
                </button>
            </td>
        </tr>
    `).join("");
}

// 3. Full Complaints Master Table with Filtering & Search
function setupFilters() {
    const search = document.getElementById("search-input");
    const filterType = document.getElementById("filter-type");
    const filterPriority = document.getElementById("filter-priority");
    const filterStatus = document.getElementById("filter-status");

    [search, filterType, filterPriority, filterStatus].forEach(el => {
        if (el) el.addEventListener("input", renderFullTable);
    });
}

function renderFullTable() {
    const tbody = document.getElementById("all-complaints-tbody");
    if (!tbody) return;

    const query = (document.getElementById("search-input")?.value || "").toLowerCase().trim();
    const type = document.getElementById("filter-type")?.value || "";
    const priority = document.getElementById("filter-priority")?.value || "";
    const status = document.getElementById("filter-status")?.value || "";

    const filtered = complaints.filter(c => {
        const matchesQuery = !query || 
            c.complaintId.toLowerCase().includes(query) ||
            c.citizenName.toLowerCase().includes(query) ||
            c.location.toLowerCase().includes(query) ||
            c.description.toLowerCase().includes(query);

        const matchesType = !type || c.complaintType === type;
        const matchesPriority = !priority || c.priority === priority;
        const matchesStatus = !status || c.status === status;

        return matchesQuery && matchesType && matchesPriority && matchesStatus;
    });

    if (filtered.length === 0) {
        tbody.innerHTML = `<tr><td colspan="8" style="text-align:center; color:var(--text-muted); padding:30px;">No complaints match the search & filter criteria.</td></tr>`;
        return;
    }

    tbody.innerHTML = filtered.map(c => `
        <tr>
            <td><strong>${c.complaintId}</strong></td>
            <td>${escapeHtml(c.citizenName)}</td>
            <td>${escapeHtml(c.complaintType)}</td>
            <td>${escapeHtml(c.location)}</td>
            <td>${getPriorityBadge(c.priority)}</td>
            <td>${getStatusBadge(c.status)}</td>
            <td>${c.createdDate || 'Today'}</td>
            <td>
                <button class="btn btn-secondary btn-sm" onclick="openModal('${c.complaintId}')">
                    <i class="fa-solid fa-eye"></i> View
                </button>
            </td>
        </tr>
    `).join("");
}

// 4. Register Complaint Handler
function handleRegisterSubmit(e) {
    e.preventDefault();

    const name = document.getElementById("citizen-name").value.trim();
    const mobile = document.getElementById("citizen-mobile").value.trim();
    const type = document.getElementById("complaint-type").value;
    const priority = document.getElementById("complaint-priority").value;
    const location = document.getElementById("complaint-location").value.trim();
    const desc = document.getElementById("complaint-desc").value.trim();

    // Auto-generate Complaint ID e.g. CMP1007
    let maxIdNum = 1000;
    complaints.forEach(c => {
        const num = parseInt(c.complaintId.replace("CMP", ""), 10);
        if (!isNaN(num) && num > maxIdNum) maxIdNum = num;
    });
    const newId = `CMP${maxIdNum + 1}`;

    const newComplaint = {
        complaintId: newId,
        citizenName: name,
        citizenMobile: mobile,
        complaintType: type,
        description: desc,
        location: location,
        priority: priority,
        status: "Pending",
        assignedDepartment: type === "Streetlight" ? "Electrical" : 
                            type === "Water Supply" ? "Water Supply" : 
                            type === "Waste Management" ? "Sanitation" : 
                            type === "Road" ? "Roads" : 
                            type === "Drainage" ? "Drainage" : "Sanitation",
        createdDate: new Date().toISOString().replace('T', ' ').substring(0, 16)
    };

    complaints.push(newComplaint);
    saveState();

    showToast(`Complaint registered successfully! Assigned ID: <strong>${newId}</strong>`);
    document.getElementById("register-form").reset();

    // Switch to complaints directory to show newly added item
    setTimeout(() => {
        switchView("complaints");
    }, 1200);
}

// 5. Priority Resolution Queue (Sorting Algorithm matching Java PriorityQueue)
function getPriorityWeight(priority) {
    switch ((priority || "").toLowerCase()) {
        case "emergency": return 1;
        case "high": return 2;
        case "medium": return 3;
        case "low": return 4;
        default: return 5;
    }
}

function renderPriorityQueue() {
    const container = document.getElementById("pq-container");
    const countBadge = document.getElementById("active-queue-count");
    if (!container) return;

    // Filter out resolved & rejected complaints for active queue
    const active = complaints.filter(c => c.status !== "Resolved" && c.status !== "Rejected");

    // Sort matching PriorityQueue comparator (Emergency > High > Medium > Low)
    active.sort((a, b) => getPriorityWeight(a.priority) - getPriorityWeight(b.priority));

    if (countBadge) {
        countBadge.innerText = `${active.length} Complaints in Active Queue`;
    }

    if (active.length === 0) {
        container.innerHTML = `<div style="text-align:center; padding:40px; color:var(--text-muted);">Priority Resolution Queue is empty! All grievances are resolved.</div>`;
        return;
    }

    container.innerHTML = active.map((c, index) => `
        <div class="pq-item ${c.priority.toLowerCase()}">
            <div class="pq-rank">#${index + 1}</div>
            <div class="pq-details">
                <div class="pq-details-header">
                    <span class="pq-title"><strong>${c.complaintId}</strong> - ${escapeHtml(c.complaintType)}</span>
                    ${getPriorityBadge(c.priority)}
                    ${getStatusBadge(c.status)}
                </div>
                <div class="pq-meta">
                    <span><i class="fa-solid fa-location-dot"></i> ${escapeHtml(c.location)}</span>
                    <span><i class="fa-solid fa-user"></i> ${escapeHtml(c.citizenName)}</span>
                    <span><i class="fa-solid fa-building"></i> Dept: ${escapeHtml(c.assignedDepartment)}</span>
                </div>
            </div>
            <button class="btn btn-secondary btn-sm" onclick="openModal('${c.complaintId}')">
                <i class="fa-solid fa-sliders"></i> Process
            </button>
        </div>
    `).join("");
}

// 6. Departments View
function renderDepartments() {
    const grid = document.getElementById("departments-grid");
    if (!grid) return;

    const depts = ["Electrical", "Sanitation", "Roads", "Water Supply", "Drainage"];

    grid.innerHTML = depts.map(d => {
        const assignedCount = complaints.filter(c => c.assignedDepartment === d).length;
        const pendingCount = complaints.filter(c => c.assignedDepartment === d && c.status === "Pending").length;
        
        return `
            <div class="stat-card" style="flex-direction:column; align-items:flex-start; gap:12px;">
                <div style="display:flex; justify-content:space-between; width:100%; align-items:center;">
                    <h3 style="font-size:18px; font-weight:700; color:var(--primary);">${d} Dept</h3>
                    <span class="badge badge-progress">${assignedCount} Total</span>
                </div>
                <div style="font-size:13px; color:var(--text-muted);">
                    <p><i class="fa-solid fa-clock"></i> Pending Tasks: <strong>${pendingCount}</strong></p>
                    <p style="margin-top:4px;"><i class="fa-solid fa-user-check"></i> Field Staff Active</p>
                </div>
            </div>
        `;
    }).join("");
}

// Modal View Details & Management
function openModal(complaintId) {
    const found = complaints.find(c => c.complaintId === complaintId);
    if (!found) return;

    activeComplaintId = complaintId;

    document.getElementById("modal-cmp-id").innerText = found.complaintId;
    document.getElementById("modal-citizen-name").innerText = found.citizenName;
    document.getElementById("modal-citizen-mobile").innerText = found.citizenMobile;
    document.getElementById("modal-type").innerText = found.complaintType;
    document.getElementById("modal-priority").innerHTML = getPriorityBadge(found.priority);
    document.getElementById("modal-location").innerText = found.location;
    document.getElementById("modal-description").innerText = found.description;
    document.getElementById("modal-date").innerText = found.createdDate || "Today";
    document.getElementById("modal-status").innerHTML = getStatusBadge(found.status);
    document.getElementById("modal-dept").innerText = found.assignedDepartment || "Unassigned";

    // Set select values
    document.getElementById("assign-dept-select").value = found.assignedDepartment || "Electrical";
    document.getElementById("update-status-select").value = found.status || "Pending";

    document.getElementById("details-modal").classList.add("active");
}

function closeModal() {
    document.getElementById("details-modal").classList.remove("active");
    activeComplaintId = null;
}

function handleAssignDept() {
    if (!activeComplaintId) return;
    const newDept = document.getElementById("assign-dept-select").value;

    const found = complaints.find(c => c.complaintId === activeComplaintId);
    if (found) {
        found.assignedDepartment = newDept;
        saveState();
        showToast(`Assigned <strong>${activeComplaintId}</strong> to <strong>${newDept} Department</strong>`);
        openModal(activeComplaintId);
        renderAll();
    }
}

function handleUpdateStatus() {
    if (!activeComplaintId) return;
    const newStatus = document.getElementById("update-status-select").value;

    const found = complaints.find(c => c.complaintId === activeComplaintId);
    if (found) {
        found.status = newStatus;
        saveState();
        showToast(`Updated status of <strong>${activeComplaintId}</strong> to <strong>${newStatus}</strong>`);
        openModal(activeComplaintId);
        renderAll();
    }
}

// Toast Notifications
function showToast(message) {
    const container = document.getElementById("toast-container");
    if (!container) return;

    const toast = document.createElement("div");
    toast.className = "toast";
    toast.innerHTML = `<i class="fa-solid fa-circle-check" style="color:var(--primary); font-size:18px;"></i> <div>${message}</div>`;

    container.appendChild(toast);

    setTimeout(() => {
        toast.style.opacity = "0";
        toast.style.transform = "translateX(50px)";
        toast.style.transition = "all 0.3s ease";
        setTimeout(() => toast.remove(), 300);
    }, 3000);
}

// Helper: Escape HTML
function escapeHtml(str) {
    if (!str) return "";
    return str.replace(/[&<>"']/g, m => ({
        '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#039;'
    })[m]);
}
