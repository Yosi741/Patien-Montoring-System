package pages.patient;

public class PatientVisit {

    private final long id;
    private final String patientId;
    private final String visitDate;
    private final String dischargeDate;
    private final String status;
    private final String report;
    private final String createdAt;

    public PatientVisit(long id, String patientId, String visitDate, String dischargeDate,
                        String status, String report, String createdAt) {
        this.id = id;
        this.patientId = patientId;
        this.visitDate = visitDate == null ? "" : visitDate;
        this.dischargeDate = dischargeDate == null ? "" : dischargeDate;
        this.status = status == null ? "" : status;
        this.report = report == null ? "" : report;
        this.createdAt = createdAt == null ? "" : createdAt;
    }

    public long getId() {
        return id;
    }

    public String getPatientId() {
        return patientId;
    }

    public String getVisitDate() {
        return visitDate;
    }

    public String getDischargeDate() {
        return dischargeDate;
    }

    public String getStatus() {
        return status;
    }

    public String getReport() {
        return report;
    }

    public String getCreatedAt() {
        return createdAt;
    }
}
