package gui;

import users.User;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

public class LoginGUI extends JFrame {

    private JTextField usernameField;
    private JPasswordField passwordField;

    public LoginGUI() {

        setTitle("Smart Patient Monitoring System");
        setSize(500, 330);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        JPanel panel = UITheme.appPanel(new GridLayout(5, 2, 12, 12));
        panel.setBorder(new EmptyBorder(30, 32, 30, 32));

        JLabel title = UITheme.title("Hospital Login", 26);
        panel.add(title);
        panel.add(new JLabel());

        panel.add(new JLabel("Username:"));
        usernameField = new JTextField();
        UITheme.styleTextField(usernameField);
        panel.add(usernameField);

        panel.add(new JLabel("Password:"));
        passwordField = new JPasswordField();
        UITheme.styleTextField(passwordField);
        panel.add(passwordField);

        JButton loginButton = UITheme.button("Login", UITheme.PRIMARY);
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
