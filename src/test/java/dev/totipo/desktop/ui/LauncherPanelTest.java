package dev.totipo.desktop.ui;

import java.awt.GraphicsEnvironment;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.JButton;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LauncherPanelTest {
    @Test void edtGuardRejectsBackgroundThread() {
        assertFalse(SwingUtilities.isEventDispatchThread());
        assertThrows(IllegalStateException.class, Edt::require);
        assertThrows(IllegalStateException.class, LauncherPanel::new);
        assertThrows(IllegalStateException.class, VaultPanel::new);
    }

    @Test void launcherEmitsActionsAndDisablesBothWhileBusy() throws Exception {
        assertTrue(GraphicsEnvironment.isHeadless());
        SwingUtilities.invokeAndWait(() -> {
            LauncherPanel panel = new LauncherPanel();
            AtomicInteger opens = new AtomicInteger();
            AtomicInteger creates = new AtomicInteger();
            panel.actions(opens::incrementAndGet, creates::incrementAndGet);
            var buttons = Arrays.stream(panel.getComponents()).filter(JButton.class::isInstance)
                    .map(JButton.class::cast).toList();
            assertEquals(2, buttons.size());
            buttons.forEach(button -> { assertTrue(button.isEnabled()); button.doClick(0); });
            assertEquals(1, opens.get());
            assertEquals(1, creates.get());
            panel.busy("Opening vault…", true);
            buttons.forEach(button -> { assertFalse(button.isEnabled()); button.doClick(0); });
            assertEquals(1, opens.get());
            assertEquals(1, creates.get());
            panel.busy("Choose a directory", false);
            buttons.forEach(button -> assertTrue(button.isEnabled()));
        });
    }
}
