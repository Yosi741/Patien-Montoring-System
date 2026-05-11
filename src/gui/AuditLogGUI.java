package gui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;

public class AuditLogGUI extends JFrame {

    private JTextArea logArea;

    public AuditLogGUI() {

        setTitle("Audit Logs");
        setSize(900, 600);
        setLocationRelativeTo(null);

        JPanel main = new JPanel(new BorderLayout());
        main.setBackground(new Color(243, 247, 251));
        main.setBorder(new EmptyBorder(20, 20, 20, 20));

        JLabel title = new JLabel("Audit Logs", SwingConstants.CENTER);
        title.setFont(new Font("Segoe UI", Font.BOLD, 30));
        title.setForeground(new Color(20, 45, 80));

        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(new Font("Consolas", Font.PLAIN, 15));

        JScrollPane scrollPane = new JScrollPane(logArea);

        JButton refreshButton = new JButton("Refresh");
        refreshButton.addActionListener(e -> loadLogs());

        main.add(title, BorderLayout.NORTH);
        main.add(scrollPane, BorderLayout.CENTER);
        main.add(refreshButton, BorderLayout.SOUTH);

        add(main);

        loadLogs();
    }

    private void loadLogs() {
        logArea.setText("");

        try {
            File file = new File("data/audit_logs.txt");

            if (!file.exists()) {
                logArea.setText("No audit logs found.");
                return;
            }

            BufferedReader reader = new BufferedReader(new FileReader(file));
            String line;

            while ((line = reader.readLine()) != null) {
                logArea.append(line + "\n");
            }

            reader.close();

        } catch (Exception e) {
            logArea.setText("Error loading logs.");
        }
    }
}