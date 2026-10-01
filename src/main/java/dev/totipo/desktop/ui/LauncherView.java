package dev.totipo.desktop.ui;

import java.nio.file.Path;

/** Launcher presentation boundary; all calls are on the EDT. */
public interface LauncherView {
    void actions(Runnable open, Runnable create, Runnable close);
    Path chooseDirectory();
    /** Returns caller-owned characters, or null on cancellation/mismatched confirmation. */
    char[] password(boolean create);
    void busy(String status, boolean busy);
    void message(String title, String text);
    void showWindow();
    void dispose();
}
