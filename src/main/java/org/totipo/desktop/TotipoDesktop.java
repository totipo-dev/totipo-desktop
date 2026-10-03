package org.totipo.desktop;

import javax.swing.SwingUtilities;

/** Swing application entry point. */
public final class TotipoDesktop {
    private TotipoDesktop() { }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            org.totipo.desktop.ui.ApplicationFonts.install();
            new DesktopApplication().show();
        });
    }
}
