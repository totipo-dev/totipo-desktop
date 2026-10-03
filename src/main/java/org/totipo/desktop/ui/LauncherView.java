package org.totipo.desktop.ui;

import java.nio.file.Path;

/** Launcher presentation boundary; all calls are on the EDT. */
public interface LauncherView {
    void actions(Runnable open, Runnable create, Runnable close);
    Path chooseDirectory(Path initialLocation, boolean create);
    /** Open offers Submit/Exit/Change Vault; Create offers Submit/Cancel (including mismatch). */
    PasswordPromptResult password(Path directory, boolean create, PasswordPromptContext context);
    /** Explicit creation decision; cancellation/closing must return false. */
    boolean confirmEmptyPassword();
    void busy(String status, boolean busy);
    void message(String title, String text);
    void showWindow();
    default void hideWindow() { }
    void dispose();
}
