package dev.totipo.desktop.ui;

import dev.totipo.*;
import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import static dev.totipo.desktop.TestSupport.edt;
import static dev.totipo.desktop.ui.TokenBrowserTest.find;
import static dev.totipo.desktop.ui.TokenFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

class TokenEditingBrowserTest {
    @Test void conflictRequiresExplicitAlternativeAndNoResolutionPromise() throws Exception {
        edt(() -> {
            var panel = new TokenBrowserPanel(new MutableClock());
            try {
                var a = active("same"); var b = active("same");
                var state = new State(token(1, a, b));
                AtomicInteger edits = new AtomicInteger();
                panel.onEdit((base, selected, explanation) -> {
                    assertSame(state.value, base); assertSame(b, selected);
                    assertTrue(explanation.contains("Alternative 2 only"));
                    assertTrue(explanation.contains("does not resolve")); edits.incrementAndGet();
                });
                panel.render(state.value); find(panel, JList.class).setSelectedIndex(0);
                JButton edit = editButton(panel);
                assertEquals("Edit Alternative…", edit.getText()); assertFalse(edit.isEnabled());
                JComboBox<?> choice = find(panel, JComboBox.class);
                assertEquals(-1, choice.getSelectedIndex());
                assertTrue(find(panel, JTextArea.class).getText().contains("does not resolve"));
                choice.setSelectedIndex(1); edit.doClick(); assertEquals(1, edits.get());
                panel.writeAvailability(false); assertFalse(edit.isEnabled()); assertFalse(choice.isEnabled());
            } finally { panel.closing(); }
        });
    }
    @Test void equalHeadsOfferOneEditAndOpeningEditorLeavesTotpTimerRunning() throws Exception {
        edt(() -> {
            var panel = new TokenBrowserPanel(new MutableClock());
            try {
                var a = alternative(TokenStatus.ACTIVE, "issuer", "account", TotpAlgorithm.SHA1, 6, 30,
                        head(1, ClientMetadata.empty()), head(2, ClientMetadata.empty()));
                var state = new State(token(1, a));
                panel.onEdit((base, selected, explanation) -> {
                    assertSame(a, selected); assertSame(state.value, base);
                    var editor = new TokenEditorPanel(selected.descriptor(), explanation, draft -> draft.close(), () -> {});
                    panel.writeAvailability(false);
                    assertTrue(display(panel).running()); editor.retire();
                });
                panel.render(state.value); find(panel, JList.class).setSelectedIndex(0);
                JButton edit = editButton(panel);
                assertEquals("Edit Token", edit.getText()); assertTrue(edit.isEnabled());
                assertFalse(find(panel, JComboBox.class).isVisible()); edit.doClick();
                assertTrue(display(panel).running());
                panel.writeAvailability(true);
                panel.render(new State(token(2)).value); find(panel, JList.class).setSelectedIndex(0);
                assertFalse(edit.isEnabled());
            } finally { panel.closing(); }
        });
    }
    private static TotpDisplay display(TokenBrowserPanel panel) {
        try {
            Field field = TokenBrowserPanel.class.getDeclaredField("totp"); field.setAccessible(true);
            return (TotpDisplay) field.get(panel);
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }
    private static JButton editButton(java.awt.Container root) {
        for (var child : root.getComponents()) {
            if (child instanceof JButton button && button.getText().startsWith("Edit")) { return button; }
            if (child instanceof java.awt.Container container) {
                JButton found = editButton(container);
                if (found != null) { return found; }
            }
        }
        return null;
    }
}
