package dev.totipo.desktop.clipboard;

import java.awt.datatransfer.*;
import java.awt.HeadlessException;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import static dev.totipo.desktop.TestSupport.edt;
import static org.junit.jupiter.api.Assertions.*;

class TotpClipboardTest {
    static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    static final class Memory implements TotpClipboard.Access {
        Transferable value = new StringSelection("previous private user data");
        ClipboardOwner owner;
        int reads;
        int writes;
        RuntimeException readFailure;
        RuntimeException writeFailure;
        public Transferable current() {
            reads++;
            if (readFailure != null) { throw readFailure; }
            return value;
        }
        public void set(Transferable next, ClipboardOwner nextOwner) {
            if (writeFailure != null) { throw writeFailure; }
            writes++; value = next; owner = nextOwner;
        }
    }
    static final class Scheduled {
        final int delay;
        final Runnable action;
        boolean cancelled;
        Scheduled(int delay, Runnable action) { this.delay = delay; this.action = action; }
        void fire() { action.run(); } // Can deliberately deliver a cancelled, stale callback.
    }
    static final class Manual implements TotpClipboard.Scheduler {
        final List<Scheduled> tasks = new ArrayList<>();
        public Runnable after(int milliseconds, Runnable action) {
            Scheduled task = new Scheduled(milliseconds, action); tasks.add(task);
            return () -> task.cancelled = true;
        }
        Scheduled last() { return tasks.get(tasks.size() - 1); }
    }
    static final class Harness {
        final Memory memory = new Memory();
        final Manual timer = new Manual();
        final TotpClipboard manager = new TotpClipboard(memory, timer, java.time.Clock.fixed(NOW, java.time.ZoneOffset.UTC));
        final Object a = new Object();
        final Object b = new Object();
        String copy(Object origin, long seconds) {
            return manager.copy(origin, "001234", NOW.minusSeconds(1), NOW.plusSeconds(seconds), NOW);
        }
    }
    static String marker(Transferable value) {
        try { return (String) value.getTransferData(TotpClipboardPayload.MARKER); }
        catch (Exception failure) { throw new AssertionError("Marker unavailable"); }
    }
    static void textIs(Transferable value, String expected) {
        try { assertTrue(expected.equals(value.getTransferData(DataFlavor.stringFlavor))); }
        catch (Exception failure) { throw new AssertionError("Clipboard text unavailable"); }
    }

    @Test void exactLeadingZerosOpaqueMarkerAndNoPreviousSnapshotOrRestore() throws Exception {
        edt(() -> {
            Harness h = new Harness();
            assertEquals(TotpClipboard.COPIED, h.copy(h.a, 8));
            assertEquals(0, h.memory.reads);
            textIs(h.memory.value, "001234");
            assertEquals(2, h.memory.value.getTransferDataFlavors().length);
            assertEquals(marker(h.memory.value), UUID.fromString(marker(h.memory.value)).toString());
            assertEquals(4, UUID.fromString(marker(h.memory.value)).version());
            assertEquals(8000, h.timer.last().delay);
            h.timer.last().fire();
            textIs(h.memory.value, "");
            assertEquals(1, h.memory.reads);
            assertEquals(2, h.memory.writes);
        });
    }

    @Test void deadlineIsEarlierOfExpiryAndThirtySeconds() throws Exception {
        edt(() -> {
            for (int seconds : new int[]{8, 24, 30, 240}) {
                Harness h = new Harness(); h.copy(h.a, seconds);
                assertEquals(Math.min(seconds, 30) * 1000, h.timer.last().delay);
                h.timer.last().fire(); textIs(h.memory.value, "");
            }
        });
    }

    @Test void externalDifferentAndIdenticalPlainTextAreNeverCleared() throws Exception {
        edt(() -> {
            for (String replacement : new String[]{"unrelated", "001234"}) {
                Harness h = new Harness(); h.copy(h.a, 8);
                Transferable external = new StringSelection(replacement);
                h.memory.value = external; // No ownership callback: identity must still be checked.
                h.timer.last().fire();
                assertSame(external, h.memory.value);
                assertEquals(1, h.memory.writes);
            }
        });
    }

    @Test void newCopyFreshMarkerAndLateOldDeadlineCannotClearIt() throws Exception {
        edt(() -> {
            Harness h = new Harness(); h.copy(h.a, 8);
            String first = marker(h.memory.value); Scheduled old = h.timer.last();
            h.copy(h.b, 24);
            Transferable newer = h.memory.value;
            assertNotEquals(first, marker(newer)); assertTrue(old.cancelled);
            old.fire(); assertSame(newer, h.memory.value);
            assertEquals(2, h.memory.writes); // New copy has no intervening empty write.
            h.timer.last().fire(); textIs(h.memory.value, "");
        });
    }

    @Test void ownershipLossFromBackgroundThreadIsExactAndDispatchedToEdt() throws Exception {
        Harness h = new Harness();
        ClipboardOwner[] owner = new ClipboardOwner[1];
        edt(() -> {
            h.copy(h.a, 8); owner[0] = h.memory.owner;
            h.copy(h.b, 24);
        });
        owner[0].lostOwnership(null, null); // Test thread, outside EDT.
        edt(() -> {
            h.timer.last().fire(); textIs(h.memory.value, "");
        });
    }

    @Test void currentOwnershipLossRetiresLease() throws Exception {
        Harness h = new Harness();
        edt(() -> h.copy(h.a, 8));
        h.memory.owner.lostOwnership(null, null);
        edt(() -> {
            h.timer.last().fire();
            assertEquals(0, h.memory.reads); assertEquals(1, h.memory.writes);
        });
    }

    @Test void originatingCloseAndCrossVaultClose() throws Exception {
        edt(() -> {
            Harness h = new Harness(); h.copy(h.a, 8); h.copy(h.b, 24);
            Transferable b = h.memory.value;
            h.manager.originClosing(h.a); assertSame(b, h.memory.value);
            h.manager.originClosing(h.b); textIs(h.memory.value, "");
            h.timer.tasks.forEach(Scheduled::fire); assertEquals(3, h.memory.writes);
        });
    }

    @Test void closeAndShutdownLeaveExternalClipboardUntouched() throws Exception {
        edt(() -> {
            for (boolean shutdown : new boolean[]{false, true}) {
                Harness h = new Harness(); h.copy(h.a, 8);
                Transferable external = new StringSelection("001234"); h.memory.value = external;
                if (shutdown) { h.manager.shutdown(); } else { h.manager.originClosing(h.a); }
                assertSame(external, h.memory.value); assertEquals(1, h.memory.writes);
            }
        });
    }

    @Test void shutdownClearsCurrentAndRejectsFurtherCopy() throws Exception {
        edt(() -> {
            Harness h = new Harness(); h.copy(h.a, 8); h.manager.shutdown();
            textIs(h.memory.value, ""); assertEquals(TotpClipboard.UNAVAILABLE, h.copy(h.b, 24));
            assertEquals(2, h.memory.writes);
        });
    }

    @Test void copyUnavailabilityCreatesNoLeaseOrRetry() throws Exception {
        edt(() -> {
            for (RuntimeException failure : List.of(new IllegalStateException(), new SecurityException(), new HeadlessException())) {
                Harness h = new Harness(); h.memory.writeFailure = failure;
                assertEquals(TotpClipboard.UNAVAILABLE, h.copy(h.a, 8));
                assertTrue(h.timer.tasks.isEmpty()); h.manager.shutdown();
                assertEquals(0, h.memory.reads); assertEquals(0, h.memory.writes);
            }
        });
    }

    @Test void failedReplacementPreservesPriorSuccessfulLease() throws Exception {
        edt(() -> {
            Harness h = new Harness(); h.copy(h.a, 8);
            h.memory.writeFailure = new IllegalStateException();
            assertEquals(TotpClipboard.UNAVAILABLE, h.copy(h.b, 24));
            assertEquals(1, h.timer.tasks.size());
            h.memory.writeFailure = null; h.timer.last().fire(); textIs(h.memory.value, "");
        });
    }

    @Test void unavailableInspectionStopsAfterFiveSecondsWithoutBlindWrites() throws Exception {
        edt(() -> {
            Harness h = new Harness(); h.copy(h.a, 8);
            h.memory.readFailure = new IllegalStateException(); h.timer.last().fire();
            for (int i = 0; i < 20; i++) {
                assertEquals(250, h.timer.last().delay); h.timer.last().fire();
            }
            assertEquals(21, h.timer.tasks.size()); assertEquals(21, h.memory.reads);
            assertEquals(1, h.memory.writes);
            h.manager.shutdown(); assertEquals(21, h.memory.reads);
        });
    }

    @Test void retryRechecksMarkerWhenAccessReturns() throws Exception {
        edt(() -> {
            for (boolean replace : new boolean[]{false, true}) {
                Harness h = new Harness(); h.copy(h.a, 8);
                h.memory.readFailure = new IllegalStateException(); h.timer.last().fire();
                Transferable external = new StringSelection("001234");
                if (replace) { h.memory.value = external; }
                h.memory.readFailure = null; h.timer.last().fire();
                if (replace) { assertSame(external, h.memory.value); assertEquals(1, h.memory.writes); }
                else { textIs(h.memory.value, ""); assertEquals(2, h.memory.writes); }
            }
        });
    }

    @Test void unavailableClearWriteAlsoRechecksMarkerOnRetry() throws Exception {
        edt(() -> {
            Harness h = new Harness(); h.copy(h.a, 8);
            h.memory.writeFailure = new IllegalStateException(); h.timer.last().fire();
            h.memory.writeFailure = null;
            Transferable external = new StringSelection("new"); h.memory.value = external;
            h.timer.last().fire(); assertSame(external, h.memory.value); assertEquals(1, h.memory.writes);
        });
    }

    @Test void retryFromOldLeaseCannotAffectNewCopy() throws Exception {
        edt(() -> {
            Harness h = new Harness(); h.copy(h.a, 8);
            h.memory.readFailure = new IllegalStateException(); h.timer.last().fire();
            Scheduled retry = h.timer.last(); h.memory.readFailure = null;
            h.copy(h.b, 24); Transferable newer = h.memory.value;
            retry.fire(); assertSame(newer, h.memory.value);
        });
    }

    @Test void invalidIntervalsNeverWriteOrSchedule() throws Exception {
        edt(() -> {
            Harness h = new Harness();
            assertEquals(TotpClipboard.UNAVAILABLE, h.manager.copy(h.a, "001234", NOW, NOW, NOW));
            assertEquals(TotpClipboard.UNAVAILABLE, h.manager.copy(h.a, "001234", NOW.plusSeconds(1), NOW.plusSeconds(8), NOW));
            assertEquals(0, h.memory.writes); assertTrue(h.timer.tasks.isEmpty());
        });
    }

    @Test void differentMarkerEvenWithSameTextIsNotOurCopy() throws Exception {
        edt(() -> {
            Harness h = new Harness(); h.copy(h.a, 8);
            Transferable external = new TotpClipboardPayload("001234", UUID.randomUUID().toString(), () -> {});
            h.memory.value = external; h.timer.last().fire();
            assertSame(external, h.memory.value); assertEquals(1, h.memory.writes);
        });
    }

    @Test void lateRetryPastElapsedBoundDoesNotInspectOrWrite() throws Exception {
        edt(() -> {
            Instant[] now = {NOW};
            java.time.Clock clock = new java.time.Clock() {
                public java.time.ZoneId getZone() { return java.time.ZoneOffset.UTC; }
                public java.time.Clock withZone(java.time.ZoneId zone) { return this; }
                public Instant instant() { return now[0]; }
            };
            Memory memory = new Memory(); Manual timer = new Manual();
            TotpClipboard manager = new TotpClipboard(memory, timer, clock);
            manager.copy(new Object(), "001234", NOW.minusSeconds(1), NOW.plusSeconds(8), NOW);
            now[0] = NOW.plusSeconds(8);
            memory.readFailure = new IllegalStateException(); timer.last().fire();
            now[0] = now[0].plusSeconds(6);
            memory.readFailure = null; timer.last().fire();
            assertEquals(1, memory.reads); assertEquals(1, memory.writes);
            manager.shutdown(); assertEquals(1, memory.reads);
        });
    }
}
