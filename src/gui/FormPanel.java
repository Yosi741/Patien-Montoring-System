package gui;

import javax.swing.*;
import java.awt.*;

public class FormPanel extends JPanel {

    private int row = 0;

    public FormPanel() {
        super(new GridBagLayout());
        setOpaque(false);
    }

    public void addRow(String labelText, JComponent field) {
        JLabel label = new JLabel(labelText);
        label.setFont(UITheme.font(Font.BOLD, 14));
        label.setForeground(UITheme.TEXT);

        GridBagConstraints labelConstraints = baseConstraints();
        labelConstraints.gridx = 0;
        labelConstraints.gridy = row;
        labelConstraints.weightx = 0;
        labelConstraints.fill = GridBagConstraints.NONE;
        labelConstraints.anchor = GridBagConstraints.WEST;
        add(label, labelConstraints);

        GridBagConstraints fieldConstraints = baseConstraints();
        fieldConstraints.gridx = 1;
        fieldConstraints.gridy = row;
        fieldConstraints.weightx = 1;
        fieldConstraints.fill = GridBagConstraints.HORIZONTAL;
        add(field, fieldConstraints);
        row++;
    }

    public void addReadOnlyRow(String labelText, String value) {
        JLabel valueLabel = new JLabel(value);
        valueLabel.setFont(UITheme.font(Font.PLAIN, 15));
        valueLabel.setForeground(UITheme.TEXT);
        addRow(labelText, valueLabel);
    }

    public void addWideRow(String labelText, JComponent component, int preferredHeight) {
        JLabel label = new JLabel(labelText);
        label.setFont(UITheme.font(Font.BOLD, 14));
        label.setForeground(UITheme.TEXT);

        GridBagConstraints labelConstraints = baseConstraints();
        labelConstraints.gridx = 0;
        labelConstraints.gridy = row;
        labelConstraints.weightx = 0;
        labelConstraints.anchor = GridBagConstraints.NORTHWEST;
        add(label, labelConstraints);

        GridBagConstraints componentConstraints = baseConstraints();
        componentConstraints.gridx = 1;
        componentConstraints.gridy = row;
        componentConstraints.weightx = 1;
        componentConstraints.weighty = 1;
        componentConstraints.fill = GridBagConstraints.BOTH;
        component.setPreferredSize(new Dimension(240, preferredHeight));
        add(component, componentConstraints);
        row++;
    }

    public void addFullWidth(JComponent component) {
        GridBagConstraints constraints = baseConstraints();
        constraints.gridx = 0;
        constraints.gridy = row;
        constraints.gridwidth = 2;
        constraints.weightx = 1;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        add(component, constraints);
        row++;
    }

    private GridBagConstraints baseConstraints() {
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.insets = new Insets(7, 8, 7, 8);
        constraints.anchor = GridBagConstraints.WEST;
        return constraints;
    }
}
