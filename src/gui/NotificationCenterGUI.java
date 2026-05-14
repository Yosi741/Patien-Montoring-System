package gui;

import database.NotificationStorage;
import models.AppNotification;
import users.Session;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;

public class NotificationCenterGUI extends JFrame {
    private DefaultTableModel model;
    private Runnable onChange;

    public NotificationCenterGUI(Runnable onChange) {
        this.onChange = onChange;
        setTitle("Notification Center");
        setSize(900, 560);
        setLocationRelativeTo(null);
        NavigationManager.configureChildWindow(this);

        JPanel main = UITheme.appPanel(new BorderLayout(14, 14));
        main.setBorder(new EmptyBorder(22, 22, 22, 22));
        main.add(new HospitalHeaderPanel("Notification Center"), BorderLayout.NORTH);

        model = new DefaultTableModel(new String[]{"Severity", "Message", "Time", "Read"}, 0) {
            @Override public boolean isCellEditable(int row, int column) { return false; }
        };
        JTable table = new JTable(model);
        UITheme.styleTable(table);
        table.setDefaultRenderer(Object.class, new NotificationRenderer());

        JButton markRead = UITheme.button("Mark All Read", UITheme.SUCCESS);
        JButton clear = UITheme.button("Clear Notifications", UITheme.DANGER);
        JButton refresh = UITheme.secondaryButton("Refresh");
        markRead.addActionListener(e -> {
            NotificationStorage.markReadForUser(Session.getUsername());
            load();
            if (onChange != null) onChange.run();
        });
        clear.addActionListener(e -> {
            NotificationStorage.clearForUser(Session.getUsername());
            load();
            if (onChange != null) onChange.run();
        });
        refresh.addActionListener(e -> load());

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.setOpaque(false);
        buttons.add(refresh);
        buttons.add(markRead);
        buttons.add(clear);

        main.add(new JScrollPane(table), BorderLayout.CENTER);
        main.add(buttons, BorderLayout.SOUTH);
        add(main);
        load();
    }

    private void load() {
        model.setRowCount(0);
        for (AppNotification n : NotificationStorage.getForUser(Session.getUsername())) {
            model.addRow(new Object[]{n.getSeverity(), n.getMessage(), n.getTimestamp(), n.isRead() ? "Yes" : "No"});
        }
    }

    private class NotificationRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            String severity = table.getValueAt(row, 0).toString();
            if (!isSelected) {
                if (severity.equals("CRITICAL")) c.setBackground(new Color(255, 226, 226));
                else if (severity.equals("WARNING")) c.setBackground(new Color(255, 246, 218));
                else c.setBackground(Color.WHITE);
                c.setForeground(UITheme.TEXT);
            }
            return c;
        }
    }
}
