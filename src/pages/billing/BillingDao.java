package pages.billing;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/**
 * Defines SQLite-facing operations for clinic invoices and billing summaries.
 */
public interface BillingDao {

    /**
     * Creates invoice for the billing workflow.
     */
    BillingRecord createInvoice(BillingWriteRecord record) throws SQLException;

    /**
     * Finds all in SQLite.
     */
    List<BillingRecord> findAll(BillingQuery query) throws SQLException;

    /**
     * Finds by ID in SQLite.
     */
    Optional<BillingRecord> findById(long invoiceId) throws SQLException;

    /**
     * Marks paid with its new workflow state.
     */
    boolean markPaid(long invoiceId, String paymentMethod, String paidAt) throws SQLException;

    /**
     * Determines whether cancel invoice for the current record or user.
     */
    boolean cancelInvoice(long invoiceId) throws SQLException;

    /**
     * Deletes invoice after the required checks.
     */
    boolean deleteInvoice(long invoiceId) throws SQLException;

    /**
     * Returns dashboard metrics used by the billing workflow.
     */
    BillingMetrics getDashboardMetrics(BillingQuery query) throws SQLException;

    record BillingWriteRecord(
            String patientId,
            String patientName,
            String serviceName,
            String visitType,
            double amount,
            String paymentStatus,
            String paymentMethod,
            String notes,
            String createdAt,
            String paidAt,
            String createdBy
    ) {
    }

    record BillingQuery(String search, String paymentStatus, String dateRange) {
    }

    record BillingMetrics(double totalRevenue, int paidInvoices, int unpaidInvoices, int cancelledInvoices) {
    }
}
