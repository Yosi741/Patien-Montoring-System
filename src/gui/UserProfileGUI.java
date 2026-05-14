package gui;

import database.UserProfileStorage;
import logs.AuditLog;
import models.UserProfile;
import users.Session;
import users.User;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

public class UserProfileGUI extends JFrame {
    private JTextField photoPath;

    public UserProfileGUI() {
        setTitle("User Profile");
        setSize(620, 460);
        setLocationRelativeTo(null);
        NavigationManager.configureChildWindow(this);

        User user = Session.getCurrentUser();
        UserProfile profile = UserProfileStorage.getProfile(user.getUsername());

        JPanel main = UITheme.appPanel(new BorderLayout(14, 14));
        main.setBorder(new EmptyBorder(22, 22, 22, 22));
        main.add(new HospitalHeaderPanel("User Profile"), BorderLayout.NORTH);

        JPanel form = UITheme.cardPanel();
        form.setLayout(new GridLayout(7, 2, 12, 12));
        JTextField name = field(profile.getDisplayName());
        JTextField phone = field(profile.getPhone());
        JTextField email = field(profile.getEmail());
        photoPath = field(profile.getPhotoPath());
        photoPath.setEditable(false);

        form.add(new JLabel("Username:")); form.add(new JLabel(user.getUsername()));
        form.add(new JLabel("Role:")); form.add(new JLabel(user.getRole()));
        form.add(new JLabel("Section:")); form.add(new JLabel(user.getSection()));
        form.add(new JLabel("Name:")); form.add(name);
        form.add(new JLabel("Phone:")); form.add(phone);
        form.add(new JLabel("Email:")); form.add(email);
        JButton photo = UITheme.secondaryButton("Choose Photo");
        photo.addActionListener(e -> choosePhoto());
        form.add(photo); form.add(photoPath);

        JButton save = UITheme.button("Save Profile", UITheme.PRIMARY);
        JButton support = UITheme.secondaryButton("Contact Support / Feedback");
        save.addActionListener(e -> {
            UserProfileStorage.saveProfile(new UserProfile(user.getUsername(), name.getText().trim(), phone.getText().trim(), email.getText().trim(), photoPath.getText().trim()));
            AuditLog.addLog(Session.getUsername(), "Updated user profile");
            JOptionPane.showMessageDialog(this, "Profile saved.");
        });
        support.addActionListener(e -> JOptionPane.showMessageDialog(this, "Support contact: hospital-it-support@example.com\nFeedback: Send a message to System Admin from Messages."));

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        bottom.setOpaque(false);
        bottom.add(support);
        bottom.add(save);
        main.add(form, BorderLayout.CENTER);
        main.add(bottom, BorderLayout.SOUTH);
        add(main);
    }

    private JTextField field(String value) {
        JTextField field = new JTextField(value);
        UITheme.styleTextField(field);
        return field;
    }

    private void choosePhoto() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Images (png, jpg, jpeg)", "png", "jpg", "jpeg"));
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) photoPath.setText(chooser.getSelectedFile().getAbsolutePath());
    }
}
