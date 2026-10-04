package org.example;
import org.example.ui.MazeFrame;
import javax.swing.*;
public final class Main {
    private Main() { }
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); }
            catch (Exception ignored) { }
            new MazeFrame().setVisible(true);
        });
    }
}
