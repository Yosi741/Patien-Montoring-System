package app.database;

import java.sql.SQLException;
import java.util.Optional;

public interface CrudDao<T, ID> {
    Optional<T> findById(ID id) throws SQLException;
    void save(T entity) throws SQLException;
}
