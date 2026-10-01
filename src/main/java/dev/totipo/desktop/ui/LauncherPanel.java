package dev.totipo.desktop.ui;

import java.awt.GridLayout;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;

/** Headless-testable launcher content. Vault actions are unavailable in M0. */
public final class LauncherPanel extends JPanel {
    private static final long serialVersionUID = 1L;

    public LauncherPanel() {
        Edt.require();
        setLayout(new GridLayout(0, 1, 0, 12));
        setBorder(BorderFactory.createEmptyBorder(24, 32, 24, 32));
        add(new JLabel("Totipo", SwingConstants.CENTER));
        add(new JLabel("M0 bootstrap — vault actions are not available yet."));
        JButton open = new JButton("Open Vault");
        open.setEnabled(false);
        add(open);
        JButton create = new JButton("Create Vault");
        create.setEnabled(false);
        add(create);
    }
}
