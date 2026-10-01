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
    private TokenEditDialog editor;

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
    @Override public void tokenActions(Runnable create, EditAction edit) { panel.tokenActions(create, edit); }
    @Override public void writeAvailability(boolean available) { panel.writeAvailability(available); }
    @Override public void editToken(TokenEditorPanel content, boolean create) {
        editor = new TokenEditDialog(this, content, create); editor.setVisible(true);
    }
    @Override public void retireEditor() { if (editor != null) { editor.dispose(); editor = null; } }
    @Override public void publicationUncertain(boolean create, boolean busy, Runnable retry, Runnable stop) {
        panel.publicationUncertain(create, busy, retry, stop);
    }
    @Override public void clearUncertainty() { panel.clearUncertainty(); }
    @Override public void abandonedPublication(boolean abandoned) { panel.abandonedPublication(abandoned); }
    @Override public void writeMessage(String message) { panel.writeMessage(message); }
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
