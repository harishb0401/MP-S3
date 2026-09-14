package service;

import model.Complaint;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

public class GrievanceManager {
    // Data Structures as required
    private List<Complaint> complaintList;                   // ArrayList for sequential storage & iteration
    private Map<String, Complaint> complaintMap;            // HashMap for fast O(1) ID lookups
    private PriorityQueue<Complaint> priorityQueue;         // PriorityQueue for priority-based resolution (Emergency > High > Medium > Low)

    private int complaintCounter = 1000;

    public GrievanceManager() {
        this.complaintList = new ArrayList<>();
        this.complaintMap = new HashMap<>();
        this.priorityQueue = new PriorityQueue<>();
    }

    // Generate Auto Complaint ID (e.g. CMP1001)
    public String generateComplaintId() {
        complaintCounter++;
        return "CMP" + complaintCounter;
    }

    // Add new complaint
    public void addComplaint(Complaint complaint) {
        complaintList.add(complaint);
        complaintMap.put(complaint.getComplaintId(), complaint);
        
        // Add to priority queue if it is not resolved/rejected
        if (!"Resolved".equalsIgnoreCase(complaint.getStatus()) && !"Rejected".equalsIgnoreCase(complaint.getStatus())) {
            priorityQueue.add(complaint);
        }
    }

    // Get complaint by ID using HashMap - O(1) Time Complexity
    public Complaint getComplaintById(String complaintId) {
        return complaintMap.get(complaintId);
    }

    // Get all complaints list
    public List<Complaint> getAllComplaints() {
        return new ArrayList<>(complaintList);
    }

    // Update Status of a complaint
    public boolean updateStatus(String complaintId, String newStatus) {
        Complaint complaint = complaintMap.get(complaintId);
        if (complaint != null) {
            complaint.setStatus(newStatus);
            rebuildPriorityQueue();
            return true;
        }
        return false;
    }

    // Assign Department to a complaint
    public boolean assignDepartment(String complaintId, String department) {
        Complaint complaint = complaintMap.get(complaintId);
        if (complaint != null) {
            complaint.setAssignedDepartment(department);
            return true;
        }
        return false;
    }

    // Rebuild PriorityQueue after updates to reflect current active pending/in-progress complaints
    private void rebuildPriorityQueue() {
        priorityQueue.clear();
        for (Complaint c : complaintList) {
            if (!"Resolved".equalsIgnoreCase(c.getStatus()) && !"Rejected".equalsIgnoreCase(c.getStatus())) {
                priorityQueue.add(c);
            }
        }
    }

    // Get PriorityQueue copy for display
    public PriorityQueue<Complaint> getPriorityQueue() {
        return new PriorityQueue<>(priorityQueue);
    }

    // Return ordered list of complaints extracted from PriorityQueue
    public List<Complaint> getOrderedPriorityList() {
        PriorityQueue<Complaint> tempQueue = new PriorityQueue<>(priorityQueue);
        List<Complaint> orderedList = new ArrayList<>();
        while (!tempQueue.isEmpty()) {
            orderedList.add(tempQueue.poll());
        }
        return orderedList;
    }

    // Statistics Methods
    public int getTotalCount() {
        return complaintList.size();
    }

    public int getPendingCount() {
        int count = 0;
        for (Complaint c : complaintList) {
            if ("Pending".equalsIgnoreCase(c.getStatus())) count++;
        }
        return count;
    }

    public int getInProgressCount() {
        int count = 0;
        for (Complaint c : complaintList) {
            if ("In Progress".equalsIgnoreCase(c.getStatus())) count++;
        }
        return count;
    }

    public int getResolvedCount() {
        int count = 0;
        for (Complaint c : complaintList) {
            if ("Resolved".equalsIgnoreCase(c.getStatus())) count++;
        }
        return count;
    }

    public int getHighOrEmergencyCount() {
        int count = 0;
        for (Complaint c : complaintList) {
            if ("High".equalsIgnoreCase(c.getPriority()) || "Emergency".equalsIgnoreCase(c.getPriority())) count++;
        }
        return count;
    }
}
