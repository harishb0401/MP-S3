const http = require('http');

async function run() {
    // 1. Citizen Login
    const loginPayload = JSON.stringify({ email: 'ramesh@test.com', password: '123456' });
    const loginRes = await fetch('http://localhost:8080/api/auth/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: loginPayload
    });
    const loginData = await loginRes.json();
    console.log('Login success:', loginData.user.name, 'Token:', loginData.token);

    // 2. Sample SVG Image DataURL
    const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="400" height="300">
        <rect width="100%" height="100%" fill="#0284c7"/>
        <circle cx="200" cy="150" r="80" fill="#38bdf8"/>
        <text x="50%" y="45%" dominant-baseline="middle" text-anchor="middle" fill="#ffffff" font-size="22" font-family="sans-serif" font-weight="bold">Water Pipeline Leak Evidence</text>
        <text x="50%" y="60%" dominant-baseline="middle" text-anchor="middle" fill="#f0f9ff" font-size="14" font-family="sans-serif">Makkal Nagar Hospital Area</text>
    </svg>`;
    const dataUrl = 'data:image/svg+xml;base64,' + Buffer.from(svg).toString('base64');

    // 3. Submit Complaint
    const complaintPayload = JSON.stringify({
        title: 'Major Rupture in Water Main',
        category: 'Water Supply',
        description: 'Underground pipe burst resulting in heavy road inundation near Government Hospital.',
        location: 'Government Hospital Area',
        affectedPeople: 60,
        citizenReportedImpact: 60,
        imageUrl: dataUrl
    });

    const submitRes = await fetch('http://localhost:8080/api/complaints', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
            'Authorization': 'Bearer ' + loginData.token
        },
        body: complaintPayload
    });

    const submitData = await submitRes.json();
    console.log('Complaint submitted successfully:');
    console.log('Complaint ID:', submitData.complaintId);
    console.log('Title/Desc:', submitData.description);
    console.log('Has Image:', !!submitData.imageUrl, 'Image length:', submitData.imageUrl ? submitData.imageUrl.length : 0);

    // 4. Verify GET /api/complaints as Manager
    const mgrLoginRes = await fetch('http://localhost:8080/api/auth/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email: 'manager@makkal.gov.in', password: 'admin123' })
    });
    const mgrData = await mgrLoginRes.json();
    const mgrComplaintsRes = await fetch('http://localhost:8080/api/complaints', {
        headers: { 'Authorization': 'Bearer ' + mgrData.token }
    });
    const mgrComplaints = await mgrComplaintsRes.json();
    const found = mgrComplaints.find(c => c.complaintId === submitData.complaintId);
    console.log('Manager sees complaint in list:', !!found);
    if (found) {
        console.log('Manager sees image_url:', !!found.imageUrl, 'length:', found.imageUrl ? found.imageUrl.length : 0);
    }
}

run().catch(console.error);
