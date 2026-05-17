package gui;

import javax.swing.*;
import java.awt.*;

public class CardPanel {

    private CardPanel() {
    }

    public static JPanel create() {
        return UITheme.cardPanel();
    }

    public static JPanel create(LayoutManager layout) {
        JPanel panel = UITheme.cardPanel();
        panel.setLayout(layout);
        return panel;
    }
}
