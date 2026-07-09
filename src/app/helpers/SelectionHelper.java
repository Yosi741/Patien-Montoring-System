package app.helpers;

import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ListView;
import javafx.scene.control.TableView;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public final class SelectionHelper {


    public static void safeClearSelection(TableView<?> table) {
        if (table != null && table.getSelectionModel() != null) {
            table.getSelectionModel().clearSelection();
        }
        if (table != null && table.getFocusModel() != null) {
            table.getFocusModel().focus(-1);
        }
    }


    public static void safeClearSelection(ListView<?> list) {
        if (list != null && list.getSelectionModel() != null) {
            list.getSelectionModel().clearSelection();
        }
        if (list != null && list.getFocusModel() != null) {
            list.getFocusModel().focus(-1);
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

    public static void runWhenTableStable(TableView<?> table, Runnable action) {
        if (action == null) {
            return;
        }
        if (table != null && table.isPressed()) {
            Platform.runLater(() -> runWhenTableStable(table, action));
            return;
        }
        action.run();
    }

    public static void runWhenTablesStable(Runnable action, TableView<?>... tables) {
        if (action == null) {
            return;
        }
        if (tables != null) {
            for (TableView<?> table : tables) {
                if (table != null && table.isPressed()) {
                    Platform.runLater(() -> runWhenTablesStable(action, tables));
                    return;
                }
            }
        }
        action.run();
    }

    public static <T> void safeReplaceItems(TableView<T> table, ObservableList<T> backingList,
                                            Collection<? extends T> newItems) {
        safeClearSelection(table);
        if (backingList != null) {
            backingList.setAll(newItems == null ? List.of() : newItems);
        }
        if (table != null && backingList != null && table.getItems() != backingList) {
            table.setItems(backingList);
        }
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
