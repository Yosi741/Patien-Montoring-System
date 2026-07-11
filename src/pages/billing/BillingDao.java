package pages.billing;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public interface BillingDao {

    BillingRecord createInvoice(BillingWriteRecord record) throws SQLException;

    List<BillingRecord> findAll(BillingQuery query) throws SQLException;

    Optional<BillingRecord> findById(long invoiceId) throws SQLException;

    boolean markPaid(long invoiceId, String paymentMethod, String paidAt) throws SQLException;

    boolean cancelInvoice(long invoiceId) throws SQLException;

    boolean deleteInvoice(long invoiceId) throws SQLException;

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
