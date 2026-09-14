import model.Citizen;
import model.Complaint;
import service.GrievanceManager;

import java.util.List;
import java.util.PriorityQueue;
import java.util.Scanner;

public class Main {
    public static void main(String[] args) {
        System.out.println("===============================================================================");
        System.out.println("     MAKKAL NAGAR MUNICIPAL OFFICE - GRIEVANCE MANAGEMENT SYSTEM (BACKEND)");
        System.out.println("===============================================================================");

        GrievanceManager manager = new GrievanceManager();

        // Preload Sample Complaints
        preloadSampleData(manager);

        // Display Dashboard Overview
        displayDashboard(manager);

        // Display All Complaints (ArrayList)
        displayAllComplaints(manager);

        // Demonstrate Fast HashMap Lookup
        demonstrateHashMapLookup(manager, "CMP1002");

        // Demonstrate PriorityQueue Ordering (Priority Resolution Queue)
        displayPriorityQueue(manager);

        // Interactive Console Menu
        runInteractiveMenu(manager);
    }

    private static void preloadSampleData(GrievanceManager manager) {
        System.out.println("\n[SYSTEM] Preloading 6 sample citizen complaints into system data structures...");

        manager.addComplaint(new Complaint("CMP1001", new Citizen("Ravi", "9876543210"), 
                "Streetlight", "Streetlight not working for 3 days", "Makkal Nagar 2nd Street", 
                "High", "Pending", "Electrical"));

        manager.addComplaint(new Complaint("CMP1002", new Citizen("Priya", "9876543211"), 
                "Water Supply", "Major pipe burst causing flooding in main street", "Makkal Nagar 5th Street", 
                "Emergency", "Pending", "Water Supply"));

        manager.addComplaint(new Complaint("CMP1003", new Citizen("Kumar", "9876543212"), 
                "Waste Management", "Uncollected solid waste overflowing bins", "Makkal Nagar Main Road", 
                "Medium", "In Progress", "Sanitation"));

        manager.addComplaint(new Complaint("CMP1004", new Citizen("Anitha", "9876543213"), 
                "Drainage", "Drainage blockage causing foul smell", "Makkal Nagar 8th Cross", 
                "High", "Pending", "Drainage"));

        manager.addComplaint(new Complaint("CMP1005", new Citizen("Suresh", "9876543214"), 
                "Road", "Deep pothole causing vehicle damage", "Bus Stand Road", 
                "Low", "In Progress", "Roads"));

        manager.addComplaint(new Complaint("CMP1006", new Citizen("Rajesh", "9876543215"), 
                "Sanitation", "Hazardous medical waste dumped near park", "Makkal Nagar Park", 
                "Emergency", "Pending", "Sanitation"));

        System.out.println("[SUCCESS] Sample data loaded successfully!\n");
    }

    private static void displayDashboard(GrievanceManager manager) {
        System.out.println("-------------------------------------------------------------------------------");
        System.out.println("                          DASHBOARD METRICS SUMMARY");
        System.out.println("-------------------------------------------------------------------------------");
        System.out.printf("| Total Complaints : %-5d | Pending Complaints   : %-5d |\n", manager.getTotalCount(), manager.getPendingCount());
        System.out.printf("| In Progress      : %-5d | Resolved Complaints  : %-5d |\n", manager.getInProgressCount(), manager.getResolvedCount());
        System.out.printf("| High/Emergency   : %-5d |\n", manager.getHighOrEmergencyCount());
        System.out.println("-------------------------------------------------------------------------------\n");
    }

    private static void displayAllComplaints(GrievanceManager manager) {
        System.out.println("--------------------------------------------------------------------------------------------------");
        System.out.println("                    ALL COMPLAINTS REGISTER (Stored in ArrayList)");
        System.out.println("--------------------------------------------------------------------------------------------------");
        System.out.printf("%-10s | %-10s | %-16s | %-10s | %-12s | %-14s\n", 
                "ID", "Citizen", "Type", "Priority", "Status", "Department");
        System.out.println("--------------------------------------------------------------------------------------------------");
        for (Complaint c : manager.getAllComplaints()) {
            System.out.printf("%-10s | %-10s | %-16s | %-10s | %-12s | %-14s\n",
                    c.getComplaintId(), c.getCitizen().getName(), c.getComplaintType(),
                    c.getPriority(), c.getStatus(), c.getAssignedDepartment());
        }
        System.out.println("--------------------------------------------------------------------------------------------------\n");
    }

    private static void demonstrateHashMapLookup(GrievanceManager manager, String targetId) {
        System.out.println("--- DEMONSTRATION: HashMap O(1) Fast Lookup ---");
        System.out.println("Querying Complaint ID: " + targetId);
        Complaint found = manager.getComplaintById(targetId);
        if (found != null) {
            System.out.println("Found Complaint Details via HashMap:");
            System.out.println("  -> ID: " + found.getComplaintId());
            System.out.println("  -> Citizen: " + found.getCitizen());
            System.out.println("  -> Location: " + found.getLocation());
            System.out.println("  -> Description: " + found.getDescription());
            System.out.println("  -> Priority: " + found.getPriority() + " | Status: " + found.getStatus());
        } else {
            System.out.println("Complaint ID " + targetId + " not found!");
        }
        System.out.println("-----------------------------------------------\n");
    }

    private static void displayPriorityQueue(GrievanceManager manager) {
        System.out.println("--------------------------------------------------------------------------------------------------");
        System.out.println("           PRIORITY RESOLUTION QUEUE (PriorityQueue: Emergency > High > Medium > Low)");
        System.out.println("--------------------------------------------------------------------------------------------------");
        List<Complaint> queueList = manager.getOrderedPriorityList();
        int rank = 1;
        for (Complaint c : queueList) {
            System.out.printf("%d. %-8s | %-15s | Priority: %-9s | Status: %-11s | Location: %s\n",
                    rank++, c.getComplaintId(), c.getComplaintType(), c.getPriority(), c.getStatus(), c.getLocation());
        }
        System.out.println("--------------------------------------------------------------------------------------------------\n");
    }

    private static void runInteractiveMenu(GrievanceManager manager) {
        Scanner scanner = new Scanner(System.in);
        while (true) {
            System.out.println("===== MENU OPTIONS =====");
            System.out.println("1. Register New Complaint");
            System.out.println("2. Search Complaint by ID (HashMap Lookup)");
            System.out.println("3. View Priority Resolution Queue (PriorityQueue)");
            System.out.println("4. Update Complaint Status / Assign Department");
            System.out.println("5. Exit");
            System.out.print("Select an option (1-5): ");

            String input = scanner.nextLine().trim();
            if ("1".equals(input)) {
                System.out.print("Enter Citizen Name: ");
                String name = scanner.nextLine();
                System.out.print("Enter Mobile Number: ");
                String mobile = scanner.nextLine();
                System.out.print("Enter Complaint Type (Streetlight/Water Supply/Waste Management/Road/Drainage/Sanitation): ");
                String type = scanner.nextLine();
                System.out.print("Enter Description: ");
                String desc = scanner.nextLine();
                System.out.print("Enter Location: ");
                String loc = scanner.nextLine();
                System.out.print("Enter Priority (Emergency/High/Medium/Low): ");
                String priority = scanner.nextLine();

                String newId = manager.generateComplaintId();
                Complaint newCmp = new Complaint(newId, new Citizen(name, mobile), type, desc, loc, priority, "Pending", "Unassigned");
                manager.addComplaint(newCmp);

                System.out.println("\n[SUCCESS] Complaint registered! Assigned ID: " + newId);
                displayDashboard(manager);

            } else if ("2".equals(input)) {
                System.out.print("Enter Complaint ID (e.g. CMP1001): ");
                String id = scanner.nextLine().trim();
                demonstrateHashMapLookup(manager, id);

            } else if ("3".equals(input)) {
                displayPriorityQueue(manager);

            } else if ("4".equals(input)) {
                System.out.print("Enter Complaint ID to update: ");
                String id = scanner.nextLine().trim();
                Complaint cmp = manager.getComplaintById(id);
                if (cmp == null) {
                    System.out.println("Complaint ID not found!");
                    continue;
                }
                System.out.print("Enter New Status (Pending/In Progress/Resolved/Rejected): ");
                String status = scanner.nextLine().trim();
                System.out.print("Enter Assigned Department (Electrical/Water Supply/Sanitation/Roads/Drainage): ");
                String dept = scanner.nextLine().trim();

                manager.updateStatus(id, status);
                manager.assignDepartment(id, dept);
                System.out.println("[SUCCESS] Updated Complaint " + id + " to Status: " + status + ", Dept: " + dept);

            } else if ("5".equals(input)) {
                System.out.println("Exiting Java Backend Demonstration. Goodbye!");
                break;
            } else {
                System.out.println("Invalid option! Please select 1-5.");
            }
        }
        scanner.close();
    }
}
