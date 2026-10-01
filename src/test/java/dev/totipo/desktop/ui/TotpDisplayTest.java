package dev.totipo.desktop.ui;

import dev.totipo.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import static dev.totipo.desktop.TestSupport.edt;
import static dev.totipo.desktop.ui.TokenFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

class TotpDisplayTest {
    @Test void authoritativeIntervalExactExpiryJumpsAndLeadingZero() throws Exception {
        edt(() -> {
            MutableClock clock = new MutableClock();
            AtomicReference<List<TotpDisplay.Display>> visible = new AtomicReference<>();
            TotpDisplay display = new TotpDisplay(clock, visible::set);
            TokenState token = token(1, active("issuer"));
            State state = new State(token);
            try {
                display.select(state.value, token);
                assertTrue(display.running());
                assertEquals(1, state.calls.size());
                assertEquals("001234", visible.get().get(0).code());
                assertEquals(800, visible.get().get(0).remaining());
                assertEquals("8 seconds remaining", visible.get().get(0).countdown());
                clock.now = clock.now.plusSeconds(3);
                display.tick(); display.tick(); display.tick();
                assertEquals(1, state.calls.size());
                assertEquals(500, visible.get().get(0).remaining());
                clock.now = clock.now.plusSeconds(5).minusNanos(1);
                display.tick();
                assertEquals(1, state.calls.size());
                clock.now = clock.now.plusNanos(1);
                display.tick();
                assertEquals(2, state.calls.size());
                assertEquals(clock.now, state.calls.get(1).now());
                clock.now = clock.now.plusSeconds(1000);
                display.tick();
                assertEquals(3, state.calls.size());
                assertEquals(clock.now, state.calls.get(2).now());
                clock.now = clock.now.minusSeconds(100);
                display.tick();
                assertEquals(4, state.calls.size());
                assertEquals(clock.now, state.calls.get(3).now());
            } finally { display.clear(); }
            display.tick();
            assertEquals(4, state.calls.size());
            assertFalse(display.running());
            assertTrue(visible.get().isEmpty());
        });
    }

    @Test void equalHeadsGenerateOnceAndEqualDigitsDoNotCollapseAlternatives() throws Exception {
        edt(() -> {
            TotpDisplay display = new TotpDisplay(new MutableClock(), ignored -> { });
            TokenAlternative a = alternative(TokenStatus.ACTIVE, "same", "account", TotpAlgorithm.SHA1, 6, 30,
                    head(1, ClientMetadata.empty()), head(2, ClientMetadata.empty()), head(3, ClientMetadata.empty()));
            TokenState equalHeads = token(1, a);
            State first = new State(equalHeads);
            try {
                display.select(first.value, equalHeads);
                assertEquals(1, first.calls.size());
                TokenAlternative b = active("same");
                TokenState conflict = token(2, a, b);
                State next = new State(conflict);
                AtomicReference<List<TotpDisplay.Display>> visible = new AtomicReference<>();
                TotpDisplay second = new TotpDisplay(new MutableClock(), visible::set);
                try {
                    second.select(next.value, conflict);
                    assertEquals(2, next.calls.size());
                    assertEquals(2, visible.get().size());
                    assertEquals(visible.get().get(0).code(), visible.get().get(1).code());
                    assertNotEquals(visible.get().get(0).label(), visible.get().get(1).label());
                } finally { second.clear(); }
            } finally { display.clear(); }
        });
    }

    @Test void tombstonesStatusOnlyConflictAndIncompleteHaveCorrectCodeEligibility() throws Exception {
        edt(() -> {
            AtomicReference<List<TotpDisplay.Display>> visible = new AtomicReference<>();
            TotpDisplay display = new TotpDisplay(new MutableClock(), visible::set);
            TokenAlternative active = active("issuer");
            TokenAlternative tombstone = alternative(TokenStatus.TOMBSTONED, "issuer", "account", TotpAlgorithm.SHA1, 6, 30);
            TokenState onlyDead = token(1, tombstone);
            TokenState mixed = token(1, active, tombstone);
            State state = new State(mixed);
            try {
                display.select(state.value, onlyDead);
                assertFalse(display.running());
                assertTrue(visible.get().isEmpty());
                assertTrue(TokenPresentation.row(onlyDead).text().contains("TOMBSTONED"));
                display.select(state.value, mixed);
                assertTrue(TokenPresentation.row(mixed).text().contains("CONFLICT"));
                assertTrue(TokenPresentation.detail(mixed).contains("Status: TOMBSTONED"));
                assertTrue(TokenPresentation.detail(mixed).contains("Status: ACTIVE"));
                assertEquals(1, state.calls.size());
                assertSame(active, state.calls.get(0).alternative());
                display.select(state.value, token(3));
                assertFalse(display.running());
                assertTrue(visible.get().isEmpty());
                assertEquals(1, state.calls.size());
            } finally { display.clear(); }
        });
    }

    @Test void replacementAndCloseDropOldGenerationAndFailuresDoNotRetry() throws Exception {
        edt(() -> {
            MutableClock clock = new MutableClock();
            AtomicReference<List<TotpDisplay.Display>> visible = new AtomicReference<>();
            TotpDisplay display = new TotpDisplay(clock, visible::set);
            TokenState a = token(1, active("a"));
            TokenState b = token(1, active("b"));
            State first = new State(a);
            State next = new State(b);
            try {
                display.select(first.value, a);
                next.fail = true;
                display.select(next.value, b);
                assertFalse(display.running());
                assertEquals("Code unavailable", visible.get().get(0).code());
                clock.now = clock.now.plusSeconds(100);
                display.tick(); display.tick();
                assertEquals(1, first.calls.size());
                assertEquals(1, next.calls.size());
                next.fail = false;
                display.select(next.value, b);
                assertTrue(display.running());
                assertEquals(2, next.calls.size());
                assertSame(b.alternatives().get(0), next.calls.get(1).alternative());
            } finally { display.clear(); }
            clock.now = clock.now.plusSeconds(100);
            display.tick();
            assertEquals(2, next.calls.size());
            assertTrue(visible.get().isEmpty());
        });
    }
}
