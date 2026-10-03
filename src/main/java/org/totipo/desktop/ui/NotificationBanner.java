package org.totipo.desktop.ui;

import java.awt.BorderLayout;
import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.JTextArea;

/** Compact, wrapping warning text; no space is reserved when there is no warning. */
final class NotificationBanner extends JPanel {
    private static final long serialVersionUID = 1L;
    private final JTextArea text = new JTextArea();

    NotificationBanner() {
        super(new BorderLayout());
        Edt.require();
        text.setEditable(false); text.setLineWrap(true); text.setWrapStyleWord(true);
        text.setOpaque(false);
        text.getAccessibleContext().setAccessibleName("Vault warning");
        setBorder(BorderFactory.createCompoundBorder(BorderFactory.createEtchedBorder(),
                BorderFactory.createEmptyBorder(8, 10, 8, 10)));
        add(text); setVisible(false);
    }
    void message(String message) {
        Edt.require();
        text.setText(message); setVisible(!message.isBlank()); revalidate(); repaint();
    }
}
