package dev.totipo.desktop.ui;

import dev.totipo.VaultState;

/** Vault presentation boundary; all calls are on the EDT. */
public interface VaultView {
    void actions(Runnable refresh, Runnable close);
    void render(VaultState state);
    void closing();
    void failure();
    void showWindow();
    void dispose();
}
