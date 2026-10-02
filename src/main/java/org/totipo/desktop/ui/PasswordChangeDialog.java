package org.totipo.desktop.ui;

import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.JDialog;
import javax.swing.JFrame;

/** Small owned modeless shell; cancellation is unavailable after submission. */
final class PasswordChangeDialog extends JDialog {
    private static final long serialVersionUID = 1L;
    PasswordChangeDialog(JFrame owner, PasswordChangePanel panel) {
        super(owner, "Change Password", false);
        Edt.require();
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setContentPane(panel);
        panel.installDialog(getRootPane());
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent event) { panel.cancel(); }
        });
        pack(); SwingUsability.fit(this, getWidth(), getHeight()); setLocationRelativeTo(owner);
    }
}
