package gui;

import ai.AIAdviceEngine;
import services.RolePermissionService;
import users.Session;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;

public class AIAdviceGUI extends JFrame {

    public AIAdviceGUI() {
        setTitle("AI Advice Notes");
        setSize(1050, 620);
        setLocationRelativeTo(null);
        NavigationManager.configureChildWindow(this);

        if (!RolePermissionService.canViewAIAdvice(Session.getCurrentUser())) {
            JOptionPane.showMessageDialog(this, "You are not authorized to view AI advice notes.");
            dispose();
            return;
        }

        JPanel main = UITheme.appPanel(new BorderLayout(14, 14));
        main.setBorder(new EmptyBorder(22, 22, 22, 22));

        JLabel title = UITheme.title("AI Advice Notes", 26);
        JLabel disclaimer = new JLabel("Rule-based educational support only. Authorized staff must review before any clinical decision.");
        disclaimer.setFont(UITheme.font(Font.PLAIN, 14));
        disclaimer.setForeground(UITheme.MUTED);

        JPanel titleBox = new JPanel();
        titleBox.setOpaque(false);
        titleBox.setLayout(new BoxLayout(titleBox, BoxLayout.Y_AXIS));
        titleBox.add(title);
        titleBox.add(Box.createVerticalStrut(6));
        titleBox.add(disclaimer);

        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        top.add(titleBox, BorderLayout.WEST);
        top.add(NavigationManager.homeButton(this), BorderLayout.EAST);

        DefaultTableModel model = new DefaultTableModel(new String[]{"Patient ID", "Date/Time", "Source", "Advice"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        for (String[] row : AIAdviceEngine.loadAllAdviceRows()) {
            model.addRow(new Object[]{row[0], row[1], row[2], row[3]});
        }

        JTable table = new JTable(model);
        UITheme.styleTable(table);

        main.add(top, BorderLayout.NORTH);
        main.add(new JScrollPane(table), BorderLayout.CENTER);

        add(main);
    }
}
