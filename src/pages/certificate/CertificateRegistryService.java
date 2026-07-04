package pages.certificate;

import pages.deceased.SqliteDeceasedRecordDao;
import pages.newborn.SqliteNewbornRecordDao;

import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class CertificateRegistryService {

    private static final Path DEATH_CERTIFICATE_DIR = Path.of("data", "generated", "death-certificates").toAbsolutePath().normalize();
    private static final Path BIRTH_CERTIFICATE_DIR = Path.of("data", "generated", "birth-certificates").toAbsolutePath().normalize();

    private final SqliteDeceasedRecordDao deceasedRecordDao;
    private final SqliteNewbornRecordDao newbornRecordDao;

    public CertificateRegistryService() {
        this(new SqliteDeceasedRecordDao(), new SqliteNewbornRecordDao());
    }

    public CertificateRegistryService(SqliteDeceasedRecordDao deceasedRecordDao, SqliteNewbornRecordDao newbornRecordDao) {
        this.deceasedRecordDao = deceasedRecordDao;
        this.newbornRecordDao = newbornRecordDao;
    }

    public List<CertificateRow> findCertificates(CertificateFilter filter) throws SQLException {
        CertificateFilter safeFilter = filter == null ? new CertificateFilter() : filter;
        ArrayList<CertificateRow> rows = new ArrayList<>();
        if (includeDeath(safeFilter)) {
            SqliteDeceasedRecordDao.RecordFilter deathFilter = new SqliteDeceasedRecordDao.RecordFilter();
            deathFilter.setSearch(safeFilter.getSearch());
            deathFilter.setDateRange(safeFilter.getDateRange());
            deathFilter.setSection(safeFilter.getSection());
            for (SqliteDeceasedRecordDao.DeathRecord record : deceasedRecordDao.findRecords(deathFilter)) {
                rows.add(fromDeath(record));
            }
        }
        if (includeBirth(safeFilter)) {
            SqliteNewbornRecordDao.RecordFilter birthFilter = new SqliteNewbornRecordDao.RecordFilter();
            birthFilter.setSearch(safeFilter.getSearch());
            birthFilter.setDateRange(safeFilter.getDateRange());
            birthFilter.setSection(safeFilter.getSection());
            for (SqliteNewbornRecordDao.NewbornRecord record : newbornRecordDao.findRecords(birthFilter)) {
                rows.add(fromBirth(record));
            }
        }
        rows.removeIf(row -> !matchesStatus(row, safeFilter.getStatus()));
        rows.removeIf(row -> !matchesReviewStatus(row, safeFilter.getReviewStatus()));
        rows.sort(Comparator.comparing(CertificateRow::getEventDateTime, Comparator.nullsLast(String::compareTo)).reversed()
                .thenComparing(CertificateRow::getSourceRecordId, Comparator.reverseOrder()));
        return rows;
    }

    public RegistrySummary loadSummary() throws SQLException {
        int totalBirth = newbornRecordDao.count();
        int totalDeath = deceasedRecordDao.count();
        int generatedBirth = newbornRecordDao.countCertificatesGenerated();
        int generatedDeath = deceasedRecordDao.countCertificatesGenerated();
        int pendingBirth = newbornRecordDao.countPendingCertificates();
        int pendingDeath = deceasedRecordDao.countPendingCertificates();
        return new RegistrySummary(totalBirth + totalDeath, generatedBirth + generatedDeath,
                pendingBirth + pendingDeath, totalBirth, totalDeath);
    }

    public CertificateRow getCertificate(String type, long sourceRecordId) throws SQLException {
        if ("DEATH".equalsIgnoreCase(type)) {
            return deceasedRecordDao.findById(sourceRecordId).map(this::fromDeath)
                    .orElseThrow(() -> new IllegalArgumentException("Death record not found in SQLite: " + sourceRecordId));
        }
        if ("BIRTH".equalsIgnoreCase(type)) {
            return newbornRecordDao.findById(sourceRecordId).map(this::fromBirth)
                    .orElseThrow(() -> new IllegalArgumentException("Newborn record not found in SQLite: " + sourceRecordId));
        }
        throw new IllegalArgumentException("Certificate type must be BIRTH or DEATH.");
    }

    public String summaryText(CertificateRow row) {
        return "Certificate type: " + row.getCertificateType()
                + "\nSource record ID: " + row.getSourceRecordId()
                + "\nIdentifier: " + row.getSubjectId()
                + "\nPerson: " + row.getPersonName()
                + "\nEvent time: " + row.getEventDateTime()
                + "\nStatus: " + row.getCertificateStatus()
                + "\nReview status: " + row.getReviewStatus()
                + "\nReviewed by: " + row.getReviewedBy()
                + "\nReviewed at: " + row.getReviewedAt()
                + "\nRejection reason: " + row.getRejectionReason()
                + "\nSection/Room: " + row.getSection() + " / " + row.getRoom()
                + "\nCertificate path: " + row.getSafeCertificatePath();
    }

    private CertificateRow fromDeath(SqliteDeceasedRecordDao.DeathRecord record) {
        return new CertificateRow(
                "DEATH",
                record.getId(),
                record.getPatientId(),
                record.getPatientName(),
                record.getDeathTime(),
                certificateStatus(record.getCertificatePath()),
                safePath(record.getCertificatePath(), DEATH_CERTIFICATE_DIR),
                record.getUpdatedAt(),
                record.getSection(),
                "",
                record.getReviewStatus(),
                record.getReviewedBy(),
                record.getReviewedAt(),
                record.getRejectionReason()
        );
    }

    private CertificateRow fromBirth(SqliteNewbornRecordDao.NewbornRecord record) {
        return new CertificateRow(
                "BIRTH",
                record.getId(),
                record.getNewbornId(),
                record.getBabyName(),
                record.getBirthTime(),
                certificateStatus(record.getCertificatePath()),
                safePath(record.getCertificatePath(), BIRTH_CERTIFICATE_DIR),
                record.getUpdatedAt(),
                record.getSection(),
                record.getRoom(),
                record.getReviewStatus(),
                record.getReviewedBy(),
                record.getReviewedAt(),
                record.getRejectionReason()
        );
    }

    private boolean includeDeath(CertificateFilter filter) {
        return filter.getCertificateType().equalsIgnoreCase("All") || filter.getCertificateType().equalsIgnoreCase("DEATH");
    }

    private boolean includeBirth(CertificateFilter filter) {
        return filter.getCertificateType().equalsIgnoreCase("All") || filter.getCertificateType().equalsIgnoreCase("BIRTH");
    }

    private boolean matchesStatus(CertificateRow row, String status) {
        return status == null || status.isBlank() || "All".equalsIgnoreCase(status)
                || row.getCertificateStatus().equalsIgnoreCase(status);
    }

    private boolean matchesReviewStatus(CertificateRow row, String reviewStatus) {
        return reviewStatus == null || reviewStatus.isBlank() || "All".equalsIgnoreCase(reviewStatus)
                || row.getReviewStatus().equalsIgnoreCase(reviewStatus);
    }

    private String certificateStatus(String path) {
        return path == null || path.isBlank() ? "PENDING" : "GENERATED";
    }

    private String safePath(String path, Path allowedBase) {
        if (path == null || path.isBlank()) {
            return "";
        }
        try {
            Path candidate = Path.of(path).toAbsolutePath().normalize();
            return candidate.startsWith(allowedBase) ? candidate.toString() : "Unsafe path hidden";
        } catch (Exception e) {
            return "Unsafe path hidden";
        }
    }

    public static class CertificateFilter {
        private String certificateType = "All";
        private String status = "All";
        private String dateRange = "All";
        private String reviewStatus = "All";
        private String section = "All";
        private String search = "";

        public String getCertificateType() { return certificateType; }
        public void setCertificateType(String certificateType) { this.certificateType = normalize(certificateType, "All"); }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = normalize(status, "All"); }
        public String getDateRange() { return dateRange; }
        public void setDateRange(String dateRange) { this.dateRange = normalize(dateRange, "All"); }
        public String getReviewStatus() { return reviewStatus; }
        public void setReviewStatus(String reviewStatus) { this.reviewStatus = normalize(reviewStatus, "All"); }
        public String getSection() { return section; }
        public void setSection(String section) { this.section = normalize(section, "All"); }
        public String getSearch() { return search; }
        public void setSearch(String search) { this.search = search == null ? "" : search.trim(); }

        private String normalize(String value, String fallback) {
            return value == null || value.isBlank() ? fallback : value.trim();
        }
    }

    public static class CertificateRow {
        private final String certificateType;
        private final long sourceRecordId;
        private final String subjectId;
        private final String personName;
        private final String eventDateTime;
        private final String certificateStatus;
        private final String safeCertificatePath;
        private final String generatedOrUpdatedAt;
        private final String section;
        private final String room;
        private final String reviewStatus;
        private final String reviewedBy;
        private final String reviewedAt;
        private final String rejectionReason;

        public CertificateRow(String certificateType, long sourceRecordId, String subjectId, String personName,
                              String eventDateTime, String certificateStatus, String safeCertificatePath,
                              String generatedOrUpdatedAt, String section, String room, String reviewStatus,
                              String reviewedBy, String reviewedAt, String rejectionReason) {
            this.certificateType = certificateType;
            this.sourceRecordId = sourceRecordId;
            this.subjectId = subjectId == null ? "" : subjectId;
            this.personName = personName == null || personName.isBlank() ? "Unknown" : personName;
            this.eventDateTime = eventDateTime == null ? "" : eventDateTime;
            this.certificateStatus = certificateStatus == null ? "PENDING" : certificateStatus.toUpperCase(Locale.ROOT);
            this.safeCertificatePath = safeCertificatePath == null ? "" : safeCertificatePath;
            this.generatedOrUpdatedAt = generatedOrUpdatedAt == null ? "" : generatedOrUpdatedAt;
            this.section = section == null || section.isBlank() ? "-" : section;
            this.room = room == null || room.isBlank() ? "-" : room;
            this.reviewStatus = reviewStatus == null || reviewStatus.isBlank() ? "DRAFT" : reviewStatus.toUpperCase(Locale.ROOT);
            this.reviewedBy = reviewedBy == null || reviewedBy.isBlank() ? "-" : reviewedBy;
            this.reviewedAt = reviewedAt == null || reviewedAt.isBlank() ? "-" : reviewedAt;
            this.rejectionReason = rejectionReason == null || rejectionReason.isBlank() ? "-" : rejectionReason;
        }

        public String getCertificateType() { return certificateType; }
        public long getSourceRecordId() { return sourceRecordId; }
        public String getSubjectId() { return subjectId; }
        public String getPersonName() { return personName; }
        public String getEventDateTime() { return eventDateTime; }
        public String getCertificateStatus() { return certificateStatus; }
        public String getSafeCertificatePath() { return safeCertificatePath; }
        public String getGeneratedOrUpdatedAt() { return generatedOrUpdatedAt; }
        public String getSection() { return section; }
        public String getRoom() { return room; }
        public String getReviewStatus() { return reviewStatus; }
        public String getReviewedBy() { return reviewedBy; }
        public String getReviewedAt() { return reviewedAt; }
        public String getRejectionReason() { return rejectionReason; }
        public String getSourceType() { return "DEATH".equals(certificateType) ? "DEATH_CERTIFICATE" : "BIRTH_CERTIFICATE"; }
    }

    public static class RegistrySummary {
        private final int totalCertificates;
        private final int generatedCertificates;
        private final int pendingCertificates;
        private final int birthCertificates;
        private final int deathCertificates;

        public RegistrySummary(int totalCertificates, int generatedCertificates, int pendingCertificates,
                               int birthCertificates, int deathCertificates) {
            this.totalCertificates = totalCertificates;
            this.generatedCertificates = generatedCertificates;
            this.pendingCertificates = pendingCertificates;
            this.birthCertificates = birthCertificates;
            this.deathCertificates = deathCertificates;
        }

        public int getTotalCertificates() { return totalCertificates; }
        public int getGeneratedCertificates() { return generatedCertificates; }
        public int getPendingCertificates() { return pendingCertificates; }
        public int getBirthCertificates() { return birthCertificates; }
        public int getDeathCertificates() { return deathCertificates; }
    }
}
