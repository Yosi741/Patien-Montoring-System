package pages.alert;

import app.database.CrudDao;

/**
 * Defines persistence operations for alert records stored in the SQLite alerts table.
 */
public interface AlertDao extends CrudDao<Alert, String> {

}
