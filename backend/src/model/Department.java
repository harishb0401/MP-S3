package model;

import java.util.ArrayList;
import java.util.List;
import java.util.PriorityQueue;

/**
 * Model representing a Municipal Department.
 * Manages operational capacity, current active load, and a department-level PriorityQueue waiting queue.
 */
public class Department {
    private String departmentId;
    private String departmentName;
    private int capacity;
    private int currentLoad;
    private DepartmentQueueManager queueManager;

    public Department() {
        this.capacity = 10;
        this.currentLoad = 0;
        this.queueManager = new DepartmentQueueManager(Complaint.DEFAULT_PRIORITY_POLICY);
    }

    public Department(String departmentId, String departmentName) {
        this(departmentId, departmentName, 10, 0);
    }

    public Department(String departmentId, String departmentName, int capacity, int currentLoad) {
        this(departmentId, departmentName, capacity, currentLoad, Complaint.DEFAULT_PRIORITY_POLICY);
    }

    public Department(String departmentId, String departmentName, int capacity, int currentLoad, GrievancePriorityPolicy policy) {
        this.departmentId = departmentId;
        this.departmentName = departmentName;
        this.capacity = capacity > 0 ? capacity : 10;
        this.currentLoad = Math.max(0, currentLoad);
        this.queueManager = new DepartmentQueueManager(policy != null ? policy : Complaint.DEFAULT_PRIORITY_POLICY);
    }

    public synchronized boolean hasAvailableCapacity() {
        return currentLoad < capacity;
    }

    /**
     * Atomically tries to acquire 1 capacity slot.
     * Returns true if acquired, false if department is at or above capacity.
     */
    public synchronized boolean tryAcquireCapacity() {
        if (this.currentLoad < this.capacity) {
            this.currentLoad++;
            return true;
        }
        return false;
    }

    public synchronized void incrementLoad() {
        if (this.currentLoad < this.capacity) {
            this.currentLoad++;
        } else {
            throw new IllegalStateException("CRITICAL INVARIANT VIOLATION: Cannot increment load beyond capacity (" +
                    capacity + ") for department " + departmentName);
        }
    }

    public synchronized void decrementLoad() {
        if (this.currentLoad > 0) {
            this.currentLoad--;
        }
    }

    public synchronized boolean addToWaitingQueue(Complaint complaint) {
        return queueManager.addToWaitingQueue(complaint);
    }

    public synchronized Complaint pollWaitingQueue() {
        return queueManager.pollWaitingQueue();
    }

    public synchronized Complaint peekWaitingQueue() {
        return queueManager.peekWaitingQueue();
    }

    public synchronized boolean containsInWaitingQueue(Complaint complaint) {
        return queueManager.containsInWaitingQueue(complaint);
    }

    public synchronized boolean removeFromWaitingQueue(Complaint complaint) {
        return queueManager.removeFromWaitingQueue(complaint);
    }

    /**
     * Reorders a waiting complaint whose ranking fields have changed.
     * Removes and re-inserts into the queue to restore heap order.
     * Does not mutate or reorder resolved complaints.
     */
    public synchronized boolean updateComplaintPriority(Complaint complaint) {
        return queueManager.updateComplaintPriority(complaint);
    }

    public synchronized List<Complaint> getWaitingQueueList() {
        return queueManager.getWaitingQueueList();
    }

    public synchronized int getWaitingQueueSize() {
        return queueManager.getWaitingQueueSize();
    }

    public DepartmentQueueManager getQueueManager() {
        return queueManager;
    }

    public GrievancePriorityPolicy getPriorityPolicy() {
        return queueManager.getPriorityPolicy();
    }

    public synchronized boolean addAssignedComplaint(Complaint c) {
        return queueManager.addAssignedComplaint(c);
    }

    public synchronized boolean removeAssignedComplaint(Complaint c) {
        return queueManager.removeAssignedComplaint(c);
    }

    public List<Complaint> getAssignedComplaints() {
        return queueManager.getAssignedComplaints();
    }

    public String getDepartmentId() {
        return departmentId;
    }

    public void setDepartmentId(String departmentId) {
        this.departmentId = departmentId;
    }

    public String getDepartmentName() {
        return departmentName;
    }

    public void setDepartmentName(String departmentName) {
        this.departmentName = departmentName;
    }

    public int getCapacity() {
        return capacity;
    }

    public void setCapacity(int capacity) {
        this.capacity = Math.max(1, capacity);
    }

    public int getCurrentLoad() {
        return currentLoad;
    }

    public synchronized void setCurrentLoad(int currentLoad) {
        this.currentLoad = Math.max(0, Math.min(currentLoad, capacity));
    }

    public PriorityQueue<Complaint> getWaitingQueue() {
        return queueManager.getWaitingQueue();
    }

    @Override
    public String toString() {
        return departmentName + " [Capacity: " + currentLoad + "/" + capacity + ", Waiting: " + queueManager.getWaitingQueueSize() + "]";
    }
}
