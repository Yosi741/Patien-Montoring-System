package gui;

import database.NewbornStorage;
import database.NewbornMeasurementStorage;
import models.NewbornRecord;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.Duration;
import java.util.ArrayList;

public class NewbornManagementGUI extends JFrame {

    private DefaultTableModel model;
    private JTable table;
    private ArrayList<NewbornRecord> visible = new ArrayList<>();
    private JComboBox<String> filter;
    private JLabel statsLabel;

    public NewbornManagementGUI() {
        setTitle("Newborn / Babies");
        setSize(1200, 720);
        setLocationRelativeTo(null);
        NavigationManager.configureChildWindow(this);

        JPanel main = UITheme.appPanel(new BorderLayout(14, 14));
        main.setBorder(new EmptyBorder(22, 22, 22, 22));

        JLabel title = UITheme.title("Newborn / Babies", 26);
        statsLabel = new JLabel();
        statsLabel.setFont(UITheme.font(Font.BOLD, 15));
        statsLabel.setForeground(UITheme.MUTED);
        filter = new JComboBox<>(new String[]{"All", "Premature only", "Currently admitted", "Discharged"});
        filter.addActionListener(e -> loadNewborns());

        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        top.add(title, BorderLayout.WEST);
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        right.setOpaque(false);
        right.add(statsLabel);
        right.add(filter);
        right.add(NavigationManager.homeButton(this));
        top.add(right, BorderLayout.EAST);

        model = new DefaultTableModel(new String[]{"Baby ID", "Baby Name", "Mother", "Birth Time", "Age", "Weight", "Room/Section", "Status", "Premature", "Vitals", "Admitted"}, 0) {
            @Override public boolean isCellEditable(int row, int column) { return false; }
        };
        table = new JTable(model);
        UITheme.styleTable(table);

        JButton details = UITheme.button("Open Details", UITheme.PRIMARY);
        JButton addWeight = UITheme.button("Add Weight", UITheme.SUCCESS);
        JButton openCertificate = UITheme.secondaryButton("Open Certificate");
        JButton refresh = UITheme.secondaryButton("Refresh");
        details.addActionListener(e -> openDetails());
        addWeight.addActionListener(e -> addWeightMeasurement());
        openCertificate.addActionListener(e -> openCertificate());
        refresh.addActionListener(e -> loadNewborns());

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        buttons.setOpaque(false);
        buttons.add(details);
        buttons.add(addWeight);
        buttons.add(openCertificate);
        buttons.add(refresh);

        JPanel northStack = new JPanel(new BorderLayout());
        northStack.setOpaque(false);
        northStack.add(new HospitalHeaderPanel("Newborn / Babies"), BorderLayout.NORTH);
        northStack.add(top, BorderLayout.SOUTH);
        main.add(northStack, BorderLayout.NORTH);
        main.add(new JScrollPane(table), BorderLayout.CENTER);
        main.add(buttons, BorderLayout.SOUTH);
        add(main);
        loadNewborns();
    }

    private void loadNewborns() {
        model.setRowCount(0);
        visible.clear();
        int admitted = 0;
        int premature = 0;
        for (NewbornRecord n : NewbornStorage.loadNewborns()) {
            if (n.getHospitalStatus().equalsIgnoreCase("Admitted")) admitted++;
            if (n.isPremature() && n.getHospitalStatus().equalsIgnoreCase("Admitted")) premature++;
            if (!matchesFilter(n)) continue;
            visible.add(n);
            model.addRow(new Object[]{
                    n.getBabyId(), n.getBabyName(), n.getMotherFirstName() + " " + n.getMotherLastName() + " / " + n.getMotherId(),
                    n.getBirthDateTime(), ageText(n.getBirthDateTime()), n.getBirthWeightKg() + " kg", n.getSection() + " " + n.getRoom(),
                    n.getHospitalStatus(), n.isPremature() ? "Yes" : "No", n.getVitals(),
                    n.getHospitalStatus().equalsIgnoreCase("Admitted") ? "Yes" : "No"
            });
        }
        statsLabel.setText("Admitted: " + admitted + " | Premature admitted: " + premature);
    }

    private boolean matchesFilter(NewbornRecord n) {
        String selected = filter.getSelectedItem().toString();
        if (selected.equals("Premature only")) return n.isPremature();
        if (selected.equals("Currently admitted")) return n.getHospitalStatus().equalsIgnoreCase("Admitted");
        if (selected.equals("Discharged")) return n.getHospitalStatus().equalsIgnoreCase("Discharged");
        return true;
    }

    private NewbornRecord selected() {
        int row = table.getSelectedRow();
        if (row == -1) {
            JOptionPane.showMessageDialog(this, "Please select a newborn first.");
            return null;
        }
        return visible.get(table.convertRowIndexToModel(row));
    }

    private void openDetails() {
        NewbornRecord n = selected();
        if (n != null) new NewbornDetailGUI(n).setVisible(true);
    }

    private void openCertificate() {
        NewbornRecord n = selected();
        if (n == null || n.getCertificatePath().isBlank()) {
            JOptionPane.showMessageDialog(this, "No certificate PDF found.");
            return;
        }
        try {
            Desktop.getDesktop().open(new File(n.getCertificatePath()));
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Could not open PDF. Saved at:\n" + n.getCertificatePath());
        }
    }

    private void addWeightMeasurement() {
        NewbornRecord n = selected();
        if (n == null) return;
        String weight = JOptionPane.showInputDialog(this, "New weight in kg:");
        if (weight == null) return;
        try {
            double parsed = Double.parseDouble(weight.trim());
            if (parsed < 0.4 || parsed > 10.0) {
                JOptionPane.showMessageDialog(this, "Weight must be realistic, between 0.4kg and 10kg.");
                return;
            }
            NewbornMeasurementStorage.addMeasurement(n.getBabyId(), "Weight", parsed + " kg", "Weight tracking update");
            JOptionPane.showMessageDialog(this, "Weight measurement saved.");
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Weight must be a number.");
        }
    }

    private String ageText(String birthDateTime) {
        try {
            LocalDateTime birth = LocalDateTime.parse(birthDateTime, DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss"));
            Duration duration = Duration.between(birth, LocalDateTime.now());
            long hours = duration.toHours();
            if (hours < 48) return hours + " hours";
            return duration.toDays() + " days";
        } catch (Exception e) {
            return "Unknown";
        }
    }
}
