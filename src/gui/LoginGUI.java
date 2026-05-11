package gui;

import users.User;

import javax.swing.*;
import java.awt.*;

public class LoginGUI extends JFrame {

    private JTextField usernameField;
    private JPasswordField passwordField;

    public LoginGUI() {

        setTitle("Smart Patient Monitoring System");
        setSize(420, 260);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        JPanel panel = new JPanel(new GridLayout(4, 2, 10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(25, 25, 25, 25));

        panel.add(new JLabel("Username:"));
        usernameField = new JTextField();
        panel.add(usernameField);

        panel.add(new JLabel("Password:"));
        passwordField = new JPasswordField();
        panel.add(passwordField);

        JButton loginButton = new JButton("Login");
        panel.add(new JLabel());
        panel.add(loginButton);

        add(panel);

        loginButton.addActionListener(e -> login());
    }

    private void login() {

        String username = usernameField.getText().trim();
        String password = new String(passwordField.getPassword());

        java.util.ArrayList<User> usersList =
                database.UserStorage.loadUsers();

        for (User user : usersList) {

            if (user.getUsername().equals(username)
                    && user.login(password)) {

                users.Session.setCurrentUser(user);

                DashboardGUI dashboard =
                        new DashboardGUI(user);

                dashboard.setVisible(true);
                dispose();
                return;
            }
        }

        JOptionPane.showMessageDialog(
                this,
                "Invalid Username or Password"
        );
    }
}