package dev.totipo.desktop.ui;

import javax.swing.*;
import org.junit.jupiter.api.Test;
import static dev.totipo.desktop.TestSupport.edt;
import static dev.totipo.desktop.ui.TokenBrowserTest.find;
import static dev.totipo.desktop.ui.TokenFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

class PasswordBrowserTest {
    @Test void passwordReservationAndRunningWorkLeaveSelectionStateTotpAndRefreshLive() throws Exception {
        edt(() -> {
            var vault = new VaultPanel();
            var browser = find(vault, TokenBrowserPanel.class);
            var display = TokenEditingBrowserTest.display(browser);
            var first = new State(token(1, active("issuer"), active("conflict")));
            var second = new State(token(1, active("issuer"), active("conflict")));
            var form = new PasswordChangePanel(submission -> submission.close(), () -> {});
            try {
                vault.render(first.value); find(browser, JList.class).setSelectedIndex(0);
                find(browser, JComboBox.class).setSelectedIndex(0);
                vault.writeAvailability(false);
                assertTrue(display.running()); display.tick();
                assertTrue(button(vault, "Refresh").isEnabled());
                assertFalse(button(vault, "Create Token").isEnabled());
                assertFalse(button(vault, "Change Password…").isEnabled());
                assertFalse(button(browser, "Edit Alternative…").isEnabled());
                assertFalse(button(browser, "Resolve Conflict…").isEnabled());
                form.busy(true, "Changing vault password…");
                vault.render(second.value);
                assertEquals(0, find(browser, JList.class).getSelectedIndex());
                assertTrue(display.running()); display.tick(); assertFalse(second.calls.isEmpty());
                form.retire(); vault.writeAvailability(true);
                assertTrue(display.running()); assertTrue(button(vault, "Change Password…").isEnabled());
                assertTrue(button(vault, "Create Token").isEnabled());
                vault.closing(); assertFalse(display.running()); assertFalse(button(vault, "Refresh").isEnabled());
            } finally { form.retire(); vault.closing(); }
        });
    }
    private static JButton button(java.awt.Container root, String label) {
        for (var child : root.getComponents()) {
            if (child instanceof JButton button && button.getText().equals(label)) { return button; }
            if (child instanceof java.awt.Container container) {
                JButton found = button(container, label); if (found != null) { return found; }
            }
        }
        return null;
    }
}
