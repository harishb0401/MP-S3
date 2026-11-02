package model;

/**
 * Model representing a detected Duplicate Candidate for a grievance complaint.
 * Encapsulates the candidate reference, matching criteria, reasons, and citizen statistics.
 */
public class DuplicateCandidate {
    private String complaintId;
    private String candidateComplaintId;
    private String matchReason;
    private String candidateCategory;
    private String candidateLocation;
    private String candidateStatus;
    private String candidateCitizenName;
    private String candidateCreatedAt;

    // Enhanced metrics
    private int distinctCitizenCount = 1;
    private double similarityScore = 0.0;
    private Double distanceMeters = null;
    private String candidateDescription = "";

    public DuplicateCandidate() {}

    public DuplicateCandidate(String complaintId, String candidateComplaintId, String matchReason,
                              String candidateCategory, String candidateLocation, String candidateStatus,
                              String candidateCitizenName, String candidateCreatedAt) {
        this.complaintId = complaintId;
        this.candidateComplaintId = candidateComplaintId;
        this.matchReason = matchReason;
        this.candidateCategory = candidateCategory;
        this.candidateLocation = candidateLocation;
        this.candidateStatus = candidateStatus;
        this.candidateCitizenName = candidateCitizenName;
        this.candidateCreatedAt = candidateCreatedAt;
    }

    public DuplicateCandidate(String complaintId, String candidateComplaintId, String matchReason,
                              String candidateCategory, String candidateLocation, String candidateStatus,
                              String candidateCitizenName, String candidateCreatedAt,
                              int distinctCitizenCount, double similarityScore, Double distanceMeters,
                              String candidateDescription) {
        this(complaintId, candidateComplaintId, matchReason, candidateCategory, candidateLocation, candidateStatus, candidateCitizenName, candidateCreatedAt);
        this.distinctCitizenCount = distinctCitizenCount;
        this.similarityScore = similarityScore;
        this.distanceMeters = distanceMeters;
        this.candidateDescription = candidateDescription != null ? candidateDescription : "";
    }

    public String getComplaintId() { return complaintId; }
    public void setComplaintId(String complaintId) { this.complaintId = complaintId; }

    public String getCandidateComplaintId() { return candidateComplaintId; }
    public void setCandidateComplaintId(String candidateComplaintId) { this.candidateComplaintId = candidateComplaintId; }

    public String getMatchReason() { return matchReason; }
    public void setMatchReason(String matchReason) { this.matchReason = matchReason; }

    public String getCandidateCategory() { return candidateCategory; }
    public void setCandidateCategory(String candidateCategory) { this.candidateCategory = candidateCategory; }

    public String getCandidateLocation() { return candidateLocation; }
    public void setCandidateLocation(String candidateLocation) { this.candidateLocation = candidateLocation; }

    public String getCandidateStatus() { return candidateStatus; }
    public void setCandidateStatus(String candidateStatus) { this.candidateStatus = candidateStatus; }

    public String getCandidateCitizenName() { return candidateCitizenName; }
    public void setCandidateCitizenName(String candidateCitizenName) { this.candidateCitizenName = candidateCitizenName; }

    public String getCandidateCreatedAt() { return candidateCreatedAt; }
    public void setCandidateCreatedAt(String candidateCreatedAt) { this.candidateCreatedAt = candidateCreatedAt; }

    public int getDistinctCitizenCount() { return distinctCitizenCount; }
    public void setDistinctCitizenCount(int distinctCitizenCount) { this.distinctCitizenCount = distinctCitizenCount; }

    public double getSimilarityScore() { return similarityScore; }
    public void setSimilarityScore(double similarityScore) { this.similarityScore = similarityScore; }

    public Double getDistanceMeters() { return distanceMeters; }
    public void setDistanceMeters(Double distanceMeters) { this.distanceMeters = distanceMeters; }

    public String getCandidateDescription() { return candidateDescription; }
    public void setCandidateDescription(String candidateDescription) { this.candidateDescription = candidateDescription; }

    // Convenience aliases
    public String getSourceComplaintId() { return complaintId; }
    public String getReason() { return matchReason; }
    public String getCategory() { return candidateCategory; }
    public String getLocation() { return candidateLocation; }
    public String getStatus() { return candidateStatus; }
    public String getCitizenName() { return candidateCitizenName; }
    public String getCreatedAt() { return candidateCreatedAt; }
    public String getDescription() { return candidateDescription; }
}
