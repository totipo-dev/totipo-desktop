package org.totipo.desktop.ui;

import org.totipo.desktop.PasswordChangeSubmission;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.totipo.desktop.TestSupport.edt;
import static org.junit.jupiter.api.Assertions.*;

class PasswordChangePanelTest {
    @ParameterizedTest(name = "valid input case {index}")
    @ValueSource(strings = {"", "ascii", "\u00e9\u6f22", "\ud83d\ude00"})
    void publicInputRulesPermitBothFieldsAndIdenticalPasswords(String input) throws Exception {
        edt(() -> {
            AtomicInteger submissions = new AtomicInteger();
            var panel = new PasswordChangePanel(value -> { submissions.incrementAndGet(); value.close(); }, () -> {});
            panel.current.setText(input); panel.next.setText(input); panel.confirmation.setText(input);
            panel.change.doClick();
            assertEquals(1, submissions.get());
            assertFalse(panel.change.isEnabled()); assertFalse(panel.cancel.isEnabled());
            assertEquals(0, panel.current.getDocument().getLength());
            assertEquals(0, panel.next.getDocument().getLength());
            assertEquals(0, panel.confirmation.getDocument().getLength());
        });
    }

    @Test void bothFieldsApplyUnicodeAndExactUtf8BoundsBeforeSubmission() throws Exception {
        for (boolean current : new boolean[]{true, false}) {
            for (String input : new String[]{"x".repeat(1024), "\u00e9".repeat(512), "\ud83d\ude00".repeat(256),
                    "x".repeat(1025), "\u00e9".repeat(513), "\ud800", "\udc00"}) {
                boolean valid = input.length() == 1024 || input.equals("\u00e9".repeat(512)) || input.equals("\ud83d\ude00".repeat(256));
                edt(() -> {
                    AtomicInteger submissions = new AtomicInteger();
                    var panel = new PasswordChangePanel(value -> { submissions.incrementAndGet(); value.close(); }, () -> {});
                    panel.current.setText(current ? input : "ordinary");
                    panel.next.setText(current ? "ordinary" : input);
                    panel.confirmation.setText(current ? "ordinary" : input);
                    panel.change.doClick();
                    assertEquals(valid ? 1 : 0, submissions.get(), "Validation must precede submission");
                    assertEquals(!valid, panel.change.isEnabled());
                    assertEquals(0, panel.current.getDocument().getLength());
                    assertEquals(0, panel.next.getDocument().getLength());
                    assertEquals(0, panel.confirmation.getDocument().getLength());
                });
            }
        }
    }

    @Test void mismatchClearsFieldsAndCancellationRetiresWithoutSubmission() throws Exception {
        edt(() -> {
            AtomicInteger submissions = new AtomicInteger(), cancellations = new AtomicInteger();
            var panel = new PasswordChangePanel(value -> { submissions.incrementAndGet(); value.close(); }, cancellations::incrementAndGet);
            panel.current.setText("old"); panel.next.setText("new"); panel.confirmation.setText("different");
            panel.change.doClick();
            assertEquals(0, submissions.get()); assertTrue(panel.change.isEnabled());
            assertEquals(0, panel.current.getDocument().getLength()); assertEquals(0, panel.next.getDocument().getLength());
            assertEquals(0, panel.confirmation.getDocument().getLength());
            panel.current.setText("unsent"); panel.next.setText("unsent"); panel.confirmation.setText("unsent");
            panel.cancel(); panel.cancel();
            assertEquals(1, cancellations.get()); assertEquals(0, submissions.get());
            assertEquals(0, panel.current.getDocument().getLength()); assertEquals(0, panel.next.getDocument().getLength());
            assertEquals(0, panel.confirmation.getDocument().getLength());
            assertFalse(panel.change.isEnabled());
        });
    }

    @Test void rejectedInputWipesEveryOwnedTemporaryArray() {
        for (boolean malformedCurrent : new boolean[]{true, false}) {
            char[] current = malformedCurrent ? new char[]{'\ud800'} : new char[]{'a'};
            char[] next = malformedCurrent ? new char[]{'a'} : new char[]{'\udc00'};
            char[] confirmation = next.clone();
            assertThrows(IllegalArgumentException.class, () -> PasswordChangeSubmission.prepare(current, next, confirmation));
            for (char[] input : new char[][]{current, next, confirmation}) {
                for (char ch : input) { assertTrue(ch == 0, "Temporary buffer must be wiped"); }
            }
        }
    }
}
