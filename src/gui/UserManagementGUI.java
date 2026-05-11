package gui;

import database.UserStorage;
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

        JPanel main = new JPanel(new BorderLayout());
        main.setBackground(new Color(243, 247, 251));
        main.setBorder(new EmptyBorder(20, 20, 20, 20));

        JLabel title = new JLabel("User Management", SwingConstants.CENTER);
        title.setFont(new Font("Segoe UI", Font.BOLD, 30));
        title.setForeground(new Color(20, 45, 80));

        String[] columns = {"Username", "Password", "Role"};

        tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        usersTable = new JTable(tableModel);
        usersTable.setRowHeight(35);

        JScrollPane scrollPane = new JScrollPane(usersTable);

        JButton addButton = new JButton("Add User");
        JButton editButton = new JButton("Edit User");
        JButton deleteButton = new JButton("Delete User");
        JButton refreshButton = new JButton("Refresh");

        JPanel buttons = new JPanel();
        buttons.add(addButton);
        buttons.add(editButton);
        buttons.add(deleteButton);
        buttons.add(refreshButton);

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
                    user.getRole()
            });
        }
    }

    private void addUser() {
        JTextField usernameField = new JTextField();
        JTextField passwordField = new JTextField();

        JComboBox<String> roleBox =
                new JComboBox<>(new String[]{"Admin", "Doctor", "Nurse"});

        Object[] fields = {
                "Username:", usernameField,
                "Password:", passwordField,
                "Role:", roleBox
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
                    roleBox.getSelectedItem().toString()
            );

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

        JComboBox<String> roleBox =
                new JComboBox<>(new String[]{"Admin", "Doctor", "Nurse"});

        roleBox.setSelectedItem(
                tableModel.getValueAt(selectedRow, 2).toString()
        );

        Object[] fields = {
                "Username:", usernameField,
                "Password:", passwordField,
                "Role:", roleBox
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
                    roleBox.getSelectedItem().toString()
            );

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
            loadUsers();
        }
    }
}