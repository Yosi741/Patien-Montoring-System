package gui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.TableColumnModel;
import java.awt.*;

public class StyledTable {

    private StyledTable() {
    }

    public static void apply(JTable table) {
        UITheme.styleTable(table);
        table.setFillsViewportHeight(true);
        table.setIntercellSpacing(new Dimension(0, 1));
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    }

    public static JScrollPane scrollPane(JTable table) {
        apply(table);
        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UITheme.LINE),
                new EmptyBorder(0, 0, 0, 0)
        ));
        scrollPane.getViewport().setBackground(UITheme.SURFACE);
        return scrollPane;
    }

    public static void setPreferredWidths(JTable table, int... widths) {
        TableColumnModel columns = table.getColumnModel();
        for (int i = 0; i < widths.length && i < columns.getColumnCount(); i++) {
            columns.getColumn(i).setPreferredWidth(widths[i]);
        }
    }
}
