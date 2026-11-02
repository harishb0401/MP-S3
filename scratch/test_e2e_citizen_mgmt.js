const http = require('http');
const { spawn } = require('child_process');

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
    return new Promise(resolve => setTimeout(resolve, ms));
}

async function run() {
    console.log("===============================================================================");
    console.log("       END-TO-END VERIFICATION: REGISTERED CITIZEN MANAGEMENT REST API");
    console.log("===============================================================================\n");

    const ts = Date.now().toString().slice(-4);
    let serverProc = null;
    let ready = false;
    try {
        const check = await request({ path: '/api/dashboard/stats', method: 'GET' });
        if (check.statusCode === 200) ready = true;
    } catch (e) {}

    if (!ready) {
        serverProc = spawn('java', ['-cp', 'backend/bin;backend/lib/*', 'Main'], {
            cwd: process.cwd(),
            stdio: 'pipe'
        });
        for (let i = 0; i < 25; i++) {
            await sleep(500);
            try {
                const check = await request({ path: '/api/dashboard/stats', method: 'GET' });
                if (check.statusCode === 200) { ready = true; break; }
            } catch (e) {}
        }
    }

    if (!ready) {
        console.error("FAIL: Server did not start up within 12 seconds.");
        if (serverProc) if (serverProc) if (serverProc) serverProc.kill();
        process.exit(1);
    }
    console.log("[SERVER READY] Server started and responding on port 8080\n");

    let passed = 0;
    let total = 0;

    function assert(cond, name, details = '') {
        total++;
        if (cond) {
            passed++;
            console.log(`[TEST ${total}] ${name}... PASSED ${details ? '(' + details + ')' : ''}`);
        } else {
            console.error(`[TEST ${total}] ${name}... FAILED ${details ? ': ' + details : ''}`);
        }
    }

    try {
        // 1. Unauthenticated Access Protection (401)
        const unauthRes = await request({ path: '/api/admin/citizens', method: 'GET' });
        assert(unauthRes.statusCode === 401, 'Unauthenticated Access to Citizens is Blocked (401)', `HTTP ${unauthRes.statusCode}`);

        // Login as Admin
        const adminLogin = await request({
            path: '/api/auth/login',
            method: 'POST'
        }, { email: 'admin@makkal.gov.in', password: 'admin123' });
        const adminToken = adminLogin.body && adminLogin.body.token;
        assert(adminLogin.statusCode === 200 && adminToken, 'Admin Authentication Successful', `Token received`);

        // Login as Worker
        const workerLogin = await request({
            path: '/api/auth/login',
            method: 'POST'
        }, { email: 'water_staff@makkal.gov.in', password: 'admin123' });
        const workerToken = workerLogin.body && workerLogin.body.token;

        // Login as Citizen
        const citizenLogin = await request({
            path: '/api/auth/login',
            method: 'POST'
        }, { email: 'ramesh@test.com', password: '123456' });
        const citizenToken = citizenLogin.body && citizenLogin.body.token;

        // 2. Non-Admin Role-Based Access Control (403 Forbidden)
        const workerCitRes = await request({
            path: '/api/admin/citizens',
            method: 'GET',
            headers: { 'Authorization': workerToken }
        });
        assert(workerCitRes.statusCode === 403, 'Worker Access to Citizens Directory is Forbidden (403)', `HTTP ${workerCitRes.statusCode}`);

        const citizenCitRes = await request({
            path: '/api/admin/citizens',
            method: 'GET',
            headers: { 'Authorization': citizenToken }
        });
        assert(citizenCitRes.statusCode === 403, 'Citizen Access to Admin Citizens Directory is Forbidden (403)', `HTTP ${citizenCitRes.statusCode}`);

        // 3. Admin Successfully Retrieves Registered Citizens List (200 OK)
        const adminCitRes = await request({
            path: '/api/admin/citizens',
            method: 'GET',
            headers: { 'Authorization': adminToken }
        });
        assert(adminCitRes.statusCode === 200 && Array.isArray(adminCitRes.body) && adminCitRes.body.length > 0, 
               'Admin Retrieves Registered Citizens Directory (200 OK)', `Found ${adminCitRes.body ? adminCitRes.body.length : 0} citizens`);

        // 4. Verify Citizen Information Fields Completeness & Data Sanitization (No Passwords / Hashes / Tokens)
        const citizensList = adminCitRes.body || [];
        const sampleCitizen = citizensList[0] || {};
        const hasRequiredFields = sampleCitizen.id && sampleCitizen.name && sampleCitizen.username && sampleCitizen.email && sampleCitizen.createdAt !== undefined;
        const noSensitiveFields = sampleCitizen.password === undefined && sampleCitizen.passwordHash === undefined && sampleCitizen.token === undefined;
        assert(hasRequiredFields && noSensitiveFields, 'Citizen Directory Shows Required Fields and Omits Sensitive Data', 
               `ID: ${sampleCitizen.id}, Username: ${sampleCitizen.username}, Safe: ${noSensitiveFields}`);

        // 5. Read-Only Protection: Modifying Citizen Data is Strictly Forbidden (403)
        const putAttempt = await request({
            path: `/api/admin/citizens/${sampleCitizen.id}`,
            method: 'PUT',
            headers: { 'Authorization': adminToken }
        }, { name: 'Hacked Name' });
        assert(putAttempt.statusCode === 403, 'Admin Cannot Modify Citizen Profile - Strictly Read-Only (403)', `HTTP ${putAttempt.statusCode}`);

        // 6. View Details: Single Citizen Profile & Grievance Statistics (200 OK)
        const detailsRes = await request({
            path: `/api/admin/citizens/${sampleCitizen.id}`,
            method: 'GET',
            headers: { 'Authorization': adminToken }
        });
        const details = detailsRes.body || {};
        const hasStats = details.stats && details.stats.total !== undefined && details.stats.resolved !== undefined;
        const hasComplaints = Array.isArray(details.complaints);
        assert(detailsRes.statusCode === 200 && details.citizen && hasStats && hasComplaints, 
               'Citizen View Details Endpoint Returns Profile, Stats & Complaints History', 
               `Total: ${details.stats ? details.stats.total : 0}, History Items: ${details.complaints ? details.complaints.length : 0}`);

        // 7. Register New Citizen and Verify Real-time Visibility in Admin Portal
        const newCitEmail = `citizen_${ts}@example.com`;
        const newCitReg = await request({
            path: '/api/auth/register',
            method: 'POST'
        }, {
            name: `Aravind Test_${ts}`,
            username: `aravind_${ts}`,
            email: newCitEmail,
            mobile: '9840112233',
            password: 'SecurePassword123'
        });
        assert(newCitReg.statusCode === 201, 'New Citizen Account Registration (201 Created)', `Email: ${newCitEmail}`);

        // 8. Verify Immediate Visibility for Admin
        const updatedCitRes = await request({
            path: '/api/admin/citizens',
            method: 'GET',
            headers: { 'Authorization': adminToken }
        });
        const updatedList = Array.isArray(updatedCitRes.body) ? updatedCitRes.body : [];
        const foundNewCit = updatedList.find(c => (c.email || '').toLowerCase() === newCitEmail.toLowerCase());
        assert(foundNewCit !== undefined, 'Newly Registered Citizen Immediately Appears in Admin Citizens List', 
               `Found ID: ${foundNewCit ? foundNewCit.id : 'N/A'}, Name: ${foundNewCit ? foundNewCit.name : 'N/A'}`);

    } catch (err) {
        console.error("Test Exception:", err);
    } finally {
        if (serverProc) serverProc.kill();
        console.log("\n-------------------------------------------------------------------------------");
        console.log(`LIVE E2E REST API RESULT: ${passed} / ${total} TESTS PASSED`);
        console.log("===============================================================================");
        process.exit(passed === total ? 0 : 1);
    }
}

run();
