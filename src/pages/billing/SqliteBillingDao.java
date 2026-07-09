package pages.billing;

import app.DatabaseManager;
import app.SchemaInitializer;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public class SqliteBillingDao implements BillingDao {

    private static final DateTimeFormatter INVOICE_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    public SqliteBillingDao() {
        ensureSchema();
    }

    @Override
    public BillingRecord createInvoice(BillingWriteRecord record) throws SQLException {
        String sql = "INSERT INTO billing_records(invoice_no, patient_id, patient_name, service_name, visit_type, amount, "
                + "payment_status, payment_method, notes, created_at, paid_at, created_by) "
                + "VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection connection = DatabaseManager.getConnection()) {
            String invoiceNo = generateInvoiceNumber(connection);
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, invoiceNo);
                statement.setString(2, clean(record.patientId()));
                statement.setString(3, clean(record.patientName()));
                statement.setString(4, clean(record.serviceName()));
                statement.setString(5, clean(record.visitType()));
                statement.setDouble(6, record.amount());
                statement.setString(7, normalizeStatus(record.paymentStatus()));
                statement.setString(8, clean(record.paymentMethod()));
                statement.setString(9, clean(record.notes()));
                statement.setString(10, clean(record.createdAt()));
                statement.setString(11, clean(record.paidAt()));
                statement.setString(12, clean(record.createdBy()));
                statement.executeUpdate();
            }
            return findByInvoiceNumber(connection, invoiceNo)
                    .orElseThrow(() -> new SQLException("Invoice was saved but could not be reloaded."));
        }
    }

    @Override
    public List<BillingRecord> findAll(BillingQuery query) throws SQLException {
        ArrayList<BillingRecord> records = new ArrayList<>();
        QueryParts parts = buildFilterQuery(query, false);
        String sql = "SELECT id, invoice_no, patient_id, patient_name, service_name, visit_type, amount, payment_status, "
                + "payment_method, notes, created_at, paid_at, created_by "
                + "FROM billing_records "
                + parts.whereClause()
                + " ORDER BY datetime(created_at) DESC, id DESC";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bindParams(statement, parts.params());
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    records.add(mapRecord(resultSet));
                }
            }
        }
        return records;
    }

    @Override
    public Optional<BillingRecord> findById(long invoiceId) throws SQLException {
        String sql = "SELECT id, invoice_no, patient_id, patient_name, service_name, visit_type, amount, payment_status, "
                + "payment_method, notes, created_at, paid_at, created_by FROM billing_records WHERE id = ?";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, invoiceId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return Optional.of(mapRecord(resultSet));
                }
            }
        }
        return Optional.empty();
    }

    @Override
    public boolean markPaid(long invoiceId, String paymentMethod, String paidAt) throws SQLException {
        String sql = "UPDATE billing_records SET payment_status = 'PAID', payment_method = ?, paid_at = ? "
                + "WHERE id = ? AND UPPER(payment_status) <> 'CANCELLED'";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, clean(paymentMethod));
            statement.setString(2, clean(paidAt));
            statement.setLong(3, invoiceId);
            return statement.executeUpdate() > 0;
        }
    }

    @Override
    public boolean cancelInvoice(long invoiceId) throws SQLException {
        String sql = "UPDATE billing_records SET payment_status = 'CANCELLED' "
                + "WHERE id = ? AND UPPER(payment_status) <> 'CANCELLED'";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, invoiceId);
            return statement.executeUpdate() > 0;
        }
    }

    @Override
    public BillingMetrics getDashboardMetrics(BillingQuery query) throws SQLException {
        QueryParts parts = buildFilterQuery(query, true);
        String sql = "SELECT "
                + "COALESCE(SUM(CASE WHEN UPPER(payment_status) = 'PAID' THEN amount ELSE 0 END), 0) AS total_revenue, "
                + "SUM(CASE WHEN UPPER(payment_status) = 'PAID' THEN 1 ELSE 0 END) AS paid_invoices, "
                + "SUM(CASE WHEN UPPER(payment_status) = 'UNPAID' THEN 1 ELSE 0 END) AS unpaid_invoices, "
                + "SUM(CASE WHEN UPPER(payment_status) = 'CANCELLED' THEN 1 ELSE 0 END) AS cancelled_invoices "
                + "FROM billing_records "
                + parts.whereClause();
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bindParams(statement, parts.params());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return new BillingMetrics(
                            resultSet.getDouble("total_revenue"),
                            resultSet.getInt("paid_invoices"),
                            resultSet.getInt("unpaid_invoices"),
                            resultSet.getInt("cancelled_invoices")
                    );
                }
            }
        }
        return new BillingMetrics(0, 0, 0, 0);
    }

    private Optional<BillingRecord> findByInvoiceNumber(Connection connection, String invoiceNo) throws SQLException {
        String sql = "SELECT id, invoice_no, patient_id, patient_name, service_name, visit_type, amount, payment_status, "
                + "payment_method, notes, created_at, paid_at, created_by FROM billing_records WHERE invoice_no = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, invoiceNo);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return Optional.of(mapRecord(resultSet));
                }
            }
        }
        return Optional.empty();
    }

    private BillingRecord mapRecord(ResultSet resultSet) throws SQLException {
        return new BillingRecord(
                resultSet.getLong("id"),
                resultSet.getString("invoice_no"),
                resultSet.getString("patient_id"),
                resultSet.getString("patient_name"),
                resultSet.getString("service_name"),
                resultSet.getString("visit_type"),
                resultSet.getDouble("amount"),
                resultSet.getString("payment_status"),
                resultSet.getString("payment_method"),
                resultSet.getString("notes"),
                resultSet.getString("created_at"),
                resultSet.getString("paid_at"),
                resultSet.getString("created_by")
        );
    }

    private QueryParts buildFilterQuery(BillingQuery query, boolean includeAllTimeAlias) {
        String search = query == null || query.search() == null ? "" : query.search().trim();
        String status = query == null || query.paymentStatus() == null ? "All" : query.paymentStatus().trim();
        String dateRange = query == null || query.dateRange() == null ? "Last 30 days" : query.dateRange().trim();
        ArrayList<String> params = new ArrayList<>();
        StringBuilder where = new StringBuilder("WHERE 1 = 1 ");

        if (!search.isBlank()) {
            where.append("AND (invoice_no LIKE ? OR patient_id LIKE ? OR COALESCE(patient_name, '') LIKE ?) ");
            String like = "%" + search + "%";
            params.add(like);
            params.add(like);
            params.add(like);
        }
        if (!status.isBlank() && !"ALL".equalsIgnoreCase(status)) {
            where.append("AND UPPER(payment_status) = ? ");
            params.add(status.trim().toUpperCase(Locale.ROOT));
        }
        if (!"ALL TIME".equalsIgnoreCase(dateRange) && !"ALL".equalsIgnoreCase(dateRange)) {
            where.append("AND datetime(created_at) >= datetime('now', '-30 days') ");
        }

        return new QueryParts(where.toString(), params);
    }

    private void bindParams(PreparedStatement statement, List<String> params) throws SQLException {
        for (int i = 0; i < params.size(); i++) {
            statement.setString(i + 1, params.get(i));
        }
    }

    private String generateInvoiceNumber(Connection connection) throws SQLException {
        String prefix = "INV-" + LocalDate.now().format(INVOICE_DATE) + "-";
        String sql = "SELECT invoice_no FROM billing_records WHERE invoice_no LIKE ? ORDER BY invoice_no DESC LIMIT 1";
        int next = 1;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, prefix + "%");
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    next = parseSequence(resultSet.getString("invoice_no")) + 1;
                }
            }
        }
        String candidate = prefix + String.format(Locale.ROOT, "%04d", next);
        while (invoiceNumberExists(connection, candidate)) {
            next++;
            candidate = prefix + String.format(Locale.ROOT, "%04d", next);
        }
        return candidate;
    }

    private boolean invoiceNumberExists(Connection connection, String invoiceNo) throws SQLException {
        String sql = "SELECT 1 FROM billing_records WHERE invoice_no = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, invoiceNo);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    private int parseSequence(String invoiceNo) {
        if (invoiceNo == null || !invoiceNo.contains("-")) {
            return 0;
        }
        int lastDash = invoiceNo.lastIndexOf('-');
        if (lastDash < 0 || lastDash >= invoiceNo.length() - 1) {
            return 0;
        }
        try {
            return Integer.parseInt(invoiceNo.substring(lastDash + 1));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            return "UNPAID";
        }
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        if ("PAID".equals(normalized) || "CANCELLED".equals(normalized)) {
            return normalized;
        }
        return "UNPAID";
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private void ensureSchema() {
        try {
            SchemaInitializer.initialize();
        } catch (Exception e) {
            System.out.println("SQLite billing schema check failed: " + e.getMessage());
        }
    }

    private record QueryParts(String whereClause, List<String> params) {
    }
}
