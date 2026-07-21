package pages.billing;

/**
 * Represents a clinic billing record displayed and edited by the billing module.
 */
public class BillingRecord {

    private final long id;
    private final String invoiceNo;
    private final String patientId;
    private final String patientName;
    private final String serviceName;
    private final String visitType;
    private final double amount;
    private final String paymentStatus;
    private final String paymentMethod;
    private final String notes;
    private final String createdAt;
    private final String paidAt;
    private final String createdBy;

    /**
     * Creates a billing record from the supplied record values.
     */
    public BillingRecord(long id, String invoiceNo, String patientId, String patientName,
                         String serviceName, String visitType, double amount,
                         String paymentStatus, String paymentMethod, String notes,
                         String createdAt, String paidAt, String createdBy) {
        this.id = id;
        this.invoiceNo = invoiceNo == null ? "" : invoiceNo.trim();
        this.patientId = patientId == null ? "" : patientId.trim();
        this.patientName = patientName == null ? "" : patientName.trim();
        this.serviceName = serviceName == null ? "" : serviceName.trim();
        this.visitType = visitType == null ? "" : visitType.trim();
        this.amount = amount;
        this.paymentStatus = paymentStatus == null ? "UNPAID" : paymentStatus.trim();
        this.paymentMethod = paymentMethod == null ? "" : paymentMethod.trim();
        this.notes = notes == null ? "" : notes.trim();
        this.createdAt = createdAt == null ? "" : createdAt.trim();
        this.paidAt = paidAt == null ? "" : paidAt.trim();
        this.createdBy = createdBy == null ? "" : createdBy.trim();
    }

    public long getId() {
        return id;
    }

    public String getInvoiceNo() {
        return invoiceNo;
    }

    public String getPatientId() {
        return patientId;
    }

    public String getPatientName() {
        return patientName;
    }

    public String getServiceName() {
        return serviceName;
    }

    public String getVisitType() {
        return visitType;
    }

    public double getAmount() {
        return amount;
    }

    public String getPaymentStatus() {
        return paymentStatus;
    }

    public String getPaymentMethod() {
        return paymentMethod;
    }

    public String getNotes() {
        return notes;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public String getPaidAt() {
        return paidAt;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public boolean isPaid() {
        return "PAID".equalsIgnoreCase(paymentStatus);
    }

    public boolean isCancelled() {
        return "CANCELLED".equalsIgnoreCase(paymentStatus);
    }
}
