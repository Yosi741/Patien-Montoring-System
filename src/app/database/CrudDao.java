package app.database;

import java.sql.SQLException;
import java.util.Optional;

/**
 * Defines the minimal generic persistence operations used by ClinicPulse DAO interfaces.
 */
public interface CrudDao<T, ID> {
    /**
     * Finds by ID in SQLite.
     */
    Optional<T> findById(ID id) throws SQLException;
    /**
     * Validates and saves save.
     */
    void save(T entity) throws SQLException;
}
