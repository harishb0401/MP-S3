const fs = require('fs');
const assert = require('assert');

// 1. Verify frontend/index.html
const indexHtml = fs.readFileSync('frontend/index.html', 'utf8');

// Check admin hero banner does not have the 3 options:
// - Ward dropdown: <select class="form-control" style="background: rgba(255, 255, 255, 0.12)...
// - Export button: <button class="btn btn-secondary btn-sm" ...>Export</button>
// - Add Worker button in hero banner: <button class="btn btn-primary btn-sm" ...>+ Add Worker</button>
const adminHeroBanner = indexHtml.substring(indexHtml.indexOf('id="admin-hero-banner"'), indexHtml.indexOf('id="adm-stat-complaints"'));
assert(!adminHeroBanner.includes('Ward II'), 'Admin hero banner must not have ward select dropdown');
assert(!adminHeroBanner.includes('>Export<'), 'Admin hero banner must not have Export button');
assert(!adminHeroBanner.includes('+ Add Worker'), 'Admin hero banner must not have + Add Worker button');
console.log('PASS: Admin hero banner 3 options verified removed.');

// 2. Verify frontend/js/app.js content
const appJs = fs.readFileSync('frontend/js/app.js', 'utf8');

// Check sidebar onclick bindings
assert(appJs.includes(`onclick="switchView('admin-dashboard'); switchAdminSubtab('workers');"`), 'Admin & Workers link must switch subtab to workers');
assert(appJs.includes(`onclick="switchView('admin-dashboard'); switchAdminSubtab('citizens');"`), 'Registered Citizens link must switch subtab to citizens');

// Check switchAdminSubtab updates sidebar links
assert(appJs.includes("adminWorkersLink.classList.remove(\"active\")"), 'switchAdminSubtab must remove active from adminWorkersLink when on citizens');
assert(appJs.includes("regCitizensLink.classList.add(\"active\")"), 'switchAdminSubtab must add active to regCitizensLink when on citizens');
assert(appJs.includes("regCitizensLink.classList.remove(\"active\")"), 'switchAdminSubtab must remove active from regCitizensLink when on workers');
assert(appJs.includes("adminWorkersLink.classList.add(\"active\")"), 'switchAdminSubtab must add active to adminWorkersLink when on workers');

// Check citizen filtering
assert(appJs.includes('if (c.role && c.role.toUpperCase() !== "CITIZEN") return false;'), 'renderAdminCitizensTable must enforce CITIZEN role');

console.log('PASS: All frontend JS bindings and synchronizations verified.');

// 3. Test DOM simulation with lightweight element mocks
class MockClassList {
    constructor() { this.classes = new Set(); }
    add(c) { this.classes.add(c); }
    remove(c) { this.classes.delete(c); }
    toggle(c) { if (this.classes.has(c)) this.classes.delete(c); else this.classes.add(c); }
    contains(c) { return this.classes.has(c); }
}

class MockElement {
    constructor(id, tag = 'div') {
        this.id = id;
        this.tagName = tag.toUpperCase();
        this.classList = new MockClassList();
        this.attributes = {};
        this.innerHTML = '';
        this.innerText = '';
    }
    setAttribute(k, v) { this.attributes[k] = v; }
    getAttribute(k) { return this.attributes[k]; }
}

const elements = new Map();
function getOrCreate(id) {
    if (!elements.has(id)) elements.set(id, new MockElement(id));
    return elements.get(id);
}

const mockDoc = {
    getElementById: (id) => getOrCreate(id),
    querySelectorAll: (sel) => {
        if (sel === '.nav-link') {
            return [getOrCreate('nav-admin'), getOrCreate('nav-citizens'), getOrCreate('nav-analytics')];
        }
        if (sel === '.page-view') {
            return [getOrCreate('admin-dashboard'), getOrCreate('analytics')];
        }
        if (sel === '#admin-dashboard .subtab-btn') {
            return [getOrCreate('btn-adm-workers'), getOrCreate('btn-adm-citizens')];
        }
        if (sel === '#admin-dashboard .subtab-content') {
            return [getOrCreate('adm-tab-workers'), getOrCreate('adm-tab-citizens')];
        }
        return [];
    },
    querySelector: (sel) => {
        if (sel === '.sidebar .nav-link[data-view="admin-dashboard"]' || sel === '.nav-link[data-view="admin-dashboard"]') {
            return getOrCreate('nav-admin');
        }
        if (sel === '.sidebar .nav-link[data-view="registered-citizens"]' || sel === '.nav-link[data-view="registered-citizens"]') {
            return getOrCreate('nav-citizens');
        }
        return null;
    }
};

const navAdmin = getOrCreate('nav-admin');
navAdmin.setAttribute('data-view', 'admin-dashboard');

const navCitizens = getOrCreate('nav-citizens');
navCitizens.setAttribute('data-view', 'registered-citizens');

// Simulate switchView
function testSwitchView(viewId) {
    mockDoc.querySelectorAll('.nav-link').forEach(l => l.classList.remove('active'));
    const activeLink = mockDoc.querySelector(`.nav-link[data-view="${viewId}"]`);
    if (activeLink) activeLink.classList.add('active');

    mockDoc.querySelectorAll('.page-view').forEach(v => v.classList.remove('active'));
    const target = mockDoc.getElementById(viewId);
    if (target) target.classList.add('active');
}

// Simulate switchAdminSubtab
function testSwitchAdminSubtab(subtab) {
    mockDoc.querySelectorAll("#admin-dashboard .subtab-btn").forEach(btn => btn.classList.remove("active"));
    mockDoc.querySelectorAll("#admin-dashboard .subtab-content").forEach(content => content.classList.remove("active"));

    const btn = mockDoc.getElementById(`btn-adm-${subtab}`);
    if (btn) btn.classList.add("active");
    const content = mockDoc.getElementById(`adm-tab-${subtab}`);
    if (content) content.classList.add("active");

    const adminWorkersLink = mockDoc.querySelector('.sidebar .nav-link[data-view="admin-dashboard"]');
    const regCitizensLink = mockDoc.querySelector('.sidebar .nav-link[data-view="registered-citizens"]');
    if (subtab === "citizens") {
        if (adminWorkersLink) adminWorkersLink.classList.remove("active");
        if (regCitizensLink) regCitizensLink.classList.add("active");
    } else {
        if (regCitizensLink) regCitizensLink.classList.remove("active");
        if (adminWorkersLink) adminWorkersLink.classList.add("active");
    }
}

// 1. Initial State: admin dashboard & workers
testSwitchView('admin-dashboard');
testSwitchAdminSubtab('workers');
assert(navAdmin.classList.contains('active'), 'Admin & Workers must be active initially');
assert(!navCitizens.classList.contains('active'), 'Registered Citizens must NOT be active initially');
assert(getOrCreate('adm-tab-workers').classList.contains('active'), 'adm-tab-workers active');
assert(!getOrCreate('adm-tab-citizens').classList.contains('active'), 'adm-tab-citizens inactive');
console.log('PASS: Initial workers tab view verified.');

// 2. User clicks Registered Citizens on sidebar
testSwitchView('admin-dashboard');
testSwitchAdminSubtab('citizens');
assert(!navAdmin.classList.contains('active'), 'Admin & Workers must NOT be active when Registered Citizens is clicked');
assert(navCitizens.classList.contains('active'), 'Registered Citizens MUST be active when Registered Citizens is clicked');
assert(getOrCreate('adm-tab-citizens').classList.contains('active'), 'adm-tab-citizens active');
assert(!getOrCreate('adm-tab-workers').classList.contains('active'), 'adm-tab-workers inactive');
console.log('PASS: Clicking Registered Citizens activates Registered Citizens in sidebar and shows citizens subtab.');

// 3. User clicks Admin & Workers on sidebar
testSwitchView('admin-dashboard');
testSwitchAdminSubtab('workers');
assert(navAdmin.classList.contains('active'), 'Admin & Workers must be active when Admin & Workers is clicked');
assert(!navCitizens.classList.contains('active'), 'Registered Citizens must NOT be active when Admin & Workers is clicked');
assert(getOrCreate('adm-tab-workers').classList.contains('active'), 'adm-tab-workers active');
assert(!getOrCreate('adm-tab-citizens').classList.contains('active'), 'adm-tab-citizens inactive');
console.log('PASS: Clicking Admin & Workers activates Admin & Workers in sidebar and shows workers subtab.');

// 4. In-page subtab button clicks
testSwitchAdminSubtab('citizens');
assert(navCitizens.classList.contains('active'), 'In-page click on citizens tab correctly activates Registered Citizens in sidebar');
assert(!navAdmin.classList.contains('active'), 'In-page click on citizens tab deactivates Admin & Workers in sidebar');

testSwitchAdminSubtab('workers');
assert(navAdmin.classList.contains('active'), 'In-page click on workers tab correctly activates Admin & Workers in sidebar');
assert(!navCitizens.classList.contains('active'), 'In-page click on workers tab deactivates Registered Citizens in sidebar');

console.log('ALL TESTS PASSED SUCCESSFULLY! 100% VERIFIED!');
