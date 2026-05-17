package gui;

import database.UserStorage;
import logs.AuditLog;
import services.RoomService;
import users.User;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.ArrayList;

public class UserManagementGUI extends JFrame {

    private JTable usersTable;
    private DefaultTableModel tableModel;

    public UserManagementGUI() {

        setTitle("User Management");
        WindowSizing.apply(this, 1050, 650, 900, 560);
        NavigationManager.configureChildWindow(this);

        JPanel main = UITheme.appPanel(new BorderLayout(14, 14));
        main.setBorder(new EmptyBorder(22, 22, 22, 22));
        main.add(new AppHeader("User Management"), BorderLayout.NORTH);

        String[] columns = {"Username", "Password", "Role", "Section"};

        tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        usersTable = new JTable(tableModel);
        StyledTable.apply(usersTable);
        StyledTable.setPreferredWidths(usersTable, 180, 180, 220, 180);

        JScrollPane scrollPane = StyledTable.scrollPane(usersTable);

        JButton addButton = StyledButton.primary("Add User");
        JButton editButton = StyledButton.warning("Edit User");
        JButton deleteButton = StyledButton.danger("Delete User");
        JButton refreshButton = StyledButton.secondary("Refresh");

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        buttons.setOpaque(false);
        buttons.add(addButton);
        buttons.add(editButton);
        buttons.add(deleteButton);
        buttons.add(refreshButton);

        main.add(scrollPane, BorderLayout.CENTER);
        main.add(buttons, BorderLayout.SOUTH);

        add(main);

        addButton.addActionListener(e -> addUser());
        editButton.addActionListener(e -> editUser());
        deleteButton.addActionListener(e -> deleteUser());
        refreshButton.addActionListener(e -> loadUsers());

        loadUsers();
    }

    private void loadUsers() {
        tableModel.setRowCount(0);

        ArrayList<User> users = UserStorage.loadUsers();

        for (User user : users) {
            tableModel.addRow(new Object[]{
                    user.getUsername(),
                    user.getPassword(),
                    user.getRole(),
                    user.getSection()
            });
        }
    }

    private void addUser() {
        JTextField usernameField = new JTextField();
        JTextField passwordField = new JTextField();

        JComboBox<String> roleBox = roleBox();
        JComboBox<String> sectionBox = sectionBox();

        Object[] fields = {
                "Username:", usernameField,
                "Password:", passwordField,
                "Role:", roleBox,
                "Section:", sectionBox
        };

        int result = JOptionPane.showConfirmDialog(
                this,
                fields,
                "Add User",
                JOptionPane.OK_CANCEL_OPTION
        );

        if (result == JOptionPane.OK_OPTION) {

            if (usernameField.getText().trim().isEmpty()
                    || passwordField.getText().trim().isEmpty()) {

                JOptionPane.showMessageDialog(
                        this,
                        "Username and password cannot be empty"
                );

                return;
            }

            UserStorage.addUser(
                    usernameField.getText().trim(),
                    passwordField.getText().trim(),
                    roleBox.getSelectedItem().toString(),
                    sectionBox.getSelectedItem().toString()
            );

            AuditLog.addLog(users.Session.getUsername(), "Added user: " + usernameField.getText().trim());
            loadUsers();
        }
    }

    private void editUser() {
        int selectedRow = usersTable.getSelectedRow();

        if (selectedRow == -1) {
            JOptionPane.showMessageDialog(this, "Please select a user first");
            return;
        }

        String oldUsername =
                tableModel.getValueAt(selectedRow, 0).toString();

        JTextField usernameField =
                new JTextField(tableModel.getValueAt(selectedRow, 0).toString());

        JTextField passwordField =
                new JTextField(tableModel.getValueAt(selectedRow, 1).toString());

        JComboBox<String> roleBox = roleBox();

        roleBox.setSelectedItem(
                tableModel.getValueAt(selectedRow, 2).toString()
        );

        JComboBox<String> sectionBox = sectionBox();
        sectionBox.setSelectedItem(
                tableModel.getValueAt(selectedRow, 3).toString()
        );

        Object[] fields = {
                "Username:", usernameField,
                "Password:", passwordField,
                "Role:", roleBox,
                "Section:", sectionBox
        };

        int result = JOptionPane.showConfirmDialog(
                this,
                fields,
                "Edit User",
                JOptionPane.OK_CANCEL_OPTION
        );

        if (result == JOptionPane.OK_OPTION) {

            if (usernameField.getText().trim().isEmpty()
                    || passwordField.getText().trim().isEmpty()) {

                JOptionPane.showMessageDialog(
                        this,
                        "Username and password cannot be empty"
                );

                return;
            }

            UserStorage.updateUser(
                    oldUsername,
                    usernameField.getText().trim(),
                    passwordField.getText().trim(),
                    roleBox.getSelectedItem().toString(),
                    sectionBox.getSelectedItem().toString()
            );

            AuditLog.addLog(users.Session.getUsername(), "Edited user: " + oldUsername + " -> " + usernameField.getText().trim());
            loadUsers();
        }
    }

    private void deleteUser() {
        int selectedRow = usersTable.getSelectedRow();

        if (selectedRow == -1) {
            JOptionPane.showMessageDialog(this, "Please select a user first");
            return;
        }

        String username =
                tableModel.getValueAt(selectedRow, 0).toString();

        int confirm = JOptionPane.showConfirmDialog(
                this,
                "Delete user: " + username + "?",
                "Confirm Delete",
                JOptionPane.YES_NO_OPTION
        );

        if (confirm == JOptionPane.YES_OPTION) {
            UserStorage.deleteUser(username);
            AuditLog.addLog(users.Session.getUsername(), "Deleted user: " + username);
            loadUsers();
        }
    }

    private JComboBox<String> roleBox() {
        return new JComboBox<>(new String[]{
                "System Admin",
                "Hospital Director",
                "Chief Medical Officer",
                "Chief of Surgery",
                "Chief Nursing Officer",
                "Department Head",
                "Doctor",
                "Nurse",
                "Technician",
                "Receptionist",
                "Admin"
        });
    }

    private JComboBox<String> sectionBox() {
        String[] sections = RoomService.getSections();
        String[] values = new String[sections.length + 1];
        values[0] = "All";
        System.arraycopy(sections, 0, values, 1, sections.length);
        return new JComboBox<>(values);
    }
}
