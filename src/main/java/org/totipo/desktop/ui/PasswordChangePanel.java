package org.totipo.desktop.ui;

import org.totipo.desktop.PasswordChangeSubmission;
import java.awt.GridLayout;
import java.awt.BorderLayout;
import java.util.Arrays;
import java.util.function.Consumer;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextArea;

/** Headless-testable secret entry. No submitted password remains in this form. */
public final class PasswordChangePanel extends JPanel {
    private static final long serialVersionUID = 1L;
    final JPasswordField current = new JPasswordField(24);
    final JPasswordField next = new JPasswordField(24);
    final JPasswordField confirmation = new JPasswordField(24);
    final JButton change = new JButton("Change");
    final JButton cancel = new JButton("Cancel");
    final JTextArea message = new JTextArea(6, 55);
    private boolean retired;

    public PasswordChangePanel(Consumer<PasswordChangeSubmission> submit, Runnable cancelled) {
        Edt.require();
        message.setEditable(false); message.setLineWrap(true); message.setWrapStyleWord(true);
        setLayout(new BorderLayout(8, 8));
        setBorder(javax.swing.BorderFactory.createEmptyBorder(12, 12, 12, 12));
        JPanel fields = new JPanel(new GridLayout(0, 1, 0, 8));
        fields.add(SwingUsability.label("Current password", current)); fields.add(current);
        fields.add(SwingUsability.label("New password", next)); fields.add(next);
        fields.add(SwingUsability.label("Confirm new password", confirmation)); fields.add(confirmation);
        JPanel buttons = new JPanel(); buttons.add(change); buttons.add(cancel);
        add(fields, BorderLayout.NORTH); add(message, BorderLayout.CENTER); add(buttons, BorderLayout.SOUTH);
        change.setMnemonic('H'); cancel.setMnemonic('C');
        message.getAccessibleContext().setAccessibleName("Password change status");
        change.addActionListener(event -> {
            if (retired || !change.isEnabled()) { return; }
            PasswordChangeSubmission submission = null;
            char[] old = null;
            char[] replacement = null;
            char[] confirmed = null;
            boolean transferred = false;
            try {
                old = current.getPassword();
                replacement = next.getPassword();
                confirmed = confirmation.getPassword();
                try {
                    submission = PasswordChangeSubmission.prepare(old, replacement, confirmed);
                } catch (IllegalArgumentException invalid) {
                    message.setText("Enter valid Unicode within 1024 UTF-8 bytes per password, with matching new passwords.");
                    return;
                }
                clearFields();
                busy(true, "Changing vault password…");
                submit.accept(submission);
                transferred = true;
            } finally {
                if (!transferred) {
                    if (submission != null) { submission.close(); }
                    if (old != null) { Arrays.fill(old, '\0'); }
                    if (replacement != null) { Arrays.fill(replacement, '\0'); }
                }
                if (confirmed != null) { Arrays.fill(confirmed, '\0'); }
                clearFields();
            }
        });
        cancel.addActionListener(event -> {
            if (canCancel()) {
                try { retire(); }
                finally { cancelled.run(); }
            }
        });
    }

    void installDialog(javax.swing.JRootPane root) { SwingUsability.dialog(root, change, this::cancel); }

    private void clearFields() {
        try { current.setText(""); }
        finally {
            try { next.setText(""); }
            finally { confirmation.setText(""); }
        }
    }
    public boolean canCancel() { return !retired && cancel.isEnabled(); }
    public void cancel() { Edt.require(); if (canCancel()) { cancel.doClick(); } }
    public void busy(boolean busy, String text) {
        Edt.require();
        boolean enabled = !busy && !retired;
        current.setEnabled(enabled); next.setEnabled(enabled); confirmation.setEnabled(enabled);
        change.setEnabled(enabled); cancel.setEnabled(enabled); message.setText(text);
    }
    public void retire() {
        Edt.require(); retired = true;
        try { clearFields(); }
        finally { busy(true, " "); }
    }
}
