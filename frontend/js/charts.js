// Analytics Chart Rendering Engine using SVG & HTML Canvas
const AnalyticsCharts = {
    // 1. Render Category Bar Chart
    renderCategoryChart(containerId, categoryData) {
        const container = document.getElementById(containerId);
        if (!container) return;

        const categories = ["Streetlight", "Waste Management", "Road Damage", "Water Supply", "Drainage", "Public Sanitation", "Other"];
        const counts = categories.map(cat => categoryData[cat] || 0);
        const maxCount = Math.max(...counts, 5);

        let svg = `
            <svg viewBox="0 0 500 220" style="width:100%; height:100%; overflow:visible;">
                <!-- Grid Lines -->
                <line x1="50" y1="20" x2="480" y2="20" stroke="rgba(255,255,255,0.08)" stroke-dasharray="4"/>
                <line x1="50" y1="70" x2="480" y2="70" stroke="rgba(255,255,255,0.08)" stroke-dasharray="4"/>
                <line x1="50" y1="120" x2="480" y2="120" stroke="rgba(255,255,255,0.08)" stroke-dasharray="4"/>
                <line x1="50" y1="170" x2="480" y2="170" stroke="rgba(255,255,255,0.2)"/>
        `;

        const colors = ["#3b82f6", "#10b981", "#f59e0b", "#06b6d4", "#8b5cf6", "#ec4899", "#64748b"];
        const barWidth = 40;
        const spacing = 60;

        categories.forEach((cat, i) => {
            const val = categoryData[cat] || 0;
            const barHeight = Math.round((val / maxCount) * 140);
            const x = 70 + (i * spacing);
            const y = 170 - barHeight;

            svg += `
                <rect x="${x}" y="${y}" width="${barWidth}" height="${barHeight}" fill="${colors[i]}" rx="4" class="chart-bar">
                    <title>${cat}: ${val} complaints</title>
                </rect>
                <text x="${x + barWidth/2}" y="${y - 6}" fill="#f8fafc" font-size="11" font-weight="600" text-anchor="middle">${val}</text>
                <text x="${x + barWidth/2}" y="190" fill="#94a3b8" font-size="10" text-anchor="middle">${cat.substring(0, 6)}..</text>
            `;
        });

        svg += `</svg>`;
        container.innerHTML = svg;
    },

    // 2. Render Priority Donut Chart
    renderPriorityDonut(containerId, priorityCounts) {
        const container = document.getElementById(containerId);
        if (!container) return;

        const total = (priorityCounts.CRITICAL || 0) + (priorityCounts.HIGH || 0) + (priorityCounts.MEDIUM || 0) + (priorityCounts.LOW || 0);

        if (total === 0) {
            container.innerHTML = `
                <div style="display:flex; flex-direction:column; align-items:center; justify-content:center; height:100%; color:var(--text-muted);">
                    <i class="fa-solid fa-chart-pie" style="font-size:36px; margin-bottom:8px; opacity:0.5;"></i>
                    <p style="font-size:13px;">No priority data recorded yet</p>
                </div>
            `;
            return;
        }

        const critical = priorityCounts.CRITICAL || 0;
        const high = priorityCounts.HIGH || 0;
        const medium = priorityCounts.MEDIUM || 0;
        const low = priorityCounts.LOW || 0;

        // Calculate stroke offset angles
        const cPerc = critical / total;
        const hPerc = high / total;
        const mPerc = medium / total;
        const lPerc = low / total;

        container.innerHTML = `
            <div style="display:flex; align-items:center; gap:24px; height:100%;">
                <div style="position:relative; width:160px; height:160px;">
                    <svg viewBox="0 0 36 36" style="width:100%; height:100%; transform:rotate(-90deg);">
                        <path d="M18 2.0845 a 15.9155 15.9155 0 0 1 0 31.831 a 15.9155 15.9155 0 0 1 0 -31.831" fill="none" stroke="rgba(255,255,255,0.05)" stroke-width="4"/>
                        <!-- Critical -->
                        <path d="M18 2.0845 a 15.9155 15.9155 0 0 1 0 31.831 a 15.9155 15.9155 0 0 1 0 -31.831" fill="none" stroke="#dc2626" stroke-width="4.5" stroke-dasharray="${cPerc * 100}, 100"/>
                        <!-- High -->
                        <path d="M18 2.0845 a 15.9155 15.9155 0 0 1 0 31.831 a 15.9155 15.9155 0 0 1 0 -31.831" fill="none" stroke="#f97316" stroke-width="4.5" stroke-dasharray="${hPerc * 100}, 100" stroke-dashoffset="-${cPerc * 100}"/>
                        <!-- Medium -->
                        <path d="M18 2.0845 a 15.9155 15.9155 0 0 1 0 31.831 a 15.9155 15.9155 0 0 1 0 -31.831" fill="none" stroke="#eab308" stroke-width="4.5" stroke-dasharray="${mPerc * 100}, 100" stroke-dashoffset="-${(cPerc + hPerc) * 100}"/>
                        <!-- Low -->
                        <path d="M18 2.0845 a 15.9155 15.9155 0 0 1 0 31.831 a 15.9155 15.9155 0 0 1 0 -31.831" fill="none" stroke="#10b981" stroke-width="4.5" stroke-dasharray="${lPerc * 100}, 100" stroke-dashoffset="-${(cPerc + hPerc + mPerc) * 100}"/>
                    </svg>
                    <div style="position:absolute; top:50%; left:50%; transform:translate(-50%, -50%); text-align:center;">
                        <span style="font-size:24px; font-weight:700; color:#fff;">${total}</span>
                        <span style="display:block; font-size:11px; color:var(--text-muted);">Total Cases</span>
                    </div>
                </div>

                <div style="display:flex; flex-direction:column; gap:10px; font-size:13px; flex:1;">
                    <div style="display:flex; justify-content:space-between; align-items:center;">
                        <span style="display:flex; align-items:center; gap:8px;"><span style="width:10px; height:10px; border-radius:50%; background:#dc2626;"></span> Critical</span>
                        <strong>${critical} (${Math.round(cPerc * 100)}%)</strong>
                    </div>
                    <div style="display:flex; justify-content:space-between; align-items:center;">
                        <span style="display:flex; align-items:center; gap:8px;"><span style="width:10px; height:10px; border-radius:50%; background:#f97316;"></span> High</span>
                        <strong>${high} (${Math.round(hPerc * 100)}%)</strong>
                    </div>
                    <div style="display:flex; justify-content:space-between; align-items:center;">
                        <span style="display:flex; align-items:center; gap:8px;"><span style="width:10px; height:10px; border-radius:50%; background:#eab308;"></span> Medium</span>
                        <strong>${medium} (${Math.round(mPerc * 100)}%)</strong>
                    </div>
                    <div style="display:flex; justify-content:space-between; align-items:center;">
                        <span style="display:flex; align-items:center; gap:8px;"><span style="width:10px; height:10px; border-radius:50%; background:#10b981;"></span> Low</span>
                        <strong>${low} (${Math.round(lPerc * 100)}%)</strong>
                    </div>
                </div>
            </div>
        `;
    }
};
