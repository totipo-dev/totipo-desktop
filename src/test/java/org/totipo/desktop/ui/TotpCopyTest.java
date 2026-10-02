package org.totipo.desktop.ui;

import org.totipo.*;
import org.totipo.desktop.clipboard.*;
import java.awt.Component;
import java.awt.Container;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.lang.reflect.Proxy;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import static org.totipo.desktop.TestSupport.edt;
import static org.totipo.desktop.ui.TokenFixtures.*;
import static org.totipo.desktop.ui.TokenBrowserTest.find;
import static org.totipo.desktop.ui.TokenEditingBrowserTest.display;
import static org.junit.jupiter.api.Assertions.*;

class TotpCopyTest {
    static List<JButton> buttons(Container root) {
        List<JButton> result = new ArrayList<>();
        for (Component child : root.getComponents()) {
            if (child instanceof JButton b && b.getText().equals("Copy code") && b.isVisible()) { result.add(b); }
            if (child instanceof Container c) { result.addAll(buttons(c)); }
        }
        return result;
    }
    static JLabel status(Container root) {
        for (Component child : root.getComponents()) {
            if (child instanceof JLabel label && "TOTP clipboard status".equals(label.getAccessibleContext().getAccessibleName())) { return label; }
            if (child instanceof Container c) {
                JLabel found = status(c); if (found != null) { return found; }
            }
        }
        return null;
    }
    @Test void normalCopyIsExplicitAccessibleAndIndependentOfWriteAvailability() throws Exception {
        edt(() -> {
            MutableClock clock = new MutableClock();
            TokenBrowserPanel panel = new TokenBrowserPanel(clock);
            AtomicInteger copies = new AtomicInteger();
            State state = new State(token(1, active("issuer")));
            panel.copyAction((code, from, until, now) -> {
                assertTrue("001234".equals(code));
                assertEquals(clock.now, now);
                copies.incrementAndGet(); return TotpClipboard.COPIED;
            });
            try {
                panel.render(state.value); find(panel, JList.class).setSelectedIndex(0);
                assertEquals(0, copies.get());
                JButton copy = buttons(panel).getFirst();
                assertEquals("Copy TOTP code", copy.getAccessibleContext().getAccessibleName());
                assertFalse(copy.getAccessibleContext().getAccessibleName().contains("001234"));
                assertNull(copy.getAccessibleContext().getAccessibleDescription());
                panel.writeAvailability(false); assertTrue(copy.isEnabled());
                copy.doClick(0); assertEquals(1, copies.get());
                assertEquals(TotpClipboard.COPIED, status(panel).getText());
                assertFalse(status(panel).getText().contains("001234"));
                assertEquals(1, state.calls.size());
                panel.closing();
                copy.getActionListeners()[0].actionPerformed(null);
                assertEquals(1, copies.get()); assertFalse(copy.isEnabled());
            } finally { panel.closing(); }
        });
    }

    @Test void expiryAndBackwardClockRefreshThroughExistingTickBeforeCopy() throws Exception {
        edt(() -> {
            for (boolean backwards : new boolean[]{false, true}) {
                MutableClock clock = new MutableClock();
                TokenState token = token(1, active("issuer"));
                State state = new State(token);
                TotpDisplay display = new TotpDisplay(clock, ignored -> {});
                try {
                    display.select(state.value, token);
                    clock.now = clock.now.plusSeconds(backwards ? -3 : 8);
                    AtomicInteger copied = new AtomicInteger();
                    display.copy(0, (code, from, until, now) -> {
                        assertEquals(2, state.calls.size());
                        assertEquals(clock.now, state.calls.getLast().now());
                        assertFalse(now.isBefore(from)); assertTrue(now.isBefore(until));
                        copied.incrementAndGet(); return TotpClipboard.COPIED;
                    });
                    assertEquals(1, copied.get());
                } finally { display.clear(); }
            }
        });
    }

    @Test void copyRefreshFailureHidesButtonAndDoesNotRetryGeneration() throws Exception {
        edt(() -> {
            MutableClock clock = new MutableClock();
            TokenBrowserPanel panel = new TokenBrowserPanel(clock);
            State state = new State(token(1, active("issuer")));
            panel.copyAction((c, f, u, n) -> { fail("Unavailable code copied"); return ""; });
            try {
                panel.render(state.value); find(panel, JList.class).setSelectedIndex(0);
                state.fail = true; clock.now = clock.now.plusSeconds(8);
                JButton copy = buttons(panel).getFirst(); copy.doClick(0);
                assertTrue(buttons(panel).isEmpty());
                assertEquals("Code unavailable; code was not copied.", status(panel).getText());
                display(panel).copy(0, (c, f, u, n) -> { fail(); return ""; });
                display(panel).tick(); assertEquals(2, state.calls.size());
            } finally { panel.closing(); }
        });
    }

    @Test void timeAdvancingDuringGenerationCannotCopyReturnedExpiredCode() throws Exception {
        edt(() -> {
            MutableClock clock = new MutableClock();
            TokenState token = token(1, active("issuer"));
            VaultState state = (VaultState) Proxy.newProxyInstance(VaultState.class.getClassLoader(),
                    new Class<?>[]{VaultState.class}, (proxy, method, args) -> {
                        if (!method.getName().equals("generateTotp")) { throw new AssertionError("Unexpected API"); }
                        Instant instant = (Instant) args[1];
                        clock.now = instant.plusSeconds(8);
                        return new TotpCode("001234", instant.minusSeconds(2), instant.plusSeconds(8));
                    });
            TotpDisplay display = new TotpDisplay(clock, ignored -> {});
            try {
                display.select(state, token);
                display.copy(0, (c, f, u, n) -> { fail("Expired code copied"); return ""; });
            } finally { display.clear(); }
        });
    }

    @Test void conflictButtonsChooseEachSemanticAlternativeEvenWhenDigitsAreEqual() throws Exception {
        edt(() -> {
            for (boolean same : new boolean[]{false, true}) {
                MutableClock clock = new MutableClock();
                TokenAlternative a = active("A"), b = active("B");
                TokenState token = token(1, a, b);
                State backing = new State(token);
                VaultState state = (VaultState) Proxy.newProxyInstance(VaultState.class.getClassLoader(),
                        new Class<?>[]{VaultState.class}, (proxy, method, args) -> {
                            if (method.getName().equals("generateTotp")) {
                                Instant now = (Instant) args[1];
                                return new TotpCode(args[0] == a || same ? "001234" : "005678",
                                        now.minusSeconds(2), now.plusSeconds(8));
                            }
                            return method.invoke(backing.value, args);
                        });
                TokenBrowserPanel panel = new TokenBrowserPanel(clock);
                List<String> copied = new ArrayList<>();
                panel.copyAction((c, f, u, n) -> { copied.add(c); return TotpClipboard.COPIED; });
                try {
                    panel.render(state); find(panel, JList.class).setSelectedIndex(0);
                    List<JButton> buttons = buttons(panel);
                    assertEquals(2, buttons.size());
                    for (int i = 0; i < 2; i++) {
                        assertEquals("Copy TOTP code for Alternative " + (i + 1),
                                buttons.get(i).getAccessibleContext().getAccessibleName());
                        buttons.get(i).doClick(0);
                    }
                    assertTrue("001234".equals(copied.get(0)));
                    assertTrue((same ? "001234" : "005678").equals(copied.get(1)));
                } finally { panel.closing(); }
            }
        });
    }

    @Test void onlyActiveCompleteDisplayedAlternativesOfferCopy() throws Exception {
        edt(() -> {
            TokenAlternative dead = alternative(TokenStatus.TOMBSTONED, "", "", TotpAlgorithm.SHA1, 6, 30);
            TokenState[] tokens = {token(1, active("A")), token(1, active("A"), active("B")),
                    token(1, dead, active("A")), token(1, dead), token(1)};
            int[] counts = {1, 2, 1, 0, 0};
            TokenBrowserPanel panel = new TokenBrowserPanel(new MutableClock());
            try {
                for (int i = 0; i < tokens.length; i++) {
                    panel.render(new State(tokens[i]).value); find(panel, JList.class).setSelectedIndex(0);
                    assertEquals(counts[i], buttons(panel).size());
                }
            } finally { panel.closing(); }
        });
    }

    @Test void selectionFilterObservationAndRolloverDoNotChangeCopiedPayload() throws Exception {
        edt(() -> {
            MutableClock clock = new MutableClock();
            TokenBrowserPanel panel = new TokenBrowserPanel(clock);
            ClipboardProbe clipboard = new ClipboardProbe();
            Object origin = new Object();
            panel.copyAction((c, f, u, n) -> clipboard.manager().copy(origin, c, f, u, n));
            State state = new State(token(1, active("A")), token(2, active("B")));
            try {
                panel.render(state.value); find(panel, JList.class).setSelectedIndex(0);
                buttons(panel).getFirst().doClick(0);
                var payload = clipboard.payload();
                clock.now = clock.now.plusSeconds(8); display(panel).tick();
                assertSame(payload, clipboard.payload()); assertEquals(1, clipboard.writes());
                find(panel, JList.class).setSelectedIndex(1);
                panel.search.setText("absent"); panel.render(state.value);
                assertSame(payload, clipboard.payload()); assertEquals(1, clipboard.writes());
                clipboard.expire(); clipboard.assertEmpty();
            } finally { panel.closing(); clipboard.manager().shutdown(); }
        });
    }

    @Test void staleDetachedButtonCannotCopyNewSelectionAndFailureStatusIsNonSecret() throws Exception {
        edt(() -> {
            TokenBrowserPanel panel = new TokenBrowserPanel(new MutableClock());
            AtomicInteger copies = new AtomicInteger();
            panel.copyAction((c, f, u, n) -> { copies.incrementAndGet(); return TotpClipboard.UNAVAILABLE; });
            try {
                panel.render(new State(token(1, active("A")), token(2, active("B"))).value);
                JList<?> list = find(panel, JList.class); list.setSelectedIndex(0);
                JButton old = buttons(panel).getFirst(); list.setSelectedIndex(1);
                old.getActionListeners()[0].actionPerformed(null); assertEquals(0, copies.get());
                buttons(panel).getFirst().doClick(0);
                assertEquals(TotpClipboard.UNAVAILABLE, status(panel).getText());
                assertFalse(status(panel).getText().contains("001234"));
            } finally { panel.closing(); }
        });
    }

    @Test void noGlobalCopyBindingAndOrdinaryTextCopyIsUntouched() throws Exception {
        edt(() -> {
            VaultPanel panel = new VaultPanel();
            try {
                for (int modifier : new int[]{InputEvent.CTRL_DOWN_MASK, InputEvent.META_DOWN_MASK}) {
                    assertNull(panel.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                            .get(KeyStroke.getKeyStroke(KeyEvent.VK_C, modifier)));
                }
                JTextField search = find(panel, JTextField.class);
                JTextField normal = new JTextField();
                KeyStroke copy = KeyStroke.getKeyStroke(KeyEvent.VK_C, SwingUsability.menuMask());
                assertEquals(normal.getInputMap().get(copy), search.getInputMap().get(copy));
                assertNotNull(search.getActionMap().get(search.getInputMap().get(copy)));
            } finally { panel.closing(); }
        });
    }

    @Test void exactExpiryCopiesNewCoreStringRatherThanOldLabel() throws Exception {
        edt(() -> {
            MutableClock clock = new MutableClock();
            TokenState token = token(1, active("issuer"));
            AtomicInteger generations = new AtomicInteger();
            VaultState state = (VaultState) Proxy.newProxyInstance(VaultState.class.getClassLoader(),
                    new Class<?>[]{VaultState.class}, (proxy, method, args) -> {
                        Instant now = (Instant) args[1];
                        return new TotpCode(generations.incrementAndGet() == 1 ? "001234" : "005678",
                                now.minusSeconds(2), now.plusSeconds(8));
                    });
            TotpDisplay display = new TotpDisplay(clock, ignored -> {});
            try {
                display.select(state, token);
                clock.now = clock.now.plusSeconds(8);
                AtomicInteger copies = new AtomicInteger();
                display.copy(0, (c, f, u, n) -> {
                    assertTrue("005678".equals(c)); copies.incrementAndGet(); return TotpClipboard.COPIED;
                });
                assertEquals(1, copies.get()); assertEquals(2, generations.get());
            } finally { display.clear(); }
        });
    }
}
