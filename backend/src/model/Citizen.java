package model;

/**
 * Concrete class representing a Citizen.
 * Inherits from User (OOP Inheritance).
 */
public class Citizen extends User {
    private String name;
    private String mobileNumber;

    public Citizen() {
        super();
        setRole("CITIZEN");
    }

    public Citizen(String id, String username, String email, String mobile, String passwordHash, String createdAt) {
        super(id, username, email, mobile, passwordHash, "CITIZEN", "", createdAt);
        this.name = username;
        this.mobileNumber = mobile;
    }

    // Convenience constructor matching legacy usage
    public Citizen(String name, String mobileNumber) {
        super(null, name, null, mobileNumber, null, "CITIZEN", "", null);
        this.name = name;
        this.mobileNumber = mobileNumber;
    }

    @Override
    public String getDisplayName() {
        return (name != null && !name.isEmpty()) ? name : getUsername();
    }

    @Override
    public boolean canVerifyComplaints() {
        return false;
    }

    @Override
    public boolean canAssignDepartment() {
        return false;
    }

    @Override
    public boolean canUpdateStatus() {
        return false;
    }

    @Override
    public String getName() {
        return name != null ? name : getUsername();
    }

    @Override
    public void setName(String name) {
        this.name = name;
        setUsername(name);
    }

    public String getMobileNumber() {
        return mobileNumber != null ? mobileNumber : getMobile();
    }

    public void setMobileNumber(String mobileNumber) {
        this.mobileNumber = mobileNumber;
        setMobile(mobileNumber);
    }

    @Override
    public String toString() {
        return getDisplayName() + " (" + getMobileNumber() + ")";
    }
}
