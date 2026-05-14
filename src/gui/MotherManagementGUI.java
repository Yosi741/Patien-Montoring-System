package gui;

import database.MotherStorage;
import logs.AuditLog;
import models.MotherInfo;
import users.Session;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;

public class MotherManagementGUI extends JFrame {

    private DefaultTableModel model;
    private JTextField searchField;

    public MotherManagementGUI() {
        setTitle("Birth / Mother Management");
        setSize(900, 620);
        setLocationRelativeTo(null);
        NavigationManager.configureChildWindow(this);

        JPanel main = UITheme.appPanel(new BorderLayout(14, 14));
        main.setBorder(new EmptyBorder(22, 22, 22, 22));

        JLabel title = UITheme.title("Birth / Mother Management", 26);
        searchField = new JTextField();
        UITheme.styleTextField(searchField);
        JButton searchButton = UITheme.secondaryButton("Search Mother ID");
        searchButton.addActionListener(e -> loadMothers(searchField.getText().trim()));

        JPanel header = new JPanel(new BorderLayout(10, 10));
        header.setOpaque(false);
        header.add(title, BorderLayout.WEST);
        JPanel search = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        search.setOpaque(false);
        search.add(searchField);
        search.add(searchButton);
        search.add(NavigationManager.homeButton(this));
        header.add(search, BorderLayout.EAST);

        model = new DefaultTableModel(new String[]{"Mother ID", "First Name", "Last Name", "Contact", "Notes"}, 0) {
            @Override public boolean isCellEditable(int row, int column) { return false; }
        };
        JTable table = new JTable(model);
        UITheme.styleTable(table);

        JButton addButton = UITheme.button("Add Mother", UITheme.PRIMARY);
        JButton refreshButton = UITheme.secondaryButton("Refresh");
        addButton.addActionListener(e -> addMother());
        refreshButton.addActionListener(e -> loadMothers(""));

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        buttons.setOpaque(false);
        buttons.add(addButton);
        buttons.add(refreshButton);

        main.add(header, BorderLayout.NORTH);
        main.add(new JScrollPane(table), BorderLayout.CENTER);
        main.add(buttons, BorderLayout.SOUTH);
        add(main);
        loadMothers("");
    }

    private void loadMothers(String filterId) {
        model.setRowCount(0);
        for (MotherInfo mother : MotherStorage.loadMothers()) {
            if (filterId.isBlank() || mother.getMotherId().contains(filterId)) {
                model.addRow(new Object[]{mother.getMotherId(), mother.getFirstName(), mother.getLastName(), mother.getContactInfo(), mother.getNotes()});
            }
        }
    }

    private void addMother() {
        JTextField id = new JTextField();
        JTextField first = new JTextField();
        JTextField last = new JTextField();
        JTextField contact = new JTextField();
        JTextField notes = new JTextField();
        Object[] fields = {"Mother ID:", id, "First Name:", first, "Last Name:", last, "Contact Info:", contact, "Notes:", notes};
        int result = JOptionPane.showConfirmDialog(this, fields, "Add Mother", JOptionPane.OK_CANCEL_OPTION);
        if (result != JOptionPane.OK_OPTION) return;

        String error = MotherStorage.addMother(new MotherInfo(id.getText().trim(), first.getText().trim(), last.getText().trim(), contact.getText().trim(), notes.getText().trim()));
        if (error != null) {
            JOptionPane.showMessageDialog(this, error);
            return;
        }
        AuditLog.addLog(Session.getUsername(), "Added mother record: " + id.getText().trim());
        JOptionPane.showMessageDialog(this, "Mother information saved.");
        loadMothers("");
    }
}
