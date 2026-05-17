package gui;

import javax.swing.*;
import java.awt.*;

public class StyledButton {

    private static final Dimension DEFAULT_SIZE = new Dimension(168, 42);
    private static final Dimension COMPACT_SIZE = new Dimension(140, 38);

    private StyledButton() {
    }

    public static JButton primary(String text) {
        return fixed(UITheme.button(text, UITheme.PRIMARY), DEFAULT_SIZE);
    }

    public static JButton success(String text) {
        return fixed(UITheme.button(text, UITheme.SUCCESS), DEFAULT_SIZE);
    }

    public static JButton warning(String text) {
        return fixed(UITheme.button(text, UITheme.WARNING), DEFAULT_SIZE);
    }

    public static JButton danger(String text) {
        return fixed(UITheme.button(text, UITheme.DANGER), DEFAULT_SIZE);
    }

    public static JButton accent(String text, Color color) {
        return fixed(UITheme.button(text, color), DEFAULT_SIZE);
    }

    public static JButton secondary(String text) {
        return fixed(UITheme.secondaryButton(text), DEFAULT_SIZE);
    }

    public static JButton compactSecondary(String text) {
        return fixed(UITheme.secondaryButton(text), COMPACT_SIZE);
    }

    public static JButton fixed(JButton button, Dimension size) {
        button.setPreferredSize(size);
        button.setMinimumSize(size);
        return button;
    }
}
