package dev.totipo.desktop.ui;

import dev.totipo.VaultState;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.nio.file.Path;
import javax.swing.JFrame;
import javax.swing.JOptionPane;

/** Visual shell for one controller-owned session. */
public final class VaultFrame extends JFrame implements VaultView {
    private static final long serialVersionUID = 1L;
    private final VaultPanel panel = new VaultPanel();

    public VaultFrame(Path directory) {
        super("Totipo — " + directory);
        Edt.require();
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setContentPane(panel);
        pack();
        setLocationByPlatform(true);
    }

    @Override public void actions(Runnable refresh, Runnable close) {
        Edt.require();
        panel.onRefresh(refresh);
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent event) { close.run(); }
        });
    }
    @Override public void render(VaultState state) { panel.render(state); }
    @Override public void closing() { panel.closing(); }
    @Override public void failure() {
        Edt.require();
        JOptionPane.showMessageDialog(this, "This vault session is unusable and will close.",
                "Session failure", JOptionPane.ERROR_MESSAGE);
    }
    @Override public void showWindow() { Edt.require(); setVisible(true); }
    @Override public void dispose() { Edt.require(); super.dispose(); }
}
