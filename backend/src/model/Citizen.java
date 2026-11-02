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

    public Citizen(
            String id,
            String username,
            String name,
            String email,
            String mobile,
            String passwordHash,
            String createdAt) {

        super(
                id,
                username,
                email,
                mobile,
                passwordHash,
                "CITIZEN",
                "",
                createdAt
        );

        this.name = name;
    }

    public Citizen(
            String id,
            String username,
            String name,
            String email,
            String mobile,
            String passwordHash,
            String identityHash,
            String maskedIdentity,
            String createdAt) {

        super(
                id,
                username,
                email,
                mobile,
                passwordHash,
                "CITIZEN",
                "",
                createdAt
        );

        this.name = name;
        setIdentityHash(identityHash);
        setMaskedIdentity(maskedIdentity);
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
        return (name != null && !name.isBlank())
                ? name
                : (getUsername() != null && !getUsername().isBlank() ? getUsername() : "Citizen");
    }

    @Override
    public void setName(String name) {
        this.name = name;
        if (getUsername() == null || getUsername().isBlank()) {
            setUsername(name);
        }
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

    public String getMaskedAadhaar() {
        return getMaskedIdentity();
    }

    public void setMaskedAadhaar(String maskedAadhaar) {
        setMaskedIdentity(maskedAadhaar);
    }

    @Override
    public String toString() {
        return getDisplayName() + " (" + getMobileNumber() + ")";
    }
}
