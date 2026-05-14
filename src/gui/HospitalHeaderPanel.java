package gui;

import database.NotificationStorage;
import database.UserProfileStorage;
import models.UserProfile;
import users.Session;
import users.User;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class HospitalHeaderPanel extends JPanel {

    private JLabel timeLabel;
    private JButton notificationButton;

    public HospitalHeaderPanel(String screenTitle) {
        setLayout(new BorderLayout(14, 0));
        setBackground(UITheme.SURFACE);
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, UITheme.LINE),
                new EmptyBorder(14, 18, 14, 18)
        ));

        User user = Session.getCurrentUser();
        UserProfile profile = UserProfileStorage.getProfile(user.getUsername());

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        left.setOpaque(false);
        left.add(avatar(profile));

        JPanel text = new JPanel();
        text.setOpaque(false);
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
        JLabel title = UITheme.title(screenTitle, 22);
        JLabel welcome = new JLabel("Welcome, " + profile.getDisplayName() + " | " + user.getRole() + " | " + user.getSection());
        welcome.setFont(UITheme.font(Font.PLAIN, 14));
        welcome.setForeground(UITheme.MUTED);
        text.add(title);
        text.add(welcome);
        left.add(text);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        right.setOpaque(false);
        timeLabel = new JLabel();
        timeLabel.setFont(UITheme.font(Font.PLAIN, 13));
        timeLabel.setForeground(UITheme.MUTED);
        notificationButton = UITheme.secondaryButton("Notifications");
        refreshNotificationCount();
        notificationButton.addActionListener(e -> new NotificationCenterGUI(this::refreshNotificationCount).setVisible(true));
        JButton messages = UITheme.secondaryButton("Messages");
        messages.addActionListener(e -> new MessagesGUI(this::refreshNotificationCount).setVisible(true));
        JButton profileButton = UITheme.secondaryButton("Profile");
        profileButton.addActionListener(e -> new UserProfileGUI().setVisible(true));
        JButton home = NavigationManager.homeButton(SwingUtilities.getWindowAncestor(this));

        right.add(timeLabel);
        right.add(notificationButton);
        right.add(messages);
        right.add(profileButton);
        right.add(home);

        add(left, BorderLayout.WEST);
        add(right, BorderLayout.EAST);

        updateTime();
        new Timer(1000, e -> updateTime()).start();
    }

    public void refreshNotificationCount() {
        int count = NotificationStorage.unreadCount(Session.getUsername());
        notificationButton.setText("Notifications (" + count + ")");
    }

    private void updateTime() {
        timeLabel.setText(LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss")));
    }

    private JLabel avatar(UserProfile profile) {
        JLabel label = new JLabel(profile.getDisplayName().isBlank() ? "U" : profile.getDisplayName().substring(0, 1).toUpperCase(), SwingConstants.CENTER);
        label.setPreferredSize(new Dimension(44, 44));
        label.setOpaque(true);
        label.setBackground(new Color(219, 236, 250));
        label.setForeground(UITheme.PRIMARY_DARK);
        label.setFont(UITheme.font(Font.BOLD, 18));
        if (!profile.getPhotoPath().isBlank() && new File(profile.getPhotoPath()).exists()) {
            ImageIcon icon = new ImageIcon(profile.getPhotoPath());
            Image scaled = icon.getImage().getScaledInstance(44, 44, Image.SCALE_SMOOTH);
            label.setText("");
            label.setIcon(new ImageIcon(scaled));
        }
        return label;
    }
}
