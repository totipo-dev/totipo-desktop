package org.totipo.desktop.ui;

import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.nio.file.Path;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JOptionPane;

/** Top-level presentation only; DesktopApplication owns behavior. */
public final class LauncherFrame extends JFrame implements LauncherView {
    private static final long serialVersionUID = 1L;
    private final LauncherPanel panel = new LauncherPanel();

    public LauncherFrame() {
        super("Totipo");
        Edt.require();
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setContentPane(panel);
        pack();
        setLocationRelativeTo(null);
    }

    @Override public void actions(Runnable open, Runnable create, Runnable close) {
        Edt.require();
        panel.actions(open, create);
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent event) { close.run(); }
        });
    }
    @Override public Path chooseDirectory() {
        Edt.require();
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setMultiSelectionEnabled(false);
        return chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION
                ? chooser.getSelectedFile().toPath() : null;
    }
    @Override public char[] password(boolean create) { return PasswordPrompt.ask(this, create); }
    @Override public boolean confirmEmptyPassword() {
        Edt.require();
        Object[] options = {"Create with empty password", "Cancel"};
        return JOptionPane.showOptionDialog(this,
                "An empty password provides no password secrecy.\n"
                + "Anyone with the vault bootstrap can try passwords offline;\n"
                + "Argon2id increases guessing cost but does not prevent guessing.\n"
                + "Create this vault with an empty password?",
                "Confirm empty vault password", JOptionPane.DEFAULT_OPTION,
                JOptionPane.WARNING_MESSAGE, null, options, options[1]) == 0;
    }
    @Override public void busy(String text, boolean busy) { panel.busy(text, busy); }
    @Override public void message(String title, String text) {
        Edt.require();
        JOptionPane.showMessageDialog(this, text, title, JOptionPane.INFORMATION_MESSAGE);
    }
    @Override public void showWindow() { Edt.require(); setVisible(true); }
    @Override public void dispose() { Edt.require(); super.dispose(); }
}
