package gui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.HashSet;
import java.util.Set;

public class NavigationManager {

    private static JFrame dashboard;
    private static final Set<Window> childWindows = new HashSet<>();

    private NavigationManager() {
    }

    public static void registerDashboard(JFrame dashboardFrame) {
        dashboard = dashboardFrame;
    }

    public static void configureChildWindow(JFrame window) {
        window.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        childWindows.add(window);
        window.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                childWindows.remove(window);
            }
        });
    }

    public static void configureDashboardReturnOnClose(JFrame window) {
        window.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        childWindows.add(window);
        window.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                showDashboard();
            }

            @Override
            public void windowClosed(WindowEvent e) {
                childWindows.remove(window);
                showDashboard();
            }
        });
    }

    public static JButton homeButton(Window currentWindow) {
        JButton button = UITheme.secondaryButton("Home");
        button.addActionListener(e -> returnHome(currentWindow));
        return button;
    }

    public static void returnHome(Window currentWindow) {
        for (Window window : new HashSet<>(childWindows)) {
            if (window != dashboard && window.isDisplayable()) {
                window.dispose();
            }
        }

        if (currentWindow != null && currentWindow != dashboard && currentWindow.isDisplayable()) {
            currentWindow.dispose();
        }

        showDashboard();
    }

    public static void showDashboard() {
        if (dashboard != null) {
            dashboard.setVisible(true);
            dashboard.toFront();
            dashboard.requestFocus();
        }
    }
}
