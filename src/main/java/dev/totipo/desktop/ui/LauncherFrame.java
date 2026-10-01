package dev.totipo.desktop.ui;

import javax.swing.JFrame;

/** Top-level window setup only; owns no application or domain state. */
public final class LauncherFrame extends JFrame {
    private static final long serialVersionUID = 1L;

    public LauncherFrame() {
        super("Totipo");
        Edt.require();
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setContentPane(new LauncherPanel());
        pack();
        setLocationRelativeTo(null);
    }
}
