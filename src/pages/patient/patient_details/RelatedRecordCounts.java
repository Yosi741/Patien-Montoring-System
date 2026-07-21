package pages.patient.patient_details;

/**
 * Counts patient-linked records that must be reviewed before deleting a patient file.
 */
public record RelatedRecordCounts(
        int patientVisits,
        int vitalReadings,
        int appointments,
        int medicalFiles,
        int billingRecords,
        int alerts,
        int notifications,
        int messages
) {
    /**
     * Returns true when at least one related record exists.
     */
    public boolean hasAny() {
        return totalCount() > 0;
    }

    /**
     * Returns the total number of related records that prevent patient deletion.
     */
    public int totalCount() {
        return patientVisits + vitalReadings + appointments + medicalFiles
                + billingRecords + alerts + notifications + messages;
    }
}



