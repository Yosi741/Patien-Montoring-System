import database.DatabaseManager;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

public class DemoDatabaseVerify {
    public static void main(String[] args) throws Exception {
        try (Connection connection = DatabaseManager.getConnection();
             Statement statement = connection.createStatement()) {
            printCount(statement, "patients");
            printCount(statement, "newborn_records");
            printCount(statement, "deceased_records");
            printCount(statement, "rooms");
            printCount(statement, "sections");
            printCount(statement, "medication_catalog");
            printCount(statement, "medication_interactions");
            printCount(statement, "reminders");
            printCount(statement, "notifications");
            printCount(statement, "medical_files");
            query(statement, "Patients",
                    "SELECT patient_id, first_name || ' ' || last_name AS name, section, room, status, priority FROM patients ORDER BY patient_id");
            query(statement, "Newborns",
                    "SELECT newborn_id, mother_patient_id, baby_name, certificate_path FROM newborn_records");
            query(statement, "Deceased",
                    "SELECT patient_id, certificate_path FROM deceased_records");
            query(statement, "Bad markers",
                    "SELECT patient_id FROM patients WHERE patient_id LIKE '%S5D_%' OR patient_id LIKE '%PHASE%' OR patient_id LIKE '%SLICE%' "
                            + "OR section = 'QA' OR room = 'QA' OR first_name LIKE '%aaaa%' OR last_name LIKE '%ddddd%'");
        }
    }

    private static void printCount(Statement statement, String table) throws Exception {
        try (ResultSet resultSet = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
            if (resultSet.next()) {
                System.out.println(table + "=" + resultSet.getInt(1));
            }
        }
    }

    private static void query(Statement statement, String label, String sql) throws Exception {
        System.out.println("-- " + label + " --");
        try (ResultSet resultSet = statement.executeQuery(sql)) {
            int columns = resultSet.getMetaData().getColumnCount();
            int rows = 0;
            while (resultSet.next()) {
                rows++;
                StringBuilder line = new StringBuilder();
                for (int i = 1; i <= columns; i++) {
                    if (i > 1) {
                        line.append(" | ");
                    }
                    line.append(resultSet.getString(i));
                }
                System.out.println(line);
            }
            if (rows == 0) {
                System.out.println("(none)");
            }
        }
    }
}
