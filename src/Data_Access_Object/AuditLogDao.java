package Data_Access_Object;

import java.sql.SQLException;

public interface AuditLogDao {
    void log(String username, String action) throws SQLException;
}
