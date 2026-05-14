package gui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.JTableHeader;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class UITheme {

    public static final Color BACKGROUND = new Color(243, 247, 251);
    public static final Color SURFACE = Color.WHITE;
    public static final Color PRIMARY = new Color(30, 95, 150);
    public static final Color PRIMARY_DARK = new Color(20, 70, 115);
    public static final Color SUCCESS = new Color(36, 140, 92);
    public static final Color WARNING = new Color(210, 145, 28);
    public static final Color DANGER = new Color(190, 55, 55);
    public static final Color TEXT = new Color(24, 38, 58);
    public static final Color MUTED = new Color(102, 116, 130);
    public static final Color LINE = new Color(222, 230, 238);

    private UITheme() {
    }

    public static Font font(int style, int size) {
        return new Font("Segoe UI", style, size);
    }

    public static JPanel appPanel(LayoutManager layout) {
        JPanel panel = new JPanel(layout);
        panel.setBackground(BACKGROUND);
        return panel;
    }

    public static JPanel cardPanel() {
        JPanel panel = new JPanel();
        panel.setBackground(SURFACE);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(LINE),
                new EmptyBorder(22, 22, 22, 22)
        ));
        return panel;
    }

    public static JLabel title(String text, int size) {
        JLabel label = new JLabel(text);
        label.setFont(font(Font.BOLD, size));
        label.setForeground(TEXT);
        return label;
    }

    public static JButton button(String text, Color color) {
        JButton button = new JButton(text);
        button.setFocusPainted(false);
        button.setBorderPainted(false);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setBackground(color);
        button.setForeground(Color.WHITE);
        button.setFont(font(Font.BOLD, 14));
        button.setBorder(new EmptyBorder(11, 18, 11, 18));
        button.setOpaque(true);

        Color hover = color.darker();
        button.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                if (button.isEnabled()) {
                    button.setBackground(hover);
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                button.setBackground(color);
            }
        });

        return button;
    }

    public static JButton secondaryButton(String text) {
        JButton button = button(text, new Color(89, 105, 124));
        return button;
    }

    public static void styleTable(JTable table) {
        table.setRowHeight(42);
        table.setFont(font(Font.PLAIN, 15));
        table.setGridColor(new Color(232, 238, 244));
        table.setSelectionBackground(new Color(219, 236, 250));
        table.setSelectionForeground(TEXT);
        table.setShowVerticalLines(false);

        JTableHeader header = table.getTableHeader();
        header.setFont(font(Font.BOLD, 15));
        header.setBackground(PRIMARY);
        header.setForeground(Color.WHITE);
        header.setPreferredSize(new Dimension(header.getPreferredSize().width, 42));
    }

    public static void styleTextField(JTextField field) {
        field.setFont(font(Font.PLAIN, 15));
        field.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(LINE),
                new EmptyBorder(8, 10, 8, 10)
        ));
    }
}
