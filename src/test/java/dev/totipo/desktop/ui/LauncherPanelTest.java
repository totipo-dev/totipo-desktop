package dev.totipo.desktop.ui;

import java.awt.GraphicsEnvironment;
import javax.swing.JButton;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LauncherPanelTest {
    @Test
    void edtGuardRejectsBackgroundThread() {
        assertFalse(SwingUtilities.isEventDispatchThread());
        assertThrows(IllegalStateException.class, Edt::require);
        assertThrows(IllegalStateException.class, LauncherPanel::new);
    }

    @Test
    void constructsDisabledLauncherOnEdtWithoutDisplay() throws Exception {
        assertTrue(GraphicsEnvironment.isHeadless());
        SwingUtilities.invokeAndWait(() -> {
            assertDoesNotThrow(Edt::require);
            LauncherPanel panel = new LauncherPanel();
            int buttons = 0;
            for (var component : panel.getComponents()) {
                if (component instanceof JButton button) {
                    buttons++;
                    assertFalse(button.isEnabled());
                    assertEquals(0, button.getActionListeners().length);
                }
            }
            assertEquals(2, buttons);
        });
    }
}
