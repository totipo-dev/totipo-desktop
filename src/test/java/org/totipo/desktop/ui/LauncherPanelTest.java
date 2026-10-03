package org.totipo.desktop.ui;

import java.awt.GraphicsEnvironment;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.JButton;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LauncherPanelTest {
    @Test void launcherHasClearActionsPaddingComfortableSizeAndDefaultOpenAction() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            LauncherPanel panel = new LauncherPanel();
            assertEquals(3, panel.getComponentCount());
            assertEquals(LauncherPanel.READY_TEXT, ((javax.swing.JLabel) panel.getComponent(0)).getText());
            JButton open = (JButton) panel.getComponent(1);
            JButton create = (JButton) panel.getComponent(2);
            assertEquals("Open Existing Vault…", open.getText());
            assertEquals("Create New Vault…", create.getText());
            assertTrue(open.getMnemonic() != 0 && create.getMnemonic() != 0);
            var root = new javax.swing.JRootPane(); root.setContentPane(panel); panel.installDefaultAction(root);
            assertSame(open, root.getDefaultButton());
            assertTrue(panel.getPreferredSize().width >= 500); assertTrue(panel.getPreferredSize().height >= 250);
            assertTrue(panel.getMinimumSize().width >= 500); assertTrue(panel.getMinimumSize().height >= 250);
            var padding = panel.getInsets();
            assertTrue(padding.top >= 24 && padding.bottom >= 24 && padding.left >= 24 && padding.right >= 24);
            assertTrue(open.getMargin().top >= 8 && create.getMargin().bottom >= 8);
            assertTrue(((java.awt.GridLayout) panel.getLayout()).getVgap() >= 12);
        });
    }
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
