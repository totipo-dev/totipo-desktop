package dev.totipo.desktop;

import dev.totipo.desktop.ui.LauncherFrame;
import javax.swing.SwingUtilities;

/** Entry point for the M0 desktop shell. */
public final class TotipoDesktop {
    private TotipoDesktop() { }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new LauncherFrame().setVisible(true));
    }
}
