package app.database;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Creates SQLite connections to the local ClinicPulse database.
 * All SQLite DAO and schema code obtains connections through this class.
 */
public class DatabaseManager {

    private static final String DATABASE_DIR = "data";
    private static final String DATABASE_FILE = DATABASE_DIR + "/smart_patient_monitoring.db";
    private static final String JDBC_URL = "jdbc:sqlite:" + DATABASE_FILE;


    /**
     * Returns connection used by the application workflow.
     */
    public static Connection getConnection() throws SQLException {
        ensureDatabaseDirectory();
        Connection connection = DriverManager.getConnection(JDBC_URL);
        configure(connection);
        return connection;
    }

    public static String getDatabasePath() {
        return DATABASE_FILE;
    }

    /**
     * Tests connection and reports whether it is available.
     */
    public static boolean testConnection() {
        try (Connection connection = getConnection()) {
            return connection.isValid(2);
        } catch (SQLException e) {
            System.out.println("SQLite connection test failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * Ensures database directory exists before continuing.
     */
    private static void ensureDatabaseDirectory() {
        File directory = new File(DATABASE_DIR);
        if (!directory.exists()) {
            directory.mkdirs();
        }
    }

    /**
     * Configures configure.
     */
    private static void configure(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
            statement.execute("PRAGMA journal_mode = WAL");
            statement.execute("PRAGMA busy_timeout = 5000");
        }
    }
}
