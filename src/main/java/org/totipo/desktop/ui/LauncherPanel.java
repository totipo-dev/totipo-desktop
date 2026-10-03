package org.totipo.desktop.ui;

import java.awt.GridLayout;
import java.awt.Dimension;
import java.awt.Insets;
import javax.swing.JRootPane;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;

/** Headless-testable launcher content. */
public final class LauncherPanel extends JPanel {
    private static final long serialVersionUID = 1L;
    public static final String READY_TEXT = "Open an existing vault or create a new one.";
    private final JButton open = new JButton("Open Existing Vault…");
    private final JButton create = new JButton("Create New Vault…");
    private final JLabel status = new JLabel(READY_TEXT);

    public LauncherPanel() {
        Edt.require();
        setLayout(new GridLayout(0, 1, 0, 16));
        setBorder(BorderFactory.createEmptyBorder(28, 32, 28, 32));
        setMinimumSize(new Dimension(500, 250));
        open.setMnemonic('O'); create.setMnemonic('C');
        open.setMargin(new Insets(10, 20, 10, 20));
        create.setMargin(new Insets(10, 20, 10, 20));
        add(status);
        add(open);
        add(create);
        Dimension content = super.getPreferredSize();
        setPreferredSize(new Dimension(Math.max(560, content.width), Math.max(280, content.height)));
    }

    public void installDefaultAction(JRootPane root) {
        Edt.require(); root.setDefaultButton(open);
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
