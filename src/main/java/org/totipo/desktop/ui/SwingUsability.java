package org.totipo.desktop.ui;

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;

/** Small Swing wiring helpers; callbacks retain their existing ownership checks. */
final class SwingUsability {
    private SwingUsability() { }
    static int menuMask() {
        try { return Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx(); }
        catch (HeadlessException unavailable) { return InputEvent.CTRL_DOWN_MASK; }
    }
    static Action action(String name, Runnable callback) {
        return new AbstractAction(name) {
            private static final long serialVersionUID = 1L;
            @Override public void actionPerformed(ActionEvent event) { if (isEnabled()) { callback.run(); } }
        };
    }
    static void bind(JComponent target, int condition, KeyStroke key, String name, Action action) {
        target.getInputMap(condition).put(key, name); target.getActionMap().put(name, action);
    }
    static JLabel label(String text, JComponent input) {
        JLabel label = new JLabel(text); label.setLabelFor(input);
        input.getAccessibleContext().setAccessibleName(text); return label;
    }
    static void dialog(JRootPane root, JButton normal, Runnable cancel) {
        root.setDefaultButton(normal);
        bind(root, JComponent.WHEN_IN_FOCUSED_WINDOW, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                "cancel-form", action("Cancel", cancel));
    }
    static void fit(Window window, int width, int height) {
        Rectangle bounds = window.getGraphicsConfiguration().getBounds();
        Insets insets = Toolkit.getDefaultToolkit().getScreenInsets(window.getGraphicsConfiguration());
        window.setSize(Math.min(width, bounds.width - insets.left - insets.right),
                Math.min(height, bounds.height - insets.top - insets.bottom));
    }
}
