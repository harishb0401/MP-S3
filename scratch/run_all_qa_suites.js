const { execSync } = require("child_process");

console.log("===============================================================================");
console.log("   GOVERNMENT GRIEVANCE SYSTEM - MASTER AUTOMATED QA TEST RUNNER");
console.log("===============================================================================\n");

const suites = [
    { name: "Master System Suite (SystemTest)", cmd: 'java -cp "backend/src;backend/lib/*" test.SystemTest' },
    { name: "Enhanced Duplicate Detection Suite", cmd: 'java -cp "backend/src;backend/lib/*" test.EnhancedDuplicateDetectionTest' },
    { name: "Complete Notification System Suite", cmd: 'java -cp "backend/src;backend/lib/*" test.NotificationSystemTest' },
    { name: "Complaint Assignment Visibility Suite", cmd: 'java -cp "backend/src;backend/lib/*" test.ComplaintAssignmentVisibilityTest' },
    { name: "Complaint Deletion & Duplicates Suite", cmd: 'java -cp "backend/src;backend/lib/*" test.ComplaintDeletionAndDuplicatesTest' },
    { name: "E2E Multiple Worker Management (HTTP)", cmd: "node scratch/test_e2e_worker_mgmt.js" },
    { name: "E2E Registered Citizen Management (HTTP)", cmd: "node scratch/test_e2e_citizen_mgmt.js" },
    { name: "E2E Unique Identity Verification (HTTP)", cmd: "node scratch/test_e2e_identity_verification.js" },
    { name: "E2E Assignment Visibility Rules (HTTP)", cmd: "node scratch/test_e2e_assignment_visibility.js" },
    { name: "E2E Enhanced Duplicate Detection (HTTP)", cmd: "node scratch/test_e2e_duplicate_detection.js" },
    { name: "E2E Notification System (HTTP)", cmd: "node scratch/test_e2e_notifications.js" },
    { name: "Phase 11 Security & Edge Case Suite", cmd: "node scratch/test_security_and_edge_cases.js" },
    { name: "Phase 12 Frontend UI/UX DOM Elements", cmd: "node scratch/test_frontend_dom.js" }
];

let totalSuites = suites.length;
let suitesPassed = 0;
let results = [];

for (let i = 0; i < suites.length; i++) {
    const s = suites[i];
    process.stdout.write(`[SUITE ${i+1}/${totalSuites}] Running ${s.name}... `);
    const start = Date.now();
    try {
        const output = execSync(s.cmd, { encoding: "utf8", stdio: "pipe" });
        const elapsed = ((Date.now() - start) / 1000).toFixed(2);
        console.log(`PASSED (${elapsed}s)`);
        suitesPassed++;
        results.push({ name: s.name, status: "PASSED", elapsed });
    } catch (err) {
        const elapsed = ((Date.now() - start) / 1000).toFixed(2);
        console.log(`FAILED (${elapsed}s)`);
        console.error(err.stdout ? err.stdout.slice(-1000) : err.message);
        results.push({ name: s.name, status: "FAILED", elapsed });
    }
}

console.log("\n===============================================================================");
console.log(`ALL TEST SUITES SUMMARY: ${suitesPassed} / ${totalSuites} SUITES PASSED (${Math.round(suitesPassed/totalSuites*100)}%)`);
console.log("===============================================================================\n");

results.forEach((r, idx) => {
    console.log(`${idx+1}. [${r.status}] ${r.name} - ${r.elapsed}s`);
});

if (suitesPassed !== totalSuites) {
    process.exit(1);
}

