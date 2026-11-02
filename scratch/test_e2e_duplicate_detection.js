const http = require('http');

const PORT = 8080;
const BASE_URL = `http://localhost:${PORT}/api`;

function request(method, path, body = null, token = null) {
    return new Promise((resolve, reject) => {
        const url = new URL(path.startsWith('http') ? path : `${BASE_URL}${path}`);
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
            options.headers['Authorization'] = `Bearer ${token}`;
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
                resolve({ status: res.statusCode, headers: res.headers, data: parsed });
            });
        });

        req.on('error', reject);

        if (body) {
            req.write(typeof body === 'string' ? body : JSON.stringify(body));
        }
        req.end();
    });
}

async function run() {
    console.log("===============================================================================");
    console.log("   LIVE REST API E2E TEST: ENHANCED DUPLICATE DETECTION & NOTIFICATIONS");
    console.log("===============================================================================\n");

    let passed = 0;
    const total = 9;
    const nowTs = Date.now();

    try {
        // 1. Authenticate Admin and Manager
        const adminLogin = await request('POST', '/auth/login', { email: 'admin@makkal.gov.in', password: 'admin123' });
        if (adminLogin.status !== 200 || !adminLogin.data || !adminLogin.data.token) {
            throw new Error(`Admin login failed: ${JSON.stringify(adminLogin.data)}`);
        }
        const adminToken = adminLogin.data.token;

        const mgrLogin = await request('POST', '/auth/login', { email: 'manager@makkal.gov.in', password: 'admin123' });
        if (mgrLogin.status !== 200 || !mgrLogin.data || !mgrLogin.data.token) {
            throw new Error(`Manager login failed: ${JSON.stringify(mgrLogin.data)}`);
        }
        const mgrToken = mgrLogin.data.token;

        // Register 3 Distinct Citizens with valid 12-digit identities
        const citAData = {
            name: `Citizen Anand ${nowTs}`,
            username: `cit_anand_${nowTs}`,
            email: `anand_${nowTs}@example.com`,
            mobile: `98765${String(nowTs).slice(-5)}`,
            password: 'Citizen@123',
            identityNumber: '5' + String(nowTs).slice(-11)
        };
        const citBData = {
            name: `Citizen Bhuvan ${nowTs}`,
            username: `cit_bhuvan_${nowTs}`,
            email: `bhuvan_${nowTs}@example.com`,
            mobile: `98766${String(nowTs + 1).slice(-5)}`,
            password: 'Citizen@123',
            identityNumber: '6' + String(nowTs + 1).slice(-11)
        };
        const citCData = {
            name: `Citizen Chitra ${nowTs}`,
            username: `cit_chitra_${nowTs}`,
            email: `chitra_${nowTs}@example.com`,
            mobile: `98767${String(nowTs + 2).slice(-5)}`,
            password: 'Citizen@123',
            identityNumber: '7' + String(nowTs + 2).slice(-11)
        };

        const regA = await request('POST', '/auth/register', citAData);
        const regB = await request('POST', '/auth/register', citBData);
        const regC = await request('POST', '/auth/register', citCData);

        if (regA.status !== 201 || regB.status !== 201 || regC.status !== 201) {
            throw new Error(`Citizen registration failed: regA=${regA.status}, regB=${regB.status}, regC=${regC.status}`);
        }

        const tokenA = regA.data.token;
        const tokenB = regB.data.token;
        const tokenC = regC.data.token;

        // Distinct location for this isolated test run
        const loc = `Anna Nagar, Street 5 - ${nowTs}`;
        const locVariant = `Anna Nagar 5th St - ${nowTs}`;

        // TEST 1: Citizen A submits first Road Damage complaint
        process.stdout.write("[TEST 1/9] Citizen A registers baseline complaint... ");
        const compAResp = await request('POST', '/complaints', {
            category: 'Road Damage',
            description: 'Severe road damage and deep pothole causing vehicle breakdown near street 5',
            location: loc,
            citizenReportedImpact: 15
        }, tokenA);

        const compA = compAResp.data;
        if (compAResp.status === 201 && compA.complaintId) {
            console.log(`PASSED (Complaint ID: ${compA.complaintId})`);
            passed++;
        } else {
            console.log(`FAILED (${JSON.stringify(compAResp.data)})`);
        }

        // TEST 2: Citizen B submits similar Road Damage complaint at same location
        process.stdout.write("[TEST 2/9] Citizen B registers similar complaint -> Potential duplicate detected... ");
        const compBResp = await request('POST', '/complaints', {
            category: 'Road Damage',
            description: 'Dangerous deep potholes and road surface damage near street 5 cross',
            location: loc,
            citizenReportedImpact: 20
        }, tokenB);

        const compB = compBResp.data;
        if (compBResp.status === 201 && compB.complaintId) {
            console.log(`PASSED (Complaint ID: ${compB.complaintId})`);
            passed++;
        } else {
            console.log(`FAILED (${JSON.stringify(compBResp.data)})`);
        }

        // TEST 3: Admin receives duplicate alert notification
        process.stdout.write("[TEST 3/9] Admin receives 'Multiple Citizens Reported the Same Issue' notification... ");
        const adminNotifsResp = await request('GET', '/notifications', null, adminToken);
        const adminNotifs = Array.isArray(adminNotifsResp.data) ? adminNotifsResp.data : [];
        const adminDupNotif = adminNotifs.find(n =>
            n.title && n.title.includes("Multiple Citizens Reported the Same Issue") &&
            n.message && n.message.includes(compB.complaintId) &&
            n.message && n.message.includes(compA.complaintId)
        );

        if (adminDupNotif && adminDupNotif.message.includes("2")) {
            console.log(`PASSED (Notif ID: ${adminDupNotif.notificationId}, Citizen Count: 2)`);
            passed++;
        } else {
            console.log(`FAILED (Notification not found in ${adminNotifs.length} records)`);
        }

        // TEST 4: Manager receives independent duplicate alert notification
        process.stdout.write("[TEST 4/9] Manager independently receives duplicate alert in Manager Portal... ");
        const mgrNotifsResp = await request('GET', '/notifications', null, mgrToken);
        const mgrNotifs = Array.isArray(mgrNotifsResp.data) ? mgrNotifsResp.data : [];
        const mgrDupNotif = mgrNotifs.find(n =>
            n.title && n.title.includes("Multiple Citizens Reported the Same Issue") &&
            n.message && n.message.includes(compB.complaintId) &&
            n.message && n.message.includes(compA.complaintId)
        );

        if (mgrDupNotif) {
            console.log(`PASSED (Manager Notif ID: ${mgrDupNotif.notificationId})`);
            passed++;
        } else {
            console.log(`FAILED (Manager did not receive notification)`);
        }

        // TEST 5: Citizen C submits 3rd complaint -> Distinct citizen count increments to 3
        process.stdout.write("[TEST 5/9] Citizen C submits 3rd complaint -> Distinct citizen count updates to 3... ");
        const compCResp = await request('POST', '/complaints', {
            category: 'Road Damage',
            description: 'Massive pothole crater on road surface near Anna Nagar 5th street',
            location: locVariant,
            citizenReportedImpact: 35
        }, tokenC);

        const compC = compCResp.data;
        const candidatesCResp = await request('GET', `/complaints/${compC.complaintId}/duplicate-candidates`, null, adminToken);
        const candidatesC = Array.isArray(candidatesCResp.data) ? candidatesCResp.data : [];
        const hasMatch = candidatesC.some(c => c.candidateComplaintId === compA.complaintId || c.candidateComplaintId === compB.complaintId);
        const citizenCount = candidatesC.length > 0 ? candidatesC[0].distinctCitizenCount : 0;

        if (compCResp.status === 201 && hasMatch && citizenCount === 3) {
            console.log(`PASSED (Citizen count = 3 across all matching complaints)`);
            passed++;
        } else {
            console.log(`FAILED (Count=${citizenCount}, Candidates=${candidatesC.length})`);
        }

        // TEST 6: Admin and Manager get updated alert for additional report
        process.stdout.write("[TEST 6/9] Admin notified of additional citizen report (Count: 3)... ");
        const adminNotifsUpdated = await request('GET', '/notifications', null, adminToken);
        const thirdNotif = adminNotifsUpdated.data.find(n =>
            n.title && n.title.includes("Multiple Citizens Reported the Same Issue") &&
            n.message && n.message.includes(compC.complaintId) &&
            n.message && n.message.includes("3")
        );

        if (thirdNotif) {
            console.log(`PASSED (Additional report alert received with Count 3)`);
            passed++;
        } else {
            console.log(`FAILED (Additional report alert not found)`);
        }

        // TEST 7: Anti-Spam: Checking duplicate candidates does not generate spam notifications
        process.stdout.write("[TEST 7/9] Anti-Spam: Repeated checks do NOT generate duplicate spam... ");
        const countBefore = adminNotifsUpdated.data.length;
        // Repeatedly fetch duplicate candidates
        await request('GET', `/complaints/${compB.complaintId}/duplicate-candidates`, null, adminToken);
        await request('GET', `/complaints/${compC.complaintId}/duplicate-candidates`, null, adminToken);

        const countAfter = (await request('GET', '/notifications', null, adminToken)).data.length;
        if (countAfter === countBefore) {
            console.log(`PASSED (Notification count unchanged at ${countBefore})`);
            passed++;
        } else {
            console.log(`FAILED (Count grew from ${countBefore} to ${countAfter})`);
        }

        // TEST 8: Negative Matching: Unrelated complaint at same location does not trigger duplicate alert
        process.stdout.write("[TEST 8/9] Negative Matching: Unrelated complaint at same location not matched... ");
        const compUnrelatedResp = await request('POST', '/complaints', {
            category: 'Road Damage',
            description: 'Fallen large tree branch on sidewalk pathway',
            location: loc,
            citizenReportedImpact: 10
        }, tokenA);

        const candUnrelated = await request('GET', `/complaints/${compUnrelatedResp.data.complaintId}/duplicate-candidates`, null, adminToken);
        const noDups = Array.isArray(candUnrelated.data) && candUnrelated.data.length === 0;

        if (noDups) {
            console.log(`PASSED (0 candidates detected for unrelated description at same location)`);
            passed++;
        } else {
            console.log(`FAILED (Unexpected matches: ${JSON.stringify(candUnrelated.data)})`);
        }

        // TEST 9: Operator Confirms Link -> Original Records preserved without automated merge/delete
        process.stdout.write("[TEST 9/9] Operator confirms duplicate link -> Original records preserved... ");
        const confirmResp = await request('POST', `/complaints/${compB.complaintId}/duplicate/confirm`, {
            primaryComplaintId: compA.complaintId,
            remarks: 'Operator verified exact same pothole issue'
        }, adminToken);

        const compAFetch = await request('GET', `/complaints/${compA.complaintId}`, null, adminToken);
        const compBFetch = await request('GET', `/complaints/${compB.complaintId}`, null, adminToken);

        const bIsLinked = compBFetch.data && compBFetch.data.isDuplicate && compBFetch.data.duplicateOfId === compA.complaintId;
        const bothExist = compAFetch.status === 200 && compBFetch.status === 200;

        if (confirmResp.status === 200 && bIsLinked && bothExist) {
            console.log(`PASSED (Duplicate confirmed; original complaint ${compB.complaintId} preserved)`);
            passed++;
        } else {
            console.log(`FAILED (Confirm status=${confirmResp.status}, bIsLinked=${bIsLinked})`);
        }

        console.log("\n-------------------------------------------------------------------------------");
        console.log(`LIVE E2E REST API RESULT: ${passed} / ${total} TESTS PASSED`);
        console.log("===============================================================================\n");

        process.exit(passed === total ? 0 : 1);
    } catch (err) {
        console.error("FATAL E2E ERROR:", err);
        process.exit(1);
    }
}

run();
