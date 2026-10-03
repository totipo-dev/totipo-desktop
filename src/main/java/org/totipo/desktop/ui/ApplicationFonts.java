package org.totipo.desktop.ui;

import java.awt.Font;
import java.util.HashMap;
import javax.swing.UIManager;
import javax.swing.plaf.FontUIResource;

/** Apply once before constructing UI, deriving every font from the active Look & Feel. */
public final class ApplicationFonts {
    private static boolean installed;
    private ApplicationFonts() { }

    public static void install() {
        Edt.require();
        if (installed) { return; }
        installed = true;
        var defaults = UIManager.getDefaults();
        var fonts = new HashMap<Object, Font>();
        for (Object key : new java.util.ArrayList<>(defaults.keySet())) {
            Object value = defaults.get(key);
            if (value instanceof Font font) { fonts.put(key, font); }
        }
        fonts.forEach((key, font) -> defaults.put(key, new FontUIResource(font.deriveFont(font.getSize2D() + 2f))));
    }
}
