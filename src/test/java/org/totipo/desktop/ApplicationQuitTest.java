package org.totipo.desktop;

import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import org.junit.jupiter.api.Test;
import org.totipo.CreateVaultResult;
import org.totipo.OpenResult;
import org.totipo.desktop.ui.VaultPanel;
import static org.junit.jupiter.api.Assertions.*;
import static org.totipo.desktop.TestSupport.*;

class ApplicationQuitTest {
    /** Headless window stand-in with the production menu panel and window-close callback. */
    private static final class MenuWindow extends Window {
        final VaultPanel panel = new VaultPanel();
        @Override public void quitAction(Runnable action) {
            super.quitAction(action); panel.exitAction(action);
        }
        void exit() { panel.menuBar().getMenu(0).getItem(0).doClick(0); }
    }

    @Test void fileExitRunsApplicationCleanupExactlyOnce() throws Exception { quit(true); }

    @Test void mainWindowCloseRunsSameQuitPathWithoutReturningToLauncher() throws Exception { quit(false); }

    private void quit(boolean fileExit) throws Exception {
        CountDownLatch releaseClose = new CountDownLatch(1);
        Session session = new Session(releaseClose);
        Launcher launcher = new Launcher();
        MenuWindow window = onEdt(MenuWindow::new);
        VaultAccess access = new VaultAccess() {
            public OpenResult open(Path path, char[] password) { return new OpenResult.Opened(session); }
            public CreateVaultResult create(Path path, char[] password) { throw new AssertionError(); }
        };
        var app = onEdt(() -> new DesktopApplication(access, launcher, path -> window));
        try {
            edt(() -> { app.show(); app.begin(Path.of("vault"), new char[] {'p'}, false); });
            await(launcher.ready);
            int shownBeforeQuit = launcher.shown;
            edt(() -> {
                if (fileExit) { window.exit(); } else { window.close.run(); }
                assertTrue(window.closing);
                assertFalse(app.executorShutdown());
                assertEquals(1, launcher.disposed.getCount());
                // Both entry points may fire again while asynchronous close is pending.
                window.exit(); window.close.run(); launcher.close.run();
            });
            await(session.closeEntered);
            assertEquals(1, session.closes.get());
            assertEquals(1, session.subscription.cancels.get());
            releaseClose.countDown();
            await(launcher.disposed);
            edt(() -> {
                assertTrue(app.executorShutdown());
                assertEquals(1, window.disposals); assertEquals(1, launcher.disposals);
                assertEquals(shownBeforeQuit, launcher.shown);
                window.exit(); window.close.run(); app.shutdown();
                assertEquals(1, window.disposals); assertEquals(1, launcher.disposals);
            });
            assertEquals(1, session.closes.get());
        } finally {
            releaseClose.countDown(); edt(app::shutdown); await(launcher.disposed);
        }
    }
}
