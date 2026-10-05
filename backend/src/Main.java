import service.Database;
import service.GrievanceManager;
import service.ServerManager;

public class Main {
    private static int getPort() {
        String envPort = System.getenv("PORT");
        if (envPort != null && !envPort.trim().isEmpty()) {
            try {
                return Integer.parseInt(envPort.trim());
            } catch (NumberFormatException ignored) {}
        }
        return 8080;
    }

    public static void main(String[] args) {
        int port = getPort();
        System.out.println("===============================================================================");
        System.out.println("     MAKKAL NAGAR MUNICIPAL OFFICE - FULL-STACK BACKEND REST API SERVER");
        System.out.println("===============================================================================");

        // Initialize Data Persistence
        System.out.println("[SYSTEM] Initializing persistent database storage...");
        Database database = new Database();

        // Initialize Grievance Business Manager
        GrievanceManager grievanceManager = new GrievanceManager(database);

        // Print Initial Database Statistics
        System.out.println("-------------------------------------------------------------------------------");
        System.out.println("                      CURRENT REAL DATABASE METRICS");
        System.out.println("-------------------------------------------------------------------------------");
        var stats = grievanceManager.getDashboardStats();
        System.out.printf("| Total Complaints : %-5d | Pending Complaints   : %-5d |\n", stats.get("total"), stats.get("pending"));
        System.out.printf("| In Progress      : %-5d | Resolved Complaints  : %-5d |\n", stats.get("inProgress"), stats.get("resolved"));
        System.out.printf("| Critical Priority: %-5d |\n", stats.get("critical"));
        System.out.println("-------------------------------------------------------------------------------\n");

        // Start REST API Server
        ServerManager serverManager = new ServerManager(port, database, grievanceManager);
        serverManager.start();

        System.out.println("\n[SERVER ACTIVE] Standard JDK REST Endpoints Ready at http://localhost:" + port + "/api/");
        System.out.println("   -> POST /api/auth/register    (Citizen Registration)");
        System.out.println("   -> POST /api/auth/login       (Citizen & Staff Login)");
        System.out.println("   -> GET  /api/dashboard/stats  (Real DB Stats)");
        System.out.println("   -> POST /api/complaints       (Submit Complaint)");
        System.out.println("   -> GET  /api/complaints       (Master Directory & Priority Queue)\n");
        System.out.println("Press Ctrl+C to stop backend server.");
    }
}
