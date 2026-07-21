package pages.billing;

import app.helpers.PermissionHelper;
import pages.patient.patient_details.PatientDetail;
import pages.patient.patient_details.PatientDetailsRepository;
import pages.user.User;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Validates billing permissions and invoice data before delegating persistence to the billing DAO.
 */
public class BillingService {

    private static final DateTimeFormatter SQL_DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final BillingDao billingDao;
    private final PatientDetailsRepository patientDetailsRepository;

    /**
     * Creates the service with the dependencies used by the billing workflow.
     */
    public BillingService() {
        this(new SqliteBillingDao(), new PatientDetailsRepository());
    }

    /**
     * Creates the service with the dependencies used by the billing workflow.
     */
    public BillingService(BillingDao billingDao, PatientDetailsRepository patientDetailsRepository) {
        this.billingDao = billingDao;
        this.patientDetailsRepository = patientDetailsRepository;
    }

    /**
     * Loads overview for the billing workflow.
     */
    public BillingOverview loadOverview(String search, String status, String dateRange) throws SQLException {
        BillingDao.BillingQuery query = new BillingDao.BillingQuery(normalizeSearch(search), normalizeStatusFilter(status), normalizeDateRange(dateRange));
        List<BillingRecord> records = billingDao.findAll(query);
        BillingDao.BillingMetrics metrics = billingDao.getDashboardMetrics(query);
        return new BillingOverview(records, metrics);
    }

    /**
     * Creates invoice for the billing workflow.
     */
    public BillingRecord createInvoice(User actor, InvoiceDraft draft) throws SQLException {
        require(PermissionHelper.canManageBilling(actor), "Billing access is not available for this user.");
        validateDraft(draft);
        PatientDetail patient = patientDetailsRepository.findPatientDetailsById(draft.patientId().trim())
                .orElseThrow(() -> new IllegalArgumentException("Patient file was not found for this ID."));
        String status = normalizeInvoiceStatus(draft.paymentStatus());
        String paymentMethod = normalizePaymentMethod(draft.paymentMethod());
        if ("PAID".equals(status) && paymentMethod.isBlank()) {
            throw new IllegalArgumentException("Payment method is required when the invoice is marked paid.");
        }

        String createdAt = now();
        String paidAt = "PAID".equals(status) ? createdAt : "";
        return billingDao.createInvoice(new BillingDao.BillingWriteRecord(
                patient.getPatientId(),
                patient.getName(),
                draft.serviceName().trim(),
                blankTo(draft.visitType(), ""),
                draft.amount(),
                status,
                paymentMethod,
                blankTo(draft.notes(), ""),
                createdAt,
                paidAt,
                username(actor)
        ));
    }

    /**
     * Finds invoice.
     */
    public Optional<BillingRecord> findInvoice(long invoiceId) throws SQLException {
        return billingDao.findById(invoiceId);
    }

    /**
     * Marks paid with its new workflow state.
     */
    public void markPaid(User actor, long invoiceId, String paymentMethod) throws SQLException {
        require(PermissionHelper.canManageBilling(actor), "Billing access is not available for this user.");
        String method = normalizePaymentMethod(paymentMethod);
        if (method.isBlank()) {
            throw new IllegalArgumentException("Payment method is required to mark an invoice paid.");
        }
        boolean updated = billingDao.markPaid(invoiceId, method, now());
        if (!updated) {
            throw new IllegalStateException("Invoice could not be marked paid.");
        }
    }

    /**
     * Determines whether cancel invoice for the current record or user.
     */
    public void cancelInvoice(User actor, long invoiceId) throws SQLException {
        require(PermissionHelper.canManageBilling(actor), "Billing access is not available for this user.");
        boolean updated = billingDao.cancelInvoice(invoiceId);
        if (!updated) {
            throw new IllegalStateException("Invoice could not be cancelled.");
        }
    }

    /**
     * Deletes invoice after the required checks.
     */
    public void deleteInvoice(User actor, long invoiceId) throws SQLException {
        require(PermissionHelper.canDeleteInvoice(actor), "Only Admin users can delete invoices.");
        findInvoice(invoiceId).orElseThrow(() -> new IllegalArgumentException("Invoice not found in the local clinic database: " + invoiceId));
        boolean deleted = billingDao.deleteInvoice(invoiceId);
        if (!deleted) {
            throw new IllegalStateException("Invoice could not be deleted.");
        }
    }

    /**
     * Finds patient name.
     */
    public Optional<String> findPatientName(String patientId) throws SQLException {
        if (patientId == null || patientId.isBlank()) {
            return Optional.empty();
        }
        return patientDetailsRepository.findPatientDetailsById(patientId.trim()).map(PatientDetail::getName);
    }

    /**
     * Validates draft against the active business rules.
     */
    private void validateDraft(InvoiceDraft draft) {
        if (draft == null) {
            throw new IllegalArgumentException("Invoice details are required.");
        }
        if (draft.patientId() == null || draft.patientId().trim().isEmpty()) {
            throw new IllegalArgumentException("Patient ID is required.");
        }
        if (draft.serviceName() == null || draft.serviceName().trim().isEmpty()) {
            throw new IllegalArgumentException("At least one clinic service must be selected.");
        }
        if (Double.isNaN(draft.amount()) || Double.isInfinite(draft.amount()) || draft.amount() <= 0) {
            throw new IllegalArgumentException("Amount must be a positive number.");
        }
        String status = normalizeInvoiceStatus(draft.paymentStatus());
        if (status.isBlank()) {
            throw new IllegalArgumentException("Payment status is required.");
        }
    }

    /**
     * Enforces require before the protected operation continues.
     */
    private void require(boolean allowed, String message) {
        if (!allowed) {
            throw new SecurityException(message);
        }
    }

    /**
     * Normalizes search to the stored application format.
     */
    private String normalizeSearch(String value) {
        return value == null ? "" : value.trim();
    }

    /**
     * Normalizes status filter to the stored application format.
     */
    private String normalizeStatusFilter(String value) {
        if (value == null || value.isBlank()) {
            return "All";
        }
        String trimmed = value.trim();
        if ("Paid".equalsIgnoreCase(trimmed)) {
            return "PAID";
        }
        if ("Cancelled".equalsIgnoreCase(trimmed)) {
            return "CANCELLED";
        }
        if ("Unpaid".equalsIgnoreCase(trimmed)) {
            return "UNPAID";
        }
        return "All";
    }

    /**
     * Normalizes date range to the stored application format.
     */
    private String normalizeDateRange(String value) {
        if (value == null || value.isBlank()) {
            return "All Time";
        }
        String trimmed = value.trim();
        if ("Today".equalsIgnoreCase(trimmed)) {
            return "Today";
        }
        if ("Last 7 days".equalsIgnoreCase(trimmed)) {
            return "Last 7 days";
        }
        if ("Last 30 days".equalsIgnoreCase(trimmed)) {
            return "Last 30 days";
        }
        return "All Time";
    }

    /**
     * Normalizes invoice status to the stored application format.
     */
    private String normalizeInvoiceStatus(String value) {
        if (value == null || value.isBlank()) {
            return "UNPAID";
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if ("PAID".equals(normalized) || "CANCELLED".equals(normalized)) {
            return normalized;
        }
        return "UNPAID";
    }

    /**
     * Normalizes payment method to the stored application format.
     */
    private String normalizePaymentMethod(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.isBlank()) {
            return "";
        }
        for (String candidate : new String[]{"Cash", "Card", "Insurance", "Other"}) {
            if (candidate.equalsIgnoreCase(trimmed)) {
                return candidate;
            }
        }
        return trimmed;
    }

    /**
     * Normalizes blank to to the workflow fallback value.
     */
    private String blankTo(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }

    /**
     * Returns the username associated with the current session or workflow record.
     */
    private String username(User actor) {
        return actor == null || actor.getUsername() == null || actor.getUsername().isBlank()
                ? "Unknown"
                : actor.getUsername().trim();
    }

    /**
     * Returns the current timestamp in the SQLite storage format.
     */
    private String now() {
        return LocalDateTime.now().format(SQL_DATE_TIME);
    }

    public record BillingOverview(List<BillingRecord> records, BillingDao.BillingMetrics metrics) {
    }

    public record InvoiceDraft(
            String patientId,
            String serviceName,
            String visitType,
            double amount,
            String paymentStatus,
            String paymentMethod,
            String notes
    ) {
    }
}



