package dev.totipo.desktop.ui;

import java.awt.GridLayout;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;

/** Headless-testable launcher content. */
public final class LauncherPanel extends JPanel {
    private static final long serialVersionUID = 1L;
    private final JButton open = new JButton("Open Vault");
    private final JButton create = new JButton("Create Vault");
    private final JLabel status = new JLabel("Choose an existing vault directory.");

    public LauncherPanel() {
        Edt.require();
        setLayout(new GridLayout(0, 1, 0, 12));
        setBorder(BorderFactory.createEmptyBorder(24, 32, 24, 32));
        add(new JLabel("Totipo", SwingConstants.CENTER));
        add(status);
        add(open);
        add(create);
    }

    public void actions(Runnable openAction, Runnable createAction) {
        Edt.require();
        open.addActionListener(event -> openAction.run());
        create.addActionListener(event -> createAction.run());
    }

    public void busy(String text, boolean busy) {
        Edt.require();
        status.setText(text);
        open.setEnabled(!busy);
        create.setEnabled(!busy);
    }
}
