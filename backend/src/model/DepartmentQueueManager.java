package model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.PriorityQueue;

/**
 * Dedicated Queue Management component composed inside Department.
 * Manages:
 * 1. Waiting queue ordered by GrievancePriorityPolicy.
 * 2. Active assigned complaints collection.
 * 3. Enforces thread-safe, consistent queue operations.
 */
public class DepartmentQueueManager {
    private final PriorityQueue<Complaint> waitingQueue;
    private final List<Complaint> assignedComplaints;
    private final GrievancePriorityPolicy priorityPolicy;

    public DepartmentQueueManager() {
        this(new StandardGrievancePriorityPolicy());
    }

    public DepartmentQueueManager(GrievancePriorityPolicy policy) {
        this.priorityPolicy = policy != null ? policy : new StandardGrievancePriorityPolicy();
        this.waitingQueue = new PriorityQueue<>(this.priorityPolicy);
        this.assignedComplaints = new ArrayList<>();
    }

    public synchronized GrievancePriorityPolicy getPriorityPolicy() {
        return priorityPolicy;
    }

    public synchronized PriorityQueue<Complaint> getWaitingQueue() {
        return waitingQueue;
    }

    public synchronized List<Complaint> getAssignedComplaints() {
        return Collections.unmodifiableList(new ArrayList<>(assignedComplaints));
    }

    public synchronized int getAssignedCount() {
        return assignedComplaints.size();
    }

    public synchronized boolean addAssignedComplaint(Complaint complaint) {
        if (complaint == null) return false;
        if (!assignedComplaints.contains(complaint)) {
            assignedComplaints.add(complaint);
            return true;
        }
        return false;
    }

    public synchronized boolean removeAssignedComplaint(Complaint complaint) {
        if (complaint == null) return false;
        return assignedComplaints.remove(complaint);
    }

    public synchronized boolean addToWaitingQueue(Complaint complaint) {
        if (complaint == null) return false;
        if (!waitingQueue.contains(complaint)) {
            complaint.setWaiting(true);
            return waitingQueue.add(complaint);
        }
        return false;
    }

    public synchronized Complaint pollWaitingQueue() {
        Complaint c = waitingQueue.poll();
        if (c != null) {
            c.setWaiting(false);
        }
        return c;
    }

    public synchronized Complaint peekWaitingQueue() {
        return waitingQueue.peek();
    }

    public synchronized boolean containsInWaitingQueue(Complaint complaint) {
        if (complaint == null) return false;
        return waitingQueue.contains(complaint);
    }

    public synchronized boolean removeFromWaitingQueue(Complaint complaint) {
        if (complaint == null) return false;
        boolean removed = waitingQueue.remove(complaint);
        if (removed) {
            complaint.setWaiting(false);
        }
        return removed;
    }

    public synchronized boolean updateComplaintPriority(Complaint complaint) {
        if (complaint == null) return false;
        boolean isResolved = "RESOLVED".equalsIgnoreCase(complaint.getStatus()) || "CLOSED".equalsIgnoreCase(complaint.getStatus());
        if (isResolved) {
            waitingQueue.remove(complaint);
            return false;
        }
        if (waitingQueue.remove(complaint)) {
            return waitingQueue.add(complaint);
        }
        return false;
    }

    public synchronized List<Complaint> getWaitingQueueList() {
        PriorityQueue<Complaint> copy = new PriorityQueue<>(waitingQueue);
        List<Complaint> list = new ArrayList<>();
        while (!copy.isEmpty()) {
            list.add(copy.poll());
        }
        return list;
    }

    public synchronized int getWaitingQueueSize() {
        return waitingQueue.size();
    }
}
