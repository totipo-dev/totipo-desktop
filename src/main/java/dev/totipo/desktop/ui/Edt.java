package dev.totipo.desktop.ui;

import javax.swing.SwingUtilities;

public final class Edt {
    private Edt() { }

    public static void require() {
        if (!SwingUtilities.isEventDispatchThread()) {
            throw new IllegalStateException("Swing UI must be accessed on the EDT");
        }
    }
}
