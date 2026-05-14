package gui;

import database.VitalStorage;
import models.Patient;
import models.VitalRecord;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;

public class VitalsHistoryGUI extends JFrame {

    public VitalsHistoryGUI(Patient patient) {
        setTitle("Vitals History - " + patient.getName());
        setSize(1100, 600);
        setLocationRelativeTo(null);
        NavigationManager.configureChildWindow(this);

        JPanel main = UITheme.appPanel(new BorderLayout(14, 14));
        main.setBorder(new EmptyBorder(22, 22, 22, 22));

        JLabel title = UITheme.title("Vitals History - " + patient.getName(), 26);
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.add(title, BorderLayout.WEST);
        header.add(NavigationManager.homeButton(this), BorderLayout.EAST);

        DefaultTableModel model = new DefaultTableModel(
                new String[]{"Date/Time", "Vital", "Value", "Unit", "Source", "Staff", "Device ID", "Serial", "Device Type"}, 0
        ) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        for (VitalRecord record : VitalStorage.getRecordsForPatient(patient.getPatientId())) {
            model.addRow(new Object[]{
                    record.getDateTime(),
                    record.getVitalType(),
                    record.getValue(),
                    record.getUnit(),
                    record.getSourceType(),
                    record.getStaffName(),
                    record.getDeviceId(),
                    record.getDeviceSerial(),
                    record.getDeviceType()
            });
        }

        JTable table = new JTable(model);
        UITheme.styleTable(table);

        main.add(header, BorderLayout.NORTH);
        main.add(new JScrollPane(table), BorderLayout.CENTER);

        add(main);
    }
}
