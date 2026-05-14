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
        setSize(950, 600);
        setLocationRelativeTo(null);
        NavigationManager.configureChildWindow(this);

        JPanel main = new JPanel(new BorderLayout());
        main.setBackground(UITheme.BACKGROUND);
        main.setBorder(new EmptyBorder(20, 20, 20, 20));

        JLabel title = new JLabel("User Management", SwingConstants.CENTER);
        title.setFont(UITheme.font(Font.BOLD, 30));
        title.setForeground(UITheme.TEXT);

        String[] columns = {"Username", "Password", "Role", "Section"};

        tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        usersTable = new JTable(tableModel);
        UITheme.styleTable(usersTable);

        JScrollPane scrollPane = new JScrollPane(usersTable);

        JButton addButton = UITheme.button("Add User", UITheme.PRIMARY);
        JButton editButton = UITheme.button("Edit User", UITheme.WARNING);
        JButton deleteButton = UITheme.button("Delete User", UITheme.DANGER);
        JButton refreshButton = UITheme.secondaryButton("Refresh");
        JButton homeButton = NavigationManager.homeButton(this);

        JPanel buttons = new JPanel();
        buttons.add(addButton);
        buttons.add(editButton);
        buttons.add(deleteButton);
        buttons.add(refreshButton);
        buttons.add(homeButton);

        main.add(title, BorderLayout.NORTH);
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
