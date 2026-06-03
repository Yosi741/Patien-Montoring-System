package ui.javafx.helpers;

import javafx.scene.control.ListView;
import javafx.scene.control.TableView;

public final class SelectionHelper {

    private SelectionHelper() {
    }

    public static void safeClearSelection(TableView<?> table) {
        if (table != null && table.getSelectionModel() != null) {
            table.getSelectionModel().clearSelection();
        }
    }

    public static void safeClearSelection(ListView<?> list) {
        if (list != null && list.getSelectionModel() != null) {
            list.getSelectionModel().clearSelection();
        }
    }

    public static void safeSelectFirst(TableView<?> table) {
        if (table != null && table.getSelectionModel() != null
                && table.getItems() != null && !table.getItems().isEmpty()) {
            table.getSelectionModel().select(0);
            table.scrollTo(0);
        }
    }

    public static void safeSelectFirst(ListView<?> list) {
        if (list != null && list.getSelectionModel() != null
                && list.getItems() != null && !list.getItems().isEmpty()) {
            list.getSelectionModel().select(0);
            list.scrollTo(0);
        }
    }

    public static boolean safeSelectIndex(TableView<?> table, int index) {
        if (table == null || table.getSelectionModel() == null || table.getItems() == null
                || index < 0 || index >= table.getItems().size()) {
            return false;
        }
        table.getSelectionModel().select(index);
        table.scrollTo(index);
        return true;
    }

    public static boolean safeSelectIndex(ListView<?> list, int index) {
        if (list == null || list.getSelectionModel() == null || list.getItems() == null
                || index < 0 || index >= list.getItems().size()) {
            return false;
        }
        list.getSelectionModel().select(index);
        list.scrollTo(index);
        return true;
    }
}
