package org.totipo.desktop.ui;

import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.JDialog;
import javax.swing.JFrame;

/** Owned modeless shell; panel/controller own cancellation and secret clearing. */
final class TokenEditDialog extends JDialog {
    private static final long serialVersionUID = 1L;
    TokenEditDialog(JFrame owner, TokenEditorPanel panel, boolean create) {
        super(owner, create ? "Create Token" : "Edit Token", false);
        Edt.require();
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setContentPane(panel);
        panel.installDialog(getRootPane());
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent event) {
                if (panel.canCancel()) { panel.cancel.doClick(); }
            }
        });
        SwingUsability.fit(this, 650, 700);
        setLocationRelativeTo(owner);
    }
}
