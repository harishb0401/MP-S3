const http = require("http");

function request(method, path, data = null, headers = {}) {
    return new Promise((resolve, reject) => {
        const url = new URL("http://localhost:8080" + path);
        const options = {
            hostname: url.hostname,
            port: url.port,
            path: url.pathname + url.search,
            method: method,
            headers: {
                "Content-Type": "application/json",
                ...headers
            }
        };

        const req = http.request(options, (res) => {
            let body = "";
            res.on("data", (chunk) => body += chunk);
            res.on("end", () => {
                let parsed = null;
                try {
                    parsed = JSON.parse(body);
                } catch (e) {
                    parsed = body;
                }
                resolve({ status: res.statusCode, headers: res.headers, body: parsed, rawBody: body });
            });
        });

        req.on("error", reject);
        if (data) {
            req.write(typeof data === "string" ? data : JSON.stringify(data));
        }
        req.end();
    });
}

async function runSecuritySuite() {
    console.log("===============================================================================");
    console.log("   PHASE 11: SECURITY, AUTHORIZATION, IDOR & EDGE CASE PENETRATION SUITE");
    console.log("===============================================================================\n");

    let passed = 0;
    let total = 0;

    function assert(cond, msg) {
        total++;
        if (cond) {
            console.log(`[PASS] Test ${total}: ${msg}`);
            passed++;
        } else {
            console.error(`[FAIL] Test ${total}: ${msg}`);
        }
    }

    // Authenticate Admin, Manager, Worker, Citizen
    const adminLogin = await request("POST", "/api/auth/login", { email: "admin@makkal.gov.in", password: "admin123" });
    const adminToken = adminLogin.body.token;

    const mgrLogin = await request("POST", "/api/auth/login", { email: "manager@makkal.gov.in", password: "admin123" });
    const mgrToken = mgrLogin.body.token;

    const workerLogin = await request("POST", "/api/auth/login", { email: "water_staff@makkal.gov.in", password: "admin123" });
    const workerToken = workerLogin.body.token;

    // Register two distinct citizens for IDOR checks
    const ts = Date.now();
    // Use distinct 12-digit identities that don't collide with timestamps
    const idA = "2" + String(Math.floor(10000000000 + Math.random() * 89999999999)).substring(0, 11);
    const idB = "3" + String(Math.floor(10000000000 + Math.random() * 89999999999)).substring(0, 11);

    const citA = await request("POST", "/api/auth/register", {
        name: "Security Tester A",
        username: "sec_cit_a_" + ts,
        email: "sec_cit_a_" + ts + "@test.com",
        mobile: "98765" + String(ts).substring(8, 13),
        password: "PassSecure123",
        identityNumber: idA
    });
    const tokenA = citA.body.token;
    const citizenIdA = citA.body.user.id;

    const citB = await request("POST", "/api/auth/register", {
        name: "Security Tester B",
        username: "sec_cit_b_" + ts,
        email: "sec_cit_b_" + ts + "@test.com",
        mobile: "98764" + String(ts).substring(8, 13),
        password: "PassSecure123",
        identityNumber: idB
    });
    const tokenB = citB.body.token;
    const citizenIdB = citB.body.user.id;

    // Citizen A creates a private complaint
    const compA = await request("POST", "/api/complaints", {
        category: "Water Supply",
        title: "Confidential Water Defect A",
        description: "Private residential water connection leak",
        location: "Central Market",
        citizenReportedImpact: 5
    }, { "Authorization": "Bearer " + tokenA });
    const compIdA = compA.body.complaintId;

    // 1. Unauthenticated request to protected endpoints
    const unauthStats = await request("GET", "/api/admin/workers");
    assert(unauthStats.status === 401, "Unauthenticated access to /api/admin/workers rejected with 401");

    // 2. Tampered token
    const tampered = await request("GET", "/api/admin/workers", null, { "Authorization": "Bearer BAD_FAKE_TOKEN_XYZ" });
    assert(tampered.status === 401, "Tampered session token rejected with 401");

    // 3. Citizen cannot access Admin Workers API
    const citOnAdmin = await request("GET", "/api/admin/workers", null, { "Authorization": "Bearer " + tokenA });
    assert(citOnAdmin.status === 403, "Citizen attempting to call /api/admin/workers rejected with 403 Forbidden");

    // 4. Citizen cannot access Admin Registered Citizens API
    const citOnRegCit = await request("GET", "/api/admin/citizens", null, { "Authorization": "Bearer " + tokenA });
    assert(citOnRegCit.status === 403, "Citizen attempting to call /api/admin/citizens rejected with 403 Forbidden");

    // 5. Worker cannot access Admin Workers API
    const wrkOnAdmin = await request("GET", "/api/admin/workers", null, { "Authorization": "Bearer " + workerToken });
    assert(wrkOnAdmin.status === 403, "Worker attempting to call /api/admin/workers rejected with 403 Forbidden");

    // 6. Citizen cannot assess severity (Manager/Admin only)
    const citAssess = await request("POST", `/api/complaints/${compIdA}/assess`, {
        severity: "CRITICAL",
        reason: "Citizen trying to promote own complaint"
    }, { "Authorization": "Bearer " + tokenA });
    assert(citAssess.status === 403, "Citizen trying to assess severity rejected with 403");

    // 7. Citizen cannot assign complaint
    const citAssign = await request("POST", `/api/complaints/${compIdA}/assign`, {
        department: "Water Department",
        staff: "Officer Suresh"
    }, { "Authorization": "Bearer " + tokenA });
    assert(citAssign.status === 403, "Citizen trying to assign complaint rejected with 403");

    // 8. Worker cannot assign complaints
    const wrkAssign = await request("POST", `/api/complaints/${compIdA}/assign`, {
        department: "Water Department",
        staff: "Officer Suresh"
    }, { "Authorization": "Bearer " + workerToken });
    assert(wrkAssign.status === 403, "Worker trying to assign complaint rejected with 403");

    // 9. IDOR / Citizen Isolation: Citizen B cannot view Citizen A's complaints in private list
    const citBComplaints = await request("GET", "/api/complaints?mine=true", null, { "Authorization": "Bearer " + tokenB });
    const hasAInBList = Array.isArray(citBComplaints.body) && citBComplaints.body.some(c => c.complaintId === compIdA);
    assert(!hasAInBList, "Citizen B's private complaints query (?mine=true) excludes Citizen A's complaint");

    // 10. IDOR / Citizen Isolation: Citizen B querying Citizen A's ID via query param
    const citBQueryA = await request("GET", `/api/complaints?citizenId=${citizenIdA}`, null, { "Authorization": "Bearer " + tokenB });
    const leakedCompA = Array.isArray(citBQueryA.body) && citBQueryA.body.some(c => c.complaintId === compIdA);
    assert(!leakedCompA, "Citizen B cannot spoof citizenId parameter to fetch Citizen A's private complaints");

    // 11. SQL Injection attempt in Login identifier
    const sqliLogin = await request("POST", "/api/auth/login", {
        email: "admin' OR '1'='1' --",
        password: "any"
    });
    assert(sqliLogin.status === 401, "SQL Injection payload in login identifier rejected with 401");

    // 12. SQL Injection in Citizen Search
    const sqliSearch = await request("GET", "/api/admin/citizens?search=" + encodeURIComponent("'; DROP TABLE users; --"), null, { "Authorization": "Bearer " + adminToken });
    const searchResultIsArray = Array.isArray(sqliSearch.body) || (sqliSearch.body && Array.isArray(sqliSearch.body.citizens));
    assert(sqliSearch.status === 200 && searchResultIsArray, "SQL Injection payload in citizen search handled safely (no crash / 200 OK)");

    // 13. XSS in Complaint Title & Description
    const xssTitle = "<script>alert('xss')</script> Water Issue";
    const xssDesc = "<img src=x onerror=alert(1)> Main pipe leak";
    const xssComp = await request("POST", "/api/complaints", {
        category: "Water Supply",
        title: xssTitle,
        description: xssDesc,
        location: "Central Market",
        citizenReportedImpact: 10
    }, { "Authorization": "Bearer " + tokenA });
    assert(xssComp.status === 201, "Complaint with XSS strings registered safely without server crash");

    // 14. Zero Plaintext Aadhaar Leak in API
    const regCitResp = await request("GET", "/api/admin/citizens", null, { "Authorization": "Bearer " + adminToken });
    const citizensList = Array.isArray(regCitResp.body) ? regCitResp.body : (regCitResp.body.citizens || []);
    const citizenEntry = citizensList.find(c => c.id === citizenIdA || c.citizenId === citizenIdA);
    console.log("citizenIdA:", citizenIdA, "found entry:", citizenEntry);
    const hasRawAadhaar = citizenEntry && JSON.stringify(citizenEntry).includes(idA);
    const hasMaskedAadhaar = citizenEntry && (citizenEntry.maskedIdentity || citizenEntry.identityNumber) && (citizenEntry.maskedIdentity || citizenEntry.identityNumber).startsWith("XXXX-XXXX-");
    assert(!hasRawAadhaar && Boolean(hasMaskedAadhaar), "Admin registered citizens API strictly displays masked identity and zero plaintext");

    // 15. Invalid GPS coordinates (out-of-range latitude > 90)
    const badGps = await request("POST", "/api/complaints", {
        category: "Water Supply",
        title: "Bad GPS",
        description: "Pipe leak",
        location: "Central Market",
        citizenReportedImpact: 10,
        latitude: 150.0,
        longitude: 80.0
    }, { "Authorization": "Bearer " + tokenA });
    assert(badGps.status === 400, "Out-of-range GPS latitude (>90) strictly rejected with 400 Bad Request");

    // 16. Negative Impact Count rejected
    const badImpact = await request("POST", "/api/complaints", {
        category: "Water Supply",
        title: "Negative Impact",
        description: "Pipe leak",
        location: "Central Market",
        citizenReportedImpact: -50
    }, { "Authorization": "Bearer " + tokenA });
    assert(badImpact.status === 400 || (badImpact.status === 201 && badImpact.body.citizenReportedImpact >= 1), "Negative impact rejected or normalized to minimum valid 1");

    // 17. Empty required fields rejected
    const emptyFields = await request("POST", "/api/complaints", {
        category: "",
        description: "",
        location: ""
    }, { "Authorization": "Bearer " + tokenA });
    assert(emptyFields.status === 400, "Empty category/description/location rejected with 400 Bad Request");

    // 18. Weak / Short Password in Registration
    const weakPass = await request("POST", "/api/auth/register", {
        name: "Weak Pass User",
        username: "weak_user_" + ts,
        email: "weak_" + ts + "@test.com",
        mobile: "9876500000",
        password: "123",
        identityNumber: "9" + String(ts).substring(2, 13)
    });
    assert(weakPass.status === 400, "Weak/short password (<6 chars) rejected with 400 Bad Request");

    console.log("\n-------------------------------------------------------------------------------");
    console.log(`SECURITY & PENETRATION SUITE RESULT: ${passed} / ${total} TESTS PASSED (${Math.round(passed/total*100)}%)`);
    console.log("===============================================================================\n");

    if (passed !== total) {
        process.exit(1);
    }
}

runSecuritySuite().catch(err => {
    console.error("Test execution failed:", err);
    process.exit(1);
});
