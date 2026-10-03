package org.totipo.desktop.ui;

import org.totipo.desktop.clipboard.TotpClipboard;

import org.totipo.VaultState;
import org.totipo.TokenAlternative;

/** Vault presentation boundary; all calls are on the EDT. */
public interface VaultView {
    default void changeVaultAction(Runnable action) { }
    default void copyAction(TotpClipboard.Copy action) { }
    default void passwordAction(Runnable action) { }
    default void editPassword(PasswordChangePanel panel) { }
    default void retirePassword() { }
    default void retirementMessage(String message) { }
    @FunctionalInterface
    interface EditAction { void open(VaultState base, TokenAlternative alternative, String explanation); }
    @FunctionalInterface
    interface MergeAction { void open(VaultState base, org.totipo.TokenState token); }
    default void mergeAction(MergeAction action) { }
    default void editMerge(MergeEditorPanel editor) { }
    default void additionalConflict(Runnable review, Runnable publish, Runnable cancel) { }
    default void confirmOriginalResolution(Runnable confirmed) { }
    default void mergePublicationUncertain(boolean original, boolean busy, Runnable retry, Runnable stop) {
        publicationUncertain(false, busy, retry, stop);
    }
    default void tokenActions(Runnable create, EditAction edit) { }
    default void writeAvailability(boolean available) { }
    default void editToken(TokenEditorPanel editor, boolean create) { }
    default void retireEditor() { }
    default void publicationUncertain(boolean create, boolean busy, Runnable retry, Runnable stop) { }
    default void clearUncertainty() { }
    default void abandonedPublication(boolean abandoned) { }
    default void writeMessage(String message) { }
    /** Classified operation warning; detail remains available to nonvisual views. */
    default void writeWarning(String detail, String userMessage) { writeMessage(detail); }
    void actions(Runnable refresh, Runnable close);
    void render(VaultState state);
    void closing();
    void failure();
    void showWindow();
    void dispose();
}
