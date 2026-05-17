import database.SqliteMigrationService;

public class DatabaseMigrationMain {
    public static void main(String[] args) {
        SqliteMigrationService.MigrationResult result = new SqliteMigrationService().migrateFromTextFiles();
        if (!result.isSuccess()) {
            System.exit(1);
        }
    }
}
