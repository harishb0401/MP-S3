const fs = require('fs');
const assert = require('assert');

console.log("===============================================================================");
console.log("   REGISTERED CITIZENS DIRECTORY UI/UX & DATA INTEGRITY TEST SUITE");
console.log("===============================================================================\n");

let passed = 0;
let total = 0;

function test(cond, name, details = '') {
    total++;
    if (cond) {
        passed++;
        console.log(`[TEST ${total}] ${name}... PASSED ${details ? '(' + details + ')' : ''}`);
    } else {
        console.error(`[TEST ${total}] ${name}... FAILED ${details ? ': ' + details : ''}`);
    }
}

// 1. Verify CSS rules in frontend/css/style.css
const css = fs.readFileSync('frontend/css/style.css', 'utf8');

test(css.includes('.citizens-table-container'), 'Citizens table container CSS exists');
test(css.includes('overflow-x: auto') && css.includes('min-width: 1290px'), 'Container has overflow-x auto and table min-width 1290px');
test(css.includes('table-layout: fixed'), 'Table layout fixed enforced');

// Exact column width definitions in CSS
const reqWidths = [
    { col: 'col-citizen-id', width: '130px' },
    { col: 'col-citizen-name', width: '180px' },
    { col: 'col-username', width: '180px' },
    { col: 'col-email', width: '260px' },
    { col: 'col-mobile', width: '140px' },
    { col: 'col-aadhaar', width: '130px' },
    { col: 'col-date', width: '140px' },
    { col: 'col-status', width: '120px' },
    { col: 'col-action', width: '130px' }
];

let allWidthsPresent = true;
for (const rw of reqWidths) {
    if (!css.includes(`${rw.col}`) || !css.includes(`width: ${rw.width}`)) {
        allWidthsPresent = false;
        console.error(`Missing CSS width for ${rw.col}: expected ${rw.width}`);
    }
}
test(allWidthsPresent, 'All 9 required column widths explicitly defined in CSS', '130, 180, 180, 260, 140, 130, 140, 120, 130px');

// Check Sticky Header
test(css.includes('position: sticky') && css.includes('top: 0'), 'Sticky header CSS defined with position: sticky and top: 0');

// Check Row Minimum 64px Height & Vertical Centering
test(css.includes('min-height: 64px') && css.includes('height: 64px'), 'Table row height minimum 64px configured');
test(css.includes('vertical-align: middle'), 'Cell vertical alignment set to middle');

// Check Hover & Zebra Striping
test(css.includes(':nth-child(even)') && css.includes('tbody tr:hover'), 'Zebra striping and row hover effects present');

// Check White-space Nowrap on critical items
test(css.includes('.cit-cell-id') && css.includes('white-space: nowrap'), 'Citizen ID has white-space nowrap');
test(css.includes('.cit-cell-mobile') && css.includes('white-space: nowrap'), 'Mobile Number has white-space nowrap');
test(css.includes('.cit-cell-aadhaar-badge') && css.includes('white-space: nowrap'), 'Masked Aadhaar badge has white-space nowrap (single line)');
test(css.includes('.cit-cell-status-badge') && css.includes('white-space: nowrap'), 'Account Status has white-space nowrap');
test(css.includes('.cit-btn-view-details') && css.includes('white-space: nowrap'), 'Action button has white-space nowrap');

// Check Uniform Button Dimensions
test(css.includes('.cit-btn-view-details') && css.includes('height: 32px') && css.includes('width: 104px'), 'View Details button has uniform 104px x 32px dimensions');

// 2. Verify HTML structure in frontend/index.html
const html = fs.readFileSync('frontend/index.html', 'utf8');

test(html.includes('class="citizens-table-container"'), 'index.html wraps table in .citizens-table-container');
test(html.includes('<colgroup>') && html.includes('col-citizen-id') && html.includes('col-action'), 'index.html defines <colgroup> with all 9 columns');
test(html.includes('<th class="col-citizen-id">Citizen ID</th>') && html.includes('<th class="col-action"'), 'index.html <thead> defines all 9 column headers');

// 3. Verify JavaScript Rendering Logic in frontend/js/app.js
const appJs = fs.readFileSync('frontend/js/app.js', 'utf8');

test(appJs.includes('function formatCitizenDateDisplay(rawDate)'), 'formatCitizenDateDisplay helper exists');
test(appJs.includes('cit-cell-date-box') && appJs.includes('cit-cell-date-main') && appJs.includes('cit-cell-date-sub'), 'Date rendering outputs clean two-line layout');
test(appJs.includes('cit-cell-username-chip'), 'Username rendered as clean badge/chip');
test(appJs.includes('cit-cell-aadhaar-badge'), 'Aadhaar rendered as compact single-line badge');

// 4. Test Rendering with Edge Case Datasets (Short, Long, Truncation, Tooltip, 10, 50, 100+ records)
// Extract helper and rendering logic to test directly
const helperCode = appJs.substring(
    appJs.indexOf('function formatCitizenDateDisplay(rawDate) {'),
    appJs.indexOf('async function openCitizenDetailsModal(citizenId) {')
);

const escapeHtml = (s) => String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');

const testScope = new Function('escapeHtml', `
    let currentAdminCitizens = [];
    let citizenStatusFilter = "ALL";
    let citizenSearchQuery = "";
    let citizenCurrentPage = 1;
    let citizenPageSize = 10;

    let mockTbodyHtml = "";
    const document = {
        getElementById: (id) => {
            if (id === "adm-citizens-tbody") {
                return { set innerHTML(val) { mockTbodyHtml = val; }, get innerHTML() { return mockTbodyHtml; } };
            }
            return { innerText: "", disabled: false };
        }
    };

    ${helperCode}

    return {
        formatCitizenDateDisplay,
        renderAdminCitizensTable,
        setCitizens: (c) => { currentAdminCitizens = c; },
        setPageSize: (s) => { citizenPageSize = s; },
        setPage: (p) => { citizenCurrentPage = p; },
        getHtml: () => mockTbodyHtml
    };
`);

const harness = testScope(escapeHtml);

// Dataset A: Short and Very Long values
const edgeDataset = [
    {
        id: "USR-SHORT",
        name: "Li",
        username: "bo",
        email: "a@b.co",
        mobile: "9876543210",
        maskedIdentity: "XXXX-XXXX-1111",
        createdAt: "2026-11-02 06:37:51",
        active: true
    },
    {
        id: "USR46134C10LONG",
        name: "Dr. Subramanian Thirunavukkarasu Ramachandran Srinivasan Pillai",
        username: "citizen_super_resident_advocate_ward42_special",
        email: "very.long.administrative.grievance.resident.account.2026@makkal.nagar.municipality.gov.in",
        mobile: "9876543210",
        maskedIdentity: "XXXX-XXXX-7607",
        createdAt: "2026-11-02 06:37:51",
        active: true
    },
    {
        id: "USR-INACTIVE",
        name: "Inactive Resident",
        username: "inactive_user",
        email: "inactive@example.com",
        mobile: "9840112233",
        maskedIdentity: "XXXX-XXXX-9999",
        createdAt: "2026-10-15 18:20:00",
        active: false
    }
];

harness.setCitizens(edgeDataset);
harness.renderAdminCitizensTable();
const renderedHtml = harness.getHtml();

test(renderedHtml.includes('class="col-citizen-id"'), 'Rendered rows include .col-citizen-id cell');
test(renderedHtml.includes('class="col-citizen-name"'), 'Rendered rows include .col-citizen-name cell');
test(renderedHtml.includes('class="col-username"'), 'Rendered rows include .col-username cell');
test(renderedHtml.includes('class="col-email"'), 'Rendered rows include .col-email cell');
test(renderedHtml.includes('class="col-mobile"'), 'Rendered rows include .col-mobile cell');
test(renderedHtml.includes('class="col-aadhaar"'), 'Rendered rows include .col-aadhaar cell');
test(renderedHtml.includes('class="col-date"'), 'Rendered rows include .col-date cell');
test(renderedHtml.includes('class="col-status"'), 'Rendered rows include .col-status cell');
test(renderedHtml.includes('class="col-action"'), 'Rendered rows include .col-action cell');

// Tooltip checks for long names & emails
test(renderedHtml.includes('title="Dr. Subramanian Thirunavukkarasu Ramachandran Srinivasan Pillai"'), 'Long name has complete value as title tooltip');
test(renderedHtml.includes('title="very.long.administrative.grievance.resident.account.2026@makkal.nagar.municipality.gov.in"'), 'Long email has complete value as title tooltip');
test(renderedHtml.includes('title="citizen_super_resident_advocate_ward42_special"'), 'Long username has complete value as title tooltip');

// Date formatting checks
test(renderedHtml.includes('02 Nov 2026') && renderedHtml.includes('06:37 AM'), 'Date formatted as "02 Nov 2026" with time "06:37 AM"');
test(renderedHtml.includes('15 Oct 2026') && renderedHtml.includes('06:20 PM'), 'Evening date formatted with "06:20 PM"');

// Aadhaar badge check
test(renderedHtml.includes('XXXX-XXXX-7607') && renderedHtml.includes('cit-cell-aadhaar-badge'), 'Aadhaar badge renders cleanly with single-line class');

// Status badge checks
test(renderedHtml.includes('cit-cell-status-badge active') && renderedHtml.includes('cit-cell-status-badge inactive'), 'Both active and inactive badges render cleanly');

// Button checks
test(renderedHtml.includes('cit-btn-view-details') && renderedHtml.includes('View Details'), 'View Details button has cit-btn-view-details class');

// Test with 50 and 100+ records
const bulkDataset = [];
for (let i = 1; i <= 125; i++) {
    bulkDataset.push({
        id: `CIT-${String(i).padStart(4, '0')}`,
        name: `Citizen Record Number ${i} With Long Surname`,
        username: `resident_user_${i}`,
        email: `resident.account.${i}.longdomain@makkal.nagar.gov.in`,
        mobile: `98765${String(i).padStart(5, '0')}`,
        maskedIdentity: `XXXX-XXXX-${String(i).padStart(4, '0')}`,
        createdAt: "2026-11-02 06:37:51",
        active: i % 7 !== 0
    });
}

// Page size 10
harness.setCitizens(bulkDataset);
harness.setPageSize(10);
harness.setPage(1);
harness.renderAdminCitizensTable();
let html10 = harness.getHtml();
let rows10 = (html10.match(/<tr/g) || []).length;
test(rows10 === 10, '125 records with page size 10 renders exactly 10 rows', `Rendered: ${rows10}`);

// Page size 50
harness.setPageSize(50);
harness.setPage(1);
harness.renderAdminCitizensTable();
let html50 = harness.getHtml();
let rows50 = (html50.match(/<tr/g) || []).length;
test(rows50 === 50, '125 records with page size 50 renders exactly 50 rows', `Rendered: ${rows50}`);

// Page 3 with remaining 25
harness.setPage(3);
harness.renderAdminCitizensTable();
let htmlPage3 = harness.getHtml();
let rowsPage3 = (htmlPage3.match(/<tr/g) || []).length;
test(rowsPage3 === 25, '125 records page 3 (with page size 50) renders exactly remaining 25 rows', `Rendered: ${rowsPage3}`);

console.log("\n===============================================================================");
console.log(`UI TEST SUITE RESULT: ${passed} / ${total} TESTS PASSED`);
console.log("===============================================================================");

process.exit(passed === total ? 0 : 1);

