package org.totipo.desktop.ui;

import java.awt.Component;
import java.awt.Dimension;
import java.io.File;
import java.nio.file.Path;
import javax.swing.JDialog;
import javax.swing.JFileChooser;

/** Standard Swing folder selection, shared by open and create. */
final class VaultDirectoryChooser extends JFileChooser {
    private static final long serialVersionUID = 1L;

    VaultDirectoryChooser(Path location, boolean create) {
        Edt.require();
        setFileSelectionMode(DIRECTORIES_ONLY);
        setMultiSelectionEnabled(false);
        setAcceptAllFileFilterUsed(false);
        setDialogTitle(create ? "Select New Vault Folder" : "Select Vault Folder");
        setApproveButtonText("Select Folder");
        setApproveButtonToolTipText(create ? "Create a vault in the selected folder" : "Open the vault in the selected folder");
        getAccessibleContext().setAccessibleName(getDialogTitle());
        setPreferredSize(new Dimension(800, 550));
        if (location != null) {
            File candidate = location.toAbsolutePath().normalize().toFile();
            File current = candidate.getParentFile();
            while (current != null && !current.isDirectory()) { current = current.getParentFile(); }
            if (current != null) { setCurrentDirectory(current); }
            else if (candidate.isDirectory()) { setCurrentDirectory(candidate); }
            if (!create && candidate.isDirectory()) { setSelectedFile(candidate); }
        }
    }

    @Override protected JDialog createDialog(Component parent) {
        JDialog dialog = super.createDialog(parent); // JFileChooser packs its preferred content size.
        SwingUsability.fit(dialog, dialog.getWidth(), dialog.getHeight());
        dialog.setLocationRelativeTo(parent);
        return dialog;
    }
}
