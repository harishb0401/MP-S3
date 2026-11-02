// Enterprise Light Theme Municipal Grievance Portal - Application Controller

let currentUser = null;
let activeComplaintId = null;
let currentComplaintsList = [];
let currentPage = 1;
const pageSize = 5;

let isSidebarCollapsed = false;
let currentWizardStep = 1;
let attachedFileName = null;
let attachedImageDataUrl = null;

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

// ------------------- NOTIFICATIONS SYSTEM (MULTI-ROLE) -------------------
let cachedNotifications = [];
let notifPollingInterval = null;

function toggleNotificationDrawer() {
    const overlay = document.getElementById("noti-drawer-overlay");
    const drawer = document.getElementById("noti-drawer");
    if (!overlay || !drawer) return;

    const isActive = drawer.classList.contains("active");
    if (isActive) {
        overlay.classList.remove("active");
        drawer.classList.remove("active");
    } else {
        overlay.classList.add("active");
        drawer.classList.add("active");
        fetchNotificationsAndBadge(true);
    }
}

async function fetchNotificationsAndBadge(forceRenderDrawer = false) {
    const badge = document.getElementById("noti-badge-count");
    const drawerBadge = document.getElementById("drawer-unread-count-badge");
    const drawer = document.getElementById("noti-drawer");
    const isDrawerOpen = drawer && drawer.classList.contains("active");

    if (!currentUser) {
        if (badge) badge.style.display = "none";
        return;
    }

    try {
        const notis = await ApiClient.getNotifications();
        cachedNotifications = Array.isArray(notis) ? notis : [];

        const unreadCount = cachedNotifications.filter(n => !n.isRead).length;

        // Top bar bell badge
        if (badge) {
            badge.innerText = unreadCount;
            badge.style.display = unreadCount > 0 ? "inline-block" : "none";
        }

        // Drawer header badge
        if (drawerBadge) {
            drawerBadge.innerText = `${unreadCount} unread`;
        }

        // Render drawer items if drawer is open or forced
        if (isDrawerOpen || forceRenderDrawer) {
            renderNotificationDrawerItems(cachedNotifications);
        }
    } catch (e) {
        console.warn("Failed to fetch notifications:", e.message);
    }
}

function renderNotificationDrawerItems(notis) {
    const body = document.getElementById("noti-drawer-body");
    if (!body) return;

    if (!notis || notis.length === 0) {
        body.innerHTML = `
            <div class="noti-empty">
                <i class="fa-regular fa-bell"></i>
                <div style="font-weight: 600; font-size: 14px; color: var(--text-main);">No Notifications Yet</div>
                <div style="font-size: 12px; max-width: 260px; line-height: 1.4;">
                    You will receive real-time updates here as grievances are registered, reviewed, assigned, or resolved.
                </div>
            </div>
        `;
        return;
    }

    body.innerHTML = notis.map(n => {
        const isUnread = !n.isRead;
        const typeClass = (n.type && ["urgent", "warning", "success", "info"].includes(n.type.toLowerCase()))
            ? n.type.toLowerCase()
            : "info";

        let iconHtml = '<i class="fa-solid fa-circle-info" style="color: #2563eb;"></i>';
        if (typeClass === "success") iconHtml = '<i class="fa-solid fa-circle-check" style="color: #059669;"></i>';
        else if (typeClass === "warning") iconHtml = '<i class="fa-solid fa-triangle-exclamation" style="color: #d97706;"></i>';
        else if (typeClass === "urgent") iconHtml = '<i class="fa-solid fa-bell" style="color: #dc2626;"></i>';

        const dotHtml = isUnread ? '<span class="noti-unread-dot" title="Unread notification"></span>' : '';
        const complaintTag = n.complaintId ? `
            <span class="noti-complaint-tag" onclick="handleNotificationComplaintClick('${escapeHtml(n.complaintId)}', '${escapeHtml(n.notificationId)}', event)" title="View Complaint Details">
                <i class="fa-solid fa-folder-open"></i> ${escapeHtml(n.complaintId)}
            </span>
        ` : '';

        const markReadBtn = isUnread ? `
            <button type="button" class="noti-mark-read-btn" onclick="handleMarkSingleNotificationRead('${escapeHtml(n.notificationId)}', event)" title="Mark as read">
                <i class="fa-solid fa-check"></i> Mark read
            </button>
        ` : `
            <span style="font-size: 10px; color: #94a3b8;"><i class="fa-solid fa-check-double"></i> Read</span>
        `;

        return `
            <div class="noti-card ${typeClass} ${isUnread ? 'unread' : 'read'}" onclick="handleNotificationCardClick('${escapeHtml(n.complaintId || '')}', '${escapeHtml(n.notificationId)}', ${!isUnread})">
                <div class="noti-title-row">
                    <div class="noti-title">
                        ${dotHtml}
                        ${iconHtml}
                        <span>${escapeHtml(n.title || 'Notification')}</span>
                    </div>
                </div>
                <div class="noti-desc">${escapeHtml(n.message || '')}</div>
                <div class="noti-meta-row">
                    <div class="noti-time">
                        <i class="fa-regular fa-clock"></i> ${escapeHtml(n.createdAt || '')}
                    </div>
                    <div style="display: flex; align-items: center; gap: 8px;">
                        ${complaintTag}
                        ${markReadBtn}
                    </div>
                </div>
            </div>
        `;
    }).join("");
}

async function handleNotificationCardClick(complaintId, notificationId, isAlreadyRead) {
    if (!isAlreadyRead && notificationId) {
        try {
            await ApiClient.markNotificationRead(notificationId);
            const found = cachedNotifications.find(x => x.notificationId === notificationId);
            if (found) found.isRead = true;
            fetchNotificationsAndBadge(true);
        } catch (e) {
            console.warn("Failed to mark notification read:", e);
        }
    }

    if (complaintId && complaintId.trim().length > 0) {
        toggleNotificationDrawer();
        await openComplaintModal(complaintId.trim());
        setTimeout(() => {
            const dupBox = document.getElementById("duplicate-candidate-box");
            if (dupBox && dupBox.style.display !== "none") {
                dupBox.scrollIntoView({ behavior: "smooth" });
            }
        }, 150);
    }
}

async function handleNotificationComplaintClick(complaintId, notificationId, event) {
    if (event) event.stopPropagation();
    if (notificationId) {
        try {
            await ApiClient.markNotificationRead(notificationId);
            const found = cachedNotifications.find(x => x.notificationId === notificationId);
            if (found) found.isRead = true;
            fetchNotificationsAndBadge(true);
        } catch (ignored) {}
    }
    toggleNotificationDrawer();
    openComplaintModal(complaintId);
}

async function handleMarkSingleNotificationRead(notificationId, event) {
    if (event) event.stopPropagation();
    try {
        await ApiClient.markNotificationRead(notificationId);
        const found = cachedNotifications.find(x => x.notificationId === notificationId);
        if (found) found.isRead = true;
        fetchNotificationsAndBadge(true);
    } catch (e) {
        showToast("error", "Error marking notification as read: " + e.message);
    }
}

async function handleMarkAllNotificationsRead() {
    try {
        const res = await ApiClient.markAllNotificationsRead();
        cachedNotifications.forEach(n => n.isRead = true);
        fetchNotificationsAndBadge(true);
        showToast("success", res.message || "All notifications marked as read.");
    } catch (e) {
        showToast("error", "Failed to mark all as read: " + e.message);
    }
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

    // Start Real-Time Notification Polling & Initial Fetch
    fetchNotificationsAndBadge();
    if (!notifPollingInterval) {
        notifPollingInterval = setInterval(() => {
            fetchNotificationsAndBadge();
        }, 10000); // 10s polling interval
    }

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
            <div class="nav-section-label">Admin Authority</div>
            <li><a class="nav-link active" data-view="admin-dashboard" onclick="switchView('admin-dashboard'); switchAdminSubtab('workers');"><i class="fa-solid fa-shield-halved"></i><span>Admin & Workers</span></a></li>
            <li><a class="nav-link" data-view="registered-citizens" onclick="switchView('admin-dashboard'); switchAdminSubtab('citizens');"><i class="fa-solid fa-address-book"></i><span>Registered Citizens</span></a></li>
            <li><a class="nav-link" data-view="analytics" onclick="switchView('analytics')"><i class="fa-solid fa-chart-line"></i><span>Grievance Analytics</span></a></li>
            <div class="nav-section-label">Department Operations</div>
            <li><a class="nav-link" data-view="staff-dashboard" onclick="switchView('staff-dashboard')"><i class="fa-solid fa-list-check"></i><span>All Complaints</span></a></li>
            <li><a class="nav-link" data-view="priority-queue" onclick="switchView('priority-queue')"><i class="fa-solid fa-layer-group"></i><span>Priority Queues</span></a></li>
            <li><a class="nav-link" data-view="departments" onclick="switchView('departments')"><i class="fa-solid fa-sitemap"></i><span>Department Loads</span></a></li>
            <div class="nav-section-label">Citizen Services</div>
            <li><a class="nav-link" data-view="profile-settings" onclick="switchView('profile-settings')"><i class="fa-solid fa-user-gear"></i><span>My Profile</span></a></li>
            <li><a class="nav-link" onclick="showHelpModal()"><i class="fa-solid fa-circle-question"></i><span>Civic Helpdesk</span></a></li>
        `;
    } else if (role === "MANAGER") {
        nav.innerHTML = `
            <div class="nav-section-label">Department Operations</div>
            <li><a class="nav-link active" data-view="manager-dashboard" onclick="switchView('manager-dashboard')"><i class="fa-solid fa-inbox"></i><span>Triage & Allocation</span></a></li>
            <li><a class="nav-link" data-view="staff-dashboard" onclick="switchView('staff-dashboard')"><i class="fa-solid fa-list-check"></i><span>All Complaints</span></a></li>
            <li><a class="nav-link" data-view="priority-queue" onclick="switchView('priority-queue')"><i class="fa-solid fa-layer-group"></i><span>Priority Queues</span></a></li>
            <li><a class="nav-link" data-view="departments" onclick="switchView('departments')"><i class="fa-solid fa-sitemap"></i><span>Department Loads</span></a></li>
            <li><a class="nav-link" data-view="analytics" onclick="switchView('analytics')"><i class="fa-solid fa-chart-line"></i><span>Reports & Stats</span></a></li>
            <div class="nav-section-label">Citizen Services</div>
            <li><a class="nav-link" data-view="profile-settings" onclick="switchView('profile-settings')"><i class="fa-solid fa-user-gear"></i><span>My Profile</span></a></li>
            <li><a class="nav-link" onclick="showHelpModal()"><i class="fa-solid fa-circle-question"></i><span>Civic Helpdesk</span></a></li>
        `;
    } else if (role === "WORKER" || role === "STAFF") {
        nav.innerHTML = `
            <div class="nav-section-label">Field Dispatch</div>
            <li><a class="nav-link active" data-view="worker-dashboard" onclick="switchView('worker-dashboard')"><i class="fa-solid fa-helmet-safety"></i><span>My Department Tasks</span></a></li>
            <div class="nav-section-label">Citizen Services</div>
            <li><a class="nav-link" data-view="profile-settings" onclick="switchView('profile-settings')"><i class="fa-solid fa-user-gear"></i><span>My Profile</span></a></li>
            <li><a class="nav-link" onclick="showHelpModal()"><i class="fa-solid fa-circle-question"></i><span>Civic Helpdesk</span></a></li>
        `;
    } else {
        nav.innerHTML = `
            <div class="nav-section-label">Citizen Services</div>
            <li><a class="nav-link active" data-view="citizen-dashboard" onclick="switchView('citizen-dashboard')"><i class="fa-solid fa-house"></i><span>My Grievances</span></a></li>
            <li><a class="nav-link" data-view="citizen-submit" onclick="switchView('citizen-submit')"><i class="fa-solid fa-file-circle-plus"></i><span>File a Complaint</span></a></li>
            <div class="nav-section-label">Account & Services</div>
            <li><a class="nav-link" data-view="profile-settings" onclick="switchView('profile-settings')"><i class="fa-solid fa-user-gear"></i><span>My Profile</span></a></li>
            <li><a class="nav-link" onclick="showHelpModal()"><i class="fa-solid fa-circle-question"></i><span>Civic Helpdesk</span></a></li>
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
    const roleBadge = document.getElementById("header-role-badge");
    const subbarView = document.getElementById("subbar-current-view");

    if (roleBadge && currentUser) {
        const r = (currentUser.role || "").toUpperCase();
        if (r === "ADMIN") roleBadge.innerText = "Administrator";
        else if (r === "MANAGER") roleBadge.innerText = "Municipal Manager";
        else if (r === "WORKER" || r === "STAFF") roleBadge.innerText = "Field Worker";
        else roleBadge.innerText = "Citizen";
    }

    if (subbarView) {
        switch (viewId) {
            case "citizen-dashboard": subbarView.innerText = "Citizen Redressal Gateway"; break;
            case "citizen-submit": subbarView.innerText = "Lodge Grievance Docket"; break;
            case "worker-dashboard": subbarView.innerText = "Field Worker Dispatch Console"; break;
            case "manager-dashboard": subbarView.innerText = "Department Allocation Console"; break;
            case "admin-dashboard": subbarView.innerText = "Central Operations Command"; break;
            case "staff-dashboard": subbarView.innerText = "Master Grievance Registry"; break;
            case "priority-queue": subbarView.innerText = "Priority Triage Queue"; break;
            case "analytics": subbarView.innerText = "Civic Intelligence & Reports"; break;
            default: subbarView.innerText = "Central Operations"; break;
        }
    }

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
    const idInput = document.getElementById("c-reg-identity");
    const rawIdentity = idInput ? idInput.value.trim() : "";
    const errEl = document.getElementById("c-reg-identity-error");

    if (errEl) errEl.style.display = "none";

    if (password !== confirm) {
        showToast("Passwords do not match!", "danger");
        return;
    }

    // Frontend identity verification validation
    const cleanId = rawIdentity.replace(/[\s-]+/g, "");
    if (!cleanId || cleanId.length !== 12 || !/^[2-9]\d{11}$/.test(cleanId)) {
        const msg = "Please enter a valid 12-digit identity number (cannot start with 0 or 1).";
        showToast(msg, "danger");
        if (errEl) {
            errEl.innerText = msg;
            errEl.style.color = "#dc2626";
            errEl.style.display = "block";
        }
        if (idInput) idInput.focus();
        return;
    }

    // Check repetitive digits
    if (/^(\d)\1{11}$/.test(cleanId)) {
        const msg = "Identity number cannot contain identical repeating digits.";
        showToast(msg, "danger");
        if (errEl) {
            errEl.innerText = msg;
            errEl.style.color = "#dc2626";
            errEl.style.display = "block";
        }
        if (idInput) idInput.focus();
        return;
    }

    try {
        const res = await ApiClient.registerCitizen(name, email, mobile, password, cleanId);
        localStorage.setItem("auth_token", res.token);
        currentUser = res.user;
        showToast("Citizen account registered and identity verified successfully!", "success");
        showAppScreen();
    } catch (err) {
        const errMsg = err.message || "Registration failed";
        showToast(errMsg, "danger");
        if (errEl) {
            errEl.innerText = errMsg;
            errEl.style.color = "#dc2626";
            errEl.style.display = "block";
        }
    }
}

function formatAndValidateIdentityInput(input) {
    if (!input) return;
    let val = input.value.replace(/\D/g, "");
    if (val.length > 12) val = val.substring(0, 12);

    // Format with dashes 4-4-4
    let formatted = "";
    for (let i = 0; i < val.length; i++) {
        if (i === 4 || i === 8) formatted += "-";
        formatted += val[i];
    }
    input.value = formatted;

    const errEl = document.getElementById("c-reg-identity-error");
    if (!errEl) return;

    if (val.length > 0 && (val.startsWith("0") || val.startsWith("1"))) {
        errEl.innerText = "Identity number cannot start with 0 or 1 per government identity standards.";
        errEl.style.color = "#dc2626";
        errEl.style.display = "block";
    } else if (val.length === 12 && /^(\d)\1{11}$/.test(val)) {
        errEl.innerText = "Identity number cannot contain identical repeating digits.";
        errEl.style.color = "#dc2626";
        errEl.style.display = "block";
    } else if (val.length === 12) {
        errEl.style.display = "none";
    } else if (val.length > 0 && val.length < 12) {
        errEl.innerText = `Entering identifier (${val.length}/12 digits)...`;
        errEl.style.color = "var(--text-muted)";
        errEl.style.display = "block";
    } else {
        errEl.style.display = "none";
    }
}

function toggleIdentityVisibility() {
    const input = document.getElementById("c-reg-identity");
    const icon = document.getElementById("icon-toggle-id-mask");
    if (!input) return;
    if (input.type === "password") {
        input.type = "text";
        if (icon) {
            icon.classList.remove("fa-eye");
            icon.classList.add("fa-eye-slash");
        }
    } else {
        input.type = "password";
        if (icon) {
            icon.classList.remove("fa-eye-slash");
            icon.classList.add("fa-eye");
        }
    }
}

async function handleLogout() {
    if (notifPollingInterval) {
        clearInterval(notifPollingInterval);
        notifPollingInterval = null;
    }
    cachedNotifications = [];
    const badge = document.getElementById("noti-badge-count");
    if (badge) badge.style.display = "none";
    clearWorkerCache();
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
    attachedImageDataUrl = null;
    const fileInput = document.getElementById("w-file-input");
    if (fileInput) {
        fileInput.value = "";
        fileInput.style.borderColor = "";
    }
    const previewBox = document.getElementById("file-preview-box");
    if (previewBox) previewBox.innerHTML = '<span style="color:var(--text-muted); font-style:italic;">No image attached yet.</span>';
    renderWizardStep(1);
}

function nextWizardStep(step) {
    if (step > currentWizardStep) {
        // Validate Step 2 inputs before advancing
        if (currentWizardStep === 2) {
            const cat = document.getElementById("w-category") ? document.getElementById("w-category").value : "";
            const loc = document.getElementById("w-location") ? document.getElementById("w-location").value.trim() : "";
            const desc = document.getElementById("w-description") ? document.getElementById("w-description").value.trim() : "";
            if (!cat || !loc || !desc) {
                showToast("Please fill in Category, Municipal Area, and Description!", "danger");
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
        const catEl = document.getElementById("w-category");
        const locEl = document.getElementById("w-location");
        const descEl = document.getElementById("w-description");
        const affEl = document.getElementById("w-affected");

        if (document.getElementById("rev-category")) {
            document.getElementById("rev-category").innerText = catEl ? catEl.value : "-";
        }
        if (document.getElementById("rev-location")) {
            document.getElementById("rev-location").innerText = locEl ? locEl.value : "-";
        }
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
            document.getElementById("rev-affected").innerText = `${affEl ? affEl.value : 1} citizens`;
        }
        if (document.getElementById("rev-description")) {
            document.getElementById("rev-description").innerText = descEl ? descEl.value : "-";
        }

        const revAttachment = document.getElementById("rev-attachment");
        if (revAttachment) {
            if (attachedImageDataUrl) {
                revAttachment.innerHTML = `
                    <div style="display:flex; align-items:center; gap:12px; margin-top:4px;">
                        <img src="${attachedImageDataUrl}" alt="Photo Evidence Preview" style="width:52px; height:52px; object-fit:cover; border-radius:6px; border:1px solid var(--border-color); box-shadow:0 2px 4px rgba(0,0,0,0.1);">
                        <div>
                            <span style="color:#059669; font-weight:600;"><i class="fa-solid fa-circle-check"></i> ${attachedFileName || 'Image Attached'}</span>
                            <div style="font-size:12px; color:var(--text-muted);">Verified photo evidence attached</div>
                        </div>
                    </div>
                `;
            } else {
                revAttachment.innerHTML = `<span style="color:var(--text-muted); font-style:italic;">None Attached (Optional)</span>`;
            }
        }
    }
}

function previewFile(e) {
    const file = e.target.files && e.target.files[0];
    const previewBox = document.getElementById("file-preview-box");
    const input = document.getElementById("w-file-input");
    if (file) {
        if (!file.type.startsWith("image/")) {
            showToast("Only image files (JPG, PNG, WEBP, GIF) are allowed.", "danger");
            e.target.value = "";
            attachedFileName = null;
            attachedImageDataUrl = null;
            if (previewBox) {
                previewBox.innerHTML = `<div style="padding:10px; background:#fee2e2; border:1px solid #f87171; border-radius:6px; color:#991b1b; font-weight:600;"><i class="fa-solid fa-triangle-exclamation"></i> Invalid file type! Please select an image file.</div>`;
            }
            if (input) input.style.borderColor = "#dc2626";
            return;
        }

        attachedFileName = file.name;
        if (input) input.style.borderColor = "#10b981";
        const reader = new FileReader();
        reader.onload = function(evt) {
            const rawDataUrl = evt.target.result;
            const img = new Image();
            img.onload = function() {
                const maxDim = 1200;
                let w = img.width;
                let h = img.height;
                if (w > maxDim || h > maxDim) {
                    if (w > h) {
                        h = Math.round((h * maxDim) / w);
                        w = maxDim;
                    } else {
                        w = Math.round((w * maxDim) / h);
                        h = maxDim;
                    }
                }
                const canvas = document.createElement("canvas");
                canvas.width = w;
                canvas.height = h;
                const ctx = canvas.getContext("2d");
                ctx.drawImage(img, 0, 0, w, h);
                attachedImageDataUrl = canvas.toDataURL("image/jpeg", 0.82);
                if (previewBox) {
                    previewBox.innerHTML = `
                        <div style="display:flex; align-items:center; gap:14px; margin-top:10px; padding:10px 14px; background:var(--bg-surface-secondary); border-radius:8px; border:1px solid #10b981;">
                            <img src="${attachedImageDataUrl}" alt="Photo Evidence Preview" style="width:72px; height:72px; object-fit:cover; border-radius:6px; border:1px solid var(--border-color); box-shadow:0 2px 6px rgba(0,0,0,0.1);">
                            <div>
                                <div style="color:#059669; font-weight:600;"><i class="fa-solid fa-circle-check"></i> Image Uploaded: ${file.name}</div>
                                <div style="font-size:12px; color:var(--text-muted); margin-top:3px;">Size: ${(attachedImageDataUrl.length / 1024).toFixed(1)} KB &bull; Verified site photo</div>
                            </div>
                        </div>
                    `;
                }
            };
            img.onerror = function() {
                attachedImageDataUrl = rawDataUrl;
                if (previewBox) {
                    previewBox.innerHTML = `
                        <div style="display:flex; align-items:center; gap:14px; margin-top:10px; padding:10px 14px; background:var(--bg-surface-secondary); border-radius:8px; border:1px solid #10b981;">
                            <img src="${attachedImageDataUrl}" alt="Photo Evidence Preview" style="width:72px; height:72px; object-fit:cover; border-radius:6px; border:1px solid var(--border-color); box-shadow:0 2px 6px rgba(0,0,0,0.1);">
                            <div>
                                <div style="color:#059669; font-weight:600;"><i class="fa-solid fa-circle-check"></i> Image Uploaded: ${file.name}</div>
                                <div style="font-size:12px; color:var(--text-muted); margin-top:3px;">Verified site photo</div>
                            </div>
                        </div>
                    `;
                }
            };
            img.src = rawDataUrl;
        };
        reader.readAsDataURL(file);
    } else {
        attachedFileName = null;
        attachedImageDataUrl = null;
        if (previewBox) {
            previewBox.innerHTML = '<span style="color:var(--text-muted); font-style:italic;">No image attached yet.</span>';
        }
        if (input) input.style.borderColor = "";
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

    // Check if a file was selected but FileReader is still processing
    const fileInput = document.getElementById("w-file-input");
    if (!attachedImageDataUrl && fileInput && fileInput.files && fileInput.files.length > 0) {
        try {
            const file = fileInput.files[0];
            attachedFileName = file.name;
            attachedImageDataUrl = await new Promise((resolve, reject) => {
                const r = new FileReader();
                r.onload = () => resolve(r.result);
                r.onerror = reject;
                r.readAsDataURL(file);
            });
        } catch (err) {
            console.warn("Could not read attached file:", err);
        }
    }

    const title = document.getElementById("w-title") ? document.getElementById("w-title").value.trim() : "";
    const category = document.getElementById("w-category") ? document.getElementById("w-category").value : "";
    const location = document.getElementById("w-location") ? document.getElementById("w-location").value.trim() : "";
    const affected = document.getElementById("w-affected") ? parseInt(document.getElementById("w-affected").value) : 1;
    let description = document.getElementById("w-description") ? document.getElementById("w-description").value.trim() : "";

    if (!category || !location || !description) {
        showToast("Category, Municipal Area, and Description are required!", "danger");
        nextWizardStep(2);
        return;
    }

    if (attachedFileName) {
        description += ` [Photo Evidence: ${attachedFileName}]`;
    }

    const latStr = document.getElementById("w-latitude") ? document.getElementById("w-latitude").value : "";
    const lngStr = document.getElementById("w-longitude") ? document.getElementById("w-longitude").value : "";
    const accStr = document.getElementById("w-accuracy") ? document.getElementById("w-accuracy").value : "";

    const lat = latStr ? parseFloat(latStr) : null;
    const lng = lngStr ? parseFloat(lngStr) : null;
    const acc = accStr ? parseFloat(accStr) : null;

    const imageToSend = attachedImageDataUrl || "";

    // Automated duplicate detection warning for Citizen submission (Requirement 2)
    if (!window._bypassCitizenDuplicateWarning) {
        try {
            const dupCheck = await ApiClient.checkDuplicates(category, location);
            if (dupCheck && dupCheck.hasDuplicates && dupCheck.duplicates && dupCheck.duplicates.length > 0) {
                openCitizenDupModal(dupCheck.duplicates, {
                    category, location, description, affected, title, lat, lng, acc, imageToSend
                });
                return;
            }
        } catch (ignored) {}
    }
    window._bypassCitizenDuplicateWarning = false;

    try {
        const created = await ApiClient.submitComplaint(category, location, description, affected, title, lat, lng, acc, imageToSend);
        showToast(`Complaint <strong>${created.complaintId}</strong> registered successfully${imageToSend ? ' with photo evidence' : ''}! Submitted for manager risk assessment.`);
        switchView("citizen-dashboard");
    } catch (err) {
        showToast("Failed to submit complaint: " + err.message, "danger");
    }
}

let pendingCitizenSubmission = null;

function openCitizenDupModal(duplicates, formData) {
    pendingCitizenSubmission = formData;
    const container = document.getElementById("citizen-dup-candidate-info");
    if (container) {
        container.innerHTML = duplicates.map(d => `
            <div style="background: var(--bg-surface); border: 1px solid var(--border-color); padding: 10px 14px; border-radius: var(--radius-sm); font-size: 13px;">
                <div style="display:flex; justify-content:space-between; align-items:center;">
                    <strong style="color:var(--primary); font-size:14px;">${escapeHtml(d.complaintId)}</strong>
                    <span class="badge" style="background:#fef3c7; color:#92400e;">${escapeHtml(d.status || 'Registered')}</span>
                </div>
                <div style="margin-top: 4px; color: var(--text-main);">
                    <strong>Category:</strong> ${escapeHtml(d.category)} &bull; <strong>Location:</strong> ${escapeHtml(d.location)}
                </div>
                <div style="font-size: 11px; color: var(--text-muted); margin-top: 4px;">
                    Reported on: ${escapeHtml(d.createdAt || 'Recent')} ${d.citizenName ? `by ${escapeHtml(d.citizenName)}` : ''}
                </div>
            </div>
        `).join("");
    }
    const modal = document.getElementById("citizen-dup-warning-modal");
    if (modal) modal.classList.add("active");
}

function closeCitizenDupModal() {
    const modal = document.getElementById("citizen-dup-warning-modal");
    if (modal) modal.classList.remove("active");
    pendingCitizenSubmission = null;
}

async function proceedSeparateSubmission() {
    if (!pendingCitizenSubmission) {
        closeCitizenDupModal();
        return;
    }
    const data = pendingCitizenSubmission;
    closeCitizenDupModal();
    window._bypassCitizenDuplicateWarning = true;
    try {
        const created = await ApiClient.submitComplaint(
            data.category, data.location, data.description, data.affected, data.title, data.lat, data.lng, data.acc, data.imageToSend
        );
        showToast(`Complaint <strong>${created.complaintId}</strong> registered successfully as a separate issue! Submitted for manager risk assessment.`);
        switchView("citizen-dashboard");
    } catch (err) {
        showToast("Failed to submit complaint: " + err.message, "danger");
    } finally {
        window._bypassCitizenDuplicateWarning = false;
    }
}

// Global State for Citizen Grievances & Live Tracker
let allCitizenComplaints = [];
let citizenFilterStatus = "ALL";
let citizenFilterKeyword = "";
window.activeTrackedComplaint = null;

async function loadCitizenDashboard() {
    try {
        const complaints = await ApiClient.getMyComplaints();
        currentComplaintsList = complaints;
        allCitizenComplaints = complaints;

        // Populate Citizen Hero Banner Info
        if (currentUser) {
            const welcomeEl = document.getElementById("citizen-welcome-name");
            if (welcomeEl) welcomeEl.innerText = currentUser.name || currentUser.username || "Citizen";

            const citIdEl = document.getElementById("citizen-cit-id");
            if (citIdEl) citIdEl.innerText = currentUser.citizenId || (currentUser.id ? "CIT-" + currentUser.id : "CIT-88214");

            const mobileEl = document.getElementById("citizen-mobile-display");
            if (mobileEl) mobileEl.innerText = currentUser.mobile || "+91 98765 43210";
        }

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

        if (document.getElementById("c-stat-total")) document.getElementById("c-stat-total").innerText = total;
        if (document.getElementById("c-stat-pending")) document.getElementById("c-stat-pending").innerText = pending;
        if (document.getElementById("c-stat-progress")) document.getElementById("c-stat-progress").innerText = progress;
        if (document.getElementById("c-stat-resolved")) document.getElementById("c-stat-resolved").innerText = resolved;

        // Update Filter Tab Count Badges
        if (document.getElementById("c-tab-all-cnt")) document.getElementById("c-tab-all-cnt").innerText = total;
        if (document.getElementById("c-tab-pending-cnt")) document.getElementById("c-tab-pending-cnt").innerText = pending;
        if (document.getElementById("c-tab-progress-cnt")) document.getElementById("c-tab-progress-cnt").innerText = progress;
        if (document.getElementById("c-tab-resolved-cnt")) document.getElementById("c-tab-resolved-cnt").innerText = resolved;

        // Apply filters and render complaints table
        applyCitizenFilters();

        // Update Right-side Live Tracker with latest grievance
        if (complaints.length > 0) {
            updateCitizenLiveTracker(complaints[0]);
        }
    } catch (e) {
        showToast("Error loading citizen complaints: " + e.message, "danger");
    }
}

function handleCitizenTokenSearch(tokenQuery) {
    const q = (tokenQuery || "").trim().toUpperCase();
    if (!q) {
        applyCitizenFilters();
        return;
    }
    const matched = allCitizenComplaints.filter(c => 
        (c.complaintId || "").toUpperCase().includes(q) ||
        (c.category || "").toUpperCase().includes(q) ||
        (c.title || "").toUpperCase().includes(q)
    );
    renderCitizenComplaintsTable(matched);
    if (matched.length > 0) {
        updateCitizenLiveTracker(matched[0]);
    }
}

function handleCitizenKeywordFilter(keyword) {
    citizenFilterKeyword = (keyword || "").trim().toLowerCase();
    applyCitizenFilters();
}

function filterCitizenTable(status) {
    citizenFilterStatus = status;
    ["all", "pending", "progress", "resolved"].forEach(t => {
        const el = document.getElementById("c-tab-" + t);
        if (el) el.classList.remove("active");
    });
    const activeBtn = document.getElementById(
        status === "ALL" ? "c-tab-all" :
        status === "PENDING" ? "c-tab-pending" :
        status === "IN_PROGRESS" ? "c-tab-progress" : "c-tab-resolved"
    );
    if (activeBtn) activeBtn.classList.add("active");
    applyCitizenFilters();
}

function applyCitizenFilters() {
    let filtered = allCitizenComplaints.filter(c => {
        const s = (c.status || "").toUpperCase();
        if (citizenFilterStatus === "PENDING") {
            return s === "REGISTERED" || s === "UNDER REVIEW" || s === "PENDING" || s === "ASSIGNED";
        } else if (citizenFilterStatus === "IN_PROGRESS") {
            return s === "IN_PROGRESS" || s === "IN PROGRESS";
        } else if (citizenFilterStatus === "RESOLVED") {
            return s === "RESOLVED" || s === "CLOSED";
        }
        return true;
    });

    if (citizenFilterKeyword) {
        filtered = filtered.filter(c => {
            const str = ((c.complaintId || '') + ' ' + (c.category || '') + ' ' + (c.location || '') + ' ' + (c.description || '') + ' ' + (c.department || '')).toLowerCase();
            return str.includes(citizenFilterKeyword);
        });
    }

    renderCitizenComplaintsTable(filtered);
}

function renderCitizenComplaintsTable(complaints) {
    const tbody = document.getElementById("citizen-complaints-tbody");
    if (!tbody) return;

    if (complaints.length === 0) {
        tbody.innerHTML = `
            <tr>
                <td colspan="8">
                    <div class="empty-state" style="padding: 32px; text-align: center; color: var(--text-muted);">
                        <i class="fa-solid fa-folder-open" style="font-size: 28px; margin-bottom: 8px; display: block;"></i>
                        <h4 style="color: var(--text-main); margin-bottom: 4px;">No matching grievances found</h4>
                        <p style="font-size: 13px;">No complaints match the selected filter criteria. Click "New Complain" to submit one.</p>
                    </div>
                </td>
            </tr>
        `;
        return;
    }

    tbody.innerHTML = complaints.map(c => {
        const photoThumb = c.imageUrl ? `<img src="${c.imageUrl}" alt="Photo Evidence" style="width:36px; height:36px; object-fit:cover; border-radius:4px; border:1px solid var(--border-color); cursor:pointer; margin-right:8px; vertical-align:middle;" onclick="openImageLightbox('${c.imageUrl}', 'Complaint ${c.complaintId} - Site Photo Evidence')" title="Click to view full photo clearly">` : '';
        const isUrgent = (c.priority || "").toUpperCase() === "CRITICAL" || (c.priority || "").toUpperCase() === "URGENT";
        const rowClass = isUrgent ? 'class="stitch-table-urgent-row"' : '';
        return `
        <tr ${rowClass}>
            <td><strong class="font-data-mono" style="color: var(--primary); cursor: pointer;" onclick="openComplaintModal('${c.complaintId}')" title="Click to view case timeline & details">#${c.complaintId}</strong></td>
            <td><div style="display:flex; align-items:center;">${photoThumb}<span>${escapeHtml(c.category)}</span></div></td>
            <td>${escapeHtml(c.location)}</td>
            <td>${getPriorityBadge(c.priority)}</td>
            <td>${escapeHtml(c.department || 'Awaiting Routing')}</td>
            <td>${getStatusBadge(c.status, c.isWaiting)}</td>
            <td class="font-data-mono" style="font-size: 12px; color: var(--text-muted);">${c.createdAt || 'Today'}</td>
            <td style="text-align: right;">
                <div class="table-action-group" style="justify-content: flex-end;">
                    <button class="btn btn-secondary btn-sm" onclick="updateCitizenLiveTrackerById('${c.complaintId}'); openComplaintModal('${c.complaintId}')" title="View Case Timeline & Details" style="font-size: 12px; padding: 4px 10px;">
                        <span class="material-symbols-outlined" style="font-size: 14px;">visibility</span> <span>Track</span>
                    </button>
                </div>
            </td>
        </tr>
    `;
    }).join("");
}

function updateCitizenLiveTracker(c) {
    if (!c) return;
    window.activeTrackedComplaint = c;

    const tokenEl = document.getElementById("citizen-tracker-token");
    if (tokenEl) tokenEl.innerText = "#" + (c.complaintId || "");

    const priEl = document.getElementById("citizen-tracker-priority");
    if (priEl) priEl.innerHTML = getPriorityBadge(c.priority);

    const titleEl = document.getElementById("citizen-tracker-title");
    if (titleEl) titleEl.innerText = c.title || c.category || "Municipal Grievance";

    const descEl = document.getElementById("citizen-tracker-desc");
    if (descEl) descEl.innerText = c.description ? (c.description.length > 80 ? c.description.slice(0, 80) + '...' : c.description) : ("Location: " + (c.location || "Makkal Nagar"));

    // Step calculation
    const status = (c.status || "").toUpperCase().replace(/[\s_-]/g, "");
    const step1 = document.getElementById("track-step-1");
    const step2 = document.getElementById("track-step-2");
    const step3 = document.getElementById("track-step-3");
    const step4 = document.getElementById("track-step-4");

    const step1Desc = document.getElementById("track-step-1-desc");
    const step2Desc = document.getElementById("track-step-2-desc");
    const step3Desc = document.getElementById("track-step-3-desc");
    const step4Desc = document.getElementById("track-step-4-desc");

    if (step1Desc) step1Desc.innerText = c.createdAt ? `${c.createdAt} • Citizen Portal` : "Citizen Portal Submission Validated";

    if (c.department) {
        if (step2) { step2.className = "stitch-step-item completed"; step2.querySelector(".stitch-step-node").innerHTML = '<i class="fa-solid fa-check" style="font-size: 10px;"></i>'; }
        if (step2Desc) step2Desc.innerText = `Routed to ${c.department}`;
    } else {
        if (step2) { step2.className = "stitch-step-item active"; step2.querySelector(".stitch-step-node").innerHTML = '<i class="fa-solid fa-rotate" style="font-size: 10px;"></i>'; }
        if (step2Desc) step2Desc.innerText = "Awaiting Department Allocation";
    }

    const workerChip = document.getElementById("citizen-tracker-worker-chip");
    if (status === "RESOLVED" || status === "CLOSED") {
        if (step3) { step3.className = "stitch-step-item completed"; step3.querySelector(".stitch-step-node").innerHTML = '<i class="fa-solid fa-check" style="font-size: 10px;"></i>'; }
        if (step4) { step4.className = "stitch-step-item completed"; step4.querySelector(".stitch-step-node").innerHTML = '<i class="fa-solid fa-check" style="font-size: 10px;"></i>'; }
        if (step3Desc) step3Desc.innerText = c.assignedStaff ? `Completed by ${c.assignedStaff}` : "Work Completed by Field Crew";
        if (step4Desc) step4Desc.innerText = "Resolved & Verified by Department Lead";
        if (workerChip) workerChip.style.display = "none";
    } else if (status === "INPROGRESS") {
        if (step3) { step3.className = "stitch-step-item active"; step3.querySelector(".stitch-step-node").innerHTML = '<i class="fa-solid fa-rotate" style="font-size: 10px;"></i>'; }
        if (step4) { step4.className = "stitch-step-item pending"; step4.querySelector(".stitch-step-node").innerHTML = '<i class="fa-solid fa-circle" style="font-size: 6px;"></i>'; }
        if (step3Desc) step3Desc.innerText = c.assignedStaff ? `Technician ${c.assignedStaff} on ground` : "Field Technician Dispatched on Site";
        if (step4Desc) step4Desc.innerText = "Pending Site Clearance & Verification";
        renderWorkerChip(c.assignedStaff, c.department);
    } else if (status === "ASSIGNED") {
        if (step3) { step3.className = "stitch-step-item active"; step3.querySelector(".stitch-step-node").innerHTML = '<i class="fa-solid fa-user-check" style="font-size: 10px;"></i>'; }
        if (step4) { step4.className = "stitch-step-item pending"; step4.querySelector(".stitch-step-node").innerHTML = '<i class="fa-solid fa-circle" style="font-size: 6px;"></i>'; }
        if (step3Desc) step3Desc.innerText = c.assignedStaff ? `Work order assigned to ${c.assignedStaff}` : "Work Order Dispatched to Field Squad";
        if (step4Desc) step4Desc.innerText = "Pending Field Execution";
        renderWorkerChip(c.assignedStaff, c.department);
    } else {
        if (step3) { step3.className = "stitch-step-item pending"; step3.querySelector(".stitch-step-node").innerHTML = '<i class="fa-solid fa-circle" style="font-size: 6px;"></i>'; }
        if (step4) { step4.className = "stitch-step-item pending"; step4.querySelector(".stitch-step-node").innerHTML = '<i class="fa-solid fa-circle" style="font-size: 6px;"></i>'; }
        if (step3Desc) step3Desc.innerText = "Pending Technician Dispatch";
        if (step4Desc) step4Desc.innerText = "Pending Site Clearance & Verification";
        if (workerChip) workerChip.style.display = "none";
    }
}

function renderWorkerChip(workerName, department) {
    const chip = document.getElementById("citizen-tracker-worker-chip");
    if (!chip) return;
    if (!workerName) {
        chip.style.display = "none";
        return;
    }
    chip.style.display = "flex";
    const initials = workerName.split(" ").map(w => w[0]).join("").toUpperCase().slice(0, 2) || "W";
    const initEl = document.getElementById("c-track-w-initials");
    if (initEl) initEl.innerText = initials;
    const nameEl = document.getElementById("c-track-w-name");
    if (nameEl) nameEl.innerText = workerName;
    const deptEl = document.getElementById("c-track-w-dept");
    if (deptEl) deptEl.innerText = department || "Department Field Squad";
}

function updateCitizenLiveTrackerById(complaintId) {
    const c = allCitizenComplaints.find(x => x.complaintId === complaintId);
    if (c) {
        updateCitizenLiveTracker(c);
    }
}

function viewComplaintLocationFromTracker() {
    if (window.activeTrackedComplaint) {
        openComplaintModal(window.activeTrackedComplaint.complaintId);
    } else if (allCitizenComplaints && allCitizenComplaints.length > 0) {
        openComplaintModal(allCitizenComplaints[0].complaintId);
    }
}

function exportGrievanceDocket() {
    const list = currentComplaintsList || [];
    if (list.length === 0) {
        showToast("No complaints currently loaded to export.", "warning");
        return;
    }
    const headers = ["Complaint ID", "Citizen", "Category", "Location", "Priority", "Department", "Assigned Staff", "Status", "Created Date"];
    const rows = list.map(c => [
        c.complaintId,
        c.citizenName || c.citizenUsername || "",
        c.category || "",
        c.location || "",
        c.priority || "",
        c.department || "",
        c.assignedStaff || "",
        c.status || "",
        c.createdAt || ""
    ]);
    const csvContent = "data:text/csv;charset=utf-8," + [headers.join(","), ...rows.map(e => e.map(x => `"${String(x).replace(/"/g, '""')}"`).join(","))].join("\n");
    const encodedUri = encodeURI(csvContent);
    const link = document.createElement("a");
    link.setAttribute("href", encodedUri);
    link.setAttribute("download", `Grievance_Docket_${new Date().toISOString().slice(0,10)}.csv`);
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    showToast("Grievance Docket CSV successfully exported.", "success");
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

                const photoThumb = c.imageUrl ? `<img src="${c.imageUrl}" alt="Evidence" style="width:38px; height:38px; object-fit:cover; border-radius:4px; border:1px solid var(--border-color); cursor:pointer; vertical-align:middle; margin-right:8px;" onclick="openImageLightbox('${c.imageUrl}', 'Complaint ${c.complaintId} - Site Photo Evidence')" title="Click to view full size photo">` : '';

                return `
                <tr>
                    <td><strong>${c.complaintId}</strong></td>
                    <td>
                        <div style="display:flex; align-items:center;">
                            ${photoThumb}
                            <div>
                                <span>${escapeHtml(c.category)}</span><br>
                                <small style="color:var(--text-muted);">${locHtml}</small><br>
                                <span style="font-size:11px; color:var(--primary); font-weight:600;"><i class="fa-solid fa-clipboard-check"></i> Action: ${escapeHtml(c.operationalAction || 'Field Inspection & Maintenance')}</span>
                            </div>
                        </div>
                    </td>
                    <td>${getPriorityBadge(c.priority)}<br><small style="color:var(--text-muted);">${c.citizenReportedImpact || c.affectedPeople || 1} people affected</small></td>
                    <td>${escapeHtml(c.citizenName || 'Citizen')}</td>
                    <td>${getStatusBadge(c.status, c.isWaiting)}</td>
                    <td>${c.createdAt || 'Today'}</td>
                    <td>
                        <div class="table-action-group">
                            ${actionBtn}
                            ${c.imageUrl ? `<button class="btn btn-secondary btn-sm" onclick="openImageLightbox('${c.imageUrl}', 'Complaint ${c.complaintId} - Site Photo Evidence')" title="Click to view image clearly" style="font-size:11px; padding:2px 8px;"><i class="fa-solid fa-camera"></i> Image</button>` : ''}
                            <button class="btn btn-secondary btn-sm" onclick="openComplaintModal('${c.complaintId}')" title="View Case Details & Photo Evidence"><i class="fa-solid fa-eye"></i></button>
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

                    const photoThumb = c.imageUrl ? `<img src="${c.imageUrl}" alt="Photo" style="width:36px; height:36px; object-fit:cover; border-radius:4px; border:1px solid var(--border-color); cursor:pointer; vertical-align:middle; margin-right:8px;" onclick="openImageLightbox('${c.imageUrl}', 'Complaint ${c.complaintId} - Site Photo Evidence')" title="Click to view photo evidence">` : '';

                    return `
                    <tr>
                        <td><strong>${c.complaintId}</strong><br><small style="color:var(--text-muted);">${c.createdAt || ''}</small></td>
                        <td><strong>${escapeHtml(c.citizenName || 'Citizen')}</strong><br><small style="color:var(--text-muted);">${escapeHtml(c.description ? (c.description.length > 50 ? c.description.substring(0, 50) + '...' : c.description) : '')}</small></td>
                        <td>
                            <div style="display:flex; align-items:center;">
                                ${photoThumb}
                                <span class="badge" style="background:var(--primary-light); color:var(--primary);">${escapeHtml(c.category)}</span>
                            </div>
                        </td>
                        <td>${locCell}</td>
                        <td>${assessmentBadge}<br><small style="color:var(--text-muted);">${c.citizenReportedImpact || c.affectedPeople || 1} affected</small></td>
                        <td>${c.arrivalOrder ? '#' + c.arrivalOrder : (c.arrivalSequence ? '#' + c.arrivalSequence : '-')}</td>
                        <td>${c.duplicateCandidates && c.duplicateCandidates.length > 0 ? '<span class="badge" style="background:#fef3c7; color:#92400e;"><i class="fa-solid fa-clone"></i> ' + c.duplicateCandidates.length + ' Candidates</span>' : '<span style="color:var(--text-dim);">None</span>'}</td>
                        <td>
                            <div class="table-action-group">
                                ${(() => {
                                    const ns = (c.status || "").toUpperCase().replace(/[\s_-]/g, "");
                                    if (ns === "RESOLVED" || ns === "CLOSED") return "";
                                    if (ns === "ASSIGNED" || ns === "INPROGRESS") {
                                        return `<button class="btn btn-warning btn-sm" onclick="openReassignModal('${c.complaintId}')" style="background:#d97706; color:#fff; border-color:#b45309;"><i class="fa-solid fa-arrows-rotate"></i> Reassign</button>`;
                                    }
                                    return `<button class="btn btn-primary btn-sm" onclick="openAssignModal('${c.complaintId}')"><i class="fa-solid fa-stamp"></i> Assess & Assign</button>`;
                                })()}
                                ${c.imageUrl ? `<button class="btn btn-secondary btn-sm" onclick="openImageLightbox('${c.imageUrl}', 'Complaint ${c.complaintId} - Site Photo Evidence')" title="Click to view image clearly" style="font-size:11px; padding:2px 8px;"><i class="fa-solid fa-camera"></i> Image</button>` : ''}
                                <button class="btn btn-secondary btn-sm" onclick="openComplaintModal('${c.complaintId}')" title="View Case File & Photo Evidence">
                                    <i class="fa-solid fa-eye"></i>
                                </button>
                                <button class="btn btn-secondary btn-sm" onclick="confirmDeleteComplaint('${c.complaintId}')" title="Delete Complaint" style="color:#dc2626; border-color:#fecaca; background:#fff5f5;">
                                    <i class="fa-solid fa-trash-can"></i>
                                </button>
                            </div>
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

// Worker Management & Dropdown Population Logic
let cachedWorkers = [];

function clearWorkerCache() {
    cachedWorkers = [];
}

async function getOrFetchWorkers() {
    if (cachedWorkers && cachedWorkers.length > 0) return cachedWorkers;
    try {
        cachedWorkers = await ApiClient.getWorkers();
        return cachedWorkers;
    } catch (err) {
        console.warn("Could not fetch workers:", err);
        return [];
    }
}

function matchWorkerDepartment(workerDept, targetDept) {
    if (!workerDept || !targetDept) return false;
    const w = workerDept.toLowerCase().trim();
    const t = targetDept.toLowerCase().trim();
    if (w === t) return true;
    if (w.includes("water") && t.includes("water")) return true;
    if (w.includes("road") && t.includes("road")) return true;
    if ((w.includes("waste") || w.includes("sanitat")) && (t.includes("waste") || t.includes("sanitat"))) return true;
    if ((w.includes("light") || w.includes("electric")) && (t.includes("light") || t.includes("electric"))) return true;
    if (w.includes("drain") && t.includes("drain")) return true;
    if (w.includes("gen") && t.includes("gen")) return true;
    return w.includes(t) || t.includes(w);
}

async function populateWorkerDropdown(deptSelectId, workerSelectId, selectedWorkerName = "") {
    const deptSelect = document.getElementById(deptSelectId);
    const workerSelect = document.getElementById(workerSelectId);
    if (!deptSelect || !workerSelect) return;

    const chosenDept = deptSelect.value;
    const workers = await getOrFetchWorkers();
    
    // Filter active workers in this department
    const deptWorkers = workers.filter(w => (w.active !== false) && matchWorkerDepartment(w.department, chosenDept));

    workerSelect.innerHTML = `<option value="">-- Auto-Assign / Department Pool --</option>`;
    deptWorkers.forEach(w => {
        const opt = document.createElement("option");
        opt.value = w.name;
        opt.textContent = `👷 ${w.name} (${w.id || 'Worker'})`;
        workerSelect.appendChild(opt);
    });

    if (deptWorkers.length === 0) {
        const noOpt = document.createElement("option");
        noOpt.disabled = true;
        noOpt.textContent = `(No specific field workers registered in ${chosenDept})`;
        workerSelect.appendChild(noOpt);
    }

    if (selectedWorkerName && selectedWorkerName.trim() && !selectedWorkerName.startsWith("Queued")) {
        const cleanSelected = selectedWorkerName.trim();
        let matched = false;
        for (let i = 0; i < workerSelect.options.length; i++) {
            if (workerSelect.options[i].value === cleanSelected || workerSelect.options[i].value.includes(cleanSelected) || cleanSelected.includes(workerSelect.options[i].value)) {
                workerSelect.selectedIndex = i;
                matched = true;
                break;
            }
        }
        if (!matched && cleanSelected !== "Unassigned" && cleanSelected !== "Assigned Officer") {
            const customOpt = document.createElement("option");
            customOpt.value = cleanSelected;
            customOpt.textContent = `👷 ${cleanSelected} (Current Assigned)`;
            customOpt.selected = true;
            workerSelect.appendChild(customOpt);
        }
    }
}

async function onAssignModalDeptChange() {
    await updateAssignCapacityFeedback();
    await populateWorkerDropdown("assign-modal-dept-select", "assign-modal-staff-select");
}

async function onComplaintModalDeptChange() {
    await populateWorkerDropdown("s-assign-dept-select", "s-assign-staff-select");
}

// Assignment Modal Logic
let currentAssigningComplaint = null;

async function openAssignModal(complaintId) {
    if (!currentUser || (currentUser.role !== 'ADMIN' && currentUser.role !== 'MANAGER')) {
        showToast("Forbidden: Only Administrators and Managers can assign complaints.", "danger");
        return;
    }

    const complaint = currentComplaintsList.find(c => c.complaintId === complaintId);
    if (!complaint) return;

    const normStatus = (complaint.status || "").toUpperCase().replace(/[\s_-]/g, "");
    if (normStatus === "RESOLVED" || normStatus === "CLOSED") {
        showToast("Invalid assignment: Resolved complaints are closed and cannot be assigned.", "warning");
        return;
    }

    if (normStatus === "ASSIGNED" || normStatus === "INPROGRESS") {
        openReassignModal(complaintId);
        return;
    }

    currentAssigningComplaint = complaint;
    document.getElementById("assign-modal-comp-id").innerText = complaint.complaintId;
    document.getElementById("assign-modal-cat").innerText = complaint.category;

    // Display comprehensive complaint summary in modal
    let gpsSnippet = "";
    if (complaint.latitude && complaint.longitude) {
        gpsSnippet = `<br><span style="color:#059669; font-weight:600;"><i class="fa-solid fa-location-crosshairs"></i> GPS: ${complaint.latitude}, ${complaint.longitude}</span> &bull; <a href="https://www.google.com/maps?q=${complaint.latitude},${complaint.longitude}" target="_blank" rel="noopener" style="color:var(--primary); text-decoration:underline;">View on Google Maps</a>`;
    }
    let photoSnippet = "";
    if (complaint.imageUrl) {
        photoSnippet = `
            <div style="display:flex; align-items:center; gap:14px; margin-top:10px; padding:10px 14px; background:var(--bg-surface-secondary); border-radius:8px; border:1px solid #3b82f6;">
                <img src="${complaint.imageUrl}" alt="Photo Evidence" style="width:72px; height:56px; object-fit:cover; border-radius:6px; cursor:pointer; border:1px solid var(--border-color); box-shadow:0 2px 6px rgba(0,0,0,0.1);" onclick="openImageLightbox('${complaint.imageUrl}', 'Complaint ${complaint.complaintId} - Site Photo Evidence')">
                <div style="flex:1;">
                    <div style="font-weight:600; font-size:13px; color:var(--text-main);"><i class="fa-solid fa-camera" style="color:#2563eb;"></i> Citizen Site Photo Evidence</div>
                    <div style="font-size:11px; color:var(--text-muted); margin-bottom:5px;">Compulsory uploaded evidence photo</div>
                    <button type="button" class="btn btn-sm btn-primary" style="background:#2563eb; color:#fff; font-size:12px; font-weight:600; padding:4px 12px;" onclick="openImageLightbox('${complaint.imageUrl}', 'Complaint ${complaint.complaintId} - Site Photo Evidence')">
                        <i class="fa-solid fa-magnifying-glass-plus"></i> Click to View Image Clearly
                    </button>
                </div>
            </div>
        `;
    }

    const detailsBox = document.getElementById("assign-modal-details-summary");
    if (detailsBox) {
        detailsBox.innerHTML = `
            <div><strong>Municipal Area:</strong> ${escapeHtml(complaint.location)}${gpsSnippet}</div>
            <div><strong>Affected People:</strong> ${complaint.citizenReportedImpact || complaint.affectedPeople || 1} citizens</div>
            <div><strong>Registered At:</strong> ${complaint.createdAt || 'N/A'}</div>
            <div><strong>Current Assessment:</strong> ${complaint.isAssessed ? getPriorityBadge(complaint.severity) : '<span class="badge" style="background:#f1f5f9; color:#475569;"><i class="fa-solid fa-hourglass"></i> Unassessed</span>'}</div>
            <div style="margin-top:4px; font-style:italic; color:var(--text-muted); font-size:12px;">"${escapeHtml(complaint.description || '')}"</div>
            ${photoSnippet}
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
    const targetDept = complaint.department && complaint.department !== "Unassigned" ? complaint.department : suggestedDept;
    document.getElementById("assign-modal-dept-select").value = targetDept;
    document.getElementById("assign-modal-reason-input").value = complaint.assessmentReason || "";

    await populateWorkerDropdown("assign-modal-dept-select", "assign-modal-staff-select", complaint.assignedStaff);
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
    const staffSelect = document.getElementById("assign-modal-staff-select");
    const staff = staffSelect ? staffSelect.value.trim() : "";

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
        showToast(`Complaint <strong>${id}</strong> assessed as <strong>${severity}</strong> and routed to ${dept}${staff ? ` (Assigned to ${staff})` : ''}!`);
        closeAssignModal();
        await refreshCurrentView();
        const complaintModal = document.getElementById("complaint-modal");
        if (complaintModal && complaintModal.classList.contains("active") && activeComplaintId === id) {
            openComplaintModal(id);
        }
    } catch (err) {
        showToast("Assessment and assignment failed: " + err.message, "danger");
    }
}

// ------------------- REASSIGNMENT MODAL LOGIC ------------------- //
let currentReassigningComplaint = null;

async function openReassignModal(complaintId) {
    if (!currentUser || (currentUser.role !== 'ADMIN' && currentUser.role !== 'MANAGER')) {
        showToast("Forbidden: Only Administrators and Managers can reassign complaints.", "danger");
        return;
    }

    const complaint = currentComplaintsList.find(c => c.complaintId === complaintId);
    if (!complaint) return;

    const normStatus = (complaint.status || "").toUpperCase().replace(/[\s_-]/g, "");
    if (normStatus === "RESOLVED" || normStatus === "CLOSED") {
        showToast("Invalid reassignment: Resolved complaints are closed and cannot be reassigned.", "danger");
        return;
    }

    currentReassigningComplaint = complaint;

    const idEl = document.getElementById("reassign-modal-comp-id");
    if (idEl) idEl.innerText = complaint.complaintId;
    const deptEl = document.getElementById("reassign-modal-current-dept");
    if (deptEl) deptEl.innerText = complaint.department || "Unassigned";
    const staffEl = document.getElementById("reassign-modal-current-staff");
    if (staffEl) staffEl.innerText = complaint.assignedStaff || "Unassigned";
    const statusEl = document.getElementById("reassign-modal-current-status");
    if (statusEl) statusEl.innerHTML = getStatusBadge(complaint.status, complaint.isWaiting);

    const reasonInput = document.getElementById("reassign-modal-reason-input");
    if (reasonInput) reasonInput.value = "";
    const checkEl = document.getElementById("reassign-modal-confirm-check");
    if (checkEl) checkEl.checked = false;

    const targetDeptSelect = document.getElementById("reassign-modal-dept-select");
    if (targetDeptSelect) {
        targetDeptSelect.value = complaint.department && complaint.department !== "Unassigned" ? complaint.department : "Water Supply";
    }

    await populateWorkerDropdown("reassign-modal-dept-select", "reassign-modal-staff-select", complaint.assignedStaff);
    await updateReassignCapacityFeedback();

    const modal = document.getElementById("reassign-modal");
    if (modal) modal.classList.add("active");
}

function closeReassignModal() {
    const modal = document.getElementById("reassign-modal");
    if (modal) modal.classList.remove("active");
    currentReassigningComplaint = null;
}

async function updateReassignCapacityFeedback() {
    const deptSelect = document.getElementById("reassign-modal-dept-select");
    const infoBox = document.getElementById("reassign-modal-capacity-info");
    if (!deptSelect || !infoBox) return;

    const chosenDept = deptSelect.value;
    try {
        const depts = await ApiClient.getDepartmentCapacity();
        const d = depts.find(item => (item.departmentName || "").toLowerCase().includes(chosenDept.toLowerCase()));
        if (d) {
            const hasRoom = d.currentLoad < d.capacity;
            if (hasRoom) {
                infoBox.innerHTML = `<span style="color:#059669; font-weight:600;"><i class="fa-solid fa-circle-check"></i> Available Capacity:</span> ${d.currentLoad} of ${d.capacity} active. Reassignment will take effect immediately.`;
            } else {
                infoBox.innerHTML = `<span style="color:#d97706; font-weight:600;"><i class="fa-solid fa-hourglass-half"></i> Department Full (${d.currentLoad}/${d.capacity}):</span> Complaint will enter destination department priority waiting queue.`;
            }
        }
    } catch (ignored) {}
}

async function onReassignModalDeptChange() {
    await populateWorkerDropdown("reassign-modal-dept-select", "reassign-modal-staff-select");
}

async function handleConfirmReassign(e) {
    e.preventDefault();
    if (!currentReassigningComplaint) return;

    const id = currentReassigningComplaint.complaintId;
    const dept = document.getElementById("reassign-modal-dept-select").value;
    const staffSelect = document.getElementById("reassign-modal-staff-select");
    const staff = staffSelect ? staffSelect.value.trim() : "";
    const reason = document.getElementById("reassign-modal-reason-input").value.trim();
    const confirmed = document.getElementById("reassign-modal-confirm-check").checked;

    if (!reason) {
        showToast("Reassignment reason is mandatory.", "danger");
        return;
    }

    if (!confirmed) {
        showToast("Please check the confirmation box to authorize reassignment.", "warning");
        return;
    }

    try {
        await ApiClient.reassignDepartment(id, dept, staff, reason);
        showToast(`Complaint <strong>${id}</strong> successfully reassigned to ${dept}${staff ? ` (${staff})` : ''}!`);
        closeReassignModal();
        await refreshCurrentView();
        const complaintModal = document.getElementById("complaint-modal");
        if (complaintModal && complaintModal.classList.contains("active") && activeComplaintId === id) {
            openComplaintModal(id);
        }
    } catch (err) {
        showToast("Reassignment failed: " + err.message, "danger");
    }
}

function handleStaffReassignWithConfirmation() {
    if (!activeComplaintId) return;
    openReassignModal(activeComplaintId);
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

// ------------------- COMPLAINT DELETION (ADMIN & MANAGER) ------------------- //
let pendingDeleteComplaintId = null;

function confirmDeleteComplaint(complaintId) {
    if (!complaintId) return;
    pendingDeleteComplaintId = complaintId;
    const disp = document.getElementById("del-modal-complaint-id");
    if (disp) disp.innerText = complaintId;
    const reasonInput = document.getElementById("del-modal-reason");
    if (reasonInput) reasonInput.value = "";
    const modal = document.getElementById("delete-complaint-modal");
    if (modal) modal.classList.add("active");
}

function closeDeleteComplaintModal() {
    const modal = document.getElementById("delete-complaint-modal");
    if (modal) modal.classList.remove("active");
    pendingDeleteComplaintId = null;
}

function confirmDeleteCurrentComplaint() {
    if (activeComplaintId) {
        confirmDeleteComplaint(activeComplaintId);
    }
}

async function executeComplaintDeletion() {
    if (!pendingDeleteComplaintId) return;
    const reasonInput = document.getElementById("del-modal-reason");
    const reason = reasonInput ? reasonInput.value.trim() : "";
    if (!reason) {
        showToast("Deletion reason is required for accountability and audit logging.", "danger");
        if (reasonInput) reasonInput.focus();
        return;
    }

    const btn = document.getElementById("btn-confirm-delete-complaint");
    if (btn) btn.disabled = true;

    const idToDelete = pendingDeleteComplaintId;
    try {
        await ApiClient.deleteComplaint(idToDelete, reason);
        showToast(`Complaint <strong>${idToDelete}</strong> deleted successfully.`);
        closeDeleteComplaintModal();
        if (activeComplaintId === idToDelete) {
            closeModal();
        }
        refreshCurrentView();
        if (currentUser && currentUser.role === "ADMIN") {
            loadAdminDashboard();
        }
        if (currentUser && currentUser.role === "MANAGER") {
            loadManagerDashboard();
        }
    } catch (err) {
        showToast("Failed to delete complaint: " + err.message, "danger");
    } finally {
        if (btn) btn.disabled = false;
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

    // Synchronize left sidebar active state with active admin subtab
    const adminWorkersLink = document.querySelector('.sidebar .nav-link[data-view="admin-dashboard"]');
    const regCitizensLink = document.querySelector('.sidebar .nav-link[data-view="registered-citizens"]');
    if (subtab === "citizens") {
        if (adminWorkersLink) adminWorkersLink.classList.remove("active");
        if (regCitizensLink) regCitizensLink.classList.add("active");
        loadAdminCitizens();
    } else {
        if (regCitizensLink) regCitizensLink.classList.remove("active");
        if (adminWorkersLink) adminWorkersLink.classList.add("active");
    }
}

let currentAdminWorkers = [];
let currentWorkerModalMode = 'single';

async function loadAdminDashboard() {
    try {
        const [workers, complaints, depts, adminReport, citizens] = await Promise.all([
            ApiClient.getWorkers().catch(() => []),
            ApiClient.getAllComplaints().catch(() => []),
            ApiClient.getDepartmentCapacity().catch(() => []),
            ApiClient.getAdminReport().catch(() => null),
            ApiClient.getRegisteredCitizens().catch(() => [])
        ]);

        currentAdminWorkers = workers || [];
        currentAdminCitizens = (Array.isArray(citizens) ? citizens : []).filter(c => !c.role || c.role.toUpperCase() === "CITIZEN");

        const admTotal = complaints.length;
        const admPending = complaints.filter(c => {
            const s = (c.status || "").toUpperCase();
            return s === "REGISTERED" || s === "UNDER REVIEW" || s === "PENDING" || s === "ASSIGNED";
        }).length;
        const admProgress = complaints.filter(c => {
            const s = (c.status || "").toUpperCase();
            return s === "IN_PROGRESS" || s === "IN PROGRESS";
        }).length;
        const admResolved = complaints.filter(c => (c.status || "").toUpperCase() === "RESOLVED").length;

        if (document.getElementById("adm-stat-complaints")) document.getElementById("adm-stat-complaints").innerText = admTotal;
        if (document.getElementById("adm-stat-pending")) document.getElementById("adm-stat-pending").innerText = admPending;
        if (document.getElementById("adm-stat-progress")) document.getElementById("adm-stat-progress").innerText = admProgress;
        if (document.getElementById("adm-stat-resolved")) document.getElementById("adm-stat-resolved").innerText = admResolved;
        if (document.getElementById("adm-stat-workers")) document.getElementById("adm-stat-workers").innerText = currentAdminWorkers.filter(w => w.active !== false).length;
        if (document.getElementById("adm-stat-citizens")) document.getElementById("adm-stat-citizens").innerText = currentAdminCitizens.length;
        if (document.getElementById("adm-compliance-rate")) {
            document.getElementById("adm-compliance-rate").innerText = admTotal > 0 ? ((admResolved / admTotal) * 100).toFixed(1) + "%" : "100%";
        }

        // 1. Render Workers Table with Search & Department Filter
        renderAdminWorkersTable();

        // 2. Render Citizens Table with Search & Status Filter & Pagination
        renderAdminCitizensTable();

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
                        <strong>Persistent Store:</strong> Authoritative database storage with password hashing (SHA-256) and strict server-side session role validation on all protected endpoints.
                    </div>
                </div>
            `;
        }
    } catch (e) {
        showToast("Error loading admin dashboard: " + e.message, "danger");
    }
}

function handleWorkerFilterChange() {
    renderAdminWorkersTable();
}

function resetWorkerFilters() {
    const s = document.getElementById("adm-worker-search");
    if (s) s.value = "";
    const d = document.getElementById("adm-worker-dept-filter");
    if (d) d.value = "";
    const st = document.getElementById("adm-worker-status-filter");
    if (st) st.value = "";
    renderAdminWorkersTable();
}

function renderAdminWorkersTable() {
    const workersTbody = document.getElementById("adm-workers-tbody");
    if (!workersTbody) return;

    const query = (document.getElementById("adm-worker-search")?.value || "").toLowerCase().trim();
    const deptFilter = (document.getElementById("adm-worker-dept-filter")?.value || "").toLowerCase().trim();
    const statusFilter = (document.getElementById("adm-worker-status-filter")?.value || "").toLowerCase().trim();

    const filtered = (currentAdminWorkers || []).filter(w => {
        const id = (w.workerId || w.id || "").toLowerCase();
        const name = (w.name || w.staffName || w.displayName || "").toLowerCase();
        const username = (w.username || "").toLowerCase();
        const email = (w.email || "").toLowerCase();
        const mobile = (w.mobile || "").toLowerCase();
        const dept = (w.department || "").toLowerCase();
        const isActive = w.active !== false;

        const matchesQuery = !query || id.includes(query) || name.includes(query) || username.includes(query) || email.includes(query) || mobile.includes(query);
        const matchesDept = !deptFilter || dept.includes(deptFilter.replace(" department", "")) || dept === deptFilter;
        const matchesStatus = !statusFilter || (statusFilter === "active" && isActive) || (statusFilter === "deactivated" && !isActive);

        return matchesQuery && matchesDept && matchesStatus;
    });

    const countSummary = document.getElementById("adm-workers-count-summary");
    if (countSummary) {
        countSummary.innerText = `Showing ${filtered.length} of ${currentAdminWorkers.length} registered field workers`;
    }

    if (filtered.length === 0) {
        workersTbody.innerHTML = `<tr><td colspan="8" class="empty-state" style="text-align:center; padding: 28px; color: var(--text-muted);"><i class="fa-solid fa-user-slash" style="font-size:24px; margin-bottom:8px; display:block;"></i>No workers found matching your filter criteria.</td></tr>`;
        return;
    }

    workersTbody.innerHTML = filtered.map(w => {
        const isActive = w.active !== false;
        const statusBadge = isActive ?
            `<span class="badge badge-resolved" style="background:#dcfce7; color:#15803d; border:1px solid #bbf7d0; padding:4px 8px; border-radius:4px; font-weight:600;"><i class="fa-solid fa-circle-check"></i> Active</span>` :
            `<span class="badge" style="background:#fee2e2; color:#dc2626; border:1px solid #fecaca; padding:4px 8px; border-radius:4px; font-weight:600;"><i class="fa-solid fa-ban"></i> Deactivated</span>`;

        const toggleLabel = isActive ? "Deactivate" : "Activate";
        const toggleIcon = isActive ? "fa-power-off" : "fa-check";
        const toggleColor = isActive ? "#dc2626" : "#059669";
        const workerId = w.workerId || w.id;
        const cleanName = w.name || w.staffName || w.displayName || "Field Officer";
        const cleanUsername = w.username || (w.email && w.email.includes("@") ? w.email.split("@")[0] : "-");

        return `
            <tr>
                <td><strong style="color:var(--primary); font-family:monospace; font-size:13px;">${escapeHtml(workerId)}</strong></td>
                <td><strong>${escapeHtml(cleanName)}</strong></td>
                <td><code style="background:var(--bg-surface-secondary); padding:2px 6px; border-radius:4px; font-weight:600; color:var(--text-main); font-size:12px;">${escapeHtml(cleanUsername)}</code></td>
                <td><span style="font-size:13px;">${escapeHtml(w.email)}</span></td>
                <td>${escapeHtml(w.mobile || '-')}</td>
                <td><span class="locked-badge"><i class="fa-solid fa-building"></i> ${escapeHtml(w.department || 'General')}</span></td>
                <td>${statusBadge}</td>
                <td>
                    <div class="table-action-group" style="display:flex; gap:6px;">
                        <button class="btn btn-secondary btn-sm" onclick='openEditWorkerModal(${JSON.stringify(w).replace(/'/g, "&#39;")})' title="Edit Worker Details">
                            <i class="fa-solid fa-user-pen"></i> Edit
                        </button>
                        <button class="btn btn-secondary btn-sm" style="color:${toggleColor};" onclick="handleToggleWorkerActive('${workerId}', ${isActive})" title="${toggleLabel} Account">
                            <i class="fa-solid ${toggleIcon}"></i> ${toggleLabel}
                        </button>
                        <button class="btn btn-secondary btn-sm" onclick="openResetPwdModal('${workerId}', '${escapeHtml(cleanName).replace(/'/g, "\\'")}')" title="Reset Password">
                            <i class="fa-solid fa-key"></i> Reset
                        </button>
                    </div>
                </td>
            </tr>
        `;
    }).join("");
}

function openAddWorkerModal(mode = 'single') {
    const modal = document.getElementById("worker-modal");
    if (!modal) return;
    document.getElementById("worker-modal-title").innerHTML = `<i class="fa-solid fa-user-plus"></i> Add Department Field Worker(s)`;
    const tabs = document.getElementById("worker-modal-tabs");
    if (tabs) tabs.style.display = "flex";

    // Reset single form
    document.getElementById("worker-form-id").value = "";
    const idGroup = document.getElementById("w-form-id-group");
    if (idGroup) idGroup.style.display = "none";
    document.getElementById("w-form-name").value = "";
    document.getElementById("w-form-username").value = "";
    document.getElementById("w-form-email").value = "";
    document.getElementById("w-form-email").readOnly = false;
    document.getElementById("w-form-mobile").value = "";
    document.getElementById("w-form-dept").value = "Water Supply";
    document.getElementById("w-form-pwd").value = "admin123";
    document.getElementById("w-form-pwd-group").style.display = "block";
    document.getElementById("w-form-active-group").style.display = "none";

    switchWorkerModalMode(mode);
    modal.classList.add("active");
}

function switchWorkerModalMode(mode) {
    currentWorkerModalMode = mode;
    const card = document.getElementById("worker-modal-card");
    const singleForm = document.getElementById("worker-single-form");
    const multiContainer = document.getElementById("worker-multi-form-container");
    const btnSingle = document.getElementById("btn-wmode-single");
    const btnMulti = document.getElementById("btn-wmode-multi");

    if (mode === 'multi') {
        if (card) card.style.maxWidth = "960px";
        if (singleForm) singleForm.style.display = "none";
        if (multiContainer) multiContainer.style.display = "block";
        if (btnSingle) { btnSingle.className = "btn btn-secondary btn-sm"; }
        if (btnMulti) { btnMulti.className = "btn btn-primary btn-sm"; }

        // Initialize multi table if empty
        const tbody = document.getElementById("multi-workers-tbody");
        if (tbody && tbody.children.length === 0) {
            clearMultiWorkerRows();
            addMultiWorkerRow();
            addMultiWorkerRow();
        }
    } else {
        if (card) card.style.maxWidth = "600px";
        if (singleForm) singleForm.style.display = "block";
        if (multiContainer) multiContainer.style.display = "none";
        if (btnSingle) { btnSingle.className = "btn btn-primary btn-sm"; }
        if (btnMulti) { btnMulti.className = "btn btn-secondary btn-sm"; }
    }
}

let multiRowCounter = 0;

function addMultiWorkerRow(data = {}) {
    const tbody = document.getElementById("multi-workers-tbody");
    if (!tbody) return;
    multiRowCounter++;
    const rowId = `mw-row-${Date.now()}-${multiRowCounter}`;

    const tr = document.createElement("tr");
    tr.id = rowId;
    tr.className = "multi-worker-row";
    tr.innerHTML = `
        <td class="row-num" style="font-weight:700; color:var(--text-muted); text-align:center;">${tbody.children.length + 1}</td>
        <td>
            <input type="text" class="form-control form-control-sm mw-name" placeholder="Full Name" value="${escapeHtml(data.name || '')}" required oninput="autoFillUsername(this)" style="padding:6px 10px; font-size:12px;">
        </td>
        <td>
            <input type="text" class="form-control form-control-sm mw-username" placeholder="Username" value="${escapeHtml(data.username || '')}" required style="padding:6px 10px; font-size:12px;">
        </td>
        <td>
            <input type="email" class="form-control form-control-sm mw-email" placeholder="user@makkal.gov.in" value="${escapeHtml(data.email || '')}" required oninput="autoFillUsernameFromEmail(this)" style="padding:6px 10px; font-size:12px;">
        </td>
        <td>
            <input type="tel" class="form-control form-control-sm mw-mobile" placeholder="9876543210" value="${escapeHtml(data.mobile || '')}" required style="padding:6px 10px; font-size:12px;">
        </td>
        <td>
            <select class="form-control form-control-sm mw-dept" required style="padding:6px 8px; font-size:12px;">
                <option value="Water Supply" ${data.department === 'Water Supply' ? 'selected' : ''}>Water Supply</option>
                <option value="Roads" ${data.department === 'Roads' ? 'selected' : ''}>Roads</option>
                <option value="Waste Management" ${data.department === 'Waste Management' ? 'selected' : ''}>Waste Management</option>
                <option value="Streetlights" ${data.department === 'Streetlights' ? 'selected' : ''}>Streetlights</option>
                <option value="General Administration" ${data.department === 'General Administration' ? 'selected' : ''}>General Admin</option>
            </select>
        </td>
        <td>
            <input type="password" class="form-control form-control-sm mw-pwd" placeholder="Min 6 chars" value="${escapeHtml(data.password || 'admin123')}" required style="padding:6px 8px; font-size:12px;">
        </td>
        <td style="text-align:center;">
            <button type="button" class="btn btn-secondary btn-sm" onclick="removeMultiWorkerRow('${rowId}')" title="Remove this worker row" style="color:#dc2626; padding:4px 8px; border-color:#fecaca;">
                <i class="fa-solid fa-trash-can"></i>
            </button>
        </td>
    `;
    tbody.appendChild(tr);
    updateMultiRowNumbers();
}

function autoFillUsername(nameInput) {
    const tr = nameInput.closest("tr");
    if (!tr) return;
    const userInput = tr.querySelector(".mw-username");
    const emailInput = tr.querySelector(".mw-email");
    if (userInput && !userInput.dataset.manuallyEdited) {
        const val = nameInput.value.trim().toLowerCase().replace(/[^a-z0-9]/g, '_');
        if (val) {
            userInput.value = val;
            if (emailInput && !emailInput.dataset.manuallyEdited) {
                emailInput.value = `${val}@makkal.gov.in`;
            }
        }
    }
}

function autoFillUsernameFromEmail(emailInput) {
    const tr = emailInput.closest("tr");
    if (!tr) return;
    const userInput = tr.querySelector(".mw-username");
    if (userInput && (!userInput.value || !userInput.dataset.manuallyEdited)) {
        const val = emailInput.value.trim().toLowerCase();
        if (val.includes("@")) {
            userInput.value = val.split("@")[0].replace(/[^a-z0-9_]/g, '');
        }
    }
}

function addMultipleEmptyRows(count = 3) {
    for (let i = 0; i < count; i++) {
        addMultiWorkerRow();
    }
}

function removeMultiWorkerRow(rowId) {
    const tbody = document.getElementById("multi-workers-tbody");
    if (!tbody) return;
    if (tbody.children.length <= 1) {
        showToast("At least one worker row is required.", "warning");
        return;
    }
    const row = document.getElementById(rowId);
    if (row) row.remove();
    updateMultiRowNumbers();
}

function clearMultiWorkerRows() {
    const tbody = document.getElementById("multi-workers-tbody");
    if (tbody) tbody.innerHTML = "";
    addMultiWorkerRow();
}

function updateMultiRowNumbers() {
    const tbody = document.getElementById("multi-workers-tbody");
    if (!tbody) return;
    const rows = tbody.querySelectorAll(".multi-worker-row");
    rows.forEach((row, i) => {
        const numCell = row.querySelector(".row-num");
        if (numCell) numCell.innerText = i + 1;
    });
    const label = document.getElementById("multi-workers-count-label");
    if (label) {
        label.innerText = `${rows.length} worker row${rows.length === 1 ? '' : 's'} ready`;
    }
}

async function handleSaveMultipleWorkers() {
    const tbody = document.getElementById("multi-workers-tbody");
    if (!tbody) return;
    const rows = tbody.querySelectorAll(".multi-worker-row");
    if (rows.length === 0) {
        showToast("Please add at least one worker row.", "warning");
        return;
    }

    const workersList = [];
    const seenUsernames = new Set();
    const seenEmails = new Set();

    for (let i = 0; i < rows.length; i++) {
        const row = rows[i];
        const rowNum = i + 1;
        const name = row.querySelector(".mw-name")?.value.trim() || "";
        const username = row.querySelector(".mw-username")?.value.trim().toLowerCase() || "";
        const email = row.querySelector(".mw-email")?.value.trim().toLowerCase() || "";
        const mobile = row.querySelector(".mw-mobile")?.value.trim() || "";
        const dept = row.querySelector(".mw-dept")?.value || "";
        const password = row.querySelector(".mw-pwd")?.value || "";

        if (!name) {
            showToast(`Row ${rowNum}: Worker Full Name is required.`, "danger");
            row.querySelector(".mw-name")?.focus();
            return;
        }
        if (!username) {
            showToast(`Row ${rowNum}: Username is required.`, "danger");
            row.querySelector(".mw-username")?.focus();
            return;
        }
        if (!email || !email.includes("@") || !email.includes(".")) {
            showToast(`Row ${rowNum}: Please enter a valid email address.`, "danger");
            row.querySelector(".mw-email")?.focus();
            return;
        }
        if (!mobile) {
            showToast(`Row ${rowNum}: Mobile number is required.`, "danger");
            row.querySelector(".mw-mobile")?.focus();
            return;
        }
        if (!password || password.length < 6) {
            showToast(`Row ${rowNum}: Password must be at least 6 characters long.`, "danger");
            row.querySelector(".mw-pwd")?.focus();
            return;
        }

        if (seenUsernames.has(username)) {
            showToast(`Row ${rowNum}: Duplicate username '${username}' within batch entry.`, "danger");
            row.querySelector(".mw-username")?.focus();
            return;
        }
        seenUsernames.add(username);

        if (seenEmails.has(email)) {
            showToast(`Row ${rowNum}: Duplicate email '${email}' within batch entry.`, "danger");
            row.querySelector(".mw-email")?.focus();
            return;
        }
        seenEmails.add(email);

        workersList.push({ name, username, email, mobile, department: dept, password });
    }

    const btn = document.getElementById("btn-save-multi-workers");
    if (btn) {
        btn.disabled = true;
        btn.innerHTML = `<i class="fa-solid fa-spinner fa-spin"></i> Registering ${workersList.length} Workers...`;
    }

    try {
        const res = await ApiClient.createMultipleWorkers(workersList);
        showToast(`<strong>Success!</strong> ${res.count || workersList.length} workers successfully registered and assigned STAFF role.`);
        closeWorkerModal();
        await loadAdminDashboard();
    } catch (err) {
        showToast("Batch Registration Error: " + err.message, "danger");
    } finally {
        if (btn) {
            btn.disabled = false;
            btn.innerHTML = `<i class="fa-solid fa-users-check"></i> Register All Workers`;
        }
    }
}

function openEditWorkerModal(worker) {
    const modal = document.getElementById("worker-modal");
    if (!modal) return;
    document.getElementById("worker-modal-title").innerHTML = `<i class="fa-solid fa-user-pen"></i> Edit Department Worker`;
    const tabs = document.getElementById("worker-modal-tabs");
    if (tabs) tabs.style.display = "none";
    switchWorkerModalMode('single');

    const workerId = worker.workerId || worker.id;
    document.getElementById("worker-form-id").value = workerId;
    const idGroup = document.getElementById("w-form-id-group");
    if (idGroup) idGroup.style.display = "block";
    const idDisp = document.getElementById("w-form-id-display");
    if (idDisp) idDisp.value = workerId;

    document.getElementById("w-form-name").value = worker.name || worker.staffName || "";
    document.getElementById("w-form-username").value = worker.username || "";
    document.getElementById("w-form-email").value = worker.email || "";
    document.getElementById("w-form-email").readOnly = false;
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
}

async function handleSaveWorker(e) {
    e.preventDefault();
    const workerId = document.getElementById("worker-form-id").value;
    const name = document.getElementById("w-form-name").value.trim();
    const username = document.getElementById("w-form-username").value.trim();
    const email = document.getElementById("w-form-email").value.trim();
    const mobile = document.getElementById("w-form-mobile").value.trim();
    const dept = document.getElementById("w-form-dept").value;

    if (!name || !username || !email || !mobile || !dept) {
        showToast("All fields are required.", "danger");
        return;
    }

    try {
        if (!workerId) {
            // Create Single Worker
            const pwd = document.getElementById("w-form-pwd").value;
            if (!pwd || pwd.length < 6) {
                showToast("Password must be at least 6 characters long.", "danger");
                return;
            }
            await ApiClient.createWorker(name, username, email, mobile, pwd, dept);
            showToast(`Worker <strong>${name}</strong> successfully created with STAFF role.`);
        } else {
            // Update Worker
            const active = document.getElementById("w-form-active").checked;
            await ApiClient.updateWorker(workerId, name, username, email, mobile, dept, active);
            showToast(`Worker <strong>${name}</strong> updated successfully.`);
        }
        closeWorkerModal();
        await loadAdminDashboard();
    } catch (err) {
        showToast("Operation Failed: " + err.message, "danger");
    }
}

async function handleToggleWorkerActive(workerId, currentActive) {
    const action = currentActive ? "deactivate" : "activate";
    if (!confirm(`Are you sure you want to ${action} worker ${workerId}?`)) return;

    try {
        await ApiClient.updateWorker(workerId, { active: !currentActive });
        showToast(`Worker <strong>${workerId}</strong> account ${action}d successfully.`);
        await loadAdminDashboard();
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

    tbody.innerHTML = paginatedItems.map(c => {
        const photoThumb = c.imageUrl ? `<img src="${c.imageUrl}" alt="Photo" style="width:34px; height:34px; object-fit:cover; border-radius:4px; border:1px solid var(--border-color); cursor:pointer; vertical-align:middle; margin-right:8px;" onclick="openImageLightbox('${c.imageUrl}', 'Complaint ${c.complaintId} - Site Photo Evidence')" title="Click to view photo evidence">` : '';
        const isUrgent = (c.priority || "").toUpperCase() === "CRITICAL" || (c.priority || "").toUpperCase() === "URGENT";
        const rowClass = isUrgent ? 'class="stitch-table-urgent-row"' : '';
        const normStatus = (c.status || "").toUpperCase().replace(/[\s_-]/g, "");
        const canAssign = currentUser && (currentUser.role === 'ADMIN' || currentUser.role === 'MANAGER');
        let assignBtnHtml = "";

        if (canAssign) {
            if (normStatus === "RESOLVED" || normStatus === "CLOSED") {
                // 5. If status is Resolved, completely hide and disable the Assign option
                assignBtnHtml = "";
            } else if (normStatus === "ASSIGNED" || normStatus === "INPROGRESS") {
                // 2, 3, 4. Hide initial Assign button; show separate Reassign option for Admin/Manager
                assignBtnHtml = `
                    <button class="btn btn-warning btn-sm" onclick="openReassignModal('${c.complaintId}')" title="Reassign Complaint" style="font-size:11px; padding:2px 8px; background:#d97706; color:#fff; border-color:#b45309;">
                        <i class="fa-solid fa-arrows-rotate"></i> Reassign
                    </button>
                `;
            } else {
                // 1. Registered / unassigned: Show initial Assign button
                assignBtnHtml = `
                    <button class="btn btn-primary btn-sm" onclick="openAssignModal('${c.complaintId}')" title="Assign to Department Field Worker" style="font-size:11px; padding:2px 8px;">
                        <i class="fa-solid fa-user-plus"></i> Assign
                    </button>
                `;
            }
        }

        return `
        <tr>
            <td><strong>${c.complaintId}</strong></td>
            <td>${escapeHtml(c.citizenName)}</td>
            <td><div style="display:flex; align-items:center;">${photoThumb}<span>${escapeHtml(c.category)}</span></div></td>
            <td>${escapeHtml(c.location)}</td>
            <td>${getPriorityBadge(c.priority)}</td>
            <td>${escapeHtml(c.department)}</td>
            <td>${getStatusBadge(c.status, c.isWaiting)}</td>
            <td>${c.createdAt || 'Today'}</td>
            <td>
                <div class="table-action-group">
                    ${c.imageUrl ? `<button class="btn btn-secondary btn-sm" onclick="openImageLightbox('${c.imageUrl}', 'Complaint ${c.complaintId} - Site Photo Evidence')" title="Click to view image clearly" style="font-size:11px; padding:2px 8px;"><i class="fa-solid fa-camera"></i> Image</button>` : ''}
                    ${assignBtnHtml}
                    <button class="btn btn-secondary btn-sm" onclick="openComplaintModal('${c.complaintId}')" title="Manage Case & View Photo Evidence">
                        <i class="fa-solid fa-sliders"></i> Manage
                    </button>
                    ${(currentUser && (currentUser.role === 'ADMIN' || currentUser.role === 'MANAGER')) ? `
                        <button class="btn btn-secondary btn-sm" onclick="confirmDeleteComplaint('${c.complaintId}')" title="Delete Complaint" style="color: #dc2626; border-color: #fecaca; background: #fff5f5;">
                            <i class="fa-solid fa-trash-can"></i> Delete
                        </button>
                    ` : ''}
                </div>
            </td>
        </tr>
    `;
    }).join("");
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
                ${(currentUser && (currentUser.role === "ADMIN" || currentUser.role === "MANAGER" || currentUser.role === "STAFF" || currentUser.role === "WORKER")) ? `
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
        
        const opActionEl = document.getElementById("m-operational-action");
        if (opActionEl) {
            opActionEl.innerText = c.operationalAction || "Field Inspection & Maintenance";
        }

        // Show/hide Delete Complaint button based on Role (Admin & Manager only)
        const deleteBtn = document.getElementById("btn-modal-delete-complaint");
        if (deleteBtn) {
            deleteBtn.style.display = (currentUser && (currentUser.role === "ADMIN" || currentUser.role === "MANAGER")) ? "inline-flex" : "none";
        }

        // Show/hide Duplicate Warning Banner
        const dupBanner = document.getElementById("m-duplicate-warning-banner");
        if (dupBanner) {
            const hasDups = c.hasDuplicateCandidates || (c.duplicateCandidates && c.duplicateCandidates.length > 0) || c.isDuplicate;
            dupBanner.style.display = hasDups ? "block" : "none";
        }

        const notesBox = document.getElementById("m-resolution-notes");
        if (c.resolutionNotes && c.resolutionNotes.trim()) {
            notesBox.innerText = c.resolutionNotes;
            notesBox.style.color = "var(--status-resolved)";
        } else {
            notesBox.innerText = "No resolution description notes recorded yet.";
            notesBox.style.color = "var(--text-muted)";
        }

        // Render Photo Evidence for all roles (Citizen, Worker, Manager, Admin)
        const photoBox = document.getElementById("m-photo-evidence");
        if (photoBox) {
            if (c.imageUrl && c.imageUrl.trim()) {
                photoBox.innerHTML = `
                    <div style="display:flex; align-items:center; gap:20px; width:100%; flex-wrap:wrap; padding:6px;">
                        <div style="position:relative; cursor:pointer;" onclick="openImageLightbox('${c.imageUrl}', 'Complaint ${c.complaintId} - Site Photo Evidence')">
                            <img src="${c.imageUrl}" alt="Citizen Site Photo Evidence" style="width:160px; height:120px; object-fit:cover; border-radius:8px; border:2px solid var(--border-color); box-shadow:0 4px 14px rgba(0,0,0,0.14); cursor:pointer; transition:transform 0.2s;" onmouseover="this.style.transform='scale(1.03)'" onmouseout="this.style.transform='scale(1)'">
                            <div style="position:absolute; bottom:6px; right:6px; background:rgba(0,0,0,0.75); color:#fff; border-radius:4px; padding:3px 8px; font-size:11px; font-weight:600;"><i class="fa-solid fa-magnifying-glass-plus"></i> Click to Zoom</div>
                        </div>
                        <div style="flex:1; min-width:220px;">
                            <div style="display:flex; align-items:center; gap:8px;">
                                <span class="badge" style="background:#ecfdf5; color:#065f46; font-size:12px; font-weight:600;"><i class="fa-solid fa-circle-check"></i> Verified Field Photo</span>
                                <span style="font-size:12px; color:var(--text-muted);">Visible to Manager &bull; Worker &bull; Admin</span>
                            </div>
                            <div style="font-size:13px; color:var(--text-main); margin:6px 0 12px 0; font-weight:500;">
                                Official site condition image submitted with complaint <strong style="color:var(--primary);">${c.complaintId}</strong>.
                            </div>
                            <div style="display:flex; gap:10px; flex-wrap:wrap;">
                                <button type="button" class="btn btn-primary btn-sm" style="background:#2563eb; color:#fff; font-weight:600; padding:6px 14px; font-size:12px;" onclick="openImageLightbox('${c.imageUrl}', 'Complaint ${c.complaintId} - Site Photo Evidence')">
                                    <i class="fa-solid fa-magnifying-glass-plus"></i> Click to View Image Clearly
                                </button>
                                <a href="${c.imageUrl}" target="_blank" rel="noopener" class="btn btn-secondary btn-sm" style="font-size:12px; padding:6px 12px;" title="Open raw image in new tab">
                                    <i class="fa-solid fa-arrow-up-right-from-square"></i> Open Raw
                                </a>
                            </div>
                        </div>
                    </div>
                `;
            } else {
                photoBox.innerHTML = `
                    <div style="padding:16px; text-align:center; color:var(--text-muted); width:100%;">
                        <i class="fa-solid fa-image-slash" style="font-size:26px; margin-bottom:6px; display:block; opacity:0.6;"></i>
                        <span style="font-size:13px;">No photographic evidence was attached to this record.</span>
                    </div>
                `;
            }
        }

        // Render Step Timeline Graphic
        renderTrackingTimeline(c.status);

        // Staff Controls Box
        const staffBox = document.getElementById("staff-controls-box");
        const isOfficial = currentUser && (currentUser.role === "ADMIN" || currentUser.role === "MANAGER" || currentUser.role === "STAFF" || currentUser.role === "WORKER");
        if (isOfficial) {
            staffBox.style.display = "flex";
            const targetDept = c.department && c.department !== "Unassigned" ? c.department : "Water Supply";
            const deptSelect = document.getElementById("s-assign-dept-select");
            if (deptSelect) deptSelect.value = targetDept;
            await populateWorkerDropdown("s-assign-dept-select", "s-assign-staff-select", c.assignedStaff);
            document.getElementById("s-resolution-input").value = c.resolutionNotes || "";

            // Role-based control of Operational Action and Assignment
            const isWorker = currentUser && (currentUser.role === "WORKER" || currentUser.role === "STAFF");
            const canAssign = currentUser && (currentUser.role === "ADMIN" || currentUser.role === "MANAGER");
            const assignWorkerGroup = document.getElementById("s-assign-worker-group");
            const btnModalAssign = document.getElementById("btn-modal-assign-worker");
            const btnModalReassign = document.getElementById("btn-modal-reassign-worker");
            const assignWorkerLabel = document.getElementById("s-assign-worker-label");
            const workerOpActionDisplay = document.getElementById("s-worker-operational-action-display");
            const managerOpActionControl = document.getElementById("s-manager-operational-action-control");
            const opActionInput = document.getElementById("s-operational-action-input");

            const normStatus = (c.status || "").toUpperCase().replace(/[\s_-]/g, "");

            if (isWorker || !canAssign) {
                // Workers CANNOT modify Operational Action or reassign department/worker
                if (assignWorkerGroup) assignWorkerGroup.style.display = "none";
                if (workerOpActionDisplay) {
                    workerOpActionDisplay.style.display = "block";
                    workerOpActionDisplay.innerText = c.operationalAction || "Field Inspection & Maintenance";
                }
                if (managerOpActionControl) managerOpActionControl.style.display = "none";
            } else {
                // Manager and Admin
                if (workerOpActionDisplay) workerOpActionDisplay.style.display = "none";
                if (managerOpActionControl) managerOpActionControl.style.display = "flex";
                if (opActionInput) opActionInput.value = c.operationalAction || "Field Inspection & Maintenance";

                if (normStatus === "RESOLVED" || normStatus === "CLOSED") {
                    // 5. If the complaint status is Resolved, completely hide and disable the Assign option
                    if (assignWorkerGroup) assignWorkerGroup.style.display = "none";
                } else if (normStatus === "ASSIGNED" || normStatus === "INPROGRESS") {
                    // 2, 3, 4. Once assigned or in progress, hide initial Assign button; show separate Reassign option
                    if (assignWorkerGroup) assignWorkerGroup.style.display = "block";
                    if (assignWorkerLabel) assignWorkerLabel.innerHTML = `<i class="fa-solid fa-arrows-rotate" style="color:#d97706;"></i> Reassign Department & Field Worker Officer`;
                    if (btnModalAssign) btnModalAssign.style.display = "none";
                    if (btnModalReassign) btnModalReassign.style.display = "inline-flex";
                } else {
                    // 1. Registered / unassigned: Show initial Assign button
                    if (assignWorkerGroup) assignWorkerGroup.style.display = "block";
                    if (assignWorkerLabel) assignWorkerLabel.innerHTML = `<i class="fa-solid fa-user-gear"></i> Assign Department & Field Worker Officer`;
                    if (btnModalAssign) btnModalAssign.style.display = "inline-flex";
                    if (btnModalReassign) btnModalReassign.style.display = "none";
                }
            }

            // Dynamic population of valid next actions based on State Machine
            const statusSelect = document.getElementById("s-update-status-select");
            const updateBtn = document.getElementById("btn-update-status");
            const resolveInput = document.getElementById("s-resolution-input");
            const resolveBtn = document.querySelector("#staff-controls-box button[onclick='handleStaffResolve()']");

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

        const maxCitizenCount = Math.max(...candidates.map(c => c.distinctCitizenCount || 2), 2);

        if (badge) {
            badge.style.display = "inline-block";
            badge.style.backgroundColor = "#fef3c7";
            badge.style.color = "#92400e";
            badge.style.borderColor = "#fcd34d";
            badge.innerText = `${maxCitizenCount} Citizens • ${candidates.length} Matching Case${candidates.length > 1 ? 's' : ''}`;
        }

        const bannerHtml = `
            <div style="background: #fffbeb; border: 1px solid #fcd34d; border-radius: 6px; padding: 10px 14px; margin-bottom: 8px; display: flex; align-items: center; justify-content: space-between;">
                <div>
                    <div style="color: #92400e; font-weight: 700; font-size: 13px;">
                        <i class="fa-solid fa-people-group"></i> Multiple Citizens Reported This Issue
                    </div>
                    <div style="font-size: 12px; color: #b45309; margin-top: 2px;">
                        Reported by <strong>${maxCitizenCount} distinct citizens</strong> across <strong>${candidates.length + 1} matching complaints</strong>.
                    </div>
                </div>
                <span class="badge" style="background: #fef3c7; color: #92400e; border: 1px solid #fde68a; font-weight: 700; font-size: 11px;">
                    ${maxCitizenCount} Distinct Citizens
                </span>
            </div>
        `;

        const cardsHtml = candidates.map(cand => `
            <div style="background: var(--bg-surface); padding: 12px; border-radius: var(--radius-sm); border: 1px solid var(--border-color); display: flex; flex-direction: column; gap: 8px;">
                <div style="display: flex; justify-content: space-between; align-items: center; font-size: 13px;">
                    <div>
                        <strong style="color: var(--primary);">Case #${escapeHtml(cand.candidateComplaintId || cand.candidateId)}</strong>
                        <span style="color: var(--text-muted); margin-left: 8px;">(${escapeHtml(cand.category)} | ${escapeHtml(cand.location)})</span>
                    </div>
                    <span class="badge badge-progress" style="font-size: 11px;">${escapeHtml(cand.status)}</span>
                </div>
                ${cand.candidateDescription ? `<div style="font-size: 12px; color: var(--text-main); background: rgba(0,0,0,0.02); padding: 6px 10px; border-radius: 4px; border-left: 3px solid var(--primary); font-style: italic;">"${escapeHtml(cand.candidateDescription)}"</div>` : ''}
                <div style="font-size: 12px; color: var(--text-muted); display: flex; flex-wrap: wrap; gap: 12px;">
                    <span><i class="fa-solid fa-triangle-exclamation" style="color: #d97706;"></i> <strong>Match Reason:</strong> ${escapeHtml(cand.reason)}</span>
                    <span><i class="fa-solid fa-user"></i> Reported by: <strong>${escapeHtml(cand.citizenName)}</strong></span>
                    ${cand.similarityScore ? `<span><i class="fa-solid fa-chart-pie" style="color: #2563eb;"></i> Similarity: <strong>${Math.round(cand.similarityScore * 100)}%</strong></span>` : ''}
                    ${cand.distanceMeters !== null && cand.distanceMeters !== undefined ? `<span><i class="fa-solid fa-location-crosshairs" style="color: #059669;"></i> Distance: <strong>${cand.distanceMeters}m</strong></span>` : ''}
                </div>
                <div style="display: flex; gap: 8px; align-items: center; margin-top: 4px;">
                    <input type="text" id="cand-remarks-${cand.candidateComplaintId || cand.candidateId}" class="form-control" placeholder="Operator review justification..." style="flex: 1; font-size: 12px; padding: 4px 8px;">
                    <button class="btn btn-primary btn-sm" style="font-size: 12px; padding: 5px 10px;" onclick="handleConfirmDuplicateLink('${escapeHtml(complaint.complaintId)}', '${escapeHtml(cand.candidateComplaintId || cand.candidateId)}')">
                        <i class="fa-solid fa-check"></i> Confirm Link
                    </button>
                    <button class="btn btn-secondary btn-sm" style="font-size: 12px; padding: 5px 10px;" onclick="handleRejectDuplicateCandidate('${escapeHtml(complaint.complaintId)}', '${escapeHtml(cand.candidateComplaintId || cand.candidateId)}')">
                        <i class="fa-solid fa-xmark"></i> Reject Candidate
                    </button>
                </div>
            </div>
        `).join("");

        const footerHtml = `
            <div style="font-size: 11px; color: var(--text-muted); padding: 4px 6px; display: flex; align-items: center; gap: 6px;">
                <i class="fa-solid fa-shield-halved" style="color: #16a34a;"></i>
                <span><strong>Data Integrity Guarantee:</strong> Confirming links grievances for coordinated dispatch. Every citizen's original complaint record and ownership are strictly preserved without deletion or automated merging.</span>
            </div>
        `;

        list.innerHTML = bannerHtml + cardsHtml + footerHtml;
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
    const complaint = currentComplaintsList.find(c => c.complaintId === activeComplaintId);
    if (complaint) {
        const normStatus = (complaint.status || "").toUpperCase().replace(/[\s_-]/g, "");
        if (normStatus === "RESOLVED" || normStatus === "CLOSED") {
            showToast("Invalid assignment: Resolved complaints cannot be assigned.", "danger");
            return;
        }
        if (normStatus === "ASSIGNED" || normStatus === "INPROGRESS") {
            showToast("Complaint is already assigned. Please use Reassign instead.", "warning");
            openReassignModal(activeComplaintId);
            return;
        }
    }
    const dept = document.getElementById("s-assign-dept-select").value;
    const staffSelect = document.getElementById("s-assign-staff-select");
    const staffName = staffSelect ? staffSelect.value.trim() : "";
    const opActionInput = document.getElementById("s-operational-action-input");
    const operationalAction = opActionInput ? opActionInput.value.trim() : "";

    try {
        await ApiClient.assignDepartment(activeComplaintId, dept, staffName, "", "", operationalAction);
        showToast(`Assigned <strong>${activeComplaintId}</strong> to ${dept} Dept${staffName ? ` (${staffName})` : ''}.`);
        openComplaintModal(activeComplaintId);
        await refreshCurrentView();
    } catch (e) {
        showToast("Failed to assign department: " + e.message, "danger");
    }
}

async function handleStaffUpdateStatus() {
    if (!activeComplaintId) return;
    const newStatus = document.getElementById("s-update-status-select").value;
    const isManagerOrAdmin = currentUser && (currentUser.role === "ADMIN" || currentUser.role === "MANAGER");
    let operationalAction = "";
    if (isManagerOrAdmin) {
        const opActionInput = document.getElementById("s-operational-action-input");
        if (opActionInput && opActionInput.value.trim()) {
            operationalAction = opActionInput.value.trim();
        }
    }

    try {
        await ApiClient.updateStatus(activeComplaintId, newStatus, "", operationalAction);
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
        return `<span class="stitch-badge" style="background:#f1f5f9; color:#475569; border:1px solid #cbd5e1;"><span class="stitch-badge-dot" style="background:#64748b;"></span> Unassessed</span>`;
    }
    if (p === "CRITICAL" || p === "URGENT") {
        return `<span class="stitch-badge stitch-badge-urgent"><span class="stitch-badge-dot animate-ping"></span> Critical Urgent</span>`;
    } else if (p === "HIGH") {
        return `<span class="stitch-badge stitch-badge-urgent"><span class="stitch-badge-dot"></span> High</span>`;
    } else if (p === "MEDIUM") {
        return `<span class="stitch-badge stitch-badge-progress"><span class="stitch-badge-dot"></span> Medium</span>`;
    } else {
        return `<span class="stitch-badge stitch-badge-resolved"><span class="stitch-badge-dot"></span> Standard</span>`;
    }
}

function getStatusBadge(status, isWaiting = false) {
    if (isWaiting) {
        return `<span class="stitch-badge stitch-badge-pending" title="Waiting for Department Capacity Slot"><span class="stitch-badge-dot"></span> In Queue</span>`;
    }
    const s = (status || "").toUpperCase().replace(/[\s_-]/g, "");
    if (s === "RESOLVED" || s === "CLOSED") {
        return `<span class="stitch-badge stitch-badge-resolved"><span class="stitch-badge-dot"></span> Resolved</span>`;
    } else if (s === "INPROGRESS") {
        return `<span class="stitch-badge stitch-badge-progress"><span class="stitch-badge-dot"></span> In Progress</span>`;
    } else if (s === "ASSIGNED") {
        return `<span class="stitch-badge stitch-badge-assigned"><span class="stitch-badge-dot"></span> Assigned</span>`;
    } else {
        return `<span class="stitch-badge stitch-badge-pending"><span class="stitch-badge-dot"></span> Registered</span>`;
    }
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

// ------------------- IMAGE LIGHTBOX (ENLARGEMENT FOR ALL ROLES) ------------------- //
function openImageLightbox(src, caption) {
    if (!src || !src.trim()) {
        showToast("No photographic evidence was attached to this complaint.", "warning");
        return;
    }
    const modal = document.getElementById("image-lightbox-modal");
    const img = document.getElementById("lightbox-img");
    const cap = document.getElementById("lightbox-caption");
    const dl = document.getElementById("lightbox-download-link");
    if (!modal || !img) return;
    img.src = src;
    if (cap) cap.innerHTML = `<i class="fa-solid fa-camera" style="color:#60a5fa;"></i> ${escapeHtml(caption || "Citizen Photo Evidence")}`;
    if (dl) dl.href = src;
    modal.classList.add("active");
}

function closeImageLightbox() {
    const modal = document.getElementById("image-lightbox-modal");
    if (modal) modal.classList.remove("active");
}

// Global Escape Key to close lightbox
document.addEventListener("keydown", function(e) {
    if (e.key === "Escape" || e.keyCode === 27) {
        closeImageLightbox();
    }
});

// ------------------- REGISTERED CITIZENS MANAGEMENT (ADMIN) ------------------- //
let currentAdminCitizens = [];
let citizenSearchQuery = "";
let citizenStatusFilter = "ALL";
let citizenCurrentPage = 1;
let citizenPageSize = 10;
let citizenPollInterval = null;

async function loadAdminCitizens(showFeedback = false) {
    try {
        const citizens = await ApiClient.getRegisteredCitizens();
        currentAdminCitizens = (Array.isArray(citizens) ? citizens : []).filter(c => !c.role || c.role.toUpperCase() === "CITIZEN");
        if (document.getElementById("adm-stat-citizens")) {
            document.getElementById("adm-stat-citizens").innerText = currentAdminCitizens.length;
        }
        renderAdminCitizensTable();
        if (showFeedback && typeof showToast === 'function') {
            showToast("Citizens directory refreshed successfully.", "success");
        }
    } catch (err) {
        console.error("Failed to load citizens:", err);
        if (showFeedback && typeof showToast === 'function') {
            showToast("Failed to refresh citizens: " + (err.message || err), "danger");
        }
    }
}

function handleCitizenFilterChange() {
    const searchInput = document.getElementById("adm-citizen-search");
    const statusSelect = document.getElementById("adm-citizen-status-filter");
    citizenSearchQuery = (searchInput ? searchInput.value : "").trim().toLowerCase();
    citizenStatusFilter = statusSelect ? statusSelect.value : "ALL";
    citizenCurrentPage = 1;
    renderAdminCitizensTable();
}

function resetCitizenFilters() {
    const searchInput = document.getElementById("adm-citizen-search");
    const statusSelect = document.getElementById("adm-citizen-status-filter");
    if (searchInput) searchInput.value = "";
    if (statusSelect) statusSelect.value = "ALL";
    citizenSearchQuery = "";
    citizenStatusFilter = "ALL";
    citizenCurrentPage = 1;
    renderAdminCitizensTable();
}

function setCitizenPageSize(size) {
    citizenPageSize = parseInt(size, 10) || 10;
    citizenCurrentPage = 1;
    renderAdminCitizensTable();
}

function changeCitizenPage(delta) {
    citizenCurrentPage += delta;
    renderAdminCitizensTable();
}

function formatCitizenDateDisplay(rawDate) {
    if (!rawDate || rawDate === "-") return { date: "-", time: "" };
    try {
        const parts = rawDate.trim().split(/[\sT]+/);
        if (parts.length >= 2 && /^\d{4}-\d{2}-\d{2}$/.test(parts[0])) {
            const [y, m, d] = parts[0].split("-");
            const months = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"];
            const monthName = months[parseInt(m, 10) - 1] || m;
            const formattedDate = `${d} ${monthName} ${y}`;

            let timeStr = parts[1];
            const timeParts = timeStr.split(":");
            if (timeParts.length >= 2) {
                let hour = parseInt(timeParts[0], 10);
                const min = timeParts[1];
                const ampm = hour >= 12 ? "PM" : "AM";
                hour = hour % 12 || 12;
                const padHour = String(hour).padStart(2, "0");
                timeStr = `${padHour}:${min} ${ampm}`;
            }
            return { date: formattedDate, time: timeStr };
        }
        const parsed = new Date(rawDate);
        if (!isNaN(parsed.getTime())) {
            const months = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"];
            const d = String(parsed.getDate()).padStart(2, "0");
            const m = months[parsed.getMonth()];
            const y = parsed.getFullYear();
            let hour = parsed.getHours();
            const min = String(parsed.getMinutes()).padStart(2, "0");
            const ampm = hour >= 12 ? "PM" : "AM";
            hour = hour % 12 || 12;
            const padHour = String(hour).padStart(2, "0");
            return {
                date: `${d} ${m} ${y}`,
                time: `${padHour}:${min} ${ampm}`
            };
        }
    } catch (e) {}
    return { date: rawDate, time: "" };
}

function renderAdminCitizensTable() {
    const tbody = document.getElementById("adm-citizens-tbody");
    if (!tbody) return;

    let filtered = currentAdminCitizens.filter(c => {
        // Enforce strict citizen role filtering
        if (c.role && c.role.toUpperCase() !== "CITIZEN") return false;

        // Status filter
        if (citizenStatusFilter === "ACTIVE" && c.active === false) return false;
        if (citizenStatusFilter === "INACTIVE" && c.active !== false) return false;

        // Search query matching Name, Username, Mobile, Citizen ID, or Email
        if (citizenSearchQuery) {
            const name = (c.name || c.citizenName || "").toLowerCase();
            const username = (c.username || "").toLowerCase();
            const mobile = (c.mobile || "").toLowerCase();
            const id = (c.id || c.citizenId || "").toLowerCase();
            const email = (c.email || "").toLowerCase();
            const match = name.includes(citizenSearchQuery) ||
                          username.includes(citizenSearchQuery) ||
                          mobile.includes(citizenSearchQuery) ||
                          id.includes(citizenSearchQuery) ||
                          email.includes(citizenSearchQuery);
            if (!match) return false;
        }
        return true;
    });

    const totalItems = filtered.length;
    const totalPages = Math.ceil(totalItems / citizenPageSize) || 1;
    if (citizenCurrentPage < 1) citizenCurrentPage = 1;
    if (citizenCurrentPage > totalPages) citizenCurrentPage = totalPages;

    const startIdx = (citizenCurrentPage - 1) * citizenPageSize;
    const endIdx = Math.min(startIdx + citizenPageSize, totalItems);
    const pageItems = filtered.slice(startIdx, endIdx);

    // Update pagination indicators
    const infoSpan = document.getElementById("adm-citizen-page-info");
    const currentSpan = document.getElementById("adm-citizen-page-current");
    const prevBtn = document.getElementById("btn-citizen-prev");
    const nextBtn = document.getElementById("btn-citizen-next");

    if (infoSpan) {
        infoSpan.innerText = totalItems > 0 
            ? `Showing ${startIdx + 1} to ${endIdx} of ${totalItems} citizens`
            : "Showing 0 of 0 citizens";
    }
    if (currentSpan) {
        currentSpan.innerText = `Page ${citizenCurrentPage} of ${totalPages}`;
    }
    if (prevBtn) prevBtn.disabled = citizenCurrentPage <= 1;
    if (nextBtn) nextBtn.disabled = citizenCurrentPage >= totalPages;

    if (pageItems.length === 0) {
        tbody.innerHTML = `<tr><td colspan="9" style="text-align:center; padding:36px; color:var(--text-muted);"><i class="fa-solid fa-users-slash" style="font-size:24px; margin-bottom:8px; display:block;"></i>No registered citizens found matching your criteria.</td></tr>`;
        return;
    }

    tbody.innerHTML = pageItems.map(c => {
        const id = escapeHtml(c.id || c.citizenId || "-");
        const name = escapeHtml(c.name || c.citizenName || c.username || "Citizen");
        const username = escapeHtml(c.username || "-");
        const email = escapeHtml(c.email || "-");
        const mobile = escapeHtml(c.mobile || "-");
        const rawDate = escapeHtml(c.createdAt || "-");
        const isActive = c.active !== false;
        const maskedId = escapeHtml(c.maskedIdentity || "XXXX-XXXX-0000");

        const dateObj = formatCitizenDateDisplay(rawDate);
        const dateHtml = dateObj.time 
            ? `<div class="cit-cell-date-box" title="${rawDate}">
                 <span class="cit-cell-date-main">${dateObj.date}</span>
                 <span class="cit-cell-date-sub"><i class="fa-regular fa-clock" style="font-size:9.5px; opacity:0.8;"></i> ${dateObj.time}</span>
               </div>`
            : `<span class="cit-cell-date-main" title="${rawDate}">${dateObj.date}</span>`;

        const statusBadge = isActive 
            ? `<span class="cit-cell-status-badge active"><i class="fa-solid fa-circle-check" style="font-size:9.5px;"></i> Active</span>`
            : `<span class="cit-cell-status-badge inactive"><i class="fa-solid fa-circle-xmark" style="font-size:9.5px;"></i> Inactive</span>`;

        return `
            <tr>
                <td class="col-citizen-id">
                    <span class="cit-cell-id" title="${id}">${id}</span>
                </td>
                <td class="col-citizen-name">
                    <div class="cit-cell-name" title="${name}">${name}</div>
                </td>
                <td class="col-username">
                    <span class="cit-cell-username-chip" title="${username}">
                        <i class="fa-solid fa-at" style="font-size:10px; opacity:0.6;"></i>
                        <span style="overflow:hidden; text-overflow:ellipsis; white-space:nowrap;">${username}</span>
                    </span>
                </td>
                <td class="col-email">
                    <div class="cit-cell-email" title="${email}">
                        <i class="fa-regular fa-envelope" style="font-size:11px; color:#94a3b8; flex-shrink:0;"></i>
                        <span style="overflow:hidden; text-overflow:ellipsis; white-space:nowrap;">${email}</span>
                    </div>
                </td>
                <td class="col-mobile">
                    <span class="cit-cell-mobile">
                        <i class="fa-solid fa-phone" style="font-size:10px; color:#94a3b8; flex-shrink:0;"></i>
                        <span>${mobile}</span>
                    </span>
                </td>
                <td class="col-aadhaar">
                    <span class="cit-cell-aadhaar-badge" title="Masked UID: ${maskedId}">
                        <i class="fa-solid fa-shield-halved" style="font-size:10px;"></i>
                        <span>${maskedId}</span>
                    </span>
                </td>
                <td class="col-date">
                    ${dateHtml}
                </td>
                <td class="col-status">
                    ${statusBadge}
                </td>
                <td class="col-action" style="text-align: center;">
                    <button type="button" class="cit-btn-view-details" onclick="openCitizenDetailsModal('${escapeHtml(c.id || c.citizenId)}')" title="View profile and grievances for ${name}">
                        <i class="fa-solid fa-eye" style="color:var(--primary); font-size:11px;"></i>
                        <span>View Details</span>
                    </button>
                </td>
            </tr>
        `;
    }).join("");
}

async function openCitizenDetailsModal(citizenId) {
    const modal = document.getElementById("citizen-details-modal");
    if (!modal) return;

    // Reset fields to loading state
    document.getElementById("cit-modal-name").innerText = "Loading...";
    document.getElementById("cit-modal-id-badge").innerText = citizenId;
    document.getElementById("cit-modal-username").innerText = "...";
    document.getElementById("cit-modal-email").innerText = "...";
    document.getElementById("cit-modal-mobile").innerText = "...";
    document.getElementById("cit-modal-date").innerText = "...";
    document.getElementById("cit-stat-total").innerText = "-";
    document.getElementById("cit-stat-pending").innerText = "-";
    document.getElementById("cit-stat-inprogress").innerText = "-";
    document.getElementById("cit-stat-resolved").innerText = "-";
    document.getElementById("cit-modal-complaints-tbody").innerHTML = `<tr><td colspan="5" style="text-align:center; padding:16px; color:var(--text-muted);">Loading complaints history...</td></tr>`;

    modal.classList.add("active");

    try {
        const data = await ApiClient.getCitizenDetails(citizenId);
        const citizen = data.citizen || {};
        const stats = data.stats || {};
        const complaints = Array.isArray(data.complaints) ? data.complaints : [];

        document.getElementById("cit-modal-name").innerText = citizen.name || citizen.citizenName || citizen.username || "Citizen";
        document.getElementById("cit-modal-id-badge").innerText = citizen.id || citizen.citizenId || citizenId;
        document.getElementById("cit-modal-username").innerText = citizen.username || "-";
        document.getElementById("cit-modal-email").innerText = citizen.email || "-";
        document.getElementById("cit-modal-mobile").innerText = citizen.mobile || "-";
        document.getElementById("cit-modal-date").innerText = citizen.createdAt || "-";
        if (document.getElementById("cit-modal-masked-id")) {
            document.getElementById("cit-modal-masked-id").innerText = citizen.maskedIdentity || "XXXX-XXXX-0000";
        }

        const statusBadge = document.getElementById("cit-modal-status-badge");
        if (statusBadge) {
            if (citizen.active !== false) {
                statusBadge.className = "badge";
                statusBadge.style.background = "#ecfdf5";
                statusBadge.style.color = "#059669";
                statusBadge.innerHTML = `<i class="fa-solid fa-circle-check"></i> Active Account`;
            } else {
                statusBadge.className = "badge";
                statusBadge.style.background = "#fef2f2";
                statusBadge.style.color = "#dc2626";
                statusBadge.innerHTML = `<i class="fa-solid fa-circle-xmark"></i> Inactive Account`;
            }
        }

        document.getElementById("cit-stat-total").innerText = stats.total || 0;
        document.getElementById("cit-stat-pending").innerText = stats.pending || 0;
        document.getElementById("cit-stat-inprogress").innerText = stats.inProgress || 0;
        document.getElementById("cit-stat-resolved").innerText = stats.resolved || 0;

        const tbody = document.getElementById("cit-modal-complaints-tbody");
        if (complaints.length === 0) {
            tbody.innerHTML = `<tr><td colspan="5" style="text-align:center; padding:16px; color:var(--text-muted);"><i class="fa-solid fa-folder-open" style="margin-right:6px;"></i>No grievances submitted by this citizen yet.</td></tr>`;
        } else {
            tbody.innerHTML = complaints.map(c => {
                const s = (c.status || "REGISTERED").toUpperCase();
                let badgeColor = "#ca8a04";
                let badgeBg = "#fefce8";
                if (s === "RESOLVED") { badgeColor = "#059669"; badgeBg = "#ecfdf5"; }
                else if (s === "IN_PROGRESS" || s === "TRIAGED") { badgeColor = "#0284c7"; badgeBg = "#f0f9ff"; }
                else if (s === "REJECTED") { badgeColor = "#dc2626"; badgeBg = "#fef2f2"; }

                return `
                    <tr>
                        <td style="font-family:monospace; font-weight:700; color:var(--primary);">${escapeHtml(c.id || c.complaintId || "-")}</td>
                        <td style="font-weight:600;">${escapeHtml(c.category || "-")}</td>
                        <td>${escapeHtml(c.location || "-")}</td>
                        <td><span class="badge" style="background:${badgeBg}; color:${badgeColor}; font-weight:600; font-size:11px;">${escapeHtml(s)}</span></td>
                        <td style="font-size:11px; color:var(--text-muted);">${escapeHtml(c.createdAt || c.timestamp || "-")}</td>
                    </tr>
                `;
            }).join("");
        }
    } catch (err) {
        console.error("Failed to load citizen details:", err);
        if (typeof showToast === 'function') {
            showToast("Failed to load citizen details: " + (err.message || err), "danger");
        }
    }
}

function closeCitizenDetailsModal() {
    const modal = document.getElementById("citizen-details-modal");
    if (modal) modal.classList.remove("active");
}

// Start periodic background refresh for admin citizens when admin is active
if (!window._adminCitizenPollingStarted) {
    window._adminCitizenPollingStarted = true;
    setInterval(() => {
        const citizenTab = document.getElementById("adm-tab-citizens");
        if (currentUser && (currentUser.role || "").toUpperCase() === "ADMIN" && citizenTab && citizenTab.classList.contains("active")) {
            loadAdminCitizens();
        }
    }, 15000); // 15 seconds real-time update
}
