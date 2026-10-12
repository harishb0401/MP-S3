async function assignAndCheckWorker() {
    // 1. Manager login
    const mgrRes = await fetch('http://localhost:8080/api/auth/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email: 'manager@makkal.gov.in', password: 'admin123' })
    });
    const mgrData = await mgrRes.json();

    // 2. Assess and assign to Water Department
    const assignRes = await fetch('http://localhost:8080/api/complaints/GRV-2026-0002/assign', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
            'Authorization': 'Bearer ' + mgrData.token
        },
        body: JSON.stringify({
            severity: 'HIGH',
            department: 'DEPT-WATER',
            assignedStaff: 'Officer Suresh',
            reason: 'Severe flooding requiring immediate response'
        })
    });
    const assignData = await assignRes.json();
    console.log('Assignment response:', assignData);

    // 3. Worker check
    const workerRes = await fetch('http://localhost:8080/api/auth/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email: 'water_staff@makkal.gov.in', password: 'admin123' })
    });
    const workerData = await workerRes.json();
    const wComplaints = await (await fetch('http://localhost:8080/api/complaints', {
        headers: { 'Authorization': 'Bearer ' + workerData.token }
    })).json();
    const wFound = wComplaints.find(c => c.complaintId === 'GRV-2026-0002');
    console.log('After assignment, Worker sees complaint GRV-2026-0002:', !!wFound);
    if (wFound) {
        console.log('Worker sees image_url:', !!wFound.imageUrl, 'length:', wFound.imageUrl ? wFound.imageUrl.length : 0);
    }
}

assignAndCheckWorker().catch(console.error);
