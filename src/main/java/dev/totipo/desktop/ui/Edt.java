package dev.totipo.desktop.ui;

import javax.swing.SwingUtilities;

final class Edt {
    private Edt() { }

    static void require() {
        if (!SwingUtilities.isEventDispatchThread()) {
            throw new IllegalStateException("Swing UI must be accessed on the EDT");
        }
    }
}
