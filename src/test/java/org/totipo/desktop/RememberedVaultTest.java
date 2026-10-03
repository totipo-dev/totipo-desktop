package org.totipo.desktop;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.prefs.Preferences;
import org.totipo.OpenResult;
import org.totipo.CreateVaultResult;
import org.totipo.desktop.clipboard.TotpClipboard;
import org.totipo.desktop.ui.PasswordPromptResult;
import org.totipo.desktop.ui.PasswordPromptContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.totipo.desktop.TestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class RememberedVaultTest {
    @TempDir Path directory;

    static final class Store implements VaultPreferences {
        Path path;
        int writes;
        Store(Path path) { this.path = path; }
        public Optional<Path> lastVault() { return Optional.ofNullable(path); }
        public void setLastVault(Path value) { path = value.toAbsolutePath().normalize(); writes++; }
        public void clearLastVault() { path = null; }
    }
    static final class Access implements VaultAccess {
        OpenResult result = new OpenResult.Absent();
        final AtomicInteger opens = new AtomicInteger();
        public OpenResult open(Path path, char[] password) { opens.incrementAndGet(); return result; }
        public CreateVaultResult create(Path path, char[] password) { return new CreateVaultResult.Failed(); }
    }
    private DesktopApplication app(Access access, Launcher launcher, Window window, Store store) throws Exception {
        return onEdt(() -> new DesktopApplication(access, launcher, path -> window, new TotpClipboard(), store));
    }
    private void stop(DesktopApplication app, Launcher launcher) throws Exception {
        edt(app::shutdown); await(launcher.disposed);
    }

    @Test void noStoredVaultShowsLauncherWithoutPrompt() throws Exception {
        Launcher launcher = new Launcher(); Access access = new Access(); Store store = new Store(null);
        var app = app(access, launcher, new Window(), store);
        try {
            edt(app::show);
            assertEquals(1, launcher.shown); assertNull(launcher.passwordDirectory); assertEquals(0, access.opens.get());
        } finally { stop(app, launcher); }
    }

    @Test void existingStoredDirectoryGoesDirectlyToPathAwarePasswordFlow() throws Exception {
        Launcher launcher = new Launcher(); Access access = new Access(); Store store = new Store(directory);
        Session session = new Session(); access.result = new OpenResult.Opened(session);
        var app = app(access, launcher, new Window(), store);
        try {
            edt(app::show); await(launcher.ready);
            assertEquals(0, launcher.shown); assertEquals(0, launcher.directories);
            assertEquals(directory, launcher.passwordDirectory); assertEquals(1, access.opens.get());
            assertEquals(java.util.List.of(PasswordPromptContext.REMEMBERED_STARTUP), launcher.passwordContexts);
            assertEquals(1, store.writes);
        } finally { stop(app, launcher); }
        assertEquals(1, session.closes.get());
    }

    @Test void explicitOpenOfRememberedPathUsesExplicitWordingContext() throws Exception {
        Launcher launcher = new Launcher(); Access access = new Access(); Store store = new Store(directory);
        launcher.directory = directory;
        Session session = new Session(); access.result = new OpenResult.Opened(session);
        var app = app(access, launcher, new Window(), store);
        try {
            edt(launcher.open); await(launcher.ready);
            assertEquals(directory, launcher.passwordDirectory);
            assertEquals(java.util.List.of(PasswordPromptContext.EXPLICIT), launcher.passwordContexts);
        } finally { stop(app, launcher); }
    }

    @Test void missingAndNonDirectoryPreferencesAreClearedAndShowLauncherWarning() throws Exception {
        for (Path stale : new Path[] {directory.resolve("missing"), Files.createFile(directory.resolve("file"))}) {
            Launcher launcher = new Launcher(); Access access = new Access(); Store store = new Store(stale);
            var app = app(access, launcher, new Window(), store);
            try {
                edt(app::show); await(launcher.ready); edt(() -> assertNull(store.path)); assertTrue(launcher.shown >= 1);
                assertEquals(0, launcher.directories); assertEquals(0, access.opens.get());
                assertEquals(1, launcher.messages.size()); assertTrue(launcher.messages.get(0).contains("could not be found"));
            } finally { stop(app, launcher); }
        }
    }

    @Test void successfulOpenRemembersNormalizedPathOnlyAfterWindowAcceptsSession() throws Exception {
        Store store = new Store(directory); Launcher launcher = new Launcher(); Access access = new Access();
        Path next = directory.resolve("child/../next"); Session session = new Session();
        access.result = new OpenResult.Opened(session);
        var app = app(access, launcher, new Window(), store);
        try {
            edt(() -> { app.begin(next, new char[] {'p'}, false); assertEquals(0, store.writes); });
            await(launcher.ready); assertEquals(next.toAbsolutePath().normalize(), store.path); assertEquals(1, store.writes);
        } finally { stop(app, launcher); }
    }

    @Test void failedOpensNeverReplacePreviousSuccessfulPreference() throws Exception {
        for (OpenResult result : new OpenResult[] {new OpenResult.AuthenticationFailed(), new OpenResult.InvalidVault(),
                new OpenResult.Unavailable(), new OpenResult.Absent()}) {
            Store store = new Store(directory); Launcher launcher = new Launcher(); Access access = new Access();
            access.result = result; var app = app(access, launcher, new Window(), store);
            try {
                edt(() -> app.begin(directory.resolve("other"), new char[] {'p'}, false)); await(launcher.ready);
                assertEquals(directory, store.path); assertEquals(0, store.writes);
            } finally { stop(app, launcher); }
        }
    }

    @Test void cancelledDirectoryOrPasswordExitDoesNotReplaceRememberedVault() throws Exception {
        for (boolean cancelDirectory : new boolean[] {true, false}) {
            Store store = new Store(directory); Launcher launcher = new Launcher(); Access access = new Access();
            if (cancelDirectory) { launcher.directory = null; } else { launcher.password = null; }
            var app = app(access, launcher, new Window(), store);
            try {
                edt(launcher.open); assertEquals(directory, store.path); assertEquals(0, store.writes); assertEquals(0, access.opens.get());
            } finally { stop(app, launcher); }
        }
    }

    @Test void exitingRememberedPasswordQuitsWithoutLauncherOrForgetting() throws Exception {
        Store store = new Store(directory); Launcher launcher = new Launcher(); launcher.password = null;
        Access access = new Access(); var app = app(access, launcher, new Window(), store);
        try {
            edt(app::show); await(launcher.disposed);
            edt(() -> {
                assertEquals(0, launcher.shown); assertEquals(0, launcher.directories);
                assertEquals(directory, store.path); assertEquals(0, store.writes);
                assertEquals(0, access.opens.get()); assertTrue(app.executorShutdown());
                assertEquals(1, launcher.disposals);
                launcher.close.run(); app.show(); assertEquals(0, launcher.shown);
            });
        } finally { stop(app, launcher); }
    }

    @Test void rememberedPromptChangeVaultChooserCancellationKeepsPreferenceAndApplicationAlive() throws Exception {
        Store store = new Store(directory); Launcher launcher = new Launcher(); Access access = new Access();
        launcher.passwordAction = PasswordPromptResult.Action.CHANGE_VAULT; launcher.directory = null;
        var app = app(access, launcher, new Window(), store);
        try {
            edt(app::show); await(launcher.ready);
            edt(() -> {
                assertEquals(directory, launcher.passwordDirectory); assertEquals(directory, launcher.chooserLocation);
                assertEquals(1, launcher.directories); assertFalse(launcher.choosingCreate);
                assertEquals(1, launcher.shown); assertFalse(app.executorShutdown());
                assertEquals(1, launcher.disposed.getCount()); assertFalse(launcher.busy);
                assertEquals(directory, store.path); assertEquals(0, store.writes); assertEquals(0, access.opens.get());
            });
        } finally { stop(app, launcher); }
    }

    @Test void rememberedPromptChangeVaultThenAlternatePasswordExitKeepsPreference() throws Exception {
        Store store = new Store(directory); Launcher launcher = new Launcher(); Access access = new Access();
        launcher.passwordAction = PasswordPromptResult.Action.CHANGE_VAULT;
        Path alternate = directory.resolve("alternate"); launcher.directory = alternate;
        launcher.duringDirectory = () -> launcher.passwordAction = PasswordPromptResult.Action.EXIT;
        var app = app(access, launcher, new Window(), store);
        try {
            edt(app::show); await(launcher.disposed);
            edt(() -> {
                assertEquals(alternate, launcher.passwordDirectory); assertEquals(1, launcher.directories);
                assertEquals(0, launcher.shown); assertTrue(app.executorShutdown());
                assertEquals(directory, store.path); assertEquals(0, store.writes); assertEquals(0, access.opens.get());
            });
        } finally { stop(app, launcher); }
    }

    @Test void rememberedPromptChangeVaultSuccessfulAlternateOpenRemembersOnlyAfterSuccess() throws Exception {
        Store store = new Store(directory); Launcher launcher = new Launcher(); Access access = new Access();
        Session session = new Session(); access.result = new OpenResult.Opened(session);
        launcher.passwordAction = PasswordPromptResult.Action.CHANGE_VAULT;
        Path alternate = directory.resolve("alternate"); launcher.directory = alternate;
        launcher.duringDirectory = () -> {
            assertEquals(directory, store.path); assertEquals(0, store.writes); assertEquals(0, launcher.shown);
            launcher.passwordAction = null;
        };
        var app = app(access, launcher, new Window(), store);
        try {
            edt(app::show); await(launcher.ready);
            edt(() -> {
                assertEquals(alternate, launcher.passwordDirectory); assertEquals(directory, launcher.chooserLocation);
                assertEquals(java.util.List.of(PasswordPromptContext.REMEMBERED_STARTUP, PasswordPromptContext.EXPLICIT),
                        launcher.passwordContexts);
                assertEquals(1, launcher.directories); assertEquals(0, launcher.shown);
                assertEquals(alternate, store.path); assertEquals(1, store.writes); assertEquals(1, access.opens.get());
                assertFalse(app.executorShutdown());
            });
        } finally { stop(app, launcher); }
        assertEquals(1, session.closes.get());
    }

    @Test void rememberedPromptChangeVaultFailedAlternateOpenKeepsPreference() throws Exception {
        Store store = new Store(directory); Launcher launcher = new Launcher(); Access access = new Access();
        access.result = new OpenResult.AuthenticationFailed();
        launcher.passwordAction = PasswordPromptResult.Action.CHANGE_VAULT;
        Path alternate = directory.resolve("alternate"); launcher.directory = alternate;
        launcher.duringDirectory = () -> launcher.passwordAction = null;
        var app = app(access, launcher, new Window(), store);
        try {
            edt(app::show); await(launcher.ready);
            edt(() -> {
                assertEquals(alternate, launcher.passwordDirectory); assertEquals(1, access.opens.get());
                assertEquals(directory, store.path); assertEquals(0, store.writes);
                assertEquals(1, launcher.shown); assertFalse(app.executorShutdown());
                assertEquals(java.util.List.of("Authentication did not succeed"), launcher.titles);
            });
        } finally { stop(app, launcher); }
    }

    @Test void repeatedPromptChangesKeepFlowReservedAndNeverOpenAbandonedVaults() throws Exception {
        Store store = new Store(directory); Launcher launcher = new Launcher(); Access access = new Access();
        launcher.passwordAction = PasswordPromptResult.Action.CHANGE_VAULT;
        Path alternate = directory.resolve("alternate"); launcher.directory = alternate;
        AtomicInteger selections = new AtomicInteger();
        var app = app(access, launcher, new Window(), store);
        launcher.duringDirectory = () -> {
            launcher.open.run(); launcher.create.run(); app.show();
            assertTrue(launcher.busy); assertEquals(0, launcher.shown);
            if (selections.incrementAndGet() == 2) { launcher.directory = null; }
        };
        try {
            edt(app::show); await(launcher.ready);
            edt(() -> {
                assertEquals(2, launcher.directories); assertEquals(alternate, launcher.chooserLocation);
                assertEquals(0, access.opens.get()); assertEquals(1, launcher.shown);
                assertEquals(directory, store.path); assertEquals(0, store.writes);
                assertFalse(app.executorShutdown()); assertFalse(launcher.busy);
            });
        } finally { stop(app, launcher); }
    }

    @Test void shutdownInChangeVaultChooserPreventsAlternatePasswordAndOpen() throws Exception {
        Store store = new Store(directory); Launcher launcher = new Launcher(); Access access = new Access();
        launcher.passwordAction = PasswordPromptResult.Action.CHANGE_VAULT;
        launcher.directory = directory.resolve("alternate");
        var app = app(access, launcher, new Window(), store);
        launcher.duringDirectory = app::shutdown;
        try {
            edt(app::show); await(launcher.disposed);
            edt(() -> {
                assertEquals(directory, launcher.passwordDirectory); assertEquals(1, launcher.directories);
                assertEquals(0, access.opens.get()); assertEquals(0, launcher.shown);
                assertEquals(directory, store.path); assertEquals(0, store.writes);
                assertTrue(app.executorShutdown()); assertEquals(1, launcher.disposals);
            });
        } finally { stop(app, launcher); }
    }

    @Test void changeVaultChooserCancellationAndFailureKeepCurrentWindowSessionAndPreference() throws Exception {
        Store store = new Store(directory); Launcher launcher = new Launcher(); Access access = new Access();
        Session session = new Session(); access.result = new OpenResult.Opened(session); Window window = new Window();
        var app = app(access, launcher, window, store);
        try {
            edt(() -> app.begin(directory, new char[] {'p'}, false)); await(launcher.ready);
            edt(() -> { launcher.directory = null; window.changeVault.run(); });
            assertEquals(directory, store.path); assertEquals(1, store.writes);
            assertEquals(0, session.closes.get()); assertEquals(1, window.disposed.getCount());
            assertFalse(app.executorShutdown()); assertEquals(1, launcher.disposed.getCount());
            assertEquals(directory, launcher.chooserLocation); assertFalse(launcher.choosingCreate);
            edt(() -> {
                launcher.ready = new CountDownLatch(1); launcher.password = new char[] {'p'};
                launcher.directory = directory.resolve("other");
                access.result = new OpenResult.AuthenticationFailed(); window.changeVault.run();
            });
            await(launcher.ready); assertEquals(directory, store.path); assertEquals(1, store.writes);
            assertEquals(0, session.closes.get()); assertEquals(1, window.disposed.getCount());
        } finally { stop(app, launcher); }
    }

    @Test void successfulChangeVaultClosesOldSessionExactlyOnceAndRemembersReplacement() throws Exception {
        Store store = new Store(directory); Launcher launcher = new Launcher(); Access access = new Access();
        Session old = new Session(); Session next = new Session(); access.result = new OpenResult.Opened(old);
        Window first = new Window(); Window second = new Window(); AtomicInteger windows = new AtomicInteger();
        var app = onEdt(() -> new DesktopApplication(access, launcher,
                path -> windows.getAndIncrement() == 0 ? first : second, new TotpClipboard(), store));
        try {
            edt(() -> app.begin(directory, new char[] {'p'}, false)); await(launcher.ready);
            Path target = directory.resolve("next");
            edt(() -> { launcher.ready = new CountDownLatch(1); access.result = new OpenResult.Opened(next);
                launcher.directory = target; launcher.password = new char[] {'p'}; first.changeVault.run(); });
            await(launcher.ready); await(first.disposed);
            assertEquals(target, store.path); assertEquals(2, store.writes);
            assertEquals(java.util.List.of(PasswordPromptContext.EXPLICIT), launcher.passwordContexts);
            assertEquals(1, old.closes.get()); assertEquals(0, next.closes.get());
            assertEquals(1, second.disposed.getCount());
        } finally { stop(app, launcher); }
        assertEquals(1, old.closes.get()); assertEquals(1, next.closes.get());
    }

    @Test void failedWindowStartupKeepsPreviousPreference() throws Exception {
        Store store = new Store(directory); Launcher launcher = new Launcher(); Access access = new Access();
        Session session = new Session(); access.result = new OpenResult.Opened(session);
        Window window = new Window(); window.failShow = true; var app = app(access, launcher, window, store);
        try {
            edt(() -> app.begin(directory.resolve("other"), new char[] {'p'}, false));
            await(window.disposed); assertEquals(directory, store.path); assertEquals(0, store.writes);
        } finally { stop(app, launcher); }
    }

    @Test void isolatedJdkStoreUsesOnlyNormalizedPathAndCanBeCleared() throws Exception {
        Preferences node = Preferences.userRoot().node("/org/totipo/desktop-tests/" + java.util.UUID.randomUUID());
        try {
            var store = new JdkVaultPreferences(node); assertTrue(store.lastVault().isEmpty());
            Path path = directory.resolve("child/../vault"); store.setLastVault(path);
            assertEquals(path.toAbsolutePath().normalize(), store.lastVault().orElseThrow());
            assertArrayEquals(new String[] {JdkVaultPreferences.KEY}, node.keys());
            assertEquals(path.toAbsolutePath().normalize().toString(), node.get(JdkVaultPreferences.KEY, null));
            store.clearLastVault(); assertTrue(store.lastVault().isEmpty());
        } finally { node.removeNode(); Preferences.userRoot().flush(); }
    }

    @Test void shutdownDuringStartupDoesNotPromptOpenOrChangePreference() throws Exception {
        Store store = new Store(directory); Launcher launcher = new Launcher(); Access access = new Access();
        var app = app(access, launcher, new Window(), store);
        edt(() -> { app.show(); app.shutdown(); }); await(launcher.disposed);
        assertEquals(0, access.opens.get()); assertNull(launcher.passwordDirectory);
        assertEquals(directory, store.path); assertEquals(0, store.writes); assertTrue(app.executorShutdown());
    }
}
