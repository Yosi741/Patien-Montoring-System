package gui;

import javax.swing.*;
import java.awt.*;

public class WindowSizing {

    private WindowSizing() {
    }

    public static void apply(JFrame frame, int width, int height, int minWidth, int minHeight) {
        frame.setSize(width, height);
        frame.setMinimumSize(new Dimension(minWidth, minHeight));
        frame.setResizable(true);
        frame.setLocationRelativeTo(null);
    }
}
