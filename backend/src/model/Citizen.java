package model;

/**
 * Concrete class representing a Citizen.
 * Inherits from User (OOP Inheritance).
 */
public class Citizen extends User {

    private String name;

    public Citizen() {
        super();
        setRole("CITIZEN");
    }

    public Citizen(
            String id,
            String name,
            String email,
            String mobile,
            String passwordHash,
            String createdAt) {

        super(
                id,
                name,
                email,
                mobile,
                passwordHash,
                "CITIZEN",
                "",
                createdAt
        );

        this.name = name;
    }

    /**
     * Convenience constructor.
     */
    public Citizen(String name, String mobileNumber) {

        super(
                null,
                name,
                null,
                mobileNumber,
                null,
                "CITIZEN",
                "",
                null
        );

        this.name = name;
    }

    @Override
    public String getDisplayName() {
        return (name != null && !name.isBlank())
                ? name
                : getUsername();
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
        return (name != null)
                ? name
                : getUsername();
    }

    @Override
    public void setName(String name) {
        this.name = name;
        setUsername(name);
    }

    /**
     * Uses User.mobile as the single source of truth.
     */
    public String getMobileNumber() {
        return getMobile();
    }

    public void setMobileNumber(String mobileNumber) {
        setMobile(mobileNumber);
    }

    @Override
    public String toString() {
        return getDisplayName() + " (" + getMobileNumber() + ")";
    }
}
