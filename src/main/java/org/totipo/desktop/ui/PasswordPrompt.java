package org.totipo.desktop.ui;

import java.awt.Component;
import java.awt.GridLayout;
import java.util.Arrays;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;

/** Best-effort JVM secret hygiene, not a secure-erasure guarantee. */
final class PasswordPrompt {
    private PasswordPrompt() { }

    static final class Fields extends JPanel {
        private static final long serialVersionUID = 1L;
        final JPasswordField primary = new JPasswordField(24);
        final JPasswordField confirmation = new JPasswordField(24);
        final javax.swing.JTextField path;

        Fields(java.nio.file.Path directory, boolean create) {
            super(new GridLayout(0, 1, 0, 8));
            Edt.require();
            path = new javax.swing.JTextField(directory.toAbsolutePath().normalize().toString(), 36);
            path.setEditable(false);
            add(SwingUsability.label("Vault", path)); add(path);
            add(SwingUsability.label("Password", primary)); add(primary);
            if (create) {
                add(SwingUsability.label("Confirm password", confirmation)); add(confirmation);
            }
        }
        void clear() { primary.setText(""); confirmation.setText(""); }
    }

    static char[] ask(Component parent, java.nio.file.Path directory, boolean create) {
        Edt.require();
        Fields fields = new Fields(directory, create);
        javax.swing.JDialog dialog = new javax.swing.JDialog(parent instanceof java.awt.Window window
                ? window : javax.swing.SwingUtilities.getWindowAncestor(parent),
                create ? "Create Vault" : "Open Vault", java.awt.Dialog.ModalityType.APPLICATION_MODAL);
        javax.swing.JButton submit = new javax.swing.JButton(create ? "Create" : "Open");
        javax.swing.JButton cancel = new javax.swing.JButton("Cancel");
        boolean[] accepted = {false};
        Runnable accept = () -> { accepted[0] = true; dialog.dispose(); };
        submit.addActionListener(event -> accept.run());
        fields.primary.addActionListener(event -> accept.run());
        fields.confirmation.addActionListener(event -> accept.run());
        cancel.addActionListener(event -> dialog.dispose());
        JPanel content = new JPanel(new java.awt.BorderLayout(0, 16));
        content.setBorder(javax.swing.BorderFactory.createEmptyBorder(16, 16, 16, 16));
        content.add(fields, java.awt.BorderLayout.CENTER);
        JPanel buttons = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.TRAILING, 8, 0));
        buttons.add(cancel); buttons.add(submit); content.add(buttons, java.awt.BorderLayout.SOUTH);
        dialog.setContentPane(content);
        SwingUsability.dialog(dialog.getRootPane(), submit, dialog::dispose);
        dialog.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override public void windowOpened(java.awt.event.WindowEvent event) { fields.primary.requestFocusInWindow(); }
        });
        dialog.pack(); dialog.setLocationRelativeTo(parent);
        try {
            dialog.setVisible(true);
            if (!accepted[0]) { return null; }
            char[] password = fields.primary.getPassword();
            if (create && !matches(password, fields.confirmation.getPassword())) {
                fields.clear();
                JOptionPane.showMessageDialog(parent, "Passwords do not match.",
                        "Create Vault", JOptionPane.INFORMATION_MESSAGE);
                return null;
            }
            return password;
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
