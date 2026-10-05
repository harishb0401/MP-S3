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
    private PriorityQueue<Complaint> waitingQueue;

    public Department() {
        this.capacity = 10;
        this.currentLoad = 0;
        this.waitingQueue = new PriorityQueue<>();
    }

    public Department(String departmentId, String departmentName) {
        this(departmentId, departmentName, 10, 0);
    }

    public Department(String departmentId, String departmentName, int capacity, int currentLoad) {
        this.departmentId = departmentId;
        this.departmentName = departmentName;
        this.capacity = capacity > 0 ? capacity : 10;
        this.currentLoad = Math.max(0, currentLoad);
        this.waitingQueue = new PriorityQueue<>();
    }

    public boolean hasAvailableCapacity() {
        return currentLoad < capacity;
    }

    public synchronized void incrementLoad() {
        this.currentLoad++;
    }

    public synchronized void decrementLoad() {
        if (this.currentLoad > 0) {
            this.currentLoad--;
        }
    }

    public synchronized boolean addToWaitingQueue(Complaint complaint) {
        if (complaint == null) return false;
        // Avoid duplicate entries in waiting queue
        if (!waitingQueue.contains(complaint)) {
            return waitingQueue.add(complaint);
        }
        return false;
    }

    public synchronized Complaint pollWaitingQueue() {
        return waitingQueue.poll();
    }

    public synchronized boolean removeFromWaitingQueue(Complaint complaint) {
        return waitingQueue.remove(complaint);
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

    public void setCurrentLoad(int currentLoad) {
        this.currentLoad = Math.max(0, currentLoad);
    }

    public PriorityQueue<Complaint> getWaitingQueue() {
        return waitingQueue;
    }

    @Override
    public String toString() {
        return departmentName + " [Capacity: " + currentLoad + "/" + capacity + ", Waiting: " + waitingQueue.size() + "]";
    }
}
