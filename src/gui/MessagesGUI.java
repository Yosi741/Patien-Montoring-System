package gui;

import database.MessageStorage;
import database.RoomStorage;
import database.UserStorage;
import logs.AuditLog;
import models.InternalMessage;
import users.Session;
import users.User;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;

public class MessagesGUI extends JFrame {
    private DefaultTableModel model;
    private Runnable onSend;

    public MessagesGUI(Runnable onSend) {
        this.onSend = onSend;
        setTitle("Internal Messages");
        setSize(1050, 650);
        setLocationRelativeTo(null);
        NavigationManager.configureChildWindow(this);

        JPanel main = UITheme.appPanel(new BorderLayout(14, 14));
        main.setBorder(new EmptyBorder(22, 22, 22, 22));
        main.add(new HospitalHeaderPanel("Internal Messages"), BorderLayout.NORTH);

        model = new DefaultTableModel(new String[]{"From", "Target", "Subject", "Message", "Time"}, 0) {
            @Override public boolean isCellEditable(int row, int column) { return false; }
        };
        JTable table = new JTable(model);
        UITheme.styleTable(table);

        JButton compose = UITheme.button("Compose Message", UITheme.PRIMARY);
        JButton refresh = UITheme.secondaryButton("Refresh");
        compose.addActionListener(e -> compose());
        refresh.addActionListener(e -> load());

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.setOpaque(false);
        buttons.add(refresh);
        buttons.add(compose);

        main.add(new JScrollPane(table), BorderLayout.CENTER);
        main.add(buttons, BorderLayout.SOUTH);
        add(main);
        load();
    }

    private void load() {
        model.setRowCount(0);
        for (InternalMessage m : MessageStorage.inboxFor(Session.getCurrentUser())) {
            model.addRow(new Object[]{m.getSender(), m.getTargetType() + ": " + m.getTarget(), m.getSubject(), m.getBody(), m.getTimestamp()});
        }
    }

    private void compose() {
        JComboBox<String> targetType = new JComboBox<>(new String[]{"User", "Section", "All"});
        JComboBox<String> target = new JComboBox<>();
        Runnable loadTargets = () -> {
            target.removeAllItems();
            if (targetType.getSelectedItem().toString().equals("User")) {
                for (User u : UserStorage.loadUsers()) target.addItem(u.getUsername());
            } else if (targetType.getSelectedItem().toString().equals("Section")) {
                for (String section : RoomStorage.loadSections()) target.addItem(section);
            } else {
                target.addItem("ALL");
            }
        };
        targetType.addActionListener(e -> loadTargets.run());
        loadTargets.run();
        JTextField subject = new JTextField();
        JTextArea body = new JTextArea(5, 25);
        Object[] fields = {"Send to:", targetType, "Target:", target, "Subject:", subject, "Message:", new JScrollPane(body)};
        int result = JOptionPane.showConfirmDialog(this, fields, "Compose Message", JOptionPane.OK_CANCEL_OPTION);
        if (result != JOptionPane.OK_OPTION) return;

        if (subject.getText().trim().isEmpty() || body.getText().trim().isEmpty()) {
            JOptionPane.showMessageDialog(this, "Subject and message cannot be empty.");
            return;
        }
        MessageStorage.sendMessage(MessageStorage.create(Session.getUsername(), targetType.getSelectedItem().toString(), target.getSelectedItem().toString(), subject.getText().trim(), body.getText().trim()));
        AuditLog.addLog(Session.getUsername(), "Sent internal message: " + subject.getText().trim());
        JOptionPane.showMessageDialog(this, "Message sent.");
        if (onSend != null) onSend.run();
        load();
    }
}
