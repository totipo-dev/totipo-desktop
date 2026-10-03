package org.totipo.desktop;

import org.totipo.CreateVaultResult;
import org.totipo.OpenResult;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import static org.totipo.desktop.TestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class DesktopApplicationTest {
    private static final Path DIRECTORY = Path.of("selected-directory");

    @Test void emptyPasswordCreationRequiresExplicitDecisionButOpenDoesNot() throws Exception {
        for (boolean create : new boolean[] {false, true}) {
            for (boolean confirm : new boolean[] {false, true}) {
                Access access = new Access();
                Launcher launcher = new Launcher(); launcher.allowEmptyPassword = confirm;
                DesktopApplication app = onEdt(() -> new DesktopApplication(access, launcher, path -> new Window()));
                try {
                    edt(() -> app.begin(DIRECTORY, new char[0], create));
                    await(launcher.ready);
                    assertEquals(create ? 1 : 0, launcher.emptyConfirmations);
                    assertEquals(create && confirm ? 1 : 0, access.creates.get());
                    assertEquals(create ? 0 : 1, access.opens.get());
                } finally { edt(app::shutdown); await(launcher.disposed); }
            }
        }
    }

    @Test void shutdownAndReentrantActionsDuringEmptyConfirmationCannotCreate() throws Exception {
        Access access = new Access(); Launcher launcher = new Launcher(); launcher.allowEmptyPassword = true;
        DesktopApplication app = onEdt(() -> new DesktopApplication(access, launcher, path -> new Window()));
        launcher.duringEmptyConfirmation = () -> {
            app.begin(DIRECTORY, new char[0], true);
            app.shutdown();
        };
        edt(() -> app.begin(DIRECTORY, new char[0], true));
        await(launcher.disposed);
        assertEquals(1, launcher.emptyConfirmations);
        assertEquals(0, access.creates.get());
        assertTrue(app.executorShutdown());
    }

    private static final class Access implements VaultAccess {
        final AtomicInteger opens = new AtomicInteger();
        final AtomicInteger creates = new AtomicInteger();
        Function<char[], OpenResult> open = password -> new OpenResult.Absent();
        Function<char[], CreateVaultResult> create = password -> new CreateVaultResult.Failed();
        @Override public OpenResult open(Path path, char[] password) {
            assertFalse(SwingUtilities.isEventDispatchThread());
            assertEquals("totipo-application", Thread.currentThread().getName());
            opens.incrementAndGet();
            return open.apply(password);
        }
        @Override public CreateVaultResult create(Path path, char[] password) {
            assertFalse(SwingUtilities.isEventDispatchThread());
            assertEquals("totipo-application", Thread.currentThread().getName());
            creates.incrementAndGet();
            return create.apply(password);
        }
    }

    @Test void everyNonSessionOpenOutcomeIsDistinctAndWipesPassword() throws Exception {
        List<OpenResult> results = List.of(new OpenResult.Absent(), new OpenResult.Unavailable(),
                new OpenResult.InvalidVault(), new OpenResult.AuthenticationFailed());
        List<String> titles = List.of("Vault absent", "Vault unavailable", "Invalid vault", "Authentication did not succeed");
        for (int i = 0; i < results.size(); i++) {
            OpenResult result = results.get(i);
            Access access = new Access();
            char[] password = {'s', 'e', 'c', 'r', 'e', 't'};
            access.open = received -> {
                assertSame(password, received);
                assertEquals('s', received[0]);
                return result;
            };
            Launcher launcher = new Launcher();
            DesktopApplication app = onEdt(() -> new DesktopApplication(access, launcher,
                    path -> { throw new AssertionError("No session to display"); }));
            try {
                edt(() -> app.begin(DIRECTORY, password, false));
                await(launcher.ready);
                assertArrayEquals(new char[password.length], password);
                String expected = titles.get(i);
                edt(() -> {
                    assertEquals(List.of(expected), launcher.titles);
                    assertFalse(launcher.busy);
                    if (result instanceof OpenResult.AuthenticationFailed) {
                        assertTrue(launcher.messages.getFirst().contains("does not prove"));
                        assertTrue(launcher.messages.getFirst().contains("changed or become unusable"));
                    }
                });
                assertEquals(1, access.opens.get());
                assertEquals(0, access.creates.get());
            } finally {
                edt(app::shutdown);
                await(launcher.disposed);
                assertTrue(app.executorShutdown());
            }
        }
    }

    @Test void everyNonSessionCreateOutcomeIsDistinctAndUncertaintyNeverRetriesOrOpens() throws Exception {
        List<CreateVaultResult> results = List.of(new CreateVaultResult.AlreadyExists(),
                new CreateVaultResult.Failed(), new CreateVaultResult.Uncertain());
        List<String> titles = List.of("Vault already exists", "Creation failed", "Creation uncertain");
        for (int i = 0; i < results.size(); i++) {
            CreateVaultResult result = results.get(i);
            Access access = new Access();
            char[] password = {'p'};
            access.create = received -> { assertSame(password, received); return result; };
            Launcher launcher = new Launcher();
            DesktopApplication app = onEdt(() -> new DesktopApplication(access, launcher,
                    path -> { throw new AssertionError("No session"); }));
            try {
                edt(() -> app.begin(DIRECTORY, password, true));
                await(launcher.ready);
                assertArrayEquals(new char[1], password);
                String expected = titles.get(i);
                edt(() -> {
                    assertEquals(List.of(expected), launcher.titles);
                    if (result instanceof CreateVaultResult.Uncertain) {
                        String message = launcher.messages.getFirst();
                        assertTrue(message.contains("may have succeeded"));
                        assertTrue(message.contains("cannot assert"));
                        assertTrue(message.contains("Do not blindly retry"));
                        assertTrue(message.contains("Open Vault"));
                    }
                });
                assertEquals(0, access.opens.get());
                assertEquals(1, access.creates.get());
            } finally {
                edt(app::shutdown);
                await(launcher.disposed);
            }
        }
    }

    @Test void runtimeFailureWipesPasswordAndDoesNotBecomeProtocolOutcome() throws Exception {
        for (boolean create : new boolean[] {false, true}) {
            Access access = new Access();
            access.open = password -> { throw new IllegalStateException(); };
            access.create = password -> { throw new IllegalStateException(); };
            Launcher launcher = new Launcher();
            DesktopApplication app = onEdt(() -> new DesktopApplication(access, launcher, path -> new Window()));
            char[] password = {'s'};
            try {
                edt(() -> app.begin(DIRECTORY, password, create));
                await(launcher.ready);
                assertArrayEquals(new char[1], password);
                edt(() -> assertEquals(List.of("Application failure"), launcher.titles));
            } finally {
                edt(app::shutdown);
                await(launcher.disposed);
            }
        }
    }

    @Test void successfulOpenAndCreateTransferOwnershipAndWipePassword() throws Exception {
        for (boolean create : new boolean[] {false, true}) {
            Access access = new Access();
            Session session = new Session();
            access.open = password -> new OpenResult.Opened(session);
            access.create = password -> new CreateVaultResult.Created(session);
            Launcher launcher = new Launcher();
            Window window = new Window();
            AtomicInteger windows = new AtomicInteger();
            DesktopApplication app = onEdt(() -> new DesktopApplication(access, launcher, path -> {
                assertEquals(DIRECTORY, path);
                windows.incrementAndGet();
                return window;
            }));
            char[] password = {'p'};
            try {
                edt(() -> app.begin(DIRECTORY, password, create));
                await(launcher.ready);
                assertArrayEquals(new char[1], password);
                assertEquals(1, windows.get());
                assertEquals(0, session.closes.get());
                edt(() -> {
                    window.refresh.run();
                    window.close.run();
                    window.close.run();
                });
                await(window.disposed);
                assertEquals(1, session.closes.get());
                assertEquals(1, session.refreshes.get());
                assertEquals("totipo-session-1", session.closeThread);
                await(launcher.disposed);
                assertTrue(app.executorShutdown());
            } finally {
                edt(app::shutdown);
                await(launcher.disposed);
            }
            assertEquals(1, session.closes.get());
        }
    }

    @Test void shutdownDuringOpenOrCreateClosesReturnedSessionWithoutWindow() throws Exception {
        for (boolean create : new boolean[] {false, true}) {
            Access access = new Access();
            CountDownLatch entered = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            CountDownLatch releaseClose = new CountDownLatch(1);
            Session session = new Session(releaseClose);
            access.open = password -> { entered.countDown(); await(release); return new OpenResult.Opened(session); };
            access.create = password -> { entered.countDown(); await(release); return new CreateVaultResult.Created(session); };
            Launcher launcher = new Launcher();
            DesktopApplication app = onEdt(() -> new DesktopApplication(access, launcher,
                    path -> { throw new AssertionError("Window created during shutdown"); }));
            char[] password = {'p'};
            try {
                edt(() -> app.begin(DIRECTORY, password, create));
                await(entered);
                edt(() -> { app.shutdown(); app.shutdown(); assertTrue(launcher.busy); });
                assertFalse(app.executorShutdown());
                assertEquals(1, launcher.disposed.getCount());
                release.countDown();
                await(session.closeEntered);
                assertArrayEquals(new char[1], password);
                assertEquals(1, launcher.disposed.getCount());
                assertFalse(app.executorShutdown());
            } finally {
                release.countDown();
                releaseClose.countDown();
                edt(app::shutdown);
                await(launcher.disposed);
            }
            assertTrue(app.executorShutdown());
            assertEquals(1, session.closes.get());
            assertEquals("totipo-application", session.closeThread);
            assertEquals(create ? 0 : 1, access.opens.get());
            assertEquals(create ? 1 : 0, access.creates.get());
        }
    }

    @Test void failedWindowConstructionClosesUnclaimedSession() throws Exception {
        for (boolean create : new boolean[] {false, true}) {
            Access access = new Access();
            Session session = new Session();
            access.open = password -> new OpenResult.Opened(session);
            access.create = password -> new CreateVaultResult.Created(session);
            Launcher launcher = new Launcher();
            DesktopApplication app = onEdt(() -> new DesktopApplication(access, launcher,
                    path -> { throw new IllegalStateException("window creation failed"); }));
            try {
                edt(() -> app.begin(DIRECTORY, new char[] {'p'}, create));
                await(launcher.ready);
                assertEquals(1, session.closes.get());
                assertEquals("totipo-application", session.closeThread);
                edt(() -> assertEquals(List.of("Application failure"), launcher.titles));
            } finally {
                edt(app::shutdown);
                await(launcher.disposed);
            }
        }
    }

    @Test void onlyOnePasswordBearingOperationIsAcceptedAndResultDialogRemainsBusy() throws Exception {
        Access access = new Access();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        access.open = password -> { entered.countDown(); await(release); return new OpenResult.Absent(); };
        Launcher launcher = new Launcher();
        DesktopApplication app = onEdt(() -> new DesktopApplication(access, launcher, path -> new Window()));
        char[] rejected = {'x'};
        try {
            edt(() -> {
                launcher.duringMessage = () -> {
                    assertTrue(launcher.busy);
                    launcher.create.run();
                };
                app.begin(DIRECTORY, new char[] {'p'}, false);
            });
            await(entered);
            edt(() -> {
                assertTrue(launcher.busy);
                launcher.open.run();
                launcher.create.run();
                app.begin(DIRECTORY, rejected, true);
            });
            assertArrayEquals(new char[1], rejected);
            assertEquals(1, access.opens.get());
            assertEquals(0, access.creates.get());
            release.countDown();
            await(launcher.ready);
            assertEquals(0, access.creates.get());
        } finally {
            release.countDown();
            edt(app::shutdown);
            await(launcher.disposed);
        }
    }

    @Test void invalidHumanInputNeverReachesCore() throws Exception {
        Access access = new Access();
        Launcher launcher = new Launcher();
        DesktopApplication app = onEdt(() -> new DesktopApplication(access, launcher, path -> new Window()));
        char[] invalid = {'\uD800'};
        try {
            edt(() -> app.begin(DIRECTORY, invalid, false));
            assertArrayEquals(new char[1], invalid);
            assertEquals(0, access.opens.get());
            assertEquals(0, access.creates.get());
            edt(() -> {
                assertEquals(List.of("Invalid password input"), launcher.titles);
                assertFalse(launcher.busy);
            });
        } finally {
            edt(app::shutdown);
            await(launcher.disposed);
        }
    }

    @Test void nestedDialogShutdownWipesUnsubmittedPassword() throws Exception {
        Access access = new Access();
        Launcher launcher = new Launcher();
        DesktopApplication app = onEdt(() -> new DesktopApplication(access, launcher, path -> new Window()));
        edt(() -> {
            launcher.duringPassword = app::shutdown;
            launcher.create.run();
        });
        await(launcher.disposed);
        assertArrayEquals(new char[1], launcher.password);
        assertEquals(0, access.opens.get());
        assertEquals(0, access.creates.get());
        assertTrue(app.executorShutdown());
    }

    @Test void cancelledDirectoryOrPasswordReenablesLauncher() throws Exception {
        for (boolean directory : new boolean[] {true, false}) {
            Access access = new Access();
            Launcher launcher = new Launcher();
            if (directory) { launcher.directory = null; }
            else { launcher.password = null; }
            DesktopApplication app = onEdt(() -> new DesktopApplication(access, launcher, path -> new Window()));
            try {
                edt(launcher.open);
                await(launcher.ready);
                edt(() -> assertFalse(launcher.busy));
                assertEquals(0, access.opens.get());
            } finally {
                edt(app::shutdown);
                await(launcher.disposed);
            }
        }
    }

    @Test void independentWindowsCloseOnTheirOwnExecutorsAndShutdownWaitsForBoth() throws Exception {
        Access access = new Access();
        CountDownLatch releaseOne = new CountDownLatch(1);
        CountDownLatch releaseTwo = new CountDownLatch(1);
        Session one = new Session(releaseOne);
        Session two = new Session(releaseTwo);
        AtomicInteger operations = new AtomicInteger();
        access.open = password -> new OpenResult.Opened(operations.getAndIncrement() == 0 ? one : two);
        Launcher launcher = new Launcher();
        CountDownLatch secondWindow = new CountDownLatch(1);
        AtomicInteger windows = new AtomicInteger();
        DesktopApplication app = onEdt(() -> new DesktopApplication(access, launcher, path -> {
            Window window = new Window();
            if (windows.incrementAndGet() == 2) { secondWindow.countDown(); }
            return window;
        }));
        try {
            edt(() -> app.begin(DIRECTORY, new char[0], false));
            await(launcher.ready);
            edt(() -> app.begin(DIRECTORY, new char[0], false));
            await(secondWindow);
            edt(app::shutdown);
            await(one.closeEntered);
            await(two.closeEntered);
            assertNotEquals(one.closeThread, two.closeThread);
            releaseOne.countDown();
            edt(() -> { });
            assertEquals(1, launcher.disposed.getCount());
            assertFalse(app.executorShutdown());
        } finally {
            releaseOne.countDown();
            releaseTwo.countDown();
            edt(app::shutdown);
            await(launcher.disposed);
        }
        assertEquals(1, one.closes.get());
        assertEquals(1, two.closes.get());
        assertTrue(app.executorShutdown());
    }

    @Test void shutdownInsideResultDialogWaitsForPresentationToReturn() throws Exception {
        Access access = new Access();
        Launcher launcher = new Launcher();
        DesktopApplication app = onEdt(() -> new DesktopApplication(access, launcher, path -> new Window()));
        edt(() -> {
            launcher.duringMessage = () -> {
                app.shutdown();
                assertTrue(launcher.busy);
                assertFalse(app.executorShutdown());
                assertEquals(1, launcher.disposed.getCount());
            };
            app.begin(DIRECTORY, new char[] {'p'}, false);
        });
        await(launcher.disposed);
        assertTrue(app.executorShutdown());
        char[] rejected = {'x'};
        edt(() -> app.begin(DIRECTORY, rejected, true));
        assertArrayEquals(new char[1], rejected);
        assertEquals(0, access.creates.get());
    }
}
