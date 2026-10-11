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

// Auth Mode Tab Switcher for 4 Portals + Sign Up
function switchAuthTab(tabId) {
    document.querySelectorAll(".auth-tab-btn").forEach(btn => btn.classList.remove("active"));
    document.querySelectorAll(".auth-form").forEach(form => form.classList.remove("active"));

    if (tabId === 'citizen-login') {
        const btn = document.getElementById("tab-btn-citizen");
        if (btn) btn.classList.add("active");
        const form = document.getElementById("citizen-login-form");
        if (form) form.classList.add("active");
    } else if (tabId === 'worker-login') {
        const btn = document.getElementById("tab-btn-worker");
        if (btn) btn.classList.add("active");
        const form = document.getElementById("worker-login-form");
        if (form) form.classList.add("active");
    } else if (tabId === 'manager-login') {
        const btn = document.getElementById("tab-btn-manager");
        if (btn) btn.classList.add("active");
        const form = document.getElementById("manager-login-form");
        if (form) form.classList.add("active");
    } else if (tabId === 'admin-login') {
        const btn = document.getElementById("tab-btn-admin");
        if (btn) btn.classList.add("active");
        const form = document.getElementById("admin-login-form");
        if (form) form.classList.add("active");
    } else if (tabId === 'citizen-register') {
        const btn = document.getElementById("tab-btn-register");
        if (btn) btn.classList.add("active");
        const form = document.getElementById("citizen-register-form");
        if (form) form.classList.add("active");
    }
}

function fillWorkerCreds(email) {
    const input = document.getElementById("w-login-id");
    if (input) input.value = email;
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
    const role = (currentUser.role || "").toUpperCase();

    let roleDisplay = "Registered Citizen";
    if (role === "ADMIN") roleDisplay = "System Administrator";
    else if (role === "MANAGER") roleDisplay = "Municipal Manager";
    else if (role === "WORKER" || role === "STAFF") roleDisplay = "Field Worker - " + (currentUser.department || "Operations");

    document.getElementById("user-display-name").innerText = name;
    document.getElementById("user-display-role").innerText = roleDisplay;
    document.getElementById("user-avatar-initials").innerText = initials;
    document.getElementById("header-avatar-initials").innerText = initials;
    document.getElementById("header-user-name").innerText = name;

    // Profile Settings View Inputs
    if (document.getElementById("setting-name")) document.getElementById("setting-name").value = name;
    if (document.getElementById("setting-email")) document.getElementById("setting-email").value = currentUser.email || "";
    if (document.getElementById("setting-mobile")) document.getElementById("setting-mobile").value = currentUser.mobile || "";
    if (document.getElementById("setting-role")) document.getElementById("setting-role").value = roleDisplay;

    setupSidebarNav();

    // Four Role Redirections
    if (role === "ADMIN") {
        switchView("admin-dashboard");
    } else if (role === "MANAGER") {
        switchView("manager-dashboard");
    } else if (role === "WORKER" || role === "STAFF") {
        switchView("worker-dashboard");
    } else {
        if (document.getElementById("citizen-welcome-name")) {
            document.getElementById("citizen-welcome-name").innerText = name;
        }
        switchView("citizen-dashboard");
    }
}

// Setup Role-Based Navigation Sidebar
function setupSidebarNav() {
    const nav = document.getElementById("sidebar-nav");
    if (!nav) return;

    const role = (currentUser.role || "").toUpperCase();

    if (role === "ADMIN") {
        nav.innerHTML = `
            <li><a class="nav-link active" data-view="admin-dashboard" onclick="switchView('admin-dashboard')"><i class="fa-solid fa-shield-halved"></i><span>Admin & Workers</span></a></li>
            <li><a class="nav-link" data-view="staff-dashboard" onclick="switchView('staff-dashboard')"><i class="fa-solid fa-list-check"></i><span>All Complaints</span></a></li>
            <li><a class="nav-link" data-view="priority-queue" onclick="switchView('priority-queue')"><i class="fa-solid fa-layer-group"></i><span>Priority Queue</span></a></li>
            <li><a class="nav-link" data-view="analytics" onclick="switchView('analytics')"><i class="fa-solid fa-chart-line"></i><span>Analytics & Reports</span></a></li>
            <li><a class="nav-link" data-view="departments" onclick="switchView('departments')"><i class="fa-solid fa-sitemap"></i><span>Departments</span></a></li>
            <li><a class="nav-link" data-view="profile-settings" onclick="switchView('profile-settings')"><i class="fa-solid fa-user-gear"></i><span>My Profile</span></a></li>
        `;
    } else if (role === "MANAGER") {
        nav.innerHTML = `
            <li><a class="nav-link active" data-view="manager-dashboard" onclick="switchView('manager-dashboard')"><i class="fa-solid fa-inbox"></i><span>Triage & Review</span></a></li>
            <li><a class="nav-link" data-view="staff-dashboard" onclick="switchView('staff-dashboard')"><i class="fa-solid fa-list-check"></i><span>All Complaints</span></a></li>
            <li><a class="nav-link" data-view="priority-queue" onclick="switchView('priority-queue')"><i class="fa-solid fa-layer-group"></i><span>Priority Queue</span></a></li>
            <li><a class="nav-link" data-view="analytics" onclick="switchView('analytics')"><i class="fa-solid fa-chart-line"></i><span>Reports & Stats</span></a></li>
            <li><a class="nav-link" data-view="departments" onclick="switchView('departments')"><i class="fa-solid fa-sitemap"></i><span>Department Loads</span></a></li>
            <li><a class="nav-link" data-view="profile-settings" onclick="switchView('profile-settings')"><i class="fa-solid fa-user-gear"></i><span>My Profile</span></a></li>
        `;
    } else if (role === "WORKER" || role === "STAFF") {
        nav.innerHTML = `
            <li><a class="nav-link active" data-view="worker-dashboard" onclick="switchView('worker-dashboard')"><i class="fa-solid fa-helmet-safety"></i><span>My Department Tasks</span></a></li>
            <li><a class="nav-link" data-view="profile-settings" onclick="switchView('profile-settings')"><i class="fa-solid fa-user-gear"></i><span>My Profile</span></a></li>
        `;
    } else {
        nav.innerHTML = `
            <li><a class="nav-link active" data-view="citizen-dashboard" onclick="switchView('citizen-dashboard')"><i class="fa-solid fa-house"></i><span>My Complaints</span></a></li>
            <li><a class="nav-link" data-view="citizen-submit" onclick="switchView('citizen-submit')"><i class="fa-solid fa-file-circle-plus"></i><span>Submit Grievance</span></a></li>
            <li><a class="nav-link" data-view="profile-settings" onclick="switchView('profile-settings')"><i class="fa-solid fa-user-gear"></i><span>My Profile</span></a></li>
        `;
    }
}

// Navigation View Switcher with Role Enforcement
function switchView(viewId) {
    const role = (currentUser ? currentUser.role || "" : "").toUpperCase();

    // Frontend RBAC Protection
    if (role === "CITIZEN") {
        const allowedCitizenViews = ["citizen-dashboard", "citizen-submit", "profile-settings"];
        if (!allowedCitizenViews.includes(viewId)) {
            showToast("Access Denied: Citizen portal accounts cannot access staff consoles.", "danger");
            viewId = "citizen-dashboard";
        }
    } else if (role === "WORKER" || role === "STAFF") {
        const allowedWorkerViews = ["worker-dashboard", "profile-settings"];
        if (!allowedWorkerViews.includes(viewId)) {
            showToast("Access Denied: Field Workers are restricted to their authorized department console.", "danger");
            viewId = "worker-dashboard";
        }
    } else if (role === "MANAGER") {
        if (viewId === "admin-dashboard") {
            showToast("Access Denied: Worker management is reserved for Administrator role.", "danger");
            viewId = "manager-dashboard";
        }
    }

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
    if (viewId === "worker-dashboard") loadWorkerDashboard();
    if (viewId === "manager-dashboard") loadManagerDashboard();
    if (viewId === "admin-dashboard") loadAdminDashboard();
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
        case "worker-dashboard":
            title.innerText = "Department Worker Operational Console";
            sub.innerText = "Process and resolve grievances assigned to your authorized municipal department";
            break;
        case "manager-dashboard":
            title.innerText = "Municipal Grievance Triage & Routing Console";
            sub.innerText = "Review submissions, assign core departments, manage capacity and confirm duplicates";
            break;
        case "admin-dashboard":
            title.innerText = "Municipal Governance & Administration Portal";
            sub.innerText = "Department worker management, capacity configuration and global system oversight";
            break;
        case "staff-dashboard":
            title.innerText = "Master Grievances Register";
            sub.innerText = "Comprehensive searchable directory of all municipal grievances";
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
            sub.innerText = "Active tasks and capacity across municipal departments";
            break;
        default:
            title.innerText = "Makkal Nagar Municipal Portal";
            sub.innerText = "Government Grievance Management System";
    }
}

// ------------------- AUTH FORM HANDLERS (4 PORTALS) ------------------- //
async function handleCitizenLogin(e) {
    e.preventDefault();
    const email = document.getElementById("c-login-email").value.trim();
    const password = document.getElementById("c-login-password").value;

    try {
        const res = await ApiClient.login(email, password);
        localStorage.setItem("auth_token", res.token);
        currentUser = res.user;
        showToast("Welcome to Citizen Portal, " + currentUser.name + "!");
        showAppScreen();
    } catch (err) {
        showToast("Citizen Login failed: " + err.message, "danger");
    }
}

async function handleWorkerLogin(e) {
    e.preventDefault();
    const workerId = document.getElementById("w-login-id").value.trim();
    const password = document.getElementById("w-login-password").value;

    try {
        const res = await ApiClient.login(workerId, password);
        localStorage.setItem("auth_token", res.token);
        currentUser = res.user;
        showToast("Worker Portal Access Granted (" + (currentUser.department || "Operations") + ")");
        showAppScreen();
    } catch (err) {
        showToast("Worker Login failed: " + err.message, "danger");
    }
}

async function handleManagerLogin(e) {
    e.preventDefault();
    const managerId = document.getElementById("m-login-id").value.trim();
    const password = document.getElementById("m-login-password").value;

    try {
        const res = await ApiClient.login(managerId, password);
        localStorage.setItem("auth_token", res.token);
        currentUser = res.user;
        showToast("Manager Triage Console Access Granted!");
        showAppScreen();
    } catch (err) {
        showToast("Manager Login failed: " + err.message, "danger");
    }
}

async function handleAdminLogin(e) {
    e.preventDefault();
    const adminId = document.getElementById("a-login-id").value.trim();
    const password = document.getElementById("a-login-password").value;

    try {
        const res = await ApiClient.login(adminId, password);
        localStorage.setItem("auth_token", res.token);
        currentUser = res.user;
        showToast("Administrator Console Access Granted!");
        showAppScreen();
    } catch (err) {
        showToast("Admin Login failed: " + err.message, "danger");
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
        showToast("Citizen account created successfully!");
        showAppScreen();
    } catch (err) {
        showToast("Registration failed: " + err.message, "danger");
    }
}

async function handleLogout() {
    await ApiClient.logout();
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
        const lat = document.getElementById("w-latitude") ? document.getElementById("w-latitude").value : "";
        const lng = document.getElementById("w-longitude") ? document.getElementById("w-longitude").value : "";
        const acc = document.getElementById("w-accuracy") ? document.getElementById("w-accuracy").value : "";
        const gpsSpan = document.getElementById("rev-gps");
        if (gpsSpan) {
            if (lat && lng) {
                gpsSpan.innerHTML = `
                    <span style="color:#059669; font-weight:600;"><i class="fa-solid fa-location-crosshairs"></i> Lat: ${lat}, Lng: ${lng}</span>
                    ${acc ? `<span style="font-size:12px; color:var(--text-muted);">(&plusmn;${Math.round(acc)}m)</span>` : ''} &bull;
                    <a href="https://www.google.com/maps?q=${lat},${lng}" target="_blank" rel="noopener" style="color:var(--primary); text-decoration:underline;">View on Google Maps</a>
                `;
            } else {
                gpsSpan.innerText = "None (Municipal Area only)";
            }
        }
        if (document.getElementById("rev-affected")) {
            document.getElementById("rev-affected").innerText = `${document.getElementById("w-affected").value} citizens`;
        }
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

// ------------------- CITIZEN GEOLOCATION CAPTURE -------------------
function captureCitizenGpsLocation() {
    const feedback = document.getElementById("gps-feedback-box");
    const btn = document.getElementById("btn-capture-gps");
    if (!feedback) return;

    if (!navigator.geolocation) {
        feedback.style.display = "block";
        feedback.style.background = "#fee2e2";
        feedback.style.border = "1px solid #f87171";
        feedback.style.color = "#991b1b";
        feedback.innerHTML = `<i class="fa-solid fa-circle-exclamation"></i> Geolocation is not supported by your browser. Please select your municipal area manually.`;
        return;
    }

    feedback.style.display = "block";
    feedback.style.background = "var(--primary-light)";
    feedback.style.border = "1px solid var(--primary-border)";
    feedback.style.color = "var(--primary)";
    feedback.innerHTML = `<i class="fa-solid fa-spinner fa-spin"></i> Requesting GPS permission and acquiring coordinates...`;
    if (btn) btn.disabled = true;

    navigator.geolocation.getCurrentPosition(
        (position) => {
            if (btn) btn.disabled = false;
            const lat = Number(position.coords.latitude.toFixed(6));
            const lng = Number(position.coords.longitude.toFixed(6));
            const acc = position.coords.accuracy ? Math.round(position.coords.accuracy) : null;

            if (document.getElementById("w-latitude")) document.getElementById("w-latitude").value = lat;
            if (document.getElementById("w-longitude")) document.getElementById("w-longitude").value = lng;
            if (document.getElementById("w-accuracy")) document.getElementById("w-accuracy").value = acc || "";

            feedback.style.background = "#ecfdf5";
            feedback.style.border = "1px solid #a7f3d0";
            feedback.style.color = "#065f46";
            feedback.innerHTML = `
                <div style="display:flex; justify-content:space-between; align-items:center; flex-wrap:wrap; gap:8px;">
                    <div>
                        <strong><i class="fa-solid fa-circle-check"></i> GPS Coordinates Captured:</strong>
                        <span>Latitude: <strong>${lat}</strong>, Longitude: <strong>${lng}</strong></span>
                        ${acc ? `<span style="font-size:12px; color:#047857; margin-left:6px;">(Accuracy: &plusmn;${acc} meters)</span>` : ''}
                    </div>
                    <div style="display:flex; gap:8px; align-items:center;">
                        <a href="https://www.google.com/maps?q=${lat},${lng}" target="_blank" rel="noopener" class="btn btn-sm" style="background:#059669; color:#fff; text-decoration:none; padding:4px 10px; font-size:12px;">
                            <i class="fa-solid fa-map-location-dot"></i> View on Google Maps
                        </a>
                        <button type="button" class="btn btn-sm btn-secondary" onclick="captureCitizenGpsLocation()" style="padding:4px 10px; font-size:12px;" title="Retry or Refresh GPS">
                            <i class="fa-solid fa-rotate-right"></i> Refresh
                        </button>
                        <button type="button" class="btn btn-sm btn-secondary" onclick="clearCitizenGpsLocation()" style="padding:4px 10px; font-size:12px;" title="Clear coordinates">
                            <i class="fa-solid fa-xmark"></i> Clear
                        </button>
                    </div>
                </div>
            `;
        },
        (error) => {
            if (btn) btn.disabled = false;
            clearCitizenGpsLocationFields();
            feedback.style.background = "#fffbeb";
            feedback.style.border = "1px solid #fde68a";
            feedback.style.color = "#92400e";
            let msg = "Could not obtain GPS location.";
            if (error.code === error.PERMISSION_DENIED) {
                msg = "Location permission denied by user. You can continue by selecting your municipal area from the dropdown.";
            } else if (error.code === error.POSITION_UNAVAILABLE) {
                msg = "Device location unavailable. Please select your municipal area manually.";
            } else if (error.code === error.TIMEOUT) {
                msg = "GPS request timed out. Please retry or select your municipal area manually.";
            }
            feedback.innerHTML = `
                <div style="display:flex; justify-content:space-between; align-items:center; flex-wrap:wrap; gap:8px;">
                    <span><i class="fa-solid fa-triangle-exclamation"></i> ${msg}</span>
                    <button type="button" class="btn btn-sm btn-secondary" onclick="captureCitizenGpsLocation()" style="padding:4px 8px; font-size:11px;">
                        <i class="fa-solid fa-rotate-right"></i> Retry
                    </button>
                </div>
            `;
        },
        {
            enableHighAccuracy: true,
            timeout: 10000,
            maximumAge: 0
        }
    );
}

function clearCitizenGpsLocation() {
    clearCitizenGpsLocationFields();
    const feedback = document.getElementById("gps-feedback-box");
    if (feedback) {
        feedback.style.display = "none";
        feedback.innerHTML = "";
    }
}

function clearCitizenGpsLocationFields() {
    if (document.getElementById("w-latitude")) document.getElementById("w-latitude").value = "";
    if (document.getElementById("w-longitude")) document.getElementById("w-longitude").value = "";
    if (document.getElementById("w-accuracy")) document.getElementById("w-accuracy").value = "";
}

function updatePriorityHint() {
    // Retired: Automatic priority classification is completely removed from citizen portal.
}

async function handleWizardSubmit(e) {
    e.preventDefault();
    const title = document.getElementById("w-title") ? document.getElementById("w-title").value.trim() : "";
    const category = document.getElementById("w-category").value;
    const location = document.getElementById("w-location").value.trim();
    const affected = document.getElementById("w-affected") ? parseInt(document.getElementById("w-affected").value) : 1;
    const description = document.getElementById("w-description").value.trim();

    const latStr = document.getElementById("w-latitude") ? document.getElementById("w-latitude").value : "";
    const lngStr = document.getElementById("w-longitude") ? document.getElementById("w-longitude").value : "";
    const accStr = document.getElementById("w-accuracy") ? document.getElementById("w-accuracy").value : "";

    const lat = latStr ? parseFloat(latStr) : null;
    const lng = lngStr ? parseFloat(lngStr) : null;
    const acc = accStr ? parseFloat(accStr) : null;

    try {
        const created = await ApiClient.submitComplaint(category, location, description, affected, title, lat, lng, acc);
        showToast(`Complaint <strong>${created.complaintId}</strong> registered successfully! Submitted for manager risk assessment.`);
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
                <td>${escapeHtml(c.department || 'Awaiting Routing')}</td>
                <td>${getStatusBadge(c.status, c.isWaiting)}</td>
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

// ------------------- DEPARTMENT WORKER PORTAL LOGIC ------------------- //
async function loadWorkerDashboard() {
    if (!currentUser) return;
    const dept = currentUser.department || "Water Supply";
    const badge = document.getElementById("worker-dept-badge");
    if (badge) badge.innerHTML = `<i class="fa-solid fa-lock"></i> ${escapeHtml(dept)} Department`;
    const nameDisplay = document.getElementById("worker-name-display");
    if (nameDisplay) nameDisplay.innerText = currentUser.name || "Worker";

    try {
        const complaints = await ApiClient.getWorkerComplaints();
        const active = complaints.filter(c => (c.status || "").toUpperCase() !== "RESOLVED").length;
        const inProgress = complaints.filter(c => (c.status || "").toUpperCase() === "INPROGRESS" || (c.status || "").toUpperCase() === "IN_PROGRESS").length;
        const resolved = complaints.filter(c => (c.status || "").toUpperCase() === "RESOLVED").length;

        if (document.getElementById("w-stat-assigned")) document.getElementById("w-stat-assigned").innerText = active;
        if (document.getElementById("w-stat-progress")) document.getElementById("w-stat-progress").innerText = inProgress;
        if (document.getElementById("w-stat-resolved")) document.getElementById("w-stat-resolved").innerText = resolved;

        // Fetch department capacity
        try {
            const depts = await ApiClient.getDepartmentCapacity();
            const myDept = depts.find(d => (d.departmentName || "").toLowerCase().includes(dept.toLowerCase()) || (d.departmentId || "").toLowerCase().includes(dept.toLowerCase()));
            if (myDept && document.getElementById("w-stat-capacity")) {
                document.getElementById("w-stat-capacity").innerText = `${myDept.currentLoad} / ${myDept.capacity}`;
            }
        } catch (ignored) {}

        const tbody = document.getElementById("worker-complaints-tbody");
        if (!tbody) return;

        if (complaints.length === 0) {
            tbody.innerHTML = `
                <tr>
                    <td colspan="7">
                        <div class="empty-state">
                            <i class="fa-solid fa-check-double"></i>
                            <h3>No Active Department Complaints</h3>
                            <p>All grievances for ${escapeHtml(dept)} Department are currently clear or resolved.</p>
                        </div>
                    </td>
                </tr>
            `;
            return;
        }

        tbody.innerHTML = complaints.map(c => {
            const statusUpper = (c.status || "").toUpperCase().replace(/[\s_-]/g, "");
            let actionBtn = "";
            if (statusUpper === "ASSIGNED") {
                actionBtn = `<button class="btn btn-primary btn-sm" onclick="handleWorkerStartWork('${c.complaintId}')"><i class="fa-solid fa-play"></i> Start Work</button>`;
            } else if (statusUpper === "INPROGRESS") {
                actionBtn = `<button class="btn btn-primary btn-sm" style="background:#059669;" onclick="openResolveModal('${c.complaintId}')"><i class="fa-solid fa-check"></i> Mark Resolved</button>`;
            } else {
                actionBtn = `<span class="badge badge-resolved"><i class="fa-solid fa-circle-check"></i> Work Completed</span>`;
            }

                let locHtml = `<strong>${escapeHtml(c.location)}</strong>`;
                if (c.latitude && c.longitude) {
                    locHtml += `<br><span style="font-size:11px; color:#059669;"><i class="fa-solid fa-location-crosshairs"></i> GPS: ${c.latitude}, ${c.longitude}</span> &bull; <a href="https://www.google.com/maps?q=${c.latitude},${c.longitude}" target="_blank" rel="noopener" style="font-size:11px; color:var(--primary); text-decoration:underline;"><i class="fa-solid fa-map-location-dot"></i> View on Google Maps</a>`;
                }
                return `
                <tr>
                    <td><strong>${c.complaintId}</strong></td>
                    <td>${escapeHtml(c.category)}<br><small style="color:var(--text-muted);">${locHtml}</small></td>
                    <td>${getPriorityBadge(c.priority)}<br><small style="color:var(--text-muted);">${c.citizenReportedImpact || c.affectedPeople || 1} people affected</small></td>
                    <td>${escapeHtml(c.citizenName || 'Citizen')}</td>
                    <td>${getStatusBadge(c.status, c.isWaiting)}</td>
                    <td>${c.createdAt || 'Today'}</td>
                    <td>
                        <div class="table-action-group">
                            ${actionBtn}
                            <button class="btn btn-secondary btn-sm" onclick="openComplaintModal('${c.complaintId}')" title="View Details"><i class="fa-solid fa-eye"></i></button>
                        </div>
                    </td>
                </tr>
            `;
        }).join("");
    } catch (e) {
        showToast("Error loading worker dashboard: " + e.message, "danger");
    }
}

async function handleWorkerStartWork(complaintId) {
    try {
        await ApiClient.updateStatus(complaintId, "InProgress");
        showToast(`Work started on <strong>${complaintId}</strong>! Status moved to InProgress.`);
        loadWorkerDashboard();
    } catch (e) {
        showToast("Failed to start work: " + e.message, "danger");
    }
}

function openResolveModal(complaintId) {
    const modal = document.getElementById("resolve-modal");
    if (!modal) return;
    document.getElementById("resolve-modal-comp-id").innerText = complaintId;
    document.getElementById("resolve-modal-notes").value = "";
    modal.classList.add("active");
}

function closeResolveModal() {
    const modal = document.getElementById("resolve-modal");
    if (modal) modal.classList.remove("active");
}

async function handleConfirmResolve(e) {
    e.preventDefault();
    const complaintId = document.getElementById("resolve-modal-comp-id").innerText;
    const notes = document.getElementById("resolve-modal-notes").value.trim();
    if (!notes) {
        showToast("Resolution notes are required!", "danger");
        return;
    }

    try {
        await ApiClient.resolveComplaint(complaintId, notes);
        showToast(`Complaint <strong>${complaintId}</strong> marked RESOLVED successfully!`);
        closeResolveModal();
        refreshCurrentView();
    } catch (e) {
        showToast("Failed to resolve complaint: " + e.message, "danger");
    }
}

// ------------------- MUNICIPAL MANAGER PORTAL LOGIC ------------------- //
function switchManagerSubtab(subtab) {
    document.querySelectorAll("#manager-dashboard .subtab-btn").forEach(btn => btn.classList.remove("active"));
    document.querySelectorAll("#manager-dashboard .subtab-content").forEach(content => content.classList.remove("active"));

    const btn = document.getElementById(`btn-mgr-${subtab}`);
    if (btn) btn.classList.add("active");
    const content = document.getElementById(`mgr-tab-${subtab}`);
    if (content) content.classList.add("active");
}

async function loadManagerDashboard() {
    try {
        const [complaints, report, depts] = await Promise.all([
            ApiClient.getAllComplaints(),
            ApiClient.getManagerReport().catch(() => null),
            ApiClient.getDepartmentCapacity().catch(() => [])
        ]);

        currentComplaintsList = complaints;

        const total = complaints.length;
        const review = complaints.filter(c => {
            const s = (c.status || "").toUpperCase();
            return s === "REGISTERED" || s === "UNDER REVIEW" || s === "PENDING";
        }).length;
        const active = complaints.filter(c => {
            const s = (c.status || "").toUpperCase();
            return s === "ASSIGNED" || s === "INPROGRESS" || s === "IN_PROGRESS";
        }).length;
        const waiting = complaints.filter(c => c.isWaiting === true).length;
        const resolved = complaints.filter(c => {
            const s = (c.status || "").toUpperCase();
            return s === "RESOLVED" || s === "CLOSED";
        }).length;

        if (document.getElementById("mgr-stat-total")) document.getElementById("mgr-stat-total").innerText = total;
        if (document.getElementById("mgr-stat-review")) document.getElementById("mgr-stat-review").innerText = review;
        if (document.getElementById("mgr-stat-active")) document.getElementById("mgr-stat-active").innerText = active;
        if (document.getElementById("mgr-stat-waiting")) document.getElementById("mgr-stat-waiting").innerText = waiting;
        if (document.getElementById("mgr-stat-resolved")) document.getElementById("mgr-stat-resolved").innerText = resolved;

        // 1. Render Triage Table
        const triageComplaints = complaints.filter(c => {
            const s = (c.status || "").toUpperCase();
            return s === "REGISTERED" || s === "UNDER REVIEW" || s === "PENDING" || c.isWaiting === true;
        });

        const triageTbody = document.getElementById("mgr-triage-tbody");
        if (triageTbody) {
            if (triageComplaints.length === 0) {
                triageTbody.innerHTML = `
                    <tr>
                        <td colspan="8">
                            <div class="empty-state">
                                <i class="fa-solid fa-circle-check" style="color:#059669;"></i>
                                <h3>All Grievances Triaged</h3>
                                <p>No newly registered complaints awaiting department assignment.</p>
                            </div>
                        </td>
                    </tr>
                `;
            } else {
                triageTbody.innerHTML = triageComplaints.map(c => {
                    let locCell = `<strong>${escapeHtml(c.location)}</strong>`;
                    if (c.latitude && c.longitude) {
                        locCell += `<br><span class="badge" style="background:#ecfdf5; color:#065f46; font-size:10px; padding:2px 6px;"><i class="fa-solid fa-location-crosshairs"></i> GPS: ${c.latitude}, ${c.longitude}</span><br><a href="https://www.google.com/maps?q=${c.latitude},${c.longitude}" target="_blank" rel="noopener" style="font-size:11px; color:var(--primary); text-decoration:underline;"><i class="fa-solid fa-map-location-dot"></i> View on Google Maps</a>`;
                    } else {
                        locCell += `<br><span style="font-size:11px; color:var(--text-muted);"><i class="fa-solid fa-map-pin"></i> Area only (No GPS)</span>`;
                    }

                    const assessmentBadge = c.isAssessed
                        ? getPriorityBadge(c.severity || c.priority)
                        : `<span class="badge" style="background:#f1f5f9; color:#475569; border:1px solid #cbd5e1;"><i class="fa-solid fa-hourglass"></i> Unassessed</span>`;

                    return `
                    <tr>
                        <td><strong>${c.complaintId}</strong><br><small style="color:var(--text-muted);">${c.createdAt || ''}</small></td>
                        <td><strong>${escapeHtml(c.citizenName || 'Citizen')}</strong><br><small style="color:var(--text-muted);">${escapeHtml(c.description ? (c.description.length > 50 ? c.description.substring(0, 50) + '...' : c.description) : '')}</small></td>
                        <td><span class="badge" style="background:var(--primary-light); color:var(--primary);">${escapeHtml(c.category)}</span></td>
                        <td>${locCell}</td>
                        <td>${assessmentBadge}<br><small style="color:var(--text-muted);">${c.citizenReportedImpact || c.affectedPeople || 1} affected</small></td>
                        <td>${c.arrivalOrder ? '#' + c.arrivalOrder : (c.arrivalSequence ? '#' + c.arrivalSequence : '-')}</td>
                        <td>${c.duplicateCandidates && c.duplicateCandidates.length > 0 ? '<span class="badge" style="background:#fef3c7; color:#92400e;"><i class="fa-solid fa-clone"></i> ' + c.duplicateCandidates.length + ' Candidates</span>' : '<span style="color:var(--text-dim);">None</span>'}</td>
                        <td>
                            <button class="btn btn-primary btn-sm" onclick="openAssignModal('${c.complaintId}')">
                                <i class="fa-solid fa-stamp"></i> Assess & Assign
                            </button>
                        </td>
                    </tr>
                `;
                }).join("");
            }
        }

        // 2. Render Duplicate Candidates Review
        const dupContainer = document.getElementById("mgr-duplicates-container");
        if (dupContainer) {
            const dupComplaints = complaints.filter(c => (c.duplicateCandidates && c.duplicateCandidates.length > 0) || c.duplicateOfId);
            if (dupComplaints.length === 0) {
                dupContainer.innerHTML = `
                    <div class="empty-state">
                        <i class="fa-solid fa-clone"></i>
                        <h3>No Active Duplicate Candidates</h3>
                        <p>All open grievances have unique category/location profiles.</p>
                    </div>
                `;
            } else {
                dupContainer.innerHTML = dupComplaints.map(c => {
                    const isConfirmedDup = !!c.duplicateOfId;
                    let candidateListHtml = "";
                    if (isConfirmedDup) {
                        candidateListHtml = `
                            <div style="background:#fee2e2; border:1px solid #fecaca; padding:12px; border-radius:var(--radius-sm); margin-top:8px;">
                                <div style="color:#dc2626; font-weight:600;"><i class="fa-solid fa-link"></i> Confirmed Duplicate of Canonical Complaint: <strong>${escapeHtml(c.duplicateOfId)}</strong></div>
                                <div style="margin-top:6px;">
                                    <button class="btn btn-primary btn-sm" style="background:#dc2626;" onclick="openDeleteDupModal('${c.complaintId}')">
                                        <i class="fa-solid fa-trash-can"></i> Delete Confirmed Duplicate
                                    </button>
                                </div>
                            </div>
                        `;
                    } else if (c.duplicateCandidates && c.duplicateCandidates.length > 0) {
                        candidateListHtml = c.duplicateCandidates.map(cand => `
                            <div style="background:var(--bg-surface-secondary); border:1px solid var(--border-color); padding:12px; border-radius:var(--radius-sm); margin-top:8px; display:flex; justify-content:space-between; align-items:center;">
                                <div>
                                    <strong>Candidate Complaint: ${cand.candidateId}</strong> (${cand.matchReason || 'Matching category & normalized location'})
                                    <div style="font-size:12px; color:var(--text-muted); margin-top:2px;">Location match: ${cand.candidateLocation || c.location} | Category: ${c.category}</div>
                                </div>
                                <div style="display:flex; gap:6px;">
                                    <button class="btn btn-primary btn-sm" onclick="handleManagerConfirmDuplicate('${c.complaintId}', '${cand.candidateId}')">
                                        <i class="fa-solid fa-link"></i> Confirm Duplicate Link
                                    </button>
                                    <button class="btn btn-secondary btn-sm" onclick="handleManagerRejectDuplicate('${c.complaintId}', '${cand.candidateId}')">
                                        <i class="fa-solid fa-xmark"></i> Reject Candidate
                                    </button>
                                </div>
                            </div>
                        `).join("");
                    }

                    return `
                        <div style="background:var(--bg-surface); border:1px solid var(--border-color); border-radius:var(--radius-md); padding:16px; margin-bottom:12px;">
                            <div style="display:flex; justify-content:space-between; align-items:center;">
                                <div>
                                    <strong style="font-size:15px; color:var(--primary);">${c.complaintId}</strong> - ${escapeHtml(c.category)} at ${escapeHtml(c.location)}
                                    <div style="font-size:13px; color:var(--text-main); margin-top:4px;">${escapeHtml(c.description)}</div>
                                </div>
                                <div>${getStatusBadge(c.status, c.isWaiting)}</div>
                            </div>
                            ${candidateListHtml}
                        </div>
                    `;
                }).join("");
            }
        }

        // 3. Render Department Capacities & Waiting Queues
        const capGrid = document.getElementById("mgr-capacity-cards-grid");
        if (capGrid && depts.length > 0) {
            capGrid.innerHTML = depts.map(d => {
                const percent = d.capacity > 0 ? Math.min(100, Math.round((d.currentLoad / d.capacity) * 100)) : 0;
                let barColor = "#059669";
                if (percent >= 80) barColor = "#d97706";
                if (percent >= 100) barColor = "#dc2626";

                return `
                    <div class="stat-card" style="display:block; padding:18px;">
                        <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:8px;">
                            <h4 style="font-size:15px; font-weight:700; color:var(--text-main);">${escapeHtml(d.departmentName)}</h4>
                            <span class="badge" style="background:#eff6ff; color:#1d4ed8;">Capacity: ${d.capacity}</span>
                        </div>
                        <div style="font-size:13px; color:var(--text-muted); margin-bottom:8px;">
                            Active Load: <strong>${d.currentLoad} / ${d.capacity}</strong> (${percent}%) | Waiting Queue: <strong style="color:#dc2626;">${d.waitingCount}</strong>
                        </div>
                        <div style="background:#e2e8f0; height:8px; border-radius:4px; overflow:hidden; margin-bottom:12px;">
                            <div style="background:${barColor}; width:${percent}%; height:100%;"></div>
                        </div>
                        <div style="font-size:12px; color:var(--text-muted);">
                            ${d.waitingCount > 0 ? `<i class="fa-solid fa-hourglass-half" style="color:#d97706;"></i> <strong>${d.waitingCount}</strong> complaints held in PriorityQueue waiting buffer.` : `<i class="fa-solid fa-circle-check" style="color:#059669;"></i> Available capacity for immediate assignment.`}
                        </div>
                    </div>
                `;
            }).join("");
        }

        // 4. Render Manager Workload Report
        const reportBox = document.getElementById("mgr-report-summary-box");
        if (reportBox) {
            reportBox.innerHTML = `
                <div class="card-title-bar">
                    <h3><i class="fa-solid fa-chart-pie"></i> Official Municipal Grievance Triage Metrics</h3>
                </div>
                <div class="stats-grid" style="margin-top:16px;">
                    <div class="stat-card">
                        <div class="stat-info">
                            <div class="stat-value">${total}</div>
                            <div class="stat-label">Total Registered Grievances</div>
                        </div>
                    </div>
                    <div class="stat-card">
                        <div class="stat-info">
                            <div class="stat-value">${review}</div>
                            <div class="stat-label">Awaiting Triage Review</div>
                        </div>
                    </div>
                    <div class="stat-card">
                        <div class="stat-info">
                            <div class="stat-value">${active}</div>
                            <div class="stat-label">Active Municipal Tasks</div>
                        </div>
                    </div>
                    <div class="stat-card">
                        <div class="stat-info">
                            <div class="stat-value">${waiting}</div>
                            <div class="stat-label">Waiting for Capacity</div>
                        </div>
                    </div>
                    <div class="stat-card">
                        <div class="stat-info">
                            <div class="stat-value">${resolved}</div>
                            <div class="stat-label">Resolved Complaints</div>
                        </div>
                    </div>
                </div>
            `;
        }
    } catch (e) {
        showToast("Error loading manager dashboard: " + e.message, "danger");
    }
}

// Assignment Modal Logic
let currentAssigningComplaint = null;

async function openAssignModal(complaintId) {
    const complaint = currentComplaintsList.find(c => c.complaintId === complaintId);
    if (!complaint) return;

    currentAssigningComplaint = complaint;
    document.getElementById("assign-modal-comp-id").innerText = complaint.complaintId;
    document.getElementById("assign-modal-cat").innerText = complaint.category;

    // Display comprehensive complaint summary in modal
    let gpsSnippet = "";
    if (complaint.latitude && complaint.longitude) {
        gpsSnippet = `<br><span style="color:#059669; font-weight:600;"><i class="fa-solid fa-location-crosshairs"></i> GPS: ${complaint.latitude}, ${complaint.longitude}</span> &bull; <a href="https://www.google.com/maps?q=${complaint.latitude},${complaint.longitude}" target="_blank" rel="noopener" style="color:var(--primary); text-decoration:underline;">View on Google Maps</a>`;
    }
    const detailsBox = document.getElementById("assign-modal-details-summary");
    if (detailsBox) {
        detailsBox.innerHTML = `
            <div><strong>Municipal Area:</strong> ${escapeHtml(complaint.location)}${gpsSnippet}</div>
            <div><strong>Affected People:</strong> ${complaint.citizenReportedImpact || complaint.affectedPeople || 1} citizens</div>
            <div><strong>Registered At:</strong> ${complaint.createdAt || 'N/A'}</div>
            <div><strong>Current Assessment:</strong> ${complaint.isAssessed ? getPriorityBadge(complaint.severity) : '<span class="badge" style="background:#f1f5f9; color:#475569;"><i class="fa-solid fa-hourglass"></i> Unassessed</span>'}</div>
            <div style="margin-top:4px; font-style:italic; color:var(--text-muted); font-size:12px;">"${escapeHtml(complaint.description || '')}"</div>
        `;
    }

    // Reset severity dropdown so manager must deliberately choose
    const sevSelect = document.getElementById("assign-modal-severity-select");
    if (sevSelect) {
        sevSelect.value = complaint.isAssessed && complaint.severity ? complaint.severity : "";
    }

    // Suggest department based on predefined category-to-department mapping
    let suggestedDept = "General Administration";
    const cat = (complaint.category || "").toLowerCase();
    if (cat.includes("water")) suggestedDept = "Water Supply";
    else if (cat.includes("road")) suggestedDept = "Roads";
    else if (cat.includes("waste")) suggestedDept = "Waste Management";
    else if (cat.includes("light") || cat.includes("street")) suggestedDept = "Streetlights";

    document.getElementById("assign-modal-suggested").innerText = suggestedDept;
    document.getElementById("assign-modal-dept-select").value = complaint.department && complaint.department !== "Unassigned" ? complaint.department : suggestedDept;
    document.getElementById("assign-modal-staff-input").value = complaint.assignedStaff && !complaint.assignedStaff.startsWith("Queued") ? complaint.assignedStaff : "";
    document.getElementById("assign-modal-reason-input").value = complaint.assessmentReason || "";

    await updateAssignCapacityFeedback();

    const modal = document.getElementById("assign-modal");
    if (modal) modal.classList.add("active");
}

function closeAssignModal() {
    const modal = document.getElementById("assign-modal");
    if (modal) modal.classList.remove("active");
    currentAssigningComplaint = null;
}

async function updateAssignCapacityFeedback() {
    const deptSelect = document.getElementById("assign-modal-dept-select");
    const infoBox = document.getElementById("assign-modal-capacity-info");
    if (!deptSelect || !infoBox) return;

    const chosenDept = deptSelect.value;
    try {
        const depts = await ApiClient.getDepartmentCapacity();
        const d = depts.find(item => (item.departmentName || "").toLowerCase().includes(chosenDept.toLowerCase()));
        if (d) {
            const hasRoom = d.currentLoad < d.capacity;
            if (hasRoom) {
                infoBox.innerHTML = `<span style="color:#059669; font-weight:600;"><i class="fa-solid fa-circle-check"></i> Available Capacity:</span> ${d.currentLoad} of ${d.capacity} active. Complaint will be assigned immediately.`;
            } else {
                infoBox.innerHTML = `<span style="color:#d97706; font-weight:600;"><i class="fa-solid fa-hourglass-half"></i> Department Full (${d.currentLoad}/${d.capacity}):</span> Complaint will enter priority-ranked waiting queue.`;
            }
        }
    } catch (ignored) {}
}

async function handleConfirmAssign(e) {
    e.preventDefault();
    if (!currentAssigningComplaint) return;

    const id = currentAssigningComplaint.complaintId;
    const severity = document.getElementById("assign-modal-severity-select") ? document.getElementById("assign-modal-severity-select").value : "";
    const reason = document.getElementById("assign-modal-reason-input").value.trim();
    const dept = document.getElementById("assign-modal-dept-select").value;
    const staff = document.getElementById("assign-modal-staff-input").value.trim();

    if (!severity) {
        showToast("Please deliberately select a severity level (Critical, High, Medium, Low).", "danger");
        return;
    }
    if (!reason) {
        showToast("Assessment reason / decision justification is required.", "danger");
        return;
    }

    try {
        await ApiClient.assessComplaint(id, severity, reason, dept, staff);
        showToast(`Complaint <strong>${id}</strong> assessed as <strong>${severity}</strong> and routed to ${dept}!`);
        closeAssignModal();
        refreshCurrentView();
    } catch (err) {
        showToast("Assessment and assignment failed: " + err.message, "danger");
    }
}

async function handleManagerConfirmDuplicate(complaintId, primaryComplaintId) {
    if (!confirm(`Are you sure you want to link complaint ${complaintId} as a confirmed duplicate of ${primaryComplaintId}?`)) return;
    try {
        await ApiClient.confirmDuplicateLink(complaintId, primaryComplaintId, "Manager operator confirmed duplicate link");
        showToast(`Complaint <strong>${complaintId}</strong> confirmed as duplicate of <strong>${primaryComplaintId}</strong>.`);
        refreshCurrentView();
    } catch (e) {
        showToast("Confirmation failed: " + e.message, "danger");
    }
}

async function handleManagerRejectDuplicate(complaintId, candidateComplaintId) {
    try {
        await ApiClient.rejectDuplicateCandidate(complaintId, candidateComplaintId, "Manager verified as distinct complaint");
        showToast(`Candidate duplicate dismissed for <strong>${complaintId}</strong>.`);
        refreshCurrentView();
    } catch (e) {
        showToast("Rejection failed: " + e.message, "danger");
    }
}

function openDeleteDupModal(complaintId) {
    const modal = document.getElementById("delete-dup-modal");
    if (!modal) return;
    document.getElementById("delete-dup-comp-id").value = complaintId;
    document.getElementById("delete-dup-display-id").innerText = complaintId;
    document.getElementById("delete-dup-reason").value = "";
    modal.classList.add("active");
}

function closeDeleteDupModal() {
    const modal = document.getElementById("delete-dup-modal");
    if (modal) modal.classList.remove("active");
}

async function handleConfirmDeleteDuplicate(e) {
    e.preventDefault();
    const id = document.getElementById("delete-dup-comp-id").value;
    const reason = document.getElementById("delete-dup-reason").value.trim();
    if (!reason) {
        showToast("Mandatory audit reason required!", "danger");
        return;
    }

    try {
        await ApiClient.deleteConfirmedDuplicate(id, reason);
        showToast(`Confirmed duplicate <strong>${id}</strong> deleted. Audit record preserved.`);
        closeDeleteDupModal();
        refreshCurrentView();
    } catch (err) {
        showToast("Deletion failed: " + err.message, "danger");
    }
}

// ------------------- ADMINISTRATOR PORTAL LOGIC ------------------- //
function switchAdminSubtab(subtab) {
    document.querySelectorAll("#admin-dashboard .subtab-btn").forEach(btn => btn.classList.remove("active"));
    document.querySelectorAll("#admin-dashboard .subtab-content").forEach(content => content.classList.remove("active"));

    const btn = document.getElementById(`btn-adm-${subtab}`);
    if (btn) btn.classList.add("active");
    const content = document.getElementById(`adm-tab-${subtab}`);
    if (content) content.classList.add("active");
}

async function loadAdminDashboard() {
    try {
        const [workers, complaints, depts, adminReport] = await Promise.all([
            ApiClient.getWorkers().catch(() => []),
            ApiClient.getAllComplaints().catch(() => []),
            ApiClient.getDepartmentCapacity().catch(() => []),
            ApiClient.getAdminReport().catch(() => null)
        ]);

        if (document.getElementById("adm-stat-complaints")) document.getElementById("adm-stat-complaints").innerText = complaints.length;
        if (document.getElementById("adm-stat-workers")) document.getElementById("adm-stat-workers").innerText = workers.filter(w => w.active !== false).length;
        if (document.getElementById("adm-stat-resolved")) document.getElementById("adm-stat-resolved").innerText = complaints.filter(c => (c.status || "").toUpperCase() === "RESOLVED").length;

        // 1. Render Workers Table
        const workersTbody = document.getElementById("adm-workers-tbody");
        if (workersTbody) {
            if (workers.length === 0) {
                workersTbody.innerHTML = `<tr><td colspan="7" class="empty-state">No workers found. Click "Add Worker" to provision accounts.</td></tr>`;
            } else {
                workersTbody.innerHTML = workers.map(w => {
                    const isActive = w.active !== false;
                    const statusBadge = isActive ?
                        `<span class="badge badge-resolved"><i class="fa-solid fa-circle-check"></i> Active</span>` :
                        `<span class="badge" style="background:#fee2e2; color:#dc2626;"><i class="fa-solid fa-ban"></i> Deactivated</span>`;

                    const toggleLabel = isActive ? "Deactivate" : "Activate";
                    const toggleColor = isActive ? "#dc2626" : "#059669";

                    return `
                        <tr>
                            <td><strong>${w.userId || w.id}</strong></td>
                            <td>${escapeHtml(w.name)}</td>
                            <td>${escapeHtml(w.email)}</td>
                            <td>${escapeHtml(w.mobile || '-')}</td>
                            <td><span class="locked-badge"><i class="fa-solid fa-lock"></i> ${escapeHtml(w.department || 'Operations')}</span></td>
                            <td>${statusBadge}</td>
                            <td>
                                <div class="table-action-group">
                                    <button class="btn btn-secondary btn-sm" onclick='openEditWorkerModal(${JSON.stringify(w)})' title="Edit Worker Details">
                                        <i class="fa-solid fa-user-pen"></i> Edit
                                    </button>
                                    <button class="btn btn-secondary btn-sm" style="color:${toggleColor};" onclick="handleToggleWorkerActive('${w.userId || w.id}', ${isActive})" title="${toggleLabel} Account">
                                        <i class="fa-solid fa-power-off"></i> ${toggleLabel}
                                    </button>
                                    <button class="btn btn-secondary btn-sm" onclick="openResetPwdModal('${w.userId || w.id}', '${escapeHtml(w.name)}')" title="Reset Password">
                                        <i class="fa-solid fa-key"></i> Reset
                                    </button>
                                </div>
                            </td>
                        </tr>
                    `;
                }).join("");
            }
        }

        // 2. Render Department Capacities Editors
        const capGrid = document.getElementById("adm-capacity-cards-grid");
        if (capGrid && depts.length > 0) {
            capGrid.innerHTML = depts.map(d => `
                <div class="stat-card" style="display:block; padding:18px;">
                    <h4 style="font-size:15px; font-weight:700; color:var(--text-main); margin-bottom:8px;">
                        <i class="fa-solid fa-building"></i> ${escapeHtml(d.departmentName)}
                    </h4>
                    <div style="font-size:13px; color:var(--text-muted); margin-bottom:12px;">
                        Current Active Load: <strong>${d.currentLoad}</strong> | Waiting Queue: <strong>${d.waitingCount}</strong>
                    </div>
                    <div class="form-group" style="margin-bottom:12px;">
                        <label class="form-label">Configured Fixed Capacity</label>
                        <input type="number" id="adm-cap-input-${escapeHtml(d.departmentName)}" class="form-control" min="1" value="${d.capacity}">
                    </div>
                    <button class="btn btn-primary btn-sm" onclick="handleUpdateCapacity('${escapeHtml(d.departmentName)}')">
                        <i class="fa-solid fa-floppy-disk"></i> Save Capacity Setting
                    </button>
                </div>
            `).join("");
        }

        // 3. Render Audit & System Logs
        const auditBox = document.getElementById("adm-audit-summary-box");
        if (auditBox) {
            auditBox.innerHTML = `
                <div class="card-title-bar">
                    <h3><i class="fa-solid fa-shield-halved"></i> Municipal Governance Audit & System Activity</h3>
                </div>
                <div style="padding:16px; font-size:14px; line-height:1.6; color:var(--text-main);">
                    <div style="margin-bottom:12px;">
                        <strong>Total Municipal System Complaints:</strong> ${complaints.length}
                    </div>
                    <div style="margin-bottom:12px;">
                        <strong>Worker Account Handover Protocol:</strong> Fully active. Deactivating a worker or transferring their department automatically triggers complaint unassignment and creates an immutable handover record in audit history.
                    </div>
                    <div style="margin-bottom:12px;">
                        <strong>Persistent Store:</strong> Authoritative JSON storage with password hashing (SHA-256) and strict server-side session role validation on all protected endpoints.
                    </div>
                </div>
            `;
        }
    } catch (e) {
        showToast("Error loading admin dashboard: " + e.message, "danger");
    }
}

function openAddWorkerModal() {
    const modal = document.getElementById("worker-modal");
    if (!modal) return;
    document.getElementById("worker-modal-title").innerHTML = `<i class="fa-solid fa-user-plus"></i> Add New Department Worker`;
    document.getElementById("worker-form-id").value = "";
    document.getElementById("w-form-name").value = "";
    document.getElementById("w-form-email").value = "";
    document.getElementById("w-form-mobile").value = "";
    document.getElementById("w-form-dept").value = "Water Supply";
    document.getElementById("w-form-pwd").value = "admin123";
    document.getElementById("w-form-pwd-group").style.display = "block";
    document.getElementById("w-form-active-group").style.display = "none";
    modal.classList.add("active");
}

function openEditWorkerModal(worker) {
    const modal = document.getElementById("worker-modal");
    if (!modal) return;
    document.getElementById("worker-modal-title").innerHTML = `<i class="fa-solid fa-user-pen"></i> Edit Department Worker`;
    document.getElementById("worker-form-id").value = worker.userId || worker.id;
    document.getElementById("w-form-name").value = worker.name || "";
    document.getElementById("w-form-email").value = worker.email || "";
    document.getElementById("w-form-email").readOnly = true;
    document.getElementById("w-form-mobile").value = worker.mobile || "";
    document.getElementById("w-form-dept").value = worker.department || "Water Supply";
    document.getElementById("w-form-pwd-group").style.display = "none";
    document.getElementById("w-form-active-group").style.display = "block";
    document.getElementById("w-form-active").checked = worker.active !== false;
    modal.classList.add("active");
}

function closeWorkerModal() {
    const modal = document.getElementById("worker-modal");
    if (modal) modal.classList.remove("active");
    if (document.getElementById("w-form-email")) document.getElementById("w-form-email").readOnly = false;
}

async function handleSaveWorker(e) {
    e.preventDefault();
    const workerId = document.getElementById("worker-form-id").value;
    const name = document.getElementById("w-form-name").value.trim();
    const email = document.getElementById("w-form-email").value.trim();
    const mobile = document.getElementById("w-form-mobile").value.trim();
    const dept = document.getElementById("w-form-dept").value;

    try {
        if (!workerId) {
            // Create
            const pwd = document.getElementById("w-form-pwd").value;
            await ApiClient.createWorker(name, email, mobile, pwd, dept);
            showToast(`Worker <strong>${name}</strong> added to ${dept} Department!`);
        } else {
            // Update
            const active = document.getElementById("w-form-active").checked;
            await ApiClient.updateWorker(workerId, name, mobile, dept, active);
            showToast(`Worker <strong>${name}</strong> updated successfully.`);
        }
        closeWorkerModal();
        loadAdminDashboard();
    } catch (err) {
        showToast("Failed to save worker: " + err.message, "danger");
    }
}

async function handleToggleWorkerActive(workerId, currentActive) {
    const action = currentActive ? "deactivate" : "activate";
    if (!confirm(`Are you sure you want to ${action} worker ${workerId}?`)) return;

    try {
        await ApiClient.updateWorker(workerId, "", "", "", !currentActive);
        showToast(`Worker <strong>${workerId}</strong> ${action}d successfully.`);
        loadAdminDashboard();
    } catch (err) {
        showToast("Action failed: " + err.message, "danger");
    }
}

function openResetPwdModal(workerId, name) {
    const modal = document.getElementById("reset-pwd-modal");
    if (!modal) return;
    document.getElementById("reset-pwd-worker-id").value = workerId;
    document.getElementById("reset-pwd-worker-name").innerText = `${name} (${workerId})`;
    document.getElementById("reset-pwd-new").value = "";
    modal.classList.add("active");
}

function closeResetPwdModal() {
    const modal = document.getElementById("reset-pwd-modal");
    if (modal) modal.classList.remove("active");
}

async function handleConfirmResetPwd(e) {
    e.preventDefault();
    const workerId = document.getElementById("reset-pwd-worker-id").value;
    const pwd = document.getElementById("reset-pwd-new").value;

    try {
        await ApiClient.resetWorkerPassword(workerId, pwd);
        showToast(`Password successfully reset for <strong>${workerId}</strong>.`);
        closeResetPwdModal();
    } catch (err) {
        showToast("Password reset failed: " + err.message, "danger");
    }
}

async function handleUpdateCapacity(deptName) {
    const input = document.getElementById(`adm-cap-input-${deptName}`);
    if (!input) return;
    const newCap = parseInt(input.value);
    if (isNaN(newCap) || newCap < 1) {
        showToast("Capacity must be at least 1!", "danger");
        return;
    }

    try {
        await ApiClient.updateDepartmentCapacity(deptName, newCap);
        showToast(`Capacity updated to <strong>${newCap}</strong> for ${deptName} Department.`);
        loadAdminDashboard();
    } catch (err) {
        showToast("Failed to update capacity: " + err.message, "danger");
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
        const matchesStat = !status || (c.status && c.status.toLowerCase().replace(/[\s_-]/g, '') === status.toLowerCase().replace(/[\s_-]/g, ''));

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
            <td>${getStatusBadge(c.status, c.isWaiting)}</td>
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
                        ${getStatusBadge(c.status, c.isWaiting)}
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
        let locDisplay = escapeHtml(c.location);
        if (c.latitude && c.longitude) {
            locDisplay += ` &bull; <span style="color:#059669; font-weight:600;"><i class="fa-solid fa-location-dot"></i> GPS: ${c.latitude}, ${c.longitude}</span>`;
            if (c.locationAccuracyMeters) {
                locDisplay += ` (&plusmn;${Math.round(c.locationAccuracyMeters)}m)`;
            }
            locDisplay += ` &bull; <a href="https://www.google.com/maps?q=${c.latitude},${c.longitude}" target="_blank" rel="noopener" style="color:var(--primary); text-decoration:underline;"><i class="fa-solid fa-map-location-dot"></i> View on Google Maps</a>`;
        }
        document.getElementById("m-location").innerHTML = locDisplay;

        let desc = c.description || "";
        if (c.citizenReportedImpact) {
            desc += `\n\n[Citizen Reported Impact: ${c.citizenReportedImpact} affected]`;
        }
        if (c.isAssessed) {
            desc += `\n[Assessed Severity: ${c.severity} | Assessed By: ${c.assessedBy || 'Manager'} at ${c.assessedAt || ''} | Reason: "${c.assessmentReason || 'Standard triage'}"]`;
        } else {
            desc += `\n[Status: Unassessed - Awaiting deliberate manager risk review]`;
        }
        if (c.adminVerifiedImpact) {
            desc += `\n[Admin Verified Impact: ${c.adminVerifiedImpact} affected | Verification Reason: "${c.verificationReason || 'Confirmed by field inspection'}"]`;
        }
        if (c.isDuplicate) {
            desc += `\n[Duplicate Case - Linked Primary: ${c.duplicateOfId || 'Master'}]`;
        }
        if (c.isWaiting) {
            desc += `\n[Department Capacity Status: Queued in Department Waiting PriorityQueue (Department Full)]`;
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
            document.getElementById("s-resolution-input").value = c.resolutionNotes || "";

            // Dynamic population of valid next actions based on State Machine
            const statusSelect = document.getElementById("s-update-status-select");
            const updateBtn = document.getElementById("btn-update-status");
            const resolveInput = document.getElementById("s-resolution-input");
            const resolveBtn = document.querySelector("#staff-controls-box button[onclick='handleStaffResolve()']");

            const normStatus = (c.status || "").toUpperCase().replace(/[\s_-]/g, "");
            statusSelect.innerHTML = "";

            if (normStatus === "REGISTERED" || normStatus === "SUBMITTED" || normStatus === "PENDING") {
                statusSelect.innerHTML = `<option value="Assigned">Assigned (Move to Department Queue)</option>`;
                statusSelect.disabled = false;
                if (updateBtn) updateBtn.disabled = false;
                if (resolveBtn) resolveBtn.disabled = true;
                if (resolveInput) resolveInput.disabled = true;
            } else if (normStatus === "ASSIGNED") {
                statusSelect.innerHTML = `<option value="InProgress">In Progress (Start Field Work)</option>`;
                statusSelect.disabled = false;
                if (updateBtn) updateBtn.disabled = false;
                if (resolveBtn) resolveBtn.disabled = true;
                if (resolveInput) resolveInput.disabled = true;
            } else if (normStatus === "INPROGRESS") {
                statusSelect.innerHTML = `<option value="Resolved">Resolved (Complete Grievance)</option>`;
                statusSelect.disabled = false;
                if (updateBtn) updateBtn.disabled = false;
                if (resolveBtn) resolveBtn.disabled = false;
                if (resolveInput) resolveInput.disabled = false;
            } else if (normStatus === "RESOLVED" || normStatus === "CLOSED") {
                statusSelect.innerHTML = `<option value="" disabled selected>Terminal State (Case Fully Resolved)</option>`;
                statusSelect.disabled = true;
                if (updateBtn) updateBtn.disabled = true;
                if (resolveBtn) resolveBtn.disabled = true;
                if (resolveInput) resolveInput.disabled = true;
            } else {
                statusSelect.innerHTML = `
                    <option value="Assigned">Assigned</option>
                    <option value="InProgress">In Progress</option>
                    <option value="Resolved">Resolved</option>
                `;
                statusSelect.disabled = false;
                if (updateBtn) updateBtn.disabled = false;
                if (resolveBtn) resolveBtn.disabled = false;
                if (resolveInput) resolveInput.disabled = false;
            }

            // Load Duplicate Candidate Controls
            loadDuplicateCandidateControls(c);
        } else {
            staffBox.style.display = "none";
            const dupBox = document.getElementById("duplicate-candidate-box");
            if (dupBox) dupBox.style.display = "none";
        }

        document.getElementById("complaint-modal").classList.add("active");
    } catch (e) {
        showToast("Error loading case file: " + e.message, "danger");
    }
}

async function loadDuplicateCandidateControls(complaint) {
    const box = document.getElementById("duplicate-candidate-box");
    const badge = document.getElementById("duplicate-candidate-badge");
    const list = document.getElementById("duplicate-candidates-list");
    if (!box || !list) return;

    box.style.display = "flex";

    if (complaint.isDuplicate) {
        if (badge) {
            badge.style.display = "inline-block";
            badge.style.backgroundColor = "#dbeafe";
            badge.style.color = "#1e40af";
            badge.style.borderColor = "#bfdbfe";
            badge.innerText = "Confirmed Duplicate";
        }
        list.innerHTML = `
            <div style="background: var(--bg-surface); padding: 12px; border-radius: var(--radius-sm); border: 1px solid var(--border-color); font-size: 13px;">
                <p style="margin: 0 0 6px 0; color: #1e40af; font-weight: 600;"><i class="fa-solid fa-link"></i> Confirmed Duplicate Link</p>
                <p style="margin: 0; color: var(--text-main);">This grievance is confirmed as a duplicate linked to primary case: <strong>${escapeHtml(complaint.duplicateOfId || 'Master')}</strong>.</p>
                <p style="margin: 6px 0 0 0; color: var(--text-muted); font-size: 12px;">Original complaint record and audit history are safely preserved.</p>
            </div>
        `;
        return;
    }

    try {
        const candidates = await ApiClient.getDuplicateCandidates(complaint.complaintId);
        if (!candidates || candidates.length === 0) {
            if (badge) {
                badge.style.display = "none";
            }
            list.innerHTML = `
                <div style="font-size: 13px; color: var(--text-muted); padding: 8px 0;">
                    <i class="fa-solid fa-circle-check" style="color: #059669;"></i> No active duplicate candidates detected for this grievance.
                </div>
            `;
            return;
        }

        if (badge) {
            badge.style.display = "inline-block";
            badge.style.backgroundColor = "#fef3c7";
            badge.style.color = "#92400e";
            badge.style.borderColor = "#fcd34d";
            badge.innerText = `${candidates.length} Candidate${candidates.length > 1 ? 's' : ''} Detected`;
        }

        list.innerHTML = candidates.map(cand => `
            <div style="background: var(--bg-surface); padding: 12px; border-radius: var(--radius-sm); border: 1px solid var(--border-color); display: flex; flex-direction: column; gap: 8px;">
                <div style="display: flex; justify-content: space-between; align-items: center; font-size: 13px;">
                    <div>
                        <strong>Candidate Case: ${escapeHtml(cand.candidateComplaintId)}</strong>
                        <span style="color: var(--text-muted); margin-left: 8px;">(${escapeHtml(cand.category)} | ${escapeHtml(cand.location)})</span>
                    </div>
                    <span class="badge badge-progress" style="font-size: 11px;">${escapeHtml(cand.status)}</span>
                </div>
                <div style="font-size: 12px; color: var(--text-muted);">
                    <span><i class="fa-solid fa-triangle-exclamation" style="color: #d97706;"></i> <strong>Match Reason:</strong> ${escapeHtml(cand.reason)}</span>
                    <span style="margin-left: 12px;"><i class="fa-solid fa-user"></i> Reported by: ${escapeHtml(cand.citizenName)}</span>
                </div>
                <div style="display: flex; gap: 8px; align-items: center; margin-top: 4px;">
                    <input type="text" id="cand-remarks-${cand.candidateComplaintId}" class="form-control" placeholder="Operator verification remarks..." style="flex: 1; font-size: 12px; padding: 4px 8px;">
                    <button class="btn btn-primary btn-sm" style="font-size: 12px; padding: 5px 10px;" onclick="handleConfirmDuplicateLink('${escapeHtml(complaint.complaintId)}', '${escapeHtml(cand.candidateComplaintId)}')">
                        <i class="fa-solid fa-check"></i> Confirm Link
                    </button>
                    <button class="btn btn-secondary btn-sm" style="font-size: 12px; padding: 5px 10px;" onclick="handleRejectDuplicateCandidate('${escapeHtml(complaint.complaintId)}', '${escapeHtml(cand.candidateComplaintId)}')">
                        <i class="fa-solid fa-xmark"></i> Reject Candidate
                    </button>
                </div>
            </div>
        `).join("");
    } catch (e) {
        list.innerHTML = `<div style="font-size: 12px; color: #dc2626;">Error querying duplicate candidates: ${escapeHtml(e.message)}</div>`;
    }
}

async function handleConfirmDuplicateLink(sourceId, primaryCandidateId) {
    const remarksInput = document.getElementById(`cand-remarks-${primaryCandidateId}`);
    const remarks = remarksInput ? remarksInput.value.trim() : "";
    try {
        await ApiClient.confirmDuplicateLink(sourceId, primaryCandidateId, remarks);
        showToast(`Duplicate link confirmed between <strong>${sourceId}</strong> and primary <strong>${primaryCandidateId}</strong>!`);
        openComplaintModal(sourceId);
        refreshCurrentView();
    } catch (e) {
        showToast("Failed to confirm duplicate link: " + e.message, "danger");
    }
}

async function handleRejectDuplicateCandidate(sourceId, candidateId) {
    const remarksInput = document.getElementById(`cand-remarks-${candidateId}`);
    const remarks = remarksInput ? remarksInput.value.trim() : "";
    try {
        await ApiClient.rejectDuplicateCandidate(sourceId, candidateId, remarks);
        showToast(`Candidate <strong>${candidateId}</strong> rejected for <strong>${sourceId}</strong>.`);
        openComplaintModal(sourceId);
        refreshCurrentView();
    } catch (e) {
        showToast("Failed to reject duplicate candidate: " + e.message, "danger");
    }
}

function renderTrackingTimeline(status) {
    const s = (status || "").toUpperCase().replace(/[\s_-]/g, "");

    const stepSubmitted = document.getElementById("step-submitted");
    const stepAssigned = document.getElementById("step-assigned");
    const stepProgress = document.getElementById("step-progress");
    const stepResolved = document.getElementById("step-resolved");

    [stepSubmitted, stepAssigned, stepProgress, stepResolved].forEach(el => {
        if (el) el.className = "timeline-step";
    });

    if (stepSubmitted) stepSubmitted.classList.add("completed");

    if (s === "ASSIGNED") {
        if (stepAssigned) stepAssigned.classList.add("active");
    } else if (s === "INPROGRESS") {
        if (stepAssigned) stepAssigned.classList.add("completed");
        if (stepProgress) stepProgress.classList.add("active");
    } else if (s === "RESOLVED" || s === "CLOSED") {
        if (stepAssigned) stepAssigned.classList.add("completed");
        if (stepProgress) stepProgress.classList.add("completed");
        if (stepResolved) stepResolved.classList.add("completed");
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
    if (!currentUser) return;
    const role = (currentUser.role || "").toUpperCase();
    if (role === "ADMIN") {
        const activePage = document.querySelector(".page-view.active");
        if (activePage && activePage.id === "staff-dashboard") loadStaffDashboard();
        else loadAdminDashboard();
    } else if (role === "MANAGER") {
        const activePage = document.querySelector(".page-view.active");
        if (activePage && activePage.id === "staff-dashboard") loadStaffDashboard();
        else loadManagerDashboard();
    } else if (role === "WORKER" || role === "STAFF") {
        loadWorkerDashboard();
    } else {
        loadCitizenDashboard();
    }
}

function showHelpModal() {
    showToast("Need assistance? Contact Municipal Office Helpline: 1800-425-1001", "success");
}

// Accessible Badge Generators (Icon + Text)
function getPriorityBadge(priority) {
    const p = (priority || "").toUpperCase();
    if (!p || p === "UNASSESSED" || p === "NULL") {
        return `<span class="badge" style="background:#f1f5f9; color:#475569; border:1px solid #cbd5e1;"><i class="fa-solid fa-hourglass"></i> Unassessed</span>`;
    }
    let pClass = "low";
    let icon = "fa-circle-down";

    if (p === "CRITICAL") { pClass = "critical"; icon = "fa-circle-radiation"; }
    else if (p === "HIGH") { pClass = "high"; icon = "fa-triangle-exclamation"; }
    else if (p === "MEDIUM") { pClass = "medium"; icon = "fa-circle-exclamation"; }

    return `<span class="badge badge-${pClass}"><i class="fa-solid ${icon}"></i> ${priority}</span>`;
}

function getStatusBadge(status, isWaiting = false) {
    if (isWaiting) {
        return `<span class="badge" style="background-color: #fef3c7; color: #92400e; border: 1px solid #fcd34d;"><i class="fa-solid fa-hourglass-half"></i> Waiting (Queued)</span>`;
    }
    const s = (status || "").toUpperCase().replace(/[\s_-]/g, "");
    let sClass = "pending";
    let icon = "fa-clock";

    if (s === "ASSIGNED") { sClass = "assigned"; icon = "fa-user-check"; }
    else if (s === "INPROGRESS") { sClass = "progress"; icon = "fa-gears"; }
    else if (s === "RESOLVED" || s === "CLOSED") { sClass = "resolved"; icon = "fa-circle-check"; }
    else if (s === "REGISTERED") { sClass = "pending"; icon = "fa-clock"; }

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
