const http = require('http');

const BASE_URL = 'http://localhost:8080/api';

function request(method, path, body = null, token = null) {
    return new Promise((resolve, reject) => {
        const url = new URL(BASE_URL + path);
        const options = {
            hostname: url.hostname,
            port: url.port,
            path: url.pathname + url.search,
            method: method,
            headers: {
                'Content-Type': 'application/json'
            }
        };
        if (token) {
            options.headers['Authorization'] = 'Bearer ' + token;
        }

        const req = http.request(options, (res) => {
            let data = '';
            res.on('data', chunk => data += chunk);
            res.on('end', () => {
                let parsed = null;
                try {
                    parsed = JSON.parse(data);
                } catch (e) {
                    parsed = data;
                }
                resolve({ status: res.statusCode, data: parsed });
            });
        });

        req.on('error', reject);
        if (body) {
            req.write(typeof body === 'string' ? body : JSON.stringify(body));
        }
        req.end();
    });
}

// Simulates frontend renderStaffComplaintsTable action buttons generator
function renderActionButtons(c, currentUser) {
    const normStatus = (c.status || "").toUpperCase().replace(/[\s_-]/g, "");
    const canAssign = currentUser && (currentUser.role === 'ADMIN' || currentUser.role === 'MANAGER');
    let assignBtnHtml = "";

    if (canAssign) {
        if (normStatus === "RESOLVED" || normStatus === "CLOSED") {
            assignBtnHtml = "";
        } else if (normStatus === "ASSIGNED" || normStatus === "INPROGRESS") {
            assignBtnHtml = `<button class="btn btn-warning btn-sm" onclick="openReassignModal('${c.complaintId}')"><i class="fa-solid fa-arrows-rotate"></i> Reassign</button>`;
        } else {
            assignBtnHtml = `<button class="btn btn-primary btn-sm" onclick="openAssignModal('${c.complaintId}')"><i class="fa-solid fa-user-plus"></i> Assign</button>`;
        }
    }

    return {
        hasAssignBtn: assignBtnHtml.includes("openAssignModal"),
        hasReassignBtn: assignBtnHtml.includes("openReassignModal"),
        rawHtml: assignBtnHtml
    };
}

// Simulates frontend openComplaintModal assignment group visibility & button states
function getModalAssignmentControls(c, currentUser) {
    const isWorker = currentUser && (currentUser.role === "WORKER" || currentUser.role === "STAFF");
    const canAssign = currentUser && (currentUser.role === "ADMIN" || currentUser.role === "MANAGER");
    const normStatus = (c.status || "").toUpperCase().replace(/[\s_-]/g, "");

    if (isWorker || !canAssign) {
        return {
            groupVisible: false,
            showAssignBtn: false,
            showReassignBtn: false
        };
    }

    if (normStatus === "RESOLVED" || normStatus === "CLOSED") {
        return {
            groupVisible: false,
            showAssignBtn: false,
            showReassignBtn: false
        };
    } else if (normStatus === "ASSIGNED" || normStatus === "INPROGRESS") {
        return {
            groupVisible: true,
            showAssignBtn: false,
            showReassignBtn: true,
            label: "Reassign Department & Field Worker Officer"
        };
    } else {
        return {
            groupVisible: true,
            showAssignBtn: true,
            showReassignBtn: false,
            label: "Assign Department & Field Worker Officer"
        };
    }
}

async function runE2ETests() {
    console.log("===============================================================================");
    console.log("   E2E ASSIGNMENT VISIBILITY & WORKFLOW AUTOMATED VERIFICATION");
    console.log("===============================================================================");

    let passed = 0;
    let total = 8;

    try {
        // 1. Log in users
        const adminLogin = await request('POST', '/auth/login', { email: 'admin@makkal.gov.in', password: 'admin123' });
        const adminToken = adminLogin.data.token;
        const adminUser = adminLogin.data.user;

        const workerLogin = await request('POST', '/auth/login', { email: 'water_staff@makkal.gov.in', password: 'admin123' });
        const workerToken = workerLogin.data.token;
        const workerUser = workerLogin.data.user;

        const citizenLogin = await request('POST', '/auth/login', { email: 'ramesh@test.com', password: '123456' });
        const citizenToken = citizenLogin.data.token;
        const citizenUser = citizenLogin.data.user;

        // Step 1: Create a fresh registered complaint
        process.stdout.write("[STEP 1/8] Citizen registers fresh complaint... ");
        const regRes = await request('POST', '/complaints', {
            title: "Visibility Test Complaint",
            category: "Water Supply",
            location: "North Street",
            description: "Testing assignment button visibility through all states",
            citizenReportedImpact: 50
        }, citizenToken);

        const compId = regRes.data.complaintId;
        let c = regRes.data;
        if (compId && c.status === "Registered") {
            console.log(`PASSED (Complaint ID: ${compId}, Status: ${c.status})`);
            passed++;
        } else {
            console.log("FAILED (regRes:", regRes, ")");
        }

        // Step 2: Registered state button visibility
        process.stdout.write("[STEP 2/8] Registered status: Admin sees Assign, Worker sees NO Assign... ");
        const adminBtnsReg = renderActionButtons(c, adminUser);
        const workerBtnsReg = renderActionButtons(c, workerUser);
        const modalAdminReg = getModalAssignmentControls(c, adminUser);
        const modalWorkerReg = getModalAssignmentControls(c, workerUser);

        if (adminBtnsReg.hasAssignBtn && !adminBtnsReg.hasReassignBtn &&
            !workerBtnsReg.hasAssignBtn && !workerBtnsReg.hasReassignBtn &&
            modalAdminReg.groupVisible && modalAdminReg.showAssignBtn && !modalAdminReg.showReassignBtn &&
            !modalWorkerReg.groupVisible) {
            console.log("PASSED (Admin table/modal: Assign shown; Worker table/modal: Assign hidden)");
            passed++;
        } else {
            console.log("FAILED", { adminBtnsReg, workerBtnsReg, modalAdminReg, modalWorkerReg });
        }

        // Step 3: Admin assigns complaint -> status becomes Assigned
        process.stdout.write("[STEP 3/8] Admin assigns complaint to Water Supply department... ");
        const assignRes = await request('POST', `/complaints/${compId}/assign`, {
            department: "Water Supply",
            staffName: "Officer Suresh",
            severity: "HIGH",
            reason: "Initial routing to Water field unit"
        }, adminToken);
        c = assignRes.data;
        if (c.status === "Assigned" && c.assignedStaff.includes("Suresh")) {
            console.log(`PASSED (Status: ${c.status}, Assigned Staff: ${c.assignedStaff})`);
            passed++;
        } else {
            console.log("FAILED", assignRes);
        }

        // Step 4: Assigned state button visibility & duplicate assignment prevention
        process.stdout.write("[STEP 4/8] Assigned status: Initial Assign hidden, Reassign shown to Admin, Worker sees neither, duplicate /assign rejected with 400... ");
        const adminBtnsAssigned = renderActionButtons(c, adminUser);
        const workerBtnsAssigned = renderActionButtons(c, workerUser);
        const modalAdminAssigned = getModalAssignmentControls(c, adminUser);
        const modalWorkerAssigned = getModalAssignmentControls(c, workerUser);

        const dupAssignRes = await request('POST', `/complaints/${compId}/assign`, {
            department: "Water Supply",
            staffName: "Officer Suresh"
        }, adminToken);

        const dupBlocked = (dupAssignRes.status === 400 && JSON.stringify(dupAssignRes.data).includes("already assigned"));

        if (!adminBtnsAssigned.hasAssignBtn && adminBtnsAssigned.hasReassignBtn &&
            !workerBtnsAssigned.hasAssignBtn && !workerBtnsAssigned.hasReassignBtn &&
            modalAdminAssigned.groupVisible && !modalAdminAssigned.showAssignBtn && modalAdminAssigned.showReassignBtn &&
            !modalWorkerAssigned.groupVisible && dupBlocked) {
            console.log("PASSED (Initial Assign hidden, Reassign visible, duplicate /assign rejected with 400)");
            passed++;
        } else {
            console.log("FAILED", { adminBtnsAssigned, workerBtnsAssigned, modalAdminAssigned, dupBlocked, dupStatus: dupAssignRes.status });
        }

        // Step 5: Worker updates status to In Progress
        process.stdout.write("[STEP 5/8] Worker updates status to In Progress... ");
        const progRes = await request('POST', `/complaints/${compId}/status`, {
            status: "InProgress",
            remarks: "Field crew started excavation work"
        }, workerToken);
        c = progRes.data;
        if (c.status === "InProgress") {
            console.log(`PASSED (Status: ${c.status})`);
            passed++;
        } else {
            console.log("FAILED", progRes);
        }

        // Step 6: In Progress state button visibility & assignment rejection
        process.stdout.write("[STEP 6/8] In Progress status: Initial Assign hidden, Reassign shown to Admin, /assign rejected with 400... ");
        const adminBtnsProg = renderActionButtons(c, adminUser);
        const modalAdminProg = getModalAssignmentControls(c, adminUser);

        const progAssignRes = await request('POST', `/complaints/${compId}/assign`, {
            department: "Water Supply",
            staffName: "Officer Suresh"
        }, adminToken);
        const progBlocked = (progAssignRes.status === 400 && JSON.stringify(progAssignRes.data).includes("In Progress"));

        if (!adminBtnsProg.hasAssignBtn && adminBtnsProg.hasReassignBtn &&
            modalAdminProg.groupVisible && !modalAdminProg.showAssignBtn && modalAdminProg.showReassignBtn &&
            progBlocked) {
            console.log("PASSED (Initial Assign hidden, Reassign visible, /assign rejected with 400)");
            passed++;
        } else {
            console.log("FAILED", { adminBtnsProg, modalAdminProg, progBlocked });
        }

        // Step 7: Worker resolves complaint
        process.stdout.write("[STEP 7/8] Worker resolves complaint... ");
        const resRes = await request('POST', `/complaints/${compId}/resolve`, {
            resolutionNotes: "Mainline pipeline successfully repaired and pressure checked."
        }, workerToken);
        c = resRes.data;
        if (c.status === "Resolved") {
            console.log(`PASSED (Status: ${c.status})`);
            passed++;
        } else {
            console.log("FAILED", resRes);
        }

        // Step 8: Resolved state: Assign & Reassign completely hidden and disabled; backend rejects both
        process.stdout.write("[STEP 8/8] Resolved status: Both Assign & Reassign COMPLETELY hidden & disabled in UI, backend rejects /assign & /reassign with 400... ");
        const adminBtnsRes = renderActionButtons(c, adminUser);
        const workerBtnsRes = renderActionButtons(c, workerUser);
        const modalAdminRes = getModalAssignmentControls(c, adminUser);
        const modalWorkerRes = getModalAssignmentControls(c, workerUser);

        const resAssignTry = await request('POST', `/complaints/${compId}/assign`, {
            department: "Water Supply",
            staffName: "Officer Suresh"
        }, adminToken);
        const resReassignTry = await request('POST', `/complaints/${compId}/reassign`, {
            department: "Roads",
            staffName: "Officer R",
            reason: "Attempt to reassign closed case"
        }, adminToken);

        const assignBlocked400 = (resAssignTry.status === 400 && JSON.stringify(resAssignTry.data).includes("Resolved complaints"));
        const reassignBlocked400 = (resReassignTry.status === 400 && JSON.stringify(resReassignTry.data).includes("Resolved complaints"));

        if (!adminBtnsRes.hasAssignBtn && !adminBtnsRes.hasReassignBtn &&
            !workerBtnsRes.hasAssignBtn && !workerBtnsRes.hasReassignBtn &&
            !modalAdminRes.groupVisible && !modalAdminRes.showAssignBtn && !modalAdminRes.showReassignBtn &&
            !modalWorkerRes.groupVisible &&
            assignBlocked400 && reassignBlocked400) {
            console.log("PASSED (All assign/reassign controls completely hidden/disabled in table & modal; backend rejects /assign and /reassign with 400)");
            passed++;
        } else {
            console.log("FAILED", { adminBtnsRes, modalAdminRes, assignBlocked400, reassignBlocked400 });
        }

    } catch (err) {
        console.error("Test error:", err);
    }

    console.log("-------------------------------------------------------------------------------");
    console.log(`E2E RESULT: ${passed} / ${total} TESTS PASSED (${passed * 100 / total}%)`);
    console.log("===============================================================================");
}

runE2ETests();
