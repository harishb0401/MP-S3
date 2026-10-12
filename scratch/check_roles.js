async function checkRoles() {
    // 1. Worker check
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
    console.log('Worker sees complaint GRV-2026-0002:', !!wFound, 'Has image:', !!(wFound && wFound.imageUrl));

    // 2. Admin check
    const adminRes = await fetch('http://localhost:8080/api/auth/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email: 'admin@makkal.gov.in', password: 'admin123' })
    });
    const adminData = await adminRes.json();
    const aComplaints = await (await fetch('http://localhost:8080/api/complaints', {
        headers: { 'Authorization': 'Bearer ' + adminData.token }
    })).json();
    const aFound = aComplaints.find(c => c.complaintId === 'GRV-2026-0002');
    console.log('Admin sees complaint GRV-2026-0002:', !!aFound, 'Has image:', !!(aFound && aFound.imageUrl));

    // 3. Citizen check
    const citRes = await fetch('http://localhost:8080/api/auth/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email: 'ramesh@test.com', password: '123456' })
    });
    const citData = await citRes.json();
    const cComplaints = await (await fetch('http://localhost:8080/api/complaints', {
        headers: { 'Authorization': 'Bearer ' + citData.token }
    })).json();
    const cFound = cComplaints.find(c => c.complaintId === 'GRV-2026-0002');
    console.log('Citizen sees complaint GRV-2026-0002:', !!cFound, 'Has image:', !!(cFound && cFound.imageUrl));
}

checkRoles().catch(console.error);
