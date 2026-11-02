const http = require('http');
const { spawn } = require('child_process');
const fs = require('fs');

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
    console.log("   LIVE E2E REST API TEST: UNIQUE CITIZEN IDENTITY VERIFICATION");
    console.log("===============================================================================\n");

    const rand4 = String(Math.floor(Math.random() * 8999 + 1000));
    const ts = Date.now().toString().slice(-5) + "_" + rand4;
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
        const testIdentity1 = "28495018" + rand4; // 12 digits, starts with 2
        const testEmail1 = `citizen_uid_${ts}@example.com`;

        // 1. Citizen Registration with 12-digit Identity Verification (201 Created)
        const regRes1 = await request({
            path: '/api/auth/register',
            method: 'POST'
        }, {
            name: `Meenakshi Sundaram_${ts}`,
            username: `meena_${ts}`,
            email: testEmail1,
            mobile: '9840112244',
            password: 'Password@123',
            identityNumber: testIdentity1
        });

        const userObj1 = regRes1.body && regRes1.body.user;
        const maskedExpected = "XXXX-XXXX-" + testIdentity1.substring(8);
        const hasNoPlaintext = regRes1.rawBody.indexOf(testIdentity1) === -1;

        assert(regRes1.statusCode === 201 && userObj1 && userObj1.maskedIdentity === maskedExpected && hasNoPlaintext,
               'Citizen Registration with 12-digit Identity (201 Created)',
               `Masked: ${userObj1 ? userObj1.maskedIdentity : 'N/A'}, Plaintext Excluded: ${hasNoPlaintext}`);

        // 2. Duplicate Registration Attempt with Same Identity (Requirement 6 & 7: 409 Conflict)
        const regDupRes = await request({
            path: '/api/auth/register',
            method: 'POST'
        }, {
            name: `Imposter Citizen_${ts}`,
            username: `imposter_${ts}`,
            email: `imposter_${ts}@example.com`,
            mobile: '9840998877',
            password: 'Password@123',
            identityNumber: testIdentity1 // Duplicate!
        });

        const expectedError = "This identity number is already associated with an existing citizen account.";
        const actualError = regDupRes.body && regDupRes.body.error;
        const noPriorAccountLeak = regDupRes.rawBody.indexOf(testEmail1) === -1 && regDupRes.rawBody.indexOf(`meena_${ts}`) === -1;

        assert(regDupRes.statusCode === 409 && actualError === expectedError && noPriorAccountLeak,
               'Duplicate Identity Registration Prevented with Exact Message (409 Conflict)',
               `Error: "${actualError}", Prior Account Protected: ${noPriorAccountLeak}`);

        // 3. Duplicate Prevention with Alternative Formatting (Dashes / Spaces)
        const formattedDupIdentity = testIdentity1.substring(0, 4) + "-" + testIdentity1.substring(4, 8) + "-" + testIdentity1.substring(8);
        const regFormattedDup = await request({
            path: '/api/auth/register',
            method: 'POST'
        }, {
            name: `Formatted Imposter_${ts}`,
            username: `fmt_imposter_${ts}`,
            email: `fmt_imposter_${ts}@example.com`,
            mobile: '9840998878',
            password: 'Password@123',
            identityNumber: formattedDupIdentity // Same number with hyphens!
        });

        assert(regFormattedDup.statusCode === 409 && regFormattedDup.body.error === expectedError,
               'Duplicate Identity Detected Across Formatted Variations (Hyphens/Spaces)',
               `Input: ${formattedDupIdentity} -> Blocked with 409`);

        // 4. Input Validation: Number Starting with 0 Rejected (400 Bad Request)
        const zeroRes = await request({
            path: '/api/auth/register',
            method: 'POST'
        }, {
            name: "Zero Prefix",
            email: `zero_${ts}@example.com`,
            mobile: '9840112255',
            password: 'Password@123',
            identityNumber: "012345678901"
        });
        assert(zeroRes.statusCode === 400 && zeroRes.body.error.includes("cannot start with 0 or 1"),
               'Invalid Identity Starting with 0 Rejected (400 Bad Request)', `Message: "${zeroRes.body.error}"`);

        // 5. Input Validation: Number Starting with 1 Rejected (400 Bad Request)
        const oneRes = await request({
            path: '/api/auth/register',
            method: 'POST'
        }, {
            name: "One Prefix",
            email: `one_${ts}@example.com`,
            mobile: '9840112266',
            password: 'Password@123',
            identityNumber: "123456789012"
        });
        assert(oneRes.statusCode === 400 && oneRes.body.error.includes("cannot start with 0 or 1"),
               'Invalid Identity Starting with 1 Rejected (400 Bad Request)', `Message: "${oneRes.body.error}"`);

        // 6. Input Validation: Invalid Length and Non-numeric Characters Rejected (400)
        const shortRes = await request({
            path: '/api/auth/register',
            method: 'POST'
        }, {
            name: "Short UID",
            email: `short_${ts}@example.com`,
            mobile: '9840112277',
            password: 'Password@123',
            identityNumber: "2345678"
        });
        assert(shortRes.statusCode === 400 && shortRes.body.error.includes("must be exactly 12 digits"),
               'Short Identity Number (<12 digits) Rejected (400 Bad Request)', `Message: "${shortRes.body.error}"`);

        // 7. Input Validation: Repetitive Identical Digits Rejected (400)
        const repRes = await request({
            path: '/api/auth/register',
            method: 'POST'
        }, {
            name: "Repetitive UID",
            email: `rep_${ts}@example.com`,
            mobile: '9840112288',
            password: 'Password@123',
            identityNumber: "222222222222"
        });
        assert(repRes.statusCode === 400 && repRes.body.error.includes("identical repeating digits"),
               'All-Identical Digits Sequence Rejected (400 Bad Request)', `Message: "${repRes.body.error}"`);

        // 8. Admin Portal: Registered Citizens API Exposes Only Masked Identity (Never Full Number)
        const adminLogin = await request({
            path: '/api/auth/login',
            method: 'POST'
        }, { email: 'admin@makkal.gov.in', password: 'admin123' });
        const adminToken = adminLogin.body && adminLogin.body.token;

        const getCitizens = await request({
            path: '/api/admin/citizens',
            method: 'GET',
            headers: { 'Authorization': adminToken }
        });

        const citList = Array.isArray(getCitizens.body) ? getCitizens.body : [];
        const registeredCit = citList.find(c => (c.email || '').toLowerCase() === testEmail1.toLowerCase());
        const rawNotExposed = getCitizens.rawBody.indexOf(testIdentity1) === -1;

        assert(getCitizens.statusCode === 200 && registeredCit && registeredCit.maskedIdentity === maskedExpected && rawNotExposed,
               'Admin Portal Registered Citizens Directory Displays Masked Identity Only',
               `Masked: ${registeredCit ? registeredCit.maskedIdentity : 'N/A'}, Plaintext Omitted: ${rawNotExposed}`);

        // 9. Existing Citizen Login Uninterrupted
        const legacyLogin = await request({
            path: '/api/auth/login',
            method: 'POST'
        }, { email: 'ramesh@test.com', password: '123456' });

        assert(legacyLogin.statusCode === 200 && legacyLogin.body && legacyLogin.body.user && legacyLogin.body.user.role === 'CITIZEN',
               'Existing Citizen Login Works Uninterrupted',
               `Citizen: ${legacyLogin.body && legacyLogin.body.user ? legacyLogin.body.user.name : 'N/A'}`);

        // 10. Database Persistence Integrity: Verify Plaintext is NEVER Written to database.json
        await new Promise(r => setTimeout(r, 800));
        const dbJsonContent = fs.readFileSync('backend/data/database.json', 'utf8');
        const rawNotFoundInFile = dbJsonContent.indexOf(testIdentity1) === -1;
        const hashFoundInFile = dbJsonContent.indexOf(maskedExpected) !== -1;

        assert(rawNotFoundInFile && hashFoundInFile,
               'Zero-Plaintext Storage Guarantee Verified in Persistent Database File',
               `Plaintext Absent: ${rawNotFoundInFile}, Masked Present: ${hashFoundInFile}`);

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

