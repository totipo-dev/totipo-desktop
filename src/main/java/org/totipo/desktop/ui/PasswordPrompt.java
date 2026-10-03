package org.totipo.desktop.ui;

import java.awt.Component;
import java.awt.GridLayout;
import java.util.Arrays;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import org.totipo.desktop.ui.PasswordPromptResult.Action;

/** Best-effort JVM secret hygiene, not a secure-erasure guarantee. */
final class PasswordPrompt {
    private PasswordPrompt() { }

    static final class Fields extends JPanel {
        private static final long serialVersionUID = 1L;
        final JPasswordField primary = new JPasswordField(24);
        final JPasswordField confirmation = new JPasswordField(24);
        final javax.swing.JTextField path;

        Fields(java.nio.file.Path directory, boolean create, PasswordPromptContext context) {
            super(new GridLayout(0, 1, 0, 8));
            Edt.require();
            path = new javax.swing.JTextField(directory.toAbsolutePath().normalize().toString(), 36);
            path.setEditable(false);
            String introduction = create ? "Create a vault in:"
                    : context == PasswordPromptContext.REMEMBERED_STARTUP
                            ? "Welcome back. Enter the password for:" : "Enter the password for:";
            add(SwingUsability.label(introduction, path)); add(path);
            add(SwingUsability.label("Password", primary)); add(primary);
            if (create) {
                add(SwingUsability.label("Confirm password", confirmation)); add(confirmation);
            }
        }
        void clear() { primary.setText(""); confirmation.setText(""); }
    }

    /** Headless-testable content and wiring used by the modal dialog. */
    static final class Form extends JPanel {
        private static final long serialVersionUID = 1L;
        final Fields fields;
        final javax.swing.JButton submit;
        final javax.swing.JButton dismiss;
        final javax.swing.JButton changeVault;
        final JPanel buttons = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.TRAILING, 8, 0));
        private final transient Runnable dispose;
        private final Action dismissal;
        Action decision;
        private boolean retired;
        final transient java.awt.event.WindowAdapter lifecycle = new java.awt.event.WindowAdapter() {
            @Override public void windowClosing(java.awt.event.WindowEvent event) { finish(dismissal); }
            @Override public void windowOpened(java.awt.event.WindowEvent event) { fields.primary.requestFocusInWindow(); }
        };

        Form(java.nio.file.Path directory, boolean create, PasswordPromptContext context, Runnable dispose) {
            super(new java.awt.BorderLayout(0, 16));
            Edt.require();
            this.dispose = dispose;
            dismissal = create ? Action.CANCEL : Action.EXIT;
            decision = dismissal;
            fields = new Fields(directory, create, context);
            submit = new javax.swing.JButton(create ? "Create" : "Open");
            dismiss = new javax.swing.JButton(create ? "Cancel" : "Exit");
            changeVault = create ? null : new javax.swing.JButton("Change Vault…");
            submit.addActionListener(event -> finish(Action.SUBMIT));
            fields.primary.addActionListener(event -> finish(Action.SUBMIT));
            fields.confirmation.addActionListener(event -> finish(Action.SUBMIT));
            dismiss.addActionListener(event -> finish(dismissal));
            setBorder(javax.swing.BorderFactory.createEmptyBorder(16, 16, 16, 16));
            add(fields, java.awt.BorderLayout.CENTER);
            buttons.add(dismiss);
            if (changeVault != null) {
                changeVault.addActionListener(event -> finish(Action.CHANGE_VAULT));
                buttons.add(changeVault);
            }
            buttons.add(submit);
            add(buttons, java.awt.BorderLayout.SOUTH);
        }

        void install(javax.swing.JRootPane root) {
            root.setDefaultButton(submit);
            SwingUsability.bind(root, javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW,
                    javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ESCAPE, 0),
                    "dismiss-password", SwingUsability.action(dismiss.getText(), () -> finish(dismissal)));
        }

        private void finish(Action action) {
            if (retired) { return; }
            retired = true;
            decision = action;
            if (action != Action.SUBMIT) { fields.clear(); }
            dispose.run();
        }
    }

    static PasswordPromptResult ask(Component parent, java.nio.file.Path directory, boolean create, PasswordPromptContext context) {
        Edt.require();
        javax.swing.JDialog dialog = new javax.swing.JDialog(parent instanceof java.awt.Window window
                ? window : javax.swing.SwingUtilities.getWindowAncestor(parent),
                create ? "Create Vault" : "Open Vault", java.awt.Dialog.ModalityType.APPLICATION_MODAL);
        Form form = new Form(directory, create, context, dialog::dispose);
        Fields fields = form.fields;
        dialog.setContentPane(form);
        form.install(dialog.getRootPane());
        dialog.setDefaultCloseOperation(javax.swing.JDialog.DO_NOTHING_ON_CLOSE);
        dialog.addWindowListener(form.lifecycle);
        dialog.pack(); dialog.setLocationRelativeTo(parent);
        try {
            dialog.setVisible(true);
            if (form.decision != Action.SUBMIT) { return PasswordPromptResult.dismissed(form.decision); }
            char[] password = fields.primary.getPassword();
            if (create && !matches(password, fields.confirmation.getPassword())) {
                fields.clear();
                JOptionPane.showMessageDialog(parent, "Passwords do not match.",
                        "Create Vault", JOptionPane.INFORMATION_MESSAGE);
                return PasswordPromptResult.dismissed(Action.CANCEL);
            }
            return PasswordPromptResult.submitted(password);
        } finally {
            fields.clear(); dialog.dispose();
        }
    }

    static boolean matches(char[] primary, char[] confirmation) {
        boolean same;
        try {
            same = Arrays.equals(primary, confirmation);
        } finally {
            Arrays.fill(confirmation, '\0');
        }
        if (!same) {
            Arrays.fill(primary, '\0');
        }
        return same;
    }
}
