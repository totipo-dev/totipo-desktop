package org.totipo.desktop.ui;

import java.awt.Font;
import java.util.HashMap;
import javax.swing.UIManager;
import org.junit.jupiter.api.Test;
import static org.totipo.desktop.TestSupport.edt;
import static org.junit.jupiter.api.Assertions.*;

class ApplicationFontsTest {
    @Test void fontsDeriveFromLookAndFeelAndIncreaseOnceWithoutChangingStyle() throws Exception {
        edt(() -> {
            var defaults = UIManager.getDefaults();
            var previous = new HashMap<Object, Font>();
            for (Object key : new java.util.ArrayList<>(defaults.keySet())) {
                if (defaults.get(key) instanceof Font font) { previous.put(key, font); }
            }
            assertFalse(previous.isEmpty());
            try {
                ApplicationFonts.install();
                for (var entry : previous.entrySet()) {
                    Font enlarged = defaults.getFont(entry.getKey());
                    assertEquals(entry.getValue().getSize2D() + 2f, enlarged.getSize2D());
                    assertEquals(entry.getValue().getStyle(), enlarged.getStyle());
                    assertEquals(entry.getValue().deriveFont(enlarged.getSize2D()), enlarged);
                }
                ApplicationFonts.install();
                assertEquals(previous.get("Button.font").getSize2D() + 2f, defaults.getFont("Button.font").getSize2D());
            } finally { previous.forEach(defaults::put); }
        });
    }
}
