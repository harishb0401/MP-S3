const http = require('http');

const BASE_URL = 'http://localhost:8080';

function request(method, path, body = null, token = null) {
    return new Promise((resolve, reject) => {
        const url = new URL(BASE_URL + path);
        const headers = { 'Content-Type': 'application/json' };
        if (token) headers['Authorization'] = `Bearer ${token}`;

        const req = http.request(url, { method, headers }, (res) => {
            let data = '';
            res.on('data', chunk => data += chunk);
            res.on('end', () => {
                try {
                    const parsed = data ? JSON.parse(data) : {};
                    resolve({ status: res.statusCode, body: parsed, raw: data });
                } catch (e) {
                    resolve({ status: res.statusCode, body: data, raw: data });
                }
            });
        });
        req.on('error', reject);
        if (body) req.write(typeof body === 'string' ? body : JSON.stringify(body));
        req.end();
    });
}

async function login(email, password) {
    const res = await request('POST', '/api/auth/login', { email, password });
    if (res.status !== 200 || !res.body.token) {
        throw new Error(`Login failed for ${email}: ${JSON.stringify(res.body)}`);
    }
    return res.body.token;
}

async function runE2ETests() {
    console.log('===============================================================================');
    console.log('   E2E INTEGRATION TEST: COMPLETE NOTIFICATION SYSTEM (HTTP / REST)');
    console.log('===============================================================================');

    let passed = 0;
    let total = 8;

    try {
        // Step 1: Login all 4 roles
        console.log('[STEP 1] Authenticating Citizen, Admin, Manager, and Worker...');
        const adminToken = await login('admin@makkal.gov.in', 'admin123');
        const managerToken = await login('manager@makkal.gov.in', 'admin123');
        const workerToken = await login('water_staff@makkal.gov.in', 'admin123');
        const citizenToken = await login('ramesh@test.com', '123456');
        console.log('  -> All 4 roles successfully authenticated.');

        // TEST 1: Retrieve notifications for all roles
        console.log('\n[TEST 1/8] Fetching notifications for all roles via GET /api/notifications...');
        const adminNotifs = await request('GET', '/api/notifications', null, adminToken);
        const managerNotifs = await request('GET', '/api/notifications', null, managerToken);
        const workerNotifs = await request('GET', '/api/notifications', null, workerToken);
        const citizenNotifs = await request('GET', '/api/notifications', null, citizenToken);

        if (adminNotifs.status === 200 && Array.isArray(adminNotifs.body) &&
            managerNotifs.status === 200 && Array.isArray(managerNotifs.body) &&
            workerNotifs.status === 200 && Array.isArray(workerNotifs.body) &&
            citizenNotifs.status === 200 && Array.isArray(citizenNotifs.body)) {
            console.log(`  -> PASSED (Admin: ${adminNotifs.body.length}, Manager: ${managerNotifs.body.length}, Worker: ${workerNotifs.body.length}, Citizen: ${citizenNotifs.body.length})`);
            passed++;
        } else {
            console.log('  -> FAILED: Non-array or bad status code returned');
        }

        // TEST 2: Citizen complaint submission triggers Citizen & Admin notifications
        console.log('\n[TEST 2/8] Citizen submits new complaint -> Citizen & Admin/Manager notifications generated...');
        const cBefore = citizenNotifs.body.length;
        const aBefore = adminNotifs.body.length;

        const newComplaintRes = await request('POST', '/api/complaints', {
            title: 'Water Pipe Rupture',
            category: 'Water Supply',
            description: 'Major water line rupture on Nehru Street',
            location: 'Central Market',
            citizenReportedImpact: 120
        }, citizenToken);

        if (newComplaintRes.status !== 201) {
            console.log('  -> FAILED to create complaint:', newComplaintRes.body);
        } else {
            const compId = newComplaintRes.body.complaintId;
            const cAfter = await request('GET', '/api/notifications', null, citizenToken);
            const aAfter = await request('GET', '/api/notifications', null, adminToken);

            const citizenGotIt = cAfter.body.some(n => n.complaintId === compId && n.title.includes('Complaint Registered'));
            const adminGotIt = aAfter.body.some(n => n.complaintId === compId && n.title.includes('New Grievance Submitted'));

            if (citizenGotIt && adminGotIt) {
                console.log(`  -> PASSED: Citizen notified of registration; Admin notified of submission for case ${compId}`);
                passed++;
            } else {
                console.log(`  -> FAILED (citizenGotIt=${citizenGotIt}, adminGotIt=${adminGotIt})`);
            }

            // TEST 3: Department Assessment & Worker Assignment triggers Citizen, Worker & Manager alerts
            console.log('\n[TEST 3/8] Department Assessment & Worker Assignment -> Citizen, Worker, and Manager notified...');
            const wBefore = (await request('GET', '/api/notifications', null, workerToken)).body.length;

            const assignRes = await request('POST', `/api/complaints/${compId}/assess`, {
                severity: 'HIGH',
                reason: 'Flooding risk assessed by municipal team',
                department: 'Water Department',
                staffName: 'Officer Suresh'
            }, managerToken);

            const cAfterAssign = await request('GET', '/api/notifications', null, citizenToken);
            const wAfterAssign = await request('GET', '/api/notifications', null, workerToken);
            const mAfterAssign = await request('GET', '/api/notifications', null, managerToken);

            const citGotWorker = cAfterAssign.body.some(n => n.complaintId === compId && n.title.includes('Worker Assigned'));
            const workerGotTask = wAfterAssign.body.some(n => n.complaintId === compId && n.title.includes('New Task Assigned'));
            const managerGotAlloc = mAfterAssign.body.some(n => n.complaintId === compId && n.title.includes('Department Allocation'));

            if (citGotWorker && workerGotTask && managerGotAlloc) {
                console.log(`  -> PASSED: Worker Suresh notified of new assignment; Citizen informed; Manager received allocation alert`);
                passed++;
            } else {
                console.log(`  -> FAILED (citGotWorker=${citGotWorker}, workerGotTask=${workerGotTask}, managerGotAlloc=${managerGotAlloc})`);
            }

            // TEST 4: Status Update to InProgress
            console.log('\n[TEST 4/8] Worker updates status to InProgress -> Citizen notified...');
            const progressRes = await request('POST', `/api/complaints/${compId}/status`, {
                status: 'InProgress',
                remarks: 'Technician on site replacing faulty coupling'
            }, workerToken);

            const cAfterProgress = await request('GET', '/api/notifications', null, citizenToken);
            const citGotProgress = cAfterProgress.body.some(n => n.complaintId === compId && n.title.includes('In Progress'));

            if (citGotProgress) {
                console.log(`  -> PASSED: Citizen notified that work is In Progress`);
                passed++;
            } else {
                console.log(`  -> FAILED: Citizen not notified of InProgress status`);
            }

            // TEST 5: Status Update to Resolved
            console.log('\n[TEST 5/8] Complaint Resolved -> Citizen, Worker, and Admin notified...');
            const resolveRes = await request('POST', `/api/complaints/${compId}/resolve`, {
                resolutionNotes: 'Coupling replaced, pressure tested and verified restored.'
            }, workerToken);

            const cAfterResolved = await request('GET', '/api/notifications', null, citizenToken);
            const aAfterResolved = await request('GET', '/api/notifications', null, adminToken);

            const citGotResolved = cAfterResolved.body.some(n => n.complaintId === compId && n.title.includes('Resolved'));
            const adminGotResolved = aAfterResolved.body.some(n => n.complaintId === compId && n.title.includes('Resolved'));

            if (citGotResolved && adminGotResolved) {
                console.log(`  -> PASSED: Citizen & Admin received verified resolution notification`);
                passed++;
            } else {
                console.log(`  -> FAILED (citGotResolved=${citGotResolved}, adminGotResolved=${adminGotResolved})`);
            }

            // TEST 6: Unread Notification Count API
            console.log('\n[TEST 6/8] GET /api/notifications/unread-count reflects true unread count...');
            const unreadRes = await request('GET', '/api/notifications/unread-count', null, citizenToken);
            const currentNotifs = (await request('GET', '/api/notifications', null, citizenToken)).body;
            const actualUnread = currentNotifs.filter(n => !n.isRead).length;

            if (unreadRes.status === 200 && unreadRes.body.unreadCount === actualUnread) {
                console.log(`  -> PASSED: Endpoint returned exact unread count (${actualUnread})`);
                passed++;
            } else {
                console.log(`  -> FAILED (api=${unreadRes.body.unreadCount}, actual=${actualUnread})`);
            }

            // TEST 7: Mark Single Notification as Read
            console.log('\n[TEST 7/8] POST /api/notifications/:id/read marks single notification as read...');
            const firstUnread = currentNotifs.find(n => !n.isRead);
            if (!firstUnread) {
                console.log('  -> FAILED: No unread notifications found to test');
            } else {
                const markRes = await request('POST', `/api/notifications/${firstUnread.notificationId}/read`, {}, citizenToken);
                const unreadAfterSingle = await request('GET', '/api/notifications/unread-count', null, citizenToken);
                if (markRes.status === 200 && unreadAfterSingle.body.unreadCount === (actualUnread - 1)) {
                    console.log(`  -> PASSED: Notification ${firstUnread.notificationId} marked read, unread count decremented to ${unreadAfterSingle.body.unreadCount}`);
                    passed++;
                } else {
                    console.log(`  -> FAILED (status=${markRes.status}, countAfter=${unreadAfterSingle.body.unreadCount})`);
                }
            }

            // TEST 8: Mark All Notifications as Read
            console.log('\n[TEST 8/8] POST /api/notifications/read-all marks all notifications as read...');
            const markAllRes = await request('POST', '/api/notifications/read-all', {}, citizenToken);
            const unreadAfterAll = await request('GET', '/api/notifications/unread-count', null, citizenToken);

            if (markAllRes.status === 200 && unreadAfterAll.body.unreadCount === 0) {
                console.log(`  -> PASSED: All notifications marked read, unread count is 0 (marked: ${markAllRes.body.markedCount})`);
                passed++;
            } else {
                console.log(`  -> FAILED (marked=${markAllRes.body.markedCount}, remainingUnread=${unreadAfterAll.body.unreadCount})`);
            }
        }

    } catch (e) {
        console.error('Fatal error during E2E test:', e);
    }

    console.log('\n-------------------------------------------------------------------------------');
    console.log(`E2E NOTIFICATION TEST RESULT: ${passed} / ${total} TESTS PASSED`);
    console.log('===============================================================================');
    if (passed !== total) process.exit(1);
}

runE2ETests();
