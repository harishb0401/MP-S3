// API Client connecting Frontend to Java REST API Backend on Port 8080
const API_BASE_URL = (() => {
    if (typeof window !== "undefined" && window.location) {
        if (window.location.hostname && !window.location.hostname.includes("localhost") && !window.location.hostname.includes("127.0.0.1") && window.location.origin.startsWith("http")) {
            return `${window.location.origin}/api`;
        }
    }
    return "http://localhost:8080/api";
})();

const ApiClient = {
    // Helper to get auth headers
    getHeaders() {
        const token = localStorage.getItem("auth_token");
        const headers = {
            "Content-Type": "application/json"
        };
        if (token) {
            headers["Authorization"] = `Bearer ${token}`;
        }
        return headers;
    },

    // Handle HTTP Responses
    async handleResponse(response) {
        let data = {};
        try {
            data = await response.json();
        } catch (e) {
            data = {};
        }

        if (!response.ok) {
            const errorMsg = data.error || `HTTP ${response.status}: Request failed`;
            throw new Error(errorMsg);
        }
        return data;
    },

    // 1. Citizen Registration
    async registerCitizen(name, email, mobile, password) {
        const response = await fetch(`${API_BASE_URL}/auth/register`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ name, email, mobile, password })
        });
        return this.handleResponse(response);
    },

    // 2. Login (Citizen & Staff)
    async login(emailOrId, password) {
        const response = await fetch(`${API_BASE_URL}/auth/login`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ email: emailOrId, staffId: emailOrId, password })
        });
        return this.handleResponse(response);
    },

    // 3. Get Current User Profile
    async getMe() {
        const response = await fetch(`${API_BASE_URL}/auth/me`, {
            method: "GET",
            headers: this.getHeaders()
        });
        return this.handleResponse(response);
    },

    // 4. Submit Complaint (Citizen)
    async submitComplaint(category, location, description) {
        const response = await fetch(`${API_BASE_URL}/complaints`, {
            method: "POST",
            headers: this.getHeaders(),
            body: JSON.stringify({ category, location, description })
        });
        return this.handleResponse(response);
    },

    // 5. Get Citizen Complaints
    async getMyComplaints() {
        const response = await fetch(`${API_BASE_URL}/my-complaints`, {
            method: "GET",
            headers: this.getHeaders()
        });
        return this.handleResponse(response);
    },

    // 6. Get All Complaints (Staff)
    async getAllComplaints() {
        const response = await fetch(`${API_BASE_URL}/complaints`, {
            method: "GET",
            headers: this.getHeaders()
        });
        return this.handleResponse(response);
    },

    // 7. Get Priority Queue List
    async getPriorityQueue() {
        const response = await fetch(`${API_BASE_URL}/complaints/priority-queue`, {
            method: "GET",
            headers: this.getHeaders()
        });
        return this.handleResponse(response);
    },

    // 8. Get Complaint by ID
    async getComplaintById(id) {
        const response = await fetch(`${API_BASE_URL}/complaints/${id}`, {
            method: "GET",
            headers: this.getHeaders()
        });
        return this.handleResponse(response);
    },

    // 9. Update Status (Staff)
    async updateStatus(id, status) {
        const response = await fetch(`${API_BASE_URL}/complaints/${id}/status`, {
            method: "PUT",
            headers: this.getHeaders(),
            body: JSON.stringify({ status })
        });
        return this.handleResponse(response);
    },

    // 10. Assign Department & Staff
    async assignDepartment(id, department, staffName) {
        const response = await fetch(`${API_BASE_URL}/complaints/${id}/assign`, {
            method: "POST",
            headers: this.getHeaders(),
            body: JSON.stringify({ department, staffName })
        });
        return this.handleResponse(response);
    },

    // 11. Resolve Complaint
    async resolveComplaint(id, resolutionNotes) {
        const response = await fetch(`${API_BASE_URL}/complaints/${id}/resolve`, {
            method: "POST",
            headers: this.getHeaders(),
            body: JSON.stringify({ resolutionNotes })
        });
        return this.handleResponse(response);
    },

    // 12. Get Dashboard Stats
    async getDashboardStats() {
        const response = await fetch(`${API_BASE_URL}/dashboard/stats`, {
            method: "GET",
            headers: this.getHeaders()
        });
        return this.handleResponse(response);
    },

    // 13. Admin Verify Complaint
    async verifyComplaint(id, adminVerifiedImpact, verificationReason, severity = "MEDIUM") {
        const response = await fetch(`${API_BASE_URL}/complaints/${id}/verify`, {
            method: "POST",
            headers: this.getHeaders(),
            body: JSON.stringify({ adminVerifiedImpact, verificationReason, severity })
        });
        return this.handleResponse(response);
    },

    // 14. Get Complaint Timeline History
    async getComplaintHistory(id) {
        const response = await fetch(`${API_BASE_URL}/complaints/${id}/history`, {
            method: "GET",
            headers: this.getHeaders()
        });
        return this.handleResponse(response);
    },

    // 15. Get Citizen Notifications
    async getNotifications() {
        const response = await fetch(`${API_BASE_URL}/notifications/my`, {
            method: "GET",
            headers: this.getHeaders()
        });
        return this.handleResponse(response);
    },

    // 16. Get Department Capacity & Workload
    async getDepartmentCapacity() {
        const response = await fetch(`${API_BASE_URL}/departments/capacity`, {
            method: "GET",
            headers: this.getHeaders()
        });
        return this.handleResponse(response);
    },

    // 17. Duplicate Complaint Action (Merge / Separate)
    async handleDuplicate(id, action, primaryComplaintId = "", reason = "") {
        const response = await fetch(`${API_BASE_URL}/complaints/${id}/duplicate`, {
            method: "POST",
            headers: this.getHeaders(),
            body: JSON.stringify({ action, primaryComplaintId, reason })
        });
        return this.handleResponse(response);
    }
};
