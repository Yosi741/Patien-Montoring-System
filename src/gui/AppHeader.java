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

public class AppHeader extends JPanel {

    private final JLabel timeLabel = new JLabel();
    private final JButton notificationButton;
    private final Timer clockTimer;

    public AppHeader(String pageTitle) {
        setLayout(new GridBagLayout());
        setBackground(UITheme.SURFACE);
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, UITheme.LINE),
                new EmptyBorder(16, 18, 16, 18)
        ));

        User user = Session.getCurrentUser();
        UserProfile profile = user == null
                ? new UserProfile("Unknown", "Unknown", "", "", "")
                : UserProfileStorage.getProfile(user.getUsername());

        JPanel identity = createIdentityPanel(pageTitle, user, profile);

        notificationButton = StyledButton.compactSecondary("Notifications");
        refreshNotificationCount();
        notificationButton.addActionListener(e -> new NotificationCenterGUI(this::refreshNotificationCount).setVisible(true));

        JButton messagesButton = StyledButton.compactSecondary("Messages");
        messagesButton.addActionListener(e -> new MessagesGUI(this::refreshNotificationCount).setVisible(true));

        JButton profileButton = StyledButton.compactSecondary("Profile");
        profileButton.addActionListener(e -> new UserProfileGUI().setVisible(true));

        JButton homeButton = StyledButton.compactSecondary("Home");
        homeButton.addActionListener(e -> NavigationManager.returnHome(SwingUtilities.getWindowAncestor(this)));

        JPanel actions = new JPanel(new GridBagLayout());
        actions.setOpaque(false);

        timeLabel.setFont(UITheme.font(Font.PLAIN, 13));
        timeLabel.setForeground(UITheme.MUTED);

        addActionButton(actions, notificationButton, 0);
        addActionButton(actions, messagesButton, 1);
        addActionButton(actions, profileButton, 2);
        addActionButton(actions, homeButton, 3);

        GridBagConstraints identityConstraints = new GridBagConstraints();
        identityConstraints.gridx = 0;
        identityConstraints.gridy = 0;
        identityConstraints.weightx = 1;
        identityConstraints.fill = GridBagConstraints.HORIZONTAL;
        identityConstraints.anchor = GridBagConstraints.WEST;
        identityConstraints.insets = new Insets(0, 0, 0, 18);
        add(identity, identityConstraints);

        GridBagConstraints timeConstraints = new GridBagConstraints();
        timeConstraints.gridx = 1;
        timeConstraints.gridy = 0;
        timeConstraints.anchor = GridBagConstraints.NORTHEAST;
        add(timeLabel, timeConstraints);

        GridBagConstraints actionConstraints = new GridBagConstraints();
        actionConstraints.gridx = 0;
        actionConstraints.gridy = 1;
        actionConstraints.gridwidth = 2;
        actionConstraints.anchor = GridBagConstraints.EAST;
        actionConstraints.insets = new Insets(12, 0, 0, 0);
        add(actions, actionConstraints);

        updateTime();
        clockTimer = new Timer(1000, e -> updateTime());
        clockTimer.start();
    }

    public void refreshNotificationCount() {
        int count = NotificationStorage.unreadCount(Session.getUsername());
        notificationButton.setText("Notifications (" + count + ")");
    }

    @Override
    public void removeNotify() {
        clockTimer.stop();
        super.removeNotify();
    }

    private JPanel createIdentityPanel(String pageTitle, User user, UserProfile profile) {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setOpaque(false);

        JLabel avatar = avatar(profile);
        GridBagConstraints avatarConstraints = new GridBagConstraints();
        avatarConstraints.gridx = 0;
        avatarConstraints.gridy = 0;
        avatarConstraints.gridheight = 2;
        avatarConstraints.insets = new Insets(0, 0, 0, 14);
        avatarConstraints.anchor = GridBagConstraints.NORTHWEST;
        panel.add(avatar, avatarConstraints);

        JLabel title = UITheme.title(pageTitle, 23);
        GridBagConstraints titleConstraints = new GridBagConstraints();
        titleConstraints.gridx = 1;
        titleConstraints.gridy = 0;
        titleConstraints.weightx = 1;
        titleConstraints.fill = GridBagConstraints.HORIZONTAL;
        titleConstraints.anchor = GridBagConstraints.WEST;
        panel.add(title, titleConstraints);

        String username = user == null ? "Unknown" : user.getUsername();
        String role = user == null ? "Unknown" : user.getRole();
        String section = user == null ? "Unknown" : user.getSection();
        String displayName = profile.getDisplayName() == null || profile.getDisplayName().isBlank()
                ? username
                : profile.getDisplayName();

        JLabel subtitle = new JLabel("Welcome, " + displayName + "   |   " + role + "   |   Section: " + section);
        subtitle.setFont(UITheme.font(Font.PLAIN, 14));
        subtitle.setForeground(UITheme.MUTED);
        GridBagConstraints subtitleConstraints = new GridBagConstraints();
        subtitleConstraints.gridx = 1;
        subtitleConstraints.gridy = 1;
        subtitleConstraints.weightx = 1;
        subtitleConstraints.fill = GridBagConstraints.HORIZONTAL;
        subtitleConstraints.anchor = GridBagConstraints.WEST;
        subtitleConstraints.insets = new Insets(5, 0, 0, 0);
        panel.add(subtitle, subtitleConstraints);

        return panel;
    }

    private void addActionButton(JPanel actions, JButton button, int x) {
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = x;
        constraints.gridy = 0;
        constraints.insets = new Insets(0, x == 0 ? 0 : 8, 0, 0);
        actions.add(button, constraints);
    }

    private void updateTime() {
        timeLabel.setText(LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss")));
    }

    private JLabel avatar(UserProfile profile) {
        String displayName = profile.getDisplayName() == null ? "" : profile.getDisplayName();
        JLabel label = new JLabel(displayName.isBlank() ? "U" : displayName.substring(0, 1).toUpperCase(), SwingConstants.CENTER);
        label.setPreferredSize(new Dimension(52, 52));
        label.setMinimumSize(new Dimension(52, 52));
        label.setOpaque(true);
        label.setBackground(new Color(219, 236, 250));
        label.setForeground(UITheme.PRIMARY_DARK);
        label.setFont(UITheme.font(Font.BOLD, 20));
        String photoPath = profile.getPhotoPath();
        if (photoPath != null && !photoPath.isBlank() && new File(photoPath).exists()) {
            ImageIcon icon = new ImageIcon(photoPath);
            Image scaled = icon.getImage().getScaledInstance(52, 52, Image.SCALE_SMOOTH);
            label.setText("");
            label.setIcon(new ImageIcon(scaled));
        }
        return label;
    }
}
