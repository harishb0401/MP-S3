// Enterprise Light Theme Municipal Grievance Portal - Application Controller

let currentUser = null;
let activeComplaintId = null;
let currentComplaintsList = [];
let currentPage = 1;
const pageSize = 5;

let isSidebarCollapsed = false;
let currentWizardStep = 1;
let attachedFileName = null;

// Initialize Application
document.addEventListener("DOMContentLoaded", () => {
    checkAuthentication();
    setupFilters();
});

// Auth Mode Tab Switcher
function switchAuthTab(tabId) {
    document.querySelectorAll(".auth-tab-btn").forEach(btn => btn.classList.remove("active"));
    document.querySelectorAll(".auth-form").forEach(form => form.classList.remove("active"));

    if (tabId === 'citizen-login') {
        document.querySelectorAll(".auth-tab-btn")[0].classList.add("active");
        document.getElementById("citizen-login-form").classList.add("active");
    } else if (tabId === 'citizen-register') {
        document.querySelectorAll(".auth-tab-btn")[1].classList.add("active");
        document.getElementById("citizen-register-form").classList.add("active");
    } else if (tabId === 'staff-login') {
        document.querySelectorAll(".auth-tab-btn")[2].classList.add("active");
        document.getElementById("staff-login-form").classList.add("active");
    }
}

// Collapsible Sidebar Toggle
function toggleSidebar() {
    const sidebar = document.getElementById("main-sidebar");
    const icon = document.getElementById("sidebar-toggle-icon");
    isSidebarCollapsed = !isSidebarCollapsed;

    if (isSidebarCollapsed) {
        sidebar.classList.add("collapsed");
        icon.className = "fa-solid fa-chevron-right";
    } else {
        sidebar.classList.remove("collapsed");
        icon.className = "fa-solid fa-chevron-left";
    }
}

// Profile Dropdown Menu Toggle
function toggleProfileMenu() {
    const dropdown = document.getElementById("profile-dropdown-menu");
    if (dropdown) dropdown.classList.toggle("active");
}

document.addEventListener("click", (e) => {
    const trigger = document.querySelector(".profile-trigger");
    const dropdown = document.getElementById("profile-dropdown-menu");
    if (dropdown && trigger && !trigger.contains(e.target) && !dropdown.contains(e.target)) {
        dropdown.classList.remove("active");
    }
});

// Notification Drawer Toggle & Renderer
function toggleNotificationDrawer() {
    const overlay = document.getElementById("noti-drawer-overlay");
    const drawer = document.getElementById("noti-drawer");
    if (!overlay || !drawer) return;

    overlay.classList.toggle("active");
    drawer.classList.toggle("active");

    if (drawer.classList.contains("active")) {
        renderNotificationDrawerItems();
    }
}

async function renderNotificationDrawerItems() {
    const body = document.getElementById("noti-drawer-body");
    const badge = document.getElementById("noti-badge-count");
    if (!body) return;

    try {
        const notis = await ApiClient.getNotifications();
        if (notis && notis.length > 0) {
            if (badge) badge.innerText = notis.length;
            body.innerHTML = notis.map(n => `
                <div class="noti-card success" onclick="toggleNotificationDrawer()">
                    <div class="noti-title"><i class="fa-solid fa-circle-check"></i> ${escapeHtml(n.title)}</div>
                    <div class="noti-desc">${escapeHtml(n.message)}</div>
                    <div class="noti-time"><i class="fa-solid fa-clock"></i> ${escapeHtml(n.createdAt)}</div>
                </div>
            `).join("");
            return;
        }
    } catch (e) {
        // Fallback to static alerts if not authenticated
    }

    const defaultNotis = [
        { title: "🔴 Priority Queue Monitor", desc: "Highest priority civic grievances ordered dynamically.", time: "Live", type: "urgent" },
        { title: "🟡 Department SLA Tracking", desc: "Department capacity limits managed via PriorityQueue waiting buffer.", time: "Live", type: "warning" },
        { title: "🟢 Impact Immutability Guard", desc: "Citizen reported impact values are preserved and locked upon review.", time: "Live", type: "success" }
    ];

    if (badge) badge.innerText = defaultNotis.length;

    body.innerHTML = defaultNotis.map(n => `
        <div class="noti-card ${n.type}" onclick="toggleNotificationDrawer()">
            <div class="noti-title">${n.title}</div>
            <div class="noti-desc">${n.desc}</div>
            <div class="noti-time"><i class="fa-solid fa-clock"></i> ${n.time}</div>
        </div>
    `).join("");
}

// Check session on load
async function checkAuthentication() {
    const token = localStorage.getItem("auth_token");
    if (!token) {
        showAuthScreen();
        return;
    }

    try {
        currentUser = await ApiClient.getMe();
        showAppScreen();
    } catch (e) {
        console.warn("Session expired or invalid:", e.message);
        localStorage.removeItem("auth_token");
        showAuthScreen();
    }
}

function showAuthScreen() {
    document.getElementById("auth-view").style.display = "flex";
    document.getElementById("app-view").style.display = "none";
}

function showAppScreen() {
    document.getElementById("auth-view").style.display = "none";
    document.getElementById("app-view").style.display = "flex";

    // Set User Profile Display
    const name = currentUser.name || "User";
    const initials = name.split(" ").map(n => n[0]).join("").toUpperCase().substring(0, 2);
    
    document.getElementById("user-display-name").innerText = name;
    document.getElementById("user-display-role").innerText = currentUser.role === "STAFF" ? "Municipal Staff" : "Citizen";
    document.getElementById("user-avatar-initials").innerText = initials;
    document.getElementById("header-avatar-initials").innerText = initials;
    document.getElementById("header-user-name").innerText = name;

    // Profile Settings View Inputs
    document.getElementById("setting-name").value = name;
    document.getElementById("setting-email").value = currentUser.email || "";
    document.getElementById("setting-mobile").value = currentUser.mobile || "";
    document.getElementById("setting-role").value = currentUser.role === "STAFF" ? "Municipal Staff / Admin Officer" : "Registered Citizen";

    setupSidebarNav();

    if (currentUser.role === "STAFF") {
        switchView("staff-dashboard");
    } else {
        document.getElementById("citizen-welcome-name").innerText = name;
        switchView("citizen-dashboard");
    }
}

// Setup Role-Based Navigation Sidebar
function setupSidebarNav() {
    const nav = document.getElementById("sidebar-nav");
    if (!nav) return;

    if (currentUser.role === "STAFF") {
        nav.innerHTML = `
            <li><a class="nav-link active" data-view="staff-dashboard" onclick="switchView('staff-dashboard')"><i class="fa-solid fa-chart-pie"></i><span>Dashboard</span></a></li>
            <li><a class="nav-link" data-view="priority-queue" onclick="switchView('priority-queue')"><i class="fa-solid fa-layer-group"></i><span>Priority Queue</span></a></li>
            <li><a class="nav-link" data-view="analytics" onclick="switchView('analytics')"><i class="fa-solid fa-chart-line"></i><span>Analytics & Reports</span></a></li>
            <li><a class="nav-link" data-view="departments" onclick="switchView('departments')"><i class="fa-solid fa-sitemap"></i><span>Departments</span></a></li>
        `;
    } else {
        nav.innerHTML = `
            <li><a class="nav-link active" data-view="citizen-dashboard" onclick="switchView('citizen-dashboard')"><i class="fa-solid fa-house"></i><span>My Dashboard</span></a></li>
            <li><a class="nav-link" data-view="citizen-submit" onclick="switchView('citizen-submit')"><i class="fa-solid fa-file-circle-plus"></i><span>Submit Complaint</span></a></li>
        `;
    }
}

// Navigation View Switcher
function switchView(viewId) {
    document.querySelectorAll(".nav-link").forEach(l => l.classList.remove("active"));
    const activeLink = document.querySelector(`.nav-link[data-view="${viewId}"]`);
    if (activeLink) activeLink.classList.add("active");

    document.querySelectorAll(".page-view").forEach(v => v.classList.remove("active"));
    const targetView = document.getElementById(viewId);
    if (targetView) targetView.classList.add("active");

    // Header Title Update
    updateHeaderTitles(viewId);

    // Refresh Data for active view
    if (viewId === "citizen-dashboard") loadCitizenDashboard();
    if (viewId === "citizen-submit") initWizardForm();
    if (viewId === "staff-dashboard") loadStaffDashboard();
    if (viewId === "priority-queue") loadPriorityQueueView();
    if (viewId === "analytics") loadAnalyticsView();
    if (viewId === "departments") loadDepartmentsView();
}

function updateHeaderTitles(viewId) {
    const title = document.getElementById("header-page-title");
    const sub = document.getElementById("header-page-subtitle");
    if (!title || !sub) return;

    switch (viewId) {
        case "citizen-dashboard":
            title.innerText = "Citizen Portal Overview";
            sub.innerText = "Track active grievances and review past resolution records";
            break;
        case "citizen-submit":
            title.innerText = "Submit New Grievance";
            sub.innerText = "Guided multi-step wizard for filing municipal complaints";
            break;
        case "staff-dashboard":
            title.innerText = "Executive Municipal Dashboard";
            sub.innerText = "Real-time grievance summary and statistics for Makkal Nagar Zone";
            break;
        case "priority-queue":
            title.innerText = "Priority Resolution Queue";
            sub.innerText = "Grievances ordered by Java PriorityQueue algorithm";
            break;
        case "analytics":
            title.innerText = "Analytics & Municipal Intelligence";
            sub.innerText = "Workload distribution, priority ratios, and SLA performance";
            break;
        case "departments":
            title.innerText = "Department Workload Allocation";
            sub.innerText = "Active tasks and staff assignments across municipal departments";
            break;
        default:
            title.innerText = "Municipal Administrative Portal";
            sub.innerText = "Government Grievance Management System";
    }
}

// ------------------- AUTH FORM HANDLERS ------------------- //
async function handleCitizenLogin(e) {
    e.preventDefault();
    const email = document.getElementById("c-login-email").value.trim();
    const password = document.getElementById("c-login-password").value;

    try {
        const res = await ApiClient.login(email, password);
        localStorage.setItem("auth_token", res.token);
        currentUser = res.user;
        showToast("Welcome back, " + currentUser.name + "!");
        showAppScreen();
    } catch (err) {
        showToast("Login failed: " + err.message, "danger");
    }
}

async function handleCitizenRegister(e) {
    e.preventDefault();
    const name = document.getElementById("c-reg-name").value.trim();
    const email = document.getElementById("c-reg-email").value.trim();
    const mobile = document.getElementById("c-reg-mobile").value.trim();
    const password = document.getElementById("c-reg-password").value;
    const confirm = document.getElementById("c-reg-confirm").value;

    if (password !== confirm) {
        showToast("Passwords do not match!", "danger");
        return;
    }

    try {
        const res = await ApiClient.registerCitizen(name, email, mobile, password);
        localStorage.setItem("auth_token", res.token);
        currentUser = res.user;
        showToast("Account created successfully!");
        showAppScreen();
    } catch (err) {
        showToast("Registration failed: " + err.message, "danger");
    }
}

async function handleStaffLogin(e) {
    e.preventDefault();
    const staffId = document.getElementById("s-login-id").value.trim();
    const password = document.getElementById("s-login-password").value;

    try {
        const res = await ApiClient.login(staffId, password);
        localStorage.setItem("auth_token", res.token);
        currentUser = res.user;
        showToast("Staff Portal Access Granted!");
        showAppScreen();
    } catch (err) {
        showToast("Staff Login failed: " + err.message, "danger");
    }
}

function handleLogout() {
    localStorage.removeItem("auth_token");
    currentUser = null;
    showToast("Logged out successfully.");
    showAuthScreen();
}


// ------------------- CITIZEN WORKFLOW & MULTI-STEP WIZARD ------------------- //
function initWizardForm() {
    currentWizardStep = 1;
    const form = document.getElementById("wizard-form");
    if (form) form.reset();
    if (currentUser) {
        if (document.getElementById("w-name")) document.getElementById("w-name").value = currentUser.name || currentUser.displayName || "";
        if (document.getElementById("w-mobile")) document.getElementById("w-mobile").value = currentUser.mobile || "";
        if (document.getElementById("w-email")) document.getElementById("w-email").value = currentUser.email || "";
    }
    attachedFileName = null;
    const previewBox = document.getElementById("file-preview-box");
    if (previewBox) previewBox.innerText = "No file attached.";
    renderWizardStep(1);
}

function nextWizardStep(step) {
    if (step > currentWizardStep) {
        // Validate Step 2 inputs before advancing
        if (currentWizardStep === 2) {
            const cat = document.getElementById("w-category").value;
            const loc = document.getElementById("w-location").value.trim();
            const desc = document.getElementById("w-description").value.trim();
            if (!cat || !loc || !desc) {
                showToast("Please fill in Category, Location, and Description!", "danger");
                return;
            }
        }
    }

    currentWizardStep = step;
    renderWizardStep(step);
}

function renderWizardStep(step) {
    document.querySelectorAll(".wizard-step").forEach((el, index) => {
        el.classList.remove("active", "completed");
        if (index + 1 === step) el.classList.add("active");
        else if (index + 1 < step) el.classList.add("completed");
    });

    document.querySelectorAll(".wizard-pane").forEach(pane => pane.classList.remove("active"));
    const activePane = document.getElementById(`w-pane-${step}`);
    if (activePane) activePane.classList.add("active");

    if (step === 4) {
        document.getElementById("rev-category").innerText = document.getElementById("w-category").value;
        document.getElementById("rev-location").innerText = document.getElementById("w-location").value;
        document.getElementById("rev-description").innerText = document.getElementById("w-description").value;
        document.getElementById("rev-attachment").innerText = attachedFileName || "None Attached";
    }
}

function previewFile(e) {
    const file = e.target.files[0];
    if (file) {
        attachedFileName = file.name;
        document.getElementById("file-preview-box").innerHTML = `
            <span style="color:var(--primary); font-weight:600;"><i class="fa-solid fa-paperclip"></i> Attached: ${file.name}</span> (${Math.round(file.size/1024)} KB)
        `;
    }
}

function updatePriorityHint() {
    const cat = document.getElementById("w-category").value;
    const hint = document.getElementById("priority-hint-text");
    if (!hint) return;

    if (cat === "Water Supply" || cat === "Streetlight") {
        hint.innerHTML = `Category <strong>${cat}</strong> evaluates to starting priority: <span style="color:var(--priority-high); font-weight:700;">HIGH</span>.`;
    } else if (cat === "Waste Management" || cat === "Road Damage" || cat === "Public Sanitation") {
        hint.innerHTML = `Category <strong>${cat}</strong> evaluates to starting priority: <span style="color:var(--priority-medium); font-weight:700;">MEDIUM</span>.`;
    } else {
        hint.innerHTML = `Category <strong>${cat}</strong> evaluates to standard queue priority: <span style="color:var(--priority-low); font-weight:700;">LOW / NORMAL</span>.`;
    }
}

async function handleWizardSubmit(e) {
    e.preventDefault();
    const category = document.getElementById("w-category").value;
    const location = document.getElementById("w-location").value.trim();
    const description = document.getElementById("w-description").value.trim();

    try {
        const created = await ApiClient.submitComplaint(category, location, description);
        showToast(`Complaint <strong>${created.complaintId}</strong> registered successfully! Priority: ${created.priority}`);
        switchView("citizen-dashboard");
    } catch (err) {
        showToast("Failed to submit complaint: " + err.message, "danger");
    }
}

async function loadCitizenDashboard() {
    try {
        const complaints = await ApiClient.getMyComplaints();
        currentComplaintsList = complaints;

        const total = complaints.length;
        const pending = complaints.filter(c => {
            const s = (c.status || "").toUpperCase();
            return s === "REGISTERED" || s === "UNDER REVIEW" || s === "PENDING" || s === "ASSIGNED";
        }).length;
        const progress = complaints.filter(c => {
            const s = (c.status || "").toUpperCase();
            return s === "IN_PROGRESS" || s === "IN PROGRESS";
        }).length;
        const resolved = complaints.filter(c => {
            const s = (c.status || "").toUpperCase();
            return s === "RESOLVED" || s === "CLOSED";
        }).length;

        document.getElementById("c-stat-total").innerText = total;
        document.getElementById("c-stat-pending").innerText = pending;
        document.getElementById("c-stat-progress").innerText = progress;
        document.getElementById("c-stat-resolved").innerText = resolved;

        const tbody = document.getElementById("citizen-complaints-tbody");
        if (complaints.length === 0) {
            tbody.innerHTML = `
                <tr>
                    <td colspan="8">
                        <div class="empty-state">
                            <i class="fa-solid fa-folder-open"></i>
                            <h3>No complaints registered yet</h3>
                            <p>You have not filed any grievances yet. Click "Submit New Complaint" to file one.</p>
                        </div>
                    </td>
                </tr>
            `;
            return;
        }

        tbody.innerHTML = complaints.map(c => `
            <tr>
                <td><strong>${c.complaintId}</strong></td>
                <td>${escapeHtml(c.category)}</td>
                <td>${escapeHtml(c.location)}</td>
                <td>${getPriorityBadge(c.priority)}</td>
                <td>${escapeHtml(c.department)}</td>
                <td>${getStatusBadge(c.status)}</td>
                <td>${c.createdAt || 'Today'}</td>
                <td>
                    <button class="btn btn-secondary btn-sm" onclick="openComplaintModal('${c.complaintId}')">
                        <i class="fa-solid fa-eye"></i> Track Case
                    </button>
                </td>
            </tr>
        `).join("");
    } catch (e) {
        showToast("Error loading citizen complaints: " + e.message, "danger");
    }
}


// ------------------- STAFF WORKFLOW, SEARCH & PAGINATION ------------------- //
async function loadStaffDashboard() {
    try {
        const stats = await ApiClient.getDashboardStats();
        document.getElementById("s-stat-total").innerText = stats.total || 0;
        document.getElementById("s-stat-pending").innerText = stats.pending || 0;
        document.getElementById("s-stat-progress").innerText = stats.inProgress || 0;
        document.getElementById("s-stat-resolved").innerText = stats.resolved || 0;
        document.getElementById("s-stat-critical").innerText = stats.critical || 0;

        const complaints = await ApiClient.getAllComplaints();
        currentComplaintsList = complaints;
        currentPage = 1;
        renderStaffComplaintsTable();
    } catch (e) {
        showToast("Error loading staff dashboard: " + e.message, "danger");
    }
}

function setupFilters() {
    const search = document.getElementById("staff-search");
    const category = document.getElementById("staff-filter-category");
    const priority = document.getElementById("staff-filter-priority");
    const status = document.getElementById("staff-filter-status");

    [search, category, priority, status].forEach(el => {
        if (el) el.addEventListener("input", () => {
            currentPage = 1;
            renderStaffComplaintsTable();
        });
    });
}

function getFilteredStaffComplaints() {
    const query = (document.getElementById("staff-search")?.value || "").toLowerCase().trim();
    const category = document.getElementById("staff-filter-category")?.value || "";
    const priority = document.getElementById("staff-filter-priority")?.value || "";
    const status = document.getElementById("staff-filter-status")?.value || "";

    return currentComplaintsList.filter(c => {
        const matchesQuery = !query || 
            (c.complaintId && c.complaintId.toLowerCase().includes(query)) ||
            (c.citizenName && c.citizenName.toLowerCase().includes(query)) ||
            (c.location && c.location.toLowerCase().includes(query)) ||
            (c.description && c.description.toLowerCase().includes(query));

        const matchesCat = !category || c.category === category;
        const matchesPrio = !priority || c.priority === priority;
        const matchesStat = !status || c.status === status;

        return matchesQuery && matchesCat && matchesPrio && matchesStat;
    });
}

function renderStaffComplaintsTable() {
    const tbody = document.getElementById("staff-complaints-tbody");
    if (!tbody) return;

    const filtered = getFilteredStaffComplaints();
    const totalItems = filtered.length;
    const totalPages = Math.ceil(totalItems / pageSize) || 1;

    if (currentPage > totalPages) currentPage = totalPages;

    const startIdx = (currentPage - 1) * pageSize;
    const paginatedItems = filtered.slice(startIdx, startIdx + pageSize);

    // Update Pagination Controls
    document.getElementById("pagination-info").innerText = `Showing ${totalItems === 0 ? 0 : startIdx + 1} to ${Math.min(startIdx + pageSize, totalItems)} of ${totalItems} complaints`;
    document.getElementById("current-page-num").innerText = `Page ${currentPage} of ${totalPages}`;
    document.getElementById("btn-prev-page").disabled = (currentPage === 1);
    document.getElementById("btn-next-page").disabled = (currentPage === totalPages || totalPages === 0);

    if (paginatedItems.length === 0) {
        tbody.innerHTML = `
            <tr>
                <td colspan="9">
                    <div class="empty-state">
                        <i class="fa-solid fa-inbox"></i>
                        <h3>No complaints registered yet</h3>
                        <p>Complaints submitted by citizens will appear in this master directory.</p>
                    </div>
                </td>
            </tr>
        `;
        return;
    }

    tbody.innerHTML = paginatedItems.map(c => `
        <tr>
            <td><strong>${c.complaintId}</strong></td>
            <td>${escapeHtml(c.citizenName)}</td>
            <td>${escapeHtml(c.category)}</td>
            <td>${escapeHtml(c.location)}</td>
            <td>${getPriorityBadge(c.priority)}</td>
            <td>${escapeHtml(c.department)}</td>
            <td>${getStatusBadge(c.status)}</td>
            <td>${c.createdAt || 'Today'}</td>
            <td>
                <button class="btn btn-secondary btn-sm" onclick="openComplaintModal('${c.complaintId}')">
                    <i class="fa-solid fa-sliders"></i> Manage
                </button>
            </td>
        </tr>
    `).join("");
}

function prevPage() {
    if (currentPage > 1) {
        currentPage--;
        renderStaffComplaintsTable();
    }
}

function nextPage() {
    const filtered = getFilteredStaffComplaints();
    const totalPages = Math.ceil(filtered.length / pageSize) || 1;
    if (currentPage < totalPages) {
        currentPage++;
        renderStaffComplaintsTable();
    }
}


// Priority Queue View
async function loadPriorityQueueView() {
    try {
        const queueList = await ApiClient.getPriorityQueue();
        const container = document.getElementById("priority-queue-container");
        if (!container) return;

        if (queueList.length === 0) {
            container.innerHTML = `
                <div class="empty-state">
                    <i class="fa-solid fa-circle-check" style="color: var(--status-resolved);"></i>
                    <h3>Priority Resolution Queue is Empty!</h3>
                    <p>All active citizen grievances have been processed and resolved.</p>
                </div>
            `;
            return;
        }

        container.innerHTML = queueList.map((c, index) => `
            <div class="pq-item ${c.priority.toLowerCase()}">
                <div class="pq-rank">#${index + 1}</div>
                <div class="pq-details">
                    <div style="display:flex; align-items:center; gap:10px; margin-bottom:4px;">
                        <strong>${c.complaintId}</strong> - ${escapeHtml(c.category)}
                        ${getPriorityBadge(c.priority)}
                        ${getStatusBadge(c.status)}
                    </div>
                    <div style="font-size:13px; color:var(--text-muted); display:flex; gap:16px;">
                        <span><i class="fa-solid fa-location-dot"></i> ${escapeHtml(c.location)}</span>
                        <span><i class="fa-solid fa-user"></i> ${escapeHtml(c.citizenName)}</span>
                        <span><i class="fa-solid fa-building"></i> ${escapeHtml(c.department)}</span>
                    </div>
                </div>
                ${currentUser.role === "STAFF" ? `
                    <button class="btn btn-secondary btn-sm" onclick="openComplaintModal('${c.complaintId}')">
                        <i class="fa-solid fa-sliders"></i> Process
                    </button>
                ` : ''}
            </div>
        `).join("");
    } catch (e) {
        showToast("Error loading Priority Queue: " + e.message, "danger");
    }
}


// ------------------- ANALYTICS DASHBOARD ------------------- //
async function loadAnalyticsView() {
    try {
        const complaints = await ApiClient.getAllComplaints();
        
        // Render Category Bar Chart
        const categories = ["Streetlight", "Waste Management", "Road Damage", "Water Supply", "Drainage", "Public Sanitation", "Other"];
        renderBarChart("chart-category-container", categories, complaints, c => c.category, "#2563eb");

        // Render Department Workload Chart
        const departments = ["Electrical", "Sanitation", "Roads", "Water Supply", "Drainage", "General Administration"];
        renderBarChart("chart-department-container", departments, complaints, c => c.department, "#7c3aed");

        // Render Priority Breakdown
        const priorities = ["CRITICAL", "HIGH", "MEDIUM", "LOW"];
        renderBarChart("chart-priority-container", priorities, complaints, c => c.priority, "#dc2626");

        // Render Status Ratio
        const statuses = ["PENDING", "ASSIGNED", "IN_PROGRESS", "RESOLVED", "CLOSED"];
        renderBarChart("chart-status-container", statuses, complaints, c => c.status, "#059669");

    } catch (e) {
        showToast("Error loading analytics: " + e.message, "danger");
    }
}

function renderBarChart(containerId, keys, data, keyExtractor, barColor) {
    const container = document.getElementById(containerId);
    if (!container) return;

    const counts = {};
    keys.forEach(k => counts[k] = 0);
    let maxCount = 1;

    data.forEach(item => {
        const val = keyExtractor(item);
        if (counts[val] !== undefined) {
            counts[val]++;
            if (counts[val] > maxCount) maxCount = counts[val];
        }
    });

    container.innerHTML = keys.map(k => {
        const count = counts[k] || 0;
        const pct = Math.round((count / maxCount) * 100);
        return `
            <div class="bar-item">
                <div class="bar-label-row">
                    <span>${k}</span>
                    <span>${count} cases</span>
                </div>
                <div class="bar-track">
                    <div class="bar-fill" style="width: ${pct}%; background: ${barColor};"></div>
                </div>
            </div>
        `;
    }).join("");
}


// ------------------- DEPARTMENT DASHBOARDS ------------------- //
async function loadDepartmentsView() {
    try {
        const complaints = await ApiClient.getAllComplaints();
        const grid = document.getElementById("departments-grid");
        if (!grid) return;

        const depts = ["Electrical", "Sanitation", "Roads", "Water Supply", "Drainage", "General Administration"];

        grid.innerHTML = depts.map(d => {
            const deptComplaints = complaints.filter(c => (c.department || "").equalsIgnoreCase ? c.department.equalsIgnoreCase(d) : c.department === d);
            const total = deptComplaints.length;
            const pending = deptComplaints.filter(c => c.status === "PENDING" || c.status === "ASSIGNED").length;
            const progress = deptComplaints.filter(c => c.status === "IN_PROGRESS").length;

            return `
                <div class="stat-card" style="flex-direction:column; align-items:flex-start; gap:12px;">
                    <div style="display:flex; justify-content:space-between; width:100%; align-items:center;">
                        <h3 style="font-size:16px; font-weight:700; color:var(--primary);">${d} Dept</h3>
                        <span class="badge badge-progress">${total} Total</span>
                    </div>
                    <div style="font-size:13px; color:var(--text-muted); width:100%;">
                        <p style="margin-bottom:4px;"><i class="fa-solid fa-clock" style="color:var(--status-pending);"></i> Pending Tasks: <strong>${pending}</strong></p>
                        <p><i class="fa-solid fa-gears" style="color:var(--status-progress);"></i> Active In Progress: <strong>${progress}</strong></p>
                    </div>
                </div>
            `;
        }).join("");
    } catch (e) {
        showToast("Error loading departments: " + e.message, "danger");
    }
}


// ------------------- MODAL & TRACKING STEP TIMELINE ------------------- //
async function openComplaintModal(complaintId) {
    try {
        const c = await ApiClient.getComplaintById(complaintId);
        activeComplaintId = complaintId;

        document.getElementById("m-comp-id").innerText = c.complaintId;
        document.getElementById("m-citizen-name").innerText = c.citizenName;
        document.getElementById("m-citizen-mobile").innerText = c.citizenMobile;
        document.getElementById("m-category").innerText = c.category;
        document.getElementById("m-priority").innerHTML = getPriorityBadge(c.priority);
        document.getElementById("m-location").innerText = c.location;
        let desc = c.description || "";
        if (c.citizenReportedImpact) {
            desc += `\n\n[Citizen Reported Impact: ${c.citizenReportedImpact} affected]`;
        }
        if (c.adminVerifiedImpact) {
            desc += `\n[Admin Verified Impact: ${c.adminVerifiedImpact} affected | Verification Reason: "${c.verificationReason || 'Confirmed by field inspection'}"]`;
        }
        if (c.isDuplicate) {
            desc += `\n[Duplicate Case - Linked Primary: ${c.duplicateOfId || 'Master'}]`;
        }
        document.getElementById("m-description").innerText = desc;
        document.getElementById("m-department").innerText = c.department || "Unassigned";
        document.getElementById("m-assigned-staff").innerText = c.assignedStaff || "Unassigned";
        
        const notesBox = document.getElementById("m-resolution-notes");
        if (c.resolutionNotes && c.resolutionNotes.trim()) {
            notesBox.innerText = c.resolutionNotes;
            notesBox.style.color = "var(--status-resolved)";
        } else {
            notesBox.innerText = "No resolution description notes recorded yet.";
            notesBox.style.color = "var(--text-muted)";
        }

        // Render Step Timeline Graphic
        renderTrackingTimeline(c.status);

        // Staff Controls Box
        const staffBox = document.getElementById("staff-controls-box");
        if (currentUser.role === "STAFF") {
            staffBox.style.display = "flex";
            document.getElementById("s-assign-dept-select").value = c.department || "Electrical";
            document.getElementById("s-assign-staff-input").value = c.assignedStaff === "Unassigned" ? "" : (c.assignedStaff || "");
            document.getElementById("s-update-status-select").value = c.status || "PENDING";
            document.getElementById("s-resolution-input").value = c.resolutionNotes || "";
        } else {
            staffBox.style.display = "none";
        }

        document.getElementById("complaint-modal").classList.add("active");
    } catch (e) {
        showToast("Error loading case file: " + e.message, "danger");
    }
}

function renderTrackingTimeline(status) {
    const s = (status || "").toUpperCase();

    const stepSubmitted = document.getElementById("step-submitted");
    const stepAssigned = document.getElementById("step-assigned");
    const stepProgress = document.getElementById("step-progress");
    const stepResolved = document.getElementById("step-resolved");

    [stepSubmitted, stepAssigned, stepProgress, stepResolved].forEach(el => {
        el.className = "timeline-step";
    });

    stepSubmitted.classList.add("completed");

    if (s === "ASSIGNED") {
        stepAssigned.classList.add("active");
    } else if (s === "IN_PROGRESS") {
        stepAssigned.classList.add("completed");
        stepProgress.classList.add("active");
    } else if (s === "RESOLVED" || s === "CLOSED") {
        stepAssigned.classList.add("completed");
        stepProgress.classList.add("completed");
        stepResolved.classList.add("completed");
    }
}

function closeModal() {
    document.getElementById("complaint-modal").classList.remove("active");
    activeComplaintId = null;
}

// Staff Actions
async function handleStaffAssign() {
    if (!activeComplaintId) return;
    const dept = document.getElementById("s-assign-dept-select").value;
    const staffName = document.getElementById("s-assign-staff-input").value.trim();

    try {
        await ApiClient.assignDepartment(activeComplaintId, dept, staffName);
        showToast(`Assigned <strong>${activeComplaintId}</strong> to ${dept} Dept.`);
        openComplaintModal(activeComplaintId);
        refreshCurrentView();
    } catch (e) {
        showToast("Failed to assign department: " + e.message, "danger");
    }
}

async function handleStaffUpdateStatus() {
    if (!activeComplaintId) return;
    const newStatus = document.getElementById("s-update-status-select").value;

    try {
        await ApiClient.updateStatus(activeComplaintId, newStatus);
        showToast(`Status updated for <strong>${activeComplaintId}</strong> to ${newStatus}`);
        openComplaintModal(activeComplaintId);
        refreshCurrentView();
    } catch (e) {
        showToast("Failed to update status: " + e.message, "danger");
    }
}

async function handleStaffResolve() {
    if (!activeComplaintId) return;
    const notes = document.getElementById("s-resolution-input").value.trim();
    if (!notes) {
        showToast("Please provide resolution description notes!", "danger");
        return;
    }

    try {
        await ApiClient.resolveComplaint(activeComplaintId, notes);
        showToast(`Complaint <strong>${activeComplaintId}</strong> marked RESOLVED!`);
        openComplaintModal(activeComplaintId);
        refreshCurrentView();
    } catch (e) {
        showToast("Failed to resolve complaint: " + e.message, "danger");
    }
}

function refreshCurrentView() {
    if (currentUser.role === "STAFF") loadStaffDashboard();
    else loadCitizenDashboard();
}

function showHelpModal() {
    showToast("Need assistance? Contact Municipal Office Helpline: 1800-425-1001", "success");
}

// Accessible Badge Generators (Icon + Text)
function getPriorityBadge(priority) {
    const p = (priority || "").toUpperCase();
    let pClass = "low";
    let icon = "fa-circle-down";

    if (p === "CRITICAL") { pClass = "critical"; icon = "fa-circle-radiation"; }
    if (p === "HIGH") { pClass = "high"; icon = "fa-triangle-exclamation"; }
    if (p === "MEDIUM") { pClass = "medium"; icon = "fa-circle-exclamation"; }

    return `<span class="badge badge-${pClass}"><i class="fa-solid ${icon}"></i> ${priority}</span>`;
}

function getStatusBadge(status) {
    const s = (status || "").toUpperCase();
    let sClass = "pending";
    let icon = "fa-clock";

    if (s === "ASSIGNED") { sClass = "assigned"; icon = "fa-user-check"; }
    if (s === "IN_PROGRESS") { sClass = "progress"; icon = "fa-gears"; }
    if (s === "RESOLVED") { sClass = "resolved"; icon = "fa-circle-check"; }
    if (s === "CLOSED") { sClass = "closed"; icon = "fa-circle-xmark"; }

    return `<span class="badge badge-${sClass}"><i class="fa-solid ${icon}"></i> ${status}</span>`;
}

function showToast(message, type = "success") {
    const container = document.getElementById("toast-container");
    if (!container) return;

    const toast = document.createElement("div");
    toast.className = "toast";
    if (type === "danger") {
        toast.style.borderLeftColor = "#dc2626";
        toast.style.borderColor = "#fecaca";
    }

    toast.innerHTML = `<i class="fa-solid fa-circle-info" style="color:var(--primary); font-size:18px;"></i> <div>${message}</div>`;
    container.appendChild(toast);

    setTimeout(() => {
        toast.style.opacity = "0";
        toast.style.transform = "translateX(50px)";
        toast.style.transition = "all 0.3s ease";
        setTimeout(() => toast.remove(), 300);
    }, 3500);
}

function escapeHtml(str) {
    if (!str) return "";
    return str.replace(/[&<>"']/g, m => ({
        '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#039;'
    })[m]);
}
