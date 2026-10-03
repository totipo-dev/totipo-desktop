package org.totipo.desktop.ui;

import org.totipo.desktop.clipboard.TotpClipboard;

import org.totipo.VaultState;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.nio.file.Path;
import javax.swing.JFrame;
import javax.swing.JOptionPane;

/** Visual shell for one controller-owned session. */
public final class VaultFrame extends JFrame implements VaultView {
    private static final long serialVersionUID = 1L;
    private final VaultPanel panel = new VaultPanel();
    private javax.swing.JDialog editor;
    private PasswordChangeDialog passwordDialog;
    private transient Runnable quit;

    public VaultFrame(Path directory) {
        super("Totipo — " + directory);
        Edt.require();
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setContentPane(panel);
        setJMenuBar(panel.menuBar());
        setMinimumSize(panel.getMinimumSize());
        setPreferredSize(new java.awt.Dimension(1000, 700));
        SwingUsability.fit(this, 1000, 700);
        setLocationByPlatform(true);
    }

    @Override public void actions(Runnable refresh, Runnable close) {
        Edt.require();
        panel.onRefresh(refresh);
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent event) {
                if (quit != null) { quit.run(); } else { close.run(); }
            }
        });
    }
    @Override public void quitAction(Runnable action) {
        Edt.require(); quit = action; panel.exitAction(action);
    }
    @Override public void changeVaultAction(Runnable action) { panel.changeVaultAction(action); }
    @Override public void copyAction(TotpClipboard.Copy action) { panel.copyAction(action); }
    @Override public void passwordAction(Runnable action) { panel.passwordAction(action); }
    @Override public void editPassword(PasswordChangePanel content) {
        passwordDialog = new PasswordChangeDialog(this, content); passwordDialog.setVisible(true);
    }
    @Override public void retirePassword() {
        if (passwordDialog != null) {
            PasswordChangeDialog owned = passwordDialog; passwordDialog = null; owned.dispose();
        }
    }
    @Override public void mergeAction(MergeAction action) { panel.mergeAction(action); }
    @Override public void editMerge(MergeEditorPanel content) {
        editor = new javax.swing.JDialog(this, "Resolve Conflict", false);
        editor.setDefaultCloseOperation(javax.swing.JDialog.DO_NOTHING_ON_CLOSE);
        editor.setContentPane(content);
        content.installDialog(editor.getRootPane());
        editor.addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent event) { content.cancel(); }
        });
        SwingUsability.fit(editor, 850, 800); editor.setLocationRelativeTo(this); editor.setVisible(true);
    }
    @Override public void additionalConflict(Runnable review, Runnable publish, Runnable cancel) {
        panel.additionalConflict(review, publish, cancel);
    }
    @Override public void confirmOriginalResolution(Runnable confirmed) {
        String text = "This publishes the exact resolution you already chose, based on the original selected inputs.\n"
                + "It does not include the newly observed information and does not run the merge new-information check again.\n"
                + "The resulting token may still have competing alternatives.";
        Object[] choices = {"Cancel", "Publish original resolution anyway"};
        if (JOptionPane.showOptionDialog(this, text, "Publish original resolution anyway", JOptionPane.DEFAULT_OPTION,
                JOptionPane.WARNING_MESSAGE, null, choices, choices[0]) == 1) { confirmed.run(); }
    }
    @Override public void mergePublicationUncertain(boolean original, boolean busy, Runnable retry, Runnable stop) {
        panel.mergePublicationUncertain(original, busy, retry, stop);
    }
    @Override public void tokenActions(Runnable create, EditAction edit) { panel.tokenActions(create, edit); }
    @Override public void writeAvailability(boolean available) { panel.writeAvailability(available); }
    @Override public void editToken(TokenEditorPanel content, boolean create) {
        editor = new TokenEditDialog(this, content, create); editor.setVisible(true);
    }
    @Override public void retireEditor() { if (editor != null) { editor.dispose(); editor = null; } }
    @Override public void publicationUncertain(boolean create, boolean busy, Runnable retry, Runnable stop) {
        panel.publicationUncertain(create, busy, retry, stop);
    }
    @Override public void clearUncertainty() { panel.clearUncertainty(); }
    @Override public void abandonedPublication(boolean abandoned) { panel.abandonedPublication(abandoned); }
    @Override public void writeMessage(String message) { panel.writeMessage(message); }
    @Override public void writeWarning(String detail, String message) { panel.writeWarning(message); }
    @Override public void render(VaultState state) { panel.render(state); }
    @Override public void closing() { panel.closing(); }
    @Override public void failure() {
        Edt.require();
        JOptionPane.showMessageDialog(this, "This vault session is unusable and will close.",
                "Session failure", JOptionPane.ERROR_MESSAGE);
    }
    @Override public void showWindow() { Edt.require(); setVisible(true); javax.swing.SwingUtilities.invokeLater(() -> { if (isShowing()) { panel.focusSearch(); } }); }
    @Override public void dispose() { Edt.require(); super.dispose(); }
}
