import gui.LoginGUI;

public class LegacySwingMain {

    public static void main(String[] args) {
        // Swing is retained temporarily while JavaFX feature parity is completed.
        // This preserves the original legacy startup path without changing Swing behavior.
        LoginGUI gui = new LoginGUI();
        gui.setVisible(true);
    }

}
