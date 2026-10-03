package org.totipo.desktop;

import org.totipo.*;
import org.totipo.desktop.clipboard.*;
import java.nio.file.Path;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.totipo.desktop.TestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class ClipboardLifecycleTest {
    private static final class CopyWindow extends Window {
        TotpClipboard.Copy copy;
        final CountDownLatch shown = new CountDownLatch(1);
        @Override public void copyAction(TotpClipboard.Copy action) { copy = action; }
        @Override public void showWindow() { super.showWindow(); shown.countDown(); }
        String copyNow() {
            Instant now = Instant.parse("2026-01-01T00:00:00Z");
            return copy.copy("001234", now.minusSeconds(1), now.plusSeconds(8), now);
        }
    }

    @Test void applicationSharesOneManagerAcrossWindowsAndGuardsClosingCallbacks() throws Exception {
        ClipboardProbe probe = new ClipboardProbe();
        Session a = new Session(), b = new Session();
        CopyWindow wa = new CopyWindow(), wb = new CopyWindow();
        AtomicInteger opened = new AtomicInteger(), windows = new AtomicInteger();
        VaultAccess access = new VaultAccess() {
            public OpenResult open(Path path, char[] password) {
                return new OpenResult.Opened(opened.getAndIncrement() == 0 ? a : b);
            }
            public CreateVaultResult create(Path path, char[] password) { throw new AssertionError(); }
        };
        Launcher launcher = new Launcher();
        DesktopApplication app = onEdt(() -> new DesktopApplication(access, launcher,
                path -> windows.getAndIncrement() == 0 ? wa : wb, probe.manager()));
        try {
            edt(() -> app.begin(Path.of("a"), new char[0], false)); await(wa.shown);
            edt(() -> app.begin(Path.of("b"), new char[0], false)); await(wb.shown);
            var current = onEdt(() -> {
                assertEquals(TotpClipboard.COPIED, wa.copyNow());
                assertEquals(TotpClipboard.COPIED, wb.copyNow());
                var payload = probe.payload();
                // Session retirement remains independent; user window-close now quits the application.
                a.subscriber.onComplete();
                return payload;
            });
            await(wa.disposed);
            edt(() -> {
                assertSame(current, probe.payload());
                assertEquals(TotpClipboard.UNAVAILABLE, wa.copyNow());
                assertEquals(2, probe.writes());
                app.shutdown();
                probe.assertEmpty();
                assertEquals(TotpClipboard.UNAVAILABLE, wb.copyNow());
            });
            await(launcher.disposed);
            assertEquals(1, a.closes.get()); assertEquals(1, b.closes.get());
            assertEquals(1, a.subscription.cancels.get()); assertEquals(1, b.subscription.cancels.get());
        } finally { edt(app::shutdown); await(launcher.disposed); }
    }

    @Test void originCloseClearsBeforeSessionFinishesAndUnavailableClipboardCannotDelayDisposal() throws Exception {
        for (boolean unavailable : new boolean[]{false, true}) {
            ClipboardProbe probe = new ClipboardProbe();
            CountDownLatch release = new CountDownLatch(1);
            Session session = new Session(release);
            CopyWindow window = new CopyWindow();
            CountDownLatch retired = new CountDownLatch(1);
            VaultWindowController controller = onEdt(() -> new VaultWindowController(session, window, 11,
                    ignored -> retired.countDown(), ignored -> {}, probe.manager()));
            try {
                edt(() -> {
                    controller.start(); window.copyNow();
                    if (unavailable) { probe.unavailable(); }
                    controller.close();
                    if (!unavailable) { probe.assertEmpty(); }
                    assertTrue(window.closing);
                    assertEquals(TotpClipboard.UNAVAILABLE, window.copyNow());
                });
                await(session.closeEntered);
                assertEquals("totipo-session-11", session.closeThread);
                release.countDown(); await(retired);
                assertEquals(0, window.disposed.getCount());
            } finally {
                release.countDown(); edt(controller::close); await(retired);
                edt(probe.manager()::shutdown);
            }
        }
    }
}
