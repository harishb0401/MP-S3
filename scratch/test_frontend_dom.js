const fs = require("fs");

console.log("===============================================================================");
console.log("   PHASE 12: FRONTEND UI/UX DOM ELEMENTS & NAVIGATION VERIFICATION");
console.log("===============================================================================\n");

const html = fs.readFileSync("frontend/index.html", "utf8");
let passed = 0;
let total = 0;

function assert(condition, message) {
    total++;
    if (condition) {
        console.log(`[PASS] Test ${total}: ${message}`);
        passed++;
    } else {
        console.error(`[FAIL] Test ${total}: ${message}`);
    }
}

// 1. Check Responsive Viewport
assert(html.includes('<meta name="viewport" content="width=device-width, initial-scale=1.0">') || html.includes('name="viewport"'), "HTML has responsive viewport meta tag");

// 2. Check 4 Core Portals
assert(html.includes('id="citizen-view"') || html.includes('id="citizen-dashboard"'), "Citizen Portal view section present");
assert(html.includes('id="admin-view"') || html.includes('id="admin-dashboard"'), "Admin Portal view section present");
assert(html.includes('id="manager-view"') || html.includes('id="manager-dashboard"'), "Manager Portal view section present");
assert(html.includes('id="staff-view"') || html.includes('id="worker-view"') || html.includes('id="worker-dashboard"'), "Worker/Staff Portal view section present");

// 3. Check Citizen Registration Identity Field
assert(html.includes('id="c-reg-identity"'), "Citizen Registration has identity verification input (id='c-reg-identity')");
assert(html.includes('id="c-reg-identity-error"'), "Citizen Registration has identity validation error element (id='c-reg-identity-error')");

// 4. Check Citizen Complaint Form Fields
assert(html.includes('id="w-category"'), "Complaint category select input present (id='w-category')");
assert(html.includes('id="w-description"'), "Complaint description textarea present (id='w-description')");
assert(html.includes('id="w-location"'), "Complaint location select input present (id='w-location')");
assert(html.includes('id="w-affected"'), "Complaint citizen-reported affected impact input present (id='w-affected')");
assert(html.includes('id="w-latitude"') && html.includes('id="w-longitude"'), "GPS coordinate inputs present (w-latitude & w-longitude)");

// 5. Check Admin Registered Citizens Section
assert(html.includes('id="adm-citizens-tbody"'), "Admin Portal has Registered Citizens table body (id='adm-citizens-tbody')");
assert(html.includes('id="adm-citizen-search"'), "Admin Portal has Registered Citizens search input (id='adm-citizen-search')");
assert(html.includes('id="adm-citizen-status-filter"'), "Admin Portal has Registered Citizens status filter (id='adm-citizen-status-filter')");
assert(html.includes('id="citizen-details-modal"'), "Admin Portal has Citizen Details modal (id='citizen-details-modal')");

// 6. Check Admin Worker Management Section
assert(html.includes('id="adm-workers-tbody"'), "Admin Portal has Worker Management table body (id='adm-workers-tbody')");
assert(html.includes('id="adm-worker-search"'), "Admin Portal has Worker Management search input (id='adm-worker-search')");
assert(html.includes('id="adm-worker-dept-filter"'), "Admin Portal has Worker Management department filter");
assert(html.includes('id="adm-worker-status-filter"'), "Admin Portal has Worker Management status filter");
assert(html.includes('id="worker-modal"'), "Admin Portal has Worker Add/Edit modal (id='worker-modal')");
assert(html.includes('id="btn-wmode-single"') && html.includes('id="btn-wmode-multi"'), "Worker modal supports Single and Multiple worker entry tabs");
assert(html.includes('id="worker-multi-form-container"') && html.includes('id="multi-workers-tbody"'), "Multiple worker batch table & rows container present");

// 7. Check Notification Drawer
assert(html.includes('id="noti-drawer"'), "Slide-out notification drawer container present (id='noti-drawer')");
assert(html.includes('id="noti-badge-count"') && html.includes('id="drawer-unread-count-badge"'), "Notification unread count badges present");
assert(html.includes('id="noti-drawer-body"'), "Notification drawer body list present (id='noti-drawer-body')");

// 8. Check CSS Styling Integrity
const css = fs.readFileSync("frontend/css/style.css", "utf8");
assert(css.includes("@media (max-width:"), "CSS stylesheet includes responsive media queries for mobile devices");
assert(css.includes(".modal-overlay") && css.includes(".modal-card"), "Modal overlay and card CSS classes properly styled");
assert(css.includes(".badge") || css.includes(".status-badge"), "Status badges styled for Priority and Lifecycle states");

console.log("\n-------------------------------------------------------------------------------");
console.log(`PHASE 12 DOM & UI/UX RESULT: ${passed} / ${total} TESTS PASSED (${Math.round(passed/total*100)}%)`);
console.log("===============================================================================\n");

if (passed !== total) {
    process.exit(1);
}
