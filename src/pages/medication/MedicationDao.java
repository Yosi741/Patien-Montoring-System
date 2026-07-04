package pages.medication;

import java.sql.SQLException;

public interface MedicationDao {
    long saveMedication(String patientId, String name, String dose, String route, String frequency, boolean active) throws SQLException;

    boolean saveMedicationEvent(long medicationId, String patientId, String givenBy, String givenAt, String notes) throws SQLException;

    int countMedications() throws SQLException;

    int countMedicationEvents() throws SQLException;
}
