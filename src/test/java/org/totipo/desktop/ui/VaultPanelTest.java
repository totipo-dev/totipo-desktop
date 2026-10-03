package org.totipo.desktop.ui;

import org.totipo.ObservationProgress;
import java.awt.FlowLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JProgressBar;
import org.junit.jupiter.api.Test;
import static org.totipo.desktop.TestSupport.*;
import static org.totipo.desktop.ui.TokenBrowserTest.find;
import static org.totipo.desktop.ui.TokenFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

class VaultPanelTest {
    @Test void readingProgressDisappearsAndOnlyWarningsShowInBanner() throws Exception {
        edt(() -> {
            VaultPanel panel = new VaultPanel();
            JProgressBar progress = find(panel, JProgressBar.class);
            try {
                assertFalse(panel.notification.isVisible());
                panel.render(state(new ObservationProgress.Enumerating(42)));
                assertTrue(progress.isVisible()); assertTrue(progress.isIndeterminate());
                assertFalse(panel.notification.isVisible());
                panel.render(state(new ObservationProgress.Processing(3, 8)));
                assertFalse(progress.isIndeterminate()); assertEquals(375, progress.getValue());
                panel.render(state(new ObservationProgress.Processing(Long.MAX_VALUE - 1, Long.MAX_VALUE)));
                assertTrue(progress.getValue() >= 999);
                panel.render(state(new ObservationProgress.Processing(0, 0)));
                assertEquals(0, progress.getValue());
                panel.render(state(new ObservationProgress.Finished(25, false)));
                assertFalse(progress.isVisible()); assertFalse(panel.notification.isVisible());
                panel.render(state(new ObservationProgress.Finished(26, true), "CODE_C"));
                assertTrue(panel.notification.isVisible());
                String warning = find(panel.notification, javax.swing.JTextArea.class).getText();
                assertTrue(warning.contains("Refresh")); assertFalse(warning.contains("CODE_C"));
                panel.render(state(new ObservationProgress.Finished(27, false)));
                assertFalse(panel.notification.isVisible());
                panel.closing(); assertFalse(panel.refreshAction.isEnabled());
            } finally { panel.closing(); }
        });
    }

    @Test void menuActionsAreReachableAndPrimaryActionsShareHorizontalRow() throws Exception {
        edt(() -> {
            VaultPanel panel = new VaultPanel();
            var bar = panel.menuBar();
            assertEquals(2, bar.getMenuCount());
            var file = bar.getMenu(0);
            assertEquals("File", file.getText()); assertNotEquals(0, file.getMnemonic());
            assertEquals(1, file.getItemCount());
            assertEquals("Exit", file.getItem(0).getText()); assertNotEquals(0, file.getItem(0).getMnemonic());
            int[] exits = {0}; panel.exitAction(() -> exits[0]++); file.getItem(0).doClick(0);
            assertEquals(1, exits[0]);
            var menu = bar.getMenu(1);
            assertEquals("Vault", menu.getText()); assertNotEquals(0, menu.getMnemonic());
            assertEquals("Change Vault…", menu.getItem(0).getText());
            assertEquals("Change Password…", menu.getItem(1).getText());
            int[] called = {0, 0};
            panel.changeVaultAction(() -> called[0]++); panel.passwordAction(() -> called[1]++);
            menu.getItem(0).doClick(); menu.getItem(1).doClick();
            assertArrayEquals(new int[] {1, 1}, called);
            JButton refresh = find(panel, JButton.class);
            var row = refresh.getParent();
            assertInstanceOf(FlowLayout.class, row.getLayout());
            assertEquals(2, row.getComponentCount());
            assertEquals("Refresh", ((JButton) row.getComponent(0)).getText());
            assertEquals("Create Token", ((JButton) row.getComponent(1)).getText());
            assertNull(javax.swing.SwingUtilities.getAncestorOfClass(VaultPanel.class, panel.changePassword));
            assertTrue(panel.getMinimumSize().width >= 900); assertTrue(panel.getMinimumSize().height >= 600);
            panel.closing(); assertFalse(menu.getItem(0).isEnabled()); assertFalse(menu.getItem(1).isEnabled());
        });
    }

    @Test void conflictAndUncertainSaveWarningsRemainVisibleWithoutDiagnostics() throws Exception {
        edt(() -> {
            VaultPanel panel = new VaultPanel();
            try {
                panel.render(new State(token(1, active("one"), active("two"))).value);
                assertTrue(panel.notification.isVisible());
                assertTrue(find(panel.notification, javax.swing.JTextArea.class).getText().contains("conflicting versions"));
                panel.render(new State().value); assertFalse(panel.notification.isVisible());
                panel.publicationUncertain(false, false, () -> {}, () -> {});
                assertTrue(panel.notification.isVisible());
                panel.clearUncertainty(); assertFalse(panel.notification.isVisible());
                panel.abandonedPublication(true); assertTrue(panel.notification.isVisible());
                panel.render(new State().value); assertTrue(panel.notification.isVisible());
                panel.abandonedPublication(false); assertFalse(panel.notification.isVisible());
                panel.writeMessage("Token publication acknowledged."); assertFalse(panel.notification.isVisible());
                panel.writeWarning("The change could not be completed."); assertTrue(panel.notification.isVisible());
                panel.render(new State().value); assertTrue(panel.notification.isVisible());
                panel.writeMessage("Token publication acknowledged."); assertFalse(panel.notification.isVisible());
                panel.additionalConflict(() -> {}, () -> {}, () -> {}); assertTrue(panel.notification.isVisible());
                assertTrue(find(panel.notification, javax.swing.JTextArea.class).getText().contains("vault changed"));
                panel.clearUncertainty(); assertFalse(panel.notification.isVisible());
            } finally { panel.closing(); }
        });
    }
}
