package dev.totipo.desktop.ui;

import java.awt.Component;
import java.awt.GridLayout;
import java.util.Arrays;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;

/** Best-effort JVM secret hygiene, not a secure-erasure guarantee. */
final class PasswordPrompt {
    private PasswordPrompt() { }

    static char[] ask(Component parent, boolean create) {
        Edt.require();
        JPasswordField primary = new JPasswordField(24);
        JPasswordField confirmation = new JPasswordField(24);
        JPanel fields = new JPanel(new GridLayout(0, 1, 0, 8));
        fields.add(new JLabel("Vault password"));
        fields.add(primary);
        if (create) {
            fields.add(new JLabel("Confirm password"));
            fields.add(confirmation);
        }
        try {
            int answer = JOptionPane.showConfirmDialog(parent, fields,
                    create ? "Create Vault" : "Open Vault", JOptionPane.OK_CANCEL_OPTION,
                    JOptionPane.PLAIN_MESSAGE);
            if (answer != JOptionPane.OK_OPTION) {
                return null;
            }
            char[] password = primary.getPassword();
            if (create && !matches(password, confirmation.getPassword())) {
                primary.setText("");
                confirmation.setText("");
                JOptionPane.showMessageDialog(parent, "Passwords do not match.",
                        "Create Vault", JOptionPane.INFORMATION_MESSAGE);
                return null;
            }
            return password;
        } finally {
            primary.setText("");
            confirmation.setText("");
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
