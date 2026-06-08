package ui.javafx.helpers;

import javafx.scene.control.ListView;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TableView;

import java.util.Objects;
import java.util.function.Function;

public final class SelectionHelper {

    private SelectionHelper() {
    }

    public static void safeClearSelection(TableView<?> table) {
        if (table != null && table.getSelectionModel() != null) {
            table.getSelectionModel().clearSelection();
        }
        if (table != null && table.getFocusModel() != null) {
            table.getFocusModel().focus(-1);
        }
    }

    public static void safeClearTableSelection(TableView<?> table) {
        safeClearSelection(table);
    }

    public static void safeClearSelection(ListView<?> list) {
        if (list != null && list.getSelectionModel() != null) {
            list.getSelectionModel().clearSelection();
        }
        if (list != null && list.getFocusModel() != null) {
            list.getFocusModel().focus(-1);
        }
    }

    public static void safeClearSelection(ComboBox<?> comboBox) {
        if (comboBox != null && comboBox.getSelectionModel() != null) {
            comboBox.getSelectionModel().clearSelection();
            comboBox.setValue(null);
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

    public static void safeSelectFirst(ComboBox<?> comboBox) {
        if (comboBox != null && comboBox.getSelectionModel() != null
                && comboBox.getItems() != null && !comboBox.getItems().isEmpty()) {
            comboBox.getSelectionModel().select(0);
        } else {
            safeClearSelection(comboBox);
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

    public static <T> boolean safeRestoreSelectionById(TableView<T> table, Object oldId, Function<T, ?> idExtractor) {
        if (table == null || table.getItems() == null || oldId == null || idExtractor == null) {
            return false;
        }
        for (int i = 0; i < table.getItems().size(); i++) {
            T item = table.getItems().get(i);
            if (Objects.equals(oldId, idExtractor.apply(item))) {
                return safeSelectIndex(table, i);
            }
        }
        return false;
    }
}
