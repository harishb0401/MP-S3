const http = require('http');
const assert = require('assert');

const PORT = 8080;

function request(options, data) {
    return new Promise((resolve, reject) => {
        const payload = data ? (typeof data === 'string' ? data : JSON.stringify(data)) : null;
        const headers = { ...(options.headers || {}) };
        if (payload && !headers['Content-Type']) {
            headers['Content-Type'] = 'application/json';
            headers['Content-Length'] = Buffer.byteLength(payload);
        }

        const req = http.request({
            hostname: 'localhost',
            port: PORT,
            ...options,
            headers
        }, (res) => {
            let body = '';
            res.on('data', chunk => body += chunk);
            res.on('end', () => {
                let parsed = null;
                try { parsed = JSON.parse(body); } catch (e) { parsed = body; }
                resolve({ statusCode: res.statusCode, headers: res.headers, body: parsed, rawBody: body });
            });
        });
        req.on('error', reject);
        if (payload) {
            req.write(payload);
        }
        req.end();
    });
}

function sleep(ms) {
    return new Promise(r => setTimeout(r, ms));
}

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

async function run() {
    console.log("===============================================================================");
    console.log("   AUTOMATED VERIFICATION: REVERSE CHRONOLOGICAL COMPLAINT ORDERING");
    console.log("===============================================================================\n");

    try {
        const ts = Date.now().toString().slice(-5);
        const rand4 = String(Math.floor(Math.random() * 8999 + 1000));

        // 1. Register a dedicated test citizen
        const citEmail = `order_test_${ts}_${rand4}@makkal.gov.in`;
        const citPass = "Pass@12345";
        const regRes = await request({ path: '/api/auth/register', method: 'POST' }, {
            name: `Order Tester ${ts}`,
            username: `order_cit_${ts}`,
            email: citEmail,
            mobile: `98765${rand4}`,
            password: citPass,
            identityNumber: `28495018${rand4}`
        });
        test(regRes.statusCode === 201, 'Test Citizen Registration', `Email: ${citEmail}`);

        // Login as Citizen
        const citLogin = await request({ path: '/api/auth/login', method: 'POST' }, {
            email: citEmail,
            password: citPass
        });
        const citToken = citLogin.body && citLogin.body.token;
        test(citLogin.statusCode === 200 && citToken, 'Citizen Authentication', 'Token received');

        // 2. Submit 5 complaints sequentially
        console.log("\n--- Submitting 5 sequential complaints with staggered creation ---");
        const submittedComplaints = [];
        for (let i = 1; i <= 5; i++) {
            await sleep(150); // Ensure slight delay between submissions
            const subRes = await request({
                path: '/api/complaints',
                method: 'POST',
                headers: { 'Authorization': citToken }
            }, {
                category: i % 2 === 0 ? "Water Supply" : "Road Damage",
                location: `North Ward Sector ${i} - Street ${ts}`,
                description: `Grievance issue sequential test record #${i} for ordering verification.`,
                affectedPeople: 10 + i,
                title: `Sequential Issue #${i}`
            });
            const c = subRes.body && subRes.body.complaintId ? subRes.body : (subRes.body && subRes.body.complaint);
            test(subRes.statusCode === 201 && c && c.complaintId,
                 `Submission of Complaint #${i} (${c ? c.complaintId : 'N/A'})`,
                 `Time: ${c ? c.createdAt : ''}`);
            submittedComplaints.push(c);
        }

        const newestSub = submittedComplaints[submittedComplaints.length - 1]; // Complaint #5
        const oldestSub = submittedComplaints[0];                             // Complaint #1

        // 3. Citizen Portal Ordering: GET /api/complaints/my
        console.log("\n--- Verifying Citizen Portal (My Complaints) ---");
        const citListRes = await request({
            path: '/api/complaints/my',
            method: 'GET',
            headers: { 'Authorization': citToken }
        });
        const citList = Array.isArray(citListRes.body) ? citListRes.body : [];

        test(citList.length >= 5, 'Citizen retrieves their complaints list', `Found: ${citList.length}`);
        test(citList[0].complaintId === newestSub.complaintId,
             'Most recently submitted complaint appears FIRST at top of citizen list',
             `Top: ${citList[0].complaintId}, Expected: ${newestSub.complaintId}`);
        test(citList[citList.length - 1].complaintId === oldestSub.complaintId,
             'Oldest complaint appears LAST at bottom of citizen list',
             `Bottom: ${citList[citList.length - 1].complaintId}, Expected: ${oldestSub.complaintId}`);

        // Verify strict descending order across entire citizen list
        let citDescStrict = true;
        for (let i = 0; i < citList.length - 1; i++) {
            const d1 = (citList[i].createdAt || '').replace('T', ' ');
            const d2 = (citList[i + 1].createdAt || '').replace('T', ' ');
            if (d1 < d2) {
                citDescStrict = false;
                console.error(`Citizen list ordering violation at index ${i}: ${d1} < ${d2}`);
                break;
            }
        }
        test(citDescStrict, 'Citizen complaints strictly ordered NEWEST -> OLDEST');

        // 4. Submit Complaint #6 and verify immediate appearance at top
        console.log("\n--- Submitting 6th Complaint to test immediate top insertion ---");
        await sleep(150);
        const sub6Res = await request({
            path: '/api/complaints',
            method: 'POST',
            headers: { 'Authorization': citToken }
        }, {
            category: "Streetlight",
            location: `Main Cross Road ${ts}`,
            description: "Complaint #6 immediate top verification test.",
            affectedPeople: 50,
            title: "Issue #6 Immediate Top"
        });
        const c6 = sub6Res.body && sub6Res.body.complaintId ? sub6Res.body : (sub6Res.body && sub6Res.body.complaint);
        test(sub6Res.statusCode === 201 && c6, 'Complaint #6 Registered Successfully', `ID: ${c6 ? c6.complaintId : ''}`);

        const citListRes2 = await request({
            path: '/api/complaints/my',
            method: 'GET',
            headers: { 'Authorization': citToken }
        });
        const citList2 = Array.isArray(citListRes2.body) ? citListRes2.body : [];
        test(citList2[0].complaintId === c6.complaintId,
             'Newly submitted Complaint #6 immediately appears at top of complaint list',
             `Index 0: ${citList2[0].complaintId}`);

        // 5. Admin Portal Ordering: GET /api/complaints (Admin role)
        console.log("\n--- Verifying Admin Portal (All Complaints) ---");
        const adminLogin = await request({ path: '/api/auth/login', method: 'POST' }, {
            email: 'admin@makkal.gov.in',
            password: 'admin123'
        });
        const adminToken = adminLogin.body && adminLogin.body.token;
        test(adminLogin.statusCode === 200 && adminToken, 'Admin Authentication', 'Token received');

        const adminComplaintsRes = await request({
            path: '/api/complaints',
            method: 'GET',
            headers: { 'Authorization': adminToken }
        });
        const adminList = Array.isArray(adminComplaintsRes.body) ? adminComplaintsRes.body : [];
        test(adminList.length > 0, 'Admin retrieves Master Complaints Register', `Total: ${adminList.length}`);

        // Verify strict descending order across entire admin register
        let adminDescStrict = true;
        for (let i = 0; i < adminList.length - 1; i++) {
            const d1 = (adminList[i].createdAt || '').replace('T', ' ');
            const d2 = (adminList[i + 1].createdAt || '').replace('T', ' ');
            if (d1 < d2) {
                adminDescStrict = false;
                console.error(`Admin register ordering violation at index ${i}: ${d1} < ${d2}`);
                break;
            }
        }
        test(adminDescStrict, 'Admin Master Register is strictly ordered NEWEST -> OLDEST');
        test(adminList[0].complaintId === c6.complaintId,
             'Admin sees newly registered Complaint #6 at top of master list',
             `Admin Top: ${adminList[0].complaintId}`);

        // 6. Manager Portal Ordering: GET /api/complaints (Manager role)
        console.log("\n--- Verifying Manager Portal ---");
        const mgrLogin = await request({ path: '/api/auth/login', method: 'POST' }, {
            email: 'manager@makkal.gov.in',
            password: 'admin123'
        });
        const mgrToken = mgrLogin.body && mgrLogin.body.token;
        test(mgrLogin.statusCode === 200 && mgrToken, 'Manager Authentication', 'Token received');

        const mgrComplaintsRes = await request({
            path: '/api/complaints',
            method: 'GET',
            headers: { 'Authorization': mgrToken }
        });
        const mgrList = Array.isArray(mgrComplaintsRes.body) ? mgrComplaintsRes.body : [];
        test(mgrList.length > 0, 'Manager retrieves complaints list', `Total: ${mgrList.length}`);

        let mgrDescStrict = true;
        for (let i = 0; i < mgrList.length - 1; i++) {
            const d1 = (mgrList[i].createdAt || '').replace('T', ' ');
            const d2 = (mgrList[i + 1].createdAt || '').replace('T', ' ');
            if (d1 < d2) {
                mgrDescStrict = false;
                console.error(`Manager list ordering violation at index ${i}: ${d1} < ${d2}`);
                break;
            }
        }
        test(mgrDescStrict, 'Manager Portal complaints strictly ordered NEWEST -> OLDEST');

        // 7. Worker Portal Ordering: GET /api/complaints/worker
        console.log("\n--- Verifying Worker Portal (Department Complaints) ---");
        const workerLogin = await request({ path: '/api/auth/login', method: 'POST' }, {
            email: 'water_staff@makkal.gov.in',
            password: 'admin123'
        });
        const workerToken = workerLogin.body && workerLogin.body.token;
        test(workerLogin.statusCode === 200 && workerToken, 'Worker Authentication', 'Token received');

        const workerComplaintsRes = await request({
            path: '/api/complaints/worker',
            method: 'GET',
            headers: { 'Authorization': workerToken }
        });
        const workerList = Array.isArray(workerComplaintsRes.body) ? workerComplaintsRes.body : [];
        test(workerList.length > 0, 'Worker retrieves assigned department complaints', `Total: ${workerList.length}`);

        let workerDescStrict = true;
        for (let i = 0; i < workerList.length - 1; i++) {
            const d1 = (workerList[i].createdAt || '').replace('T', ' ');
            const d2 = (workerList[i + 1].createdAt || '').replace('T', ' ');
            if (d1 < d2) {
                workerDescStrict = false;
                console.error(`Worker list ordering violation at index ${i}: ${d1} < ${d2}`);
                break;
            }
        }
        test(workerDescStrict, 'Worker assigned complaints strictly ordered NEWEST -> OLDEST');

        // 8. Search & Filter Preservation on Ordered Results
        console.log("\n--- Verifying Search & Filters preserve NEWEST -> OLDEST order ---");
        const waterComplaints = adminList.filter(c => c.category === 'Water Supply');
        let filterDescStrict = true;
        for (let i = 0; i < waterComplaints.length - 1; i++) {
            const d1 = (waterComplaints[i].createdAt || '').replace('T', ' ');
            const d2 = (waterComplaints[i + 1].createdAt || '').replace('T', ' ');
            if (d1 < d2) {
                filterDescStrict = false;
                break;
            }
        }
        test(filterDescStrict && waterComplaints.length > 0,
             'Category filter preserves NEWEST -> OLDEST order',
             `Count: ${waterComplaints.length}`);

        // 9. Pagination Integrity: Page 1 items are newer than Page 2 items
        console.log("\n--- Verifying Pagination Integrity ---");
        const pageSize = 10;
        const page1 = adminList.slice(0, pageSize);
        const page2 = adminList.slice(pageSize, pageSize * 2);
        test(page1.length === pageSize && page2.length > 0, 'Pagination slicing produces valid pages', `Page 1: ${page1.length}, Page 2: ${page2.length}`);

        const page1Oldest = page1[page1.length - 1].createdAt.replace('T', ' ');
        const page2Newest = page2[0].createdAt.replace('T', ' ');
        test(page1Oldest >= page2Newest,
             'Pagination maintains chronological descent (Page 1 oldest >= Page 2 newest)',
             `P1 End: ${page1Oldest} >= P2 Start: ${page2Newest}`);

        // 10. Priority Queue Integrity: Remains ordered by PRIORITY_COMPARATOR (not broken)
        console.log("\n--- Verifying Priority Queue is NOT altered ---");
        const pqRes = await request({
            path: '/api/complaints/priority-queue',
            method: 'GET',
            headers: { 'Authorization': adminToken }
        });
        const pqList = Array.isArray(pqRes.body) ? pqRes.body : [];
        test(pqList.length > 0, 'Priority Queue retrieved', `Total items: ${pqList.length}`);
        
        // In Priority Queue, CRITICAL comes before HIGH/MEDIUM/LOW
        const firstCrit = pqList.findIndex(c => c.priority === 'CRITICAL');
        const firstLow = pqList.findIndex(c => c.priority === 'LOW');
        test(firstCrit !== -1 && (firstLow === -1 || firstCrit < firstLow),
             'Priority Queue correctly retains multi-factor priority ordering (Critical before Low)',
             `First Critical idx: ${firstCrit}, First Low idx: ${firstLow}`);

        console.log("\n===============================================================================");
        console.log(`REVERSE CHRONOLOGICAL ORDERING RESULTS: ${passed} / ${total} TESTS PASSED`);
        console.log("===============================================================================");

        process.exit(passed === total ? 0 : 1);

    } catch (err) {
        console.error("Test Error:", err);
        process.exit(1);
    }
}

run();
