const http = require('http');
const { spawn } = require('child_process');

function request(options, data) {
    return new Promise((resolve, reject) => {
        const req = http.request(options, (res) => {
            let body = '';
            res.on('data', chunk => body += chunk);
            res.on('end', () => {
                let parsed = null;
                try {
                    parsed = JSON.parse(body);
                } catch (e) {
                    parsed = body;
                }
                resolve({ statusCode: res.statusCode, headers: res.headers, body: parsed, rawBody: body });
            });
        });
        req.on('error', reject);
        if (data) {
            req.write(typeof data === 'string' ? data : JSON.stringify(data));
        }
        req.end();
    });
}

function sleep(ms) {
    return new Promise(resolve => setTimeout(resolve, ms));
}

async function run() {
    console.log("===============================================================================");
    console.log("       END-TO-END VERIFICATION: MULTIPLE WORKER MANAGEMENT REST API");
    console.log("===============================================================================\n");

    const ts = Date.now().toString().slice(-4);
    let serverProc = null;
    let ready = false;
    try {
        const check = await request({ hostname: 'localhost', port: 8080, path: '/api/dashboard/stats', method: 'GET' });
        if (check.statusCode === 200) ready = true;
    } catch (e) {}

    if (!ready) {
        serverProc = spawn('java', ['-cp', 'backend/bin;backend/lib/*', 'Main'], {
            cwd: process.cwd(),
            stdio: 'pipe'
        });
        for (let i = 0; i < 20; i++) {
            await sleep(500);
            try {
                const check = await request({
                    hostname: 'localhost',
                    port: 8080,
                    path: '/api/dashboard/stats',
                    method: 'GET'
                });
                if (check.statusCode === 200) {
                    ready = true;
                    break;
                }
            } catch (e) {}
        }
    }

    if (!ready) {
        console.error("Server failed to start on http://localhost:8080");
        if (serverProc) serverProc.kill();
        process.exit(1);
    }
    console.log("[SERVER READY] Connected to http://localhost:8080/api/\n");

    let passed = 0;
    let total = 8;

    try {
        // 1. Authenticate as Admin
        console.log("[STEP 1/8] Admin Authentication...");
        const adminLogin = await request({
            hostname: 'localhost',
            port: 8080,
            path: '/api/auth/login',
            method: 'POST',
            headers: { 'Content-Type': 'application/json' }
        }, { email: 'admin@makkal.gov.in', password: 'admin123' });

        if (adminLogin.statusCode !== 200 || !adminLogin.body.token) {
            throw new Error("Admin login failed: " + JSON.stringify(adminLogin.body));
        }
        const adminToken = adminLogin.body.token;
        console.log("  -> SUCCESS: Admin token acquired.");
        passed++;

        // 2. Query initial workers list
        console.log("\n[STEP 2/8] Fetching All Registered Workers (GET /api/admin/workers)...");
        const getWorkers = await request({
            hostname: 'localhost',
            port: 8080,
            path: '/api/admin/workers',
            method: 'GET',
            headers: { 'Authorization': adminToken }
        });

        if (getWorkers.statusCode !== 200 || !Array.isArray(getWorkers.body)) {
            throw new Error("Failed to get workers: " + JSON.stringify(getWorkers.body));
        }
        console.log(`  -> SUCCESS: Retrieved ${getWorkers.body.length} workers.`);
        passed++;

        // 3. Add Individual Worker
        console.log("\n[STEP 3/8] Adding Single Worker via POST /api/admin/workers...");
        const testWorker1 = {
            name: "Rajesh Kannan",
            username: `rajesh_${ts}`,
            email: `rajesh_${ts}@makkal.gov.in`,
            mobile: "9876543291",
            password: "Password@123",
            department: "Water Supply"
        };
        const createSingle = await request({
            hostname: 'localhost',
            port: 8080,
            path: '/api/admin/workers',
            method: 'POST',
            headers: { 'Content-Type': 'application/json', 'Authorization': adminToken }
        }, testWorker1);

        if (createSingle.statusCode !== 201 || !createSingle.body.worker) {
            throw new Error("Create single worker failed: " + JSON.stringify(createSingle.body));
        }
        const singleWorker = createSingle.body.worker;
        console.log(`  -> SUCCESS: Worker Created with ID: ${singleWorker.id || singleWorker.workerId}, Username: ${singleWorker.username}, Role: ${singleWorker.role}`);
        passed++;

        // 4. Add Multiple Workers in Batch
        console.log("\n[STEP 4/8] Adding Multiple Workers in Batch via POST /api/admin/workers...");
        const batchPayload = {
            workers: [
                {
                    name: "Selvamani Natarajan",
                    username: `selvam_${ts}`,
                    email: `selvamani_${ts}@makkal.gov.in`,
                    mobile: "9876543292",
                    password: "Password@123",
                    department: "Roads"
                },
                {
                    name: "Manimegalai Krishnan",
                    username: `mani_${ts}`,
                    email: `manimegalai_${ts}@makkal.gov.in`,
                    mobile: "9876543293",
                    password: "Password@123",
                    department: "Waste Management"
                },
                {
                    name: "Venkatesh Babu",
                    username: `venkat_${ts}`,
                    email: `venkatesh_${ts}@makkal.gov.in`,
                    mobile: "9876543294",
                    password: "Password@123",
                    department: "Streetlights"
                }
            ]
        };

        const createBatch = await request({
            hostname: 'localhost',
            port: 8080,
            path: '/api/admin/workers',
            method: 'POST',
            headers: { 'Content-Type': 'application/json', 'Authorization': adminToken }
        }, batchPayload);

        if (createBatch.statusCode !== 201 || !Array.isArray(createBatch.body.workers) || createBatch.body.workers.length !== 3) {
            throw new Error("Create batch workers failed: " + JSON.stringify(createBatch.body));
        }
        console.log(`  -> SUCCESS: Registered ${createBatch.body.workers.length} workers in batch.`);
        passed++;

        // 5. Test Duplicate Prevention (Duplicate Username & Duplicate Email)
        console.log("\n[STEP 5/8] Verifying Duplicate Prevention (Username & Email)...");
        const dupUsernameAttempt = await request({
            hostname: 'localhost',
            port: 8080,
            path: '/api/admin/workers',
            method: 'POST',
            headers: { 'Content-Type': 'application/json', 'Authorization': adminToken }
        }, {
            name: "Duplicate Tester",
            username: `RAJESH_${ts}`, // Already used
            email: `other_${ts}@makkal.gov.in`,
            mobile: "9876543299",
            password: "Password@123",
            department: "Water Supply"
        });

        if (dupUsernameAttempt.statusCode !== 400 || !JSON.stringify(dupUsernameAttempt.body).includes("already taken")) {
            throw new Error("Expected 400 Bad Request for duplicate username");
        }
        console.log(`  -> SUCCESS: Duplicate username 'RAJESH_${ts}' was blocked with 400 Bad Request.`);

        const dupEmailAttempt = await request({
            hostname: 'localhost',
            port: 8080,
            path: '/api/admin/workers',
            method: 'POST',
            headers: { 'Content-Type': 'application/json', 'Authorization': adminToken }
        }, {
            name: "Duplicate Email Tester",
            username: `unique_user_${ts}`,
            email: `rajesh_${ts}@makkal.gov.in`, // Already used
            mobile: "9876543299",
            password: "Password@123",
            department: "Water Supply"
        });

        if (dupEmailAttempt.statusCode !== 400 || !JSON.stringify(dupEmailAttempt.body).includes("already exists")) {
            throw new Error("Expected 400 Bad Request for duplicate email");
        }
        console.log(`  -> SUCCESS: Duplicate email 'rajesh_${ts}@makkal.gov.in' was blocked with 400 Bad Request.`);
        passed++;

        // 6. Edit Worker Details & Department Assignment
        console.log("\n[STEP 6/8] Editing Worker Details and Changing Department...");
        const targetWorkerId = singleWorker.id || singleWorker.workerId;
        const updateRes = await request({
            hostname: 'localhost',
            port: 8080,
            path: `/api/admin/workers/${targetWorkerId}`,
            method: 'PUT',
            headers: { 'Content-Type': 'application/json', 'Authorization': adminToken }
        }, {
            name: "Rajesh Kannan (Lead Officer)",
            username: `rajesh_lead_${ts}`,
            email: `rajesh_lead_${ts}@makkal.gov.in`,
            mobile: "9876599999",
            department: "Roads Department",
            active: true
        });

        if (updateRes.statusCode !== 200 || !updateRes.body.worker) {
            throw new Error("Update worker failed: " + JSON.stringify(updateRes.body));
        }
        const updated = updateRes.body.worker;
        console.log(`  -> SUCCESS: Worker Updated: Name="${updated.name}", Username="${updated.username}", Dept="${updated.department}"`);
        passed++;

        // 7. Test Worker Login via Username, Worker ID, and Email
        console.log("\n[STEP 7/8] Verifying Multi-Identifier Login into Worker Portal...");
        // 7a. Login by Username
        const loginByUsername = await request({
            hostname: 'localhost',
            port: 8080,
            path: '/api/auth/login',
            method: 'POST',
            headers: { 'Content-Type': 'application/json' }
        }, { email: `rajesh_lead_${ts}`, password: 'Password@123' });

        if (loginByUsername.statusCode !== 200 || (loginByUsername.body.user.role !== 'STAFF' && loginByUsername.body.user.role !== 'WORKER')) {
            throw new Error("Worker login by username failed: " + JSON.stringify(loginByUsername.body));
        }
        console.log(`  -> SUCCESS: Logged in using Username 'rajesh_lead_${ts}'. Role: ` + loginByUsername.body.user.role);

        // 7b. Login by Worker ID
        const loginById = await request({
            hostname: 'localhost',
            port: 8080,
            path: '/api/auth/login',
            method: 'POST',
            headers: { 'Content-Type': 'application/json' }
        }, { email: targetWorkerId, password: 'Password@123' });

        if (loginById.statusCode !== 200) {
            throw new Error("Worker login by ID failed: " + JSON.stringify(loginById.body));
        }
        console.log(`  -> SUCCESS: Logged in using Worker ID '${targetWorkerId}'.`);

        // 7c. Login by Email
        const loginByEmail = await request({
            hostname: 'localhost',
            port: 8080,
            path: '/api/auth/login',
            method: 'POST',
            headers: { 'Content-Type': 'application/json' }
        }, { email: `rajesh_lead_${ts}@makkal.gov.in`, password: 'Password@123' });

        if (loginByEmail.statusCode !== 200) {
            throw new Error("Worker login by email failed: " + JSON.stringify(loginByEmail.body));
        }
        console.log(`  -> SUCCESS: Logged in using Email 'rajesh_lead_${ts}@makkal.gov.in'.`);
        passed++;

        // 8. Test Worker Deactivation & Reactivation
        console.log("\n[STEP 8/8] Testing Worker Deactivation & Activation Access Control...");
        const deactRes = await request({
            hostname: 'localhost',
            port: 8080,
            path: `/api/admin/workers/${targetWorkerId}`,
            method: 'PUT',
            headers: { 'Content-Type': 'application/json', 'Authorization': adminToken }
        }, { active: false });

        if (deactRes.statusCode !== 200) {
            throw new Error("Deactivate worker failed: " + JSON.stringify(deactRes.body));
        }

        const loginDeact = await request({
            hostname: 'localhost',
            port: 8080,
            path: '/api/auth/login',
            method: 'POST',
            headers: { 'Content-Type': 'application/json' }
        }, { email: `rajesh_lead_${ts}`, password: 'Password@123' });

        if (loginDeact.statusCode !== 403) {
            throw new Error("Expected 403 Forbidden for deactivated worker login, got: " + loginDeact.statusCode);
        }
        console.log("  -> SUCCESS: Deactivated worker login blocked with 403 Forbidden.");

        const reactRes = await request({
            hostname: 'localhost',
            port: 8080,
            path: `/api/admin/workers/${targetWorkerId}`,
            method: 'PUT',
            headers: { 'Content-Type': 'application/json', 'Authorization': adminToken }
        }, { active: true });

        const loginReact = await request({
            hostname: 'localhost',
            port: 8080,
            path: '/api/auth/login',
            method: 'POST',
            headers: { 'Content-Type': 'application/json' }
        }, { email: `rajesh_lead_${ts}`, password: 'Password@123' });

        if (loginReact.statusCode !== 200) {
            throw new Error("Reactivated worker login failed: " + JSON.stringify(loginReact.body));
        }
        console.log("  -> SUCCESS: Reactivated worker login restored successfully.");
        passed++;

        console.log("\n===============================================================================");
        console.log(`E2E TEST RESULT: ${passed} / ${total} TESTS PASSED (100% SUCCESS RATE)`);
        console.log("===============================================================================\n");

    } catch (err) {
        console.error("\n[E2E TEST ERROR]", err);
    } finally {
        if (serverProc) {
            try {
                process.kill(serverProc.pid);
            } catch (e) {}
        }
        process.exit(passed === total ? 0 : 1);
    }
}

run();
