package org.totipo.desktop;

import org.totipo.*;
import org.totipo.desktop.ui.Edt;
import org.totipo.desktop.ui.LauncherView;
import org.totipo.desktop.ui.PasswordPromptResult;
import org.totipo.desktop.ui.PasswordPromptContext;
import org.totipo.desktop.ui.VaultView;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import javax.swing.SwingUtilities;
import static org.junit.jupiter.api.Assertions.*;

public final class TestSupport {
    private TestSupport() { }

    public static void edt(Runnable action) throws Exception {
        SwingUtilities.invokeAndWait(action);
    }

    static <T> T onEdt(Supplier<T> action) throws Exception {
        AtomicReference<T> value = new AtomicReference<>();
        edt(() -> value.set(action.get()));
        return value.get();
    }

    static void await(CountDownLatch latch) {
        try {
            assertTrue(latch.await(10, TimeUnit.SECONDS), "Timed out waiting for lifecycle event");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError(interrupted);
        }
    }

    public static VaultState state(ObservationProgress progress, String... codes) {
        List<VaultDiagnostic> diagnostics = java.util.Arrays.stream(codes).map(VaultDiagnostic::new).toList();
        return (VaultState) Proxy.newProxyInstance(VaultState.class.getClassLoader(),
                new Class<?>[] {VaultState.class}, (proxy, method, arguments) -> switch (method.getName()) {
                    case "observation" -> progress;
                    case "tokens" -> List.of();
                    case "diagnostics" -> diagnostics;
                    default -> throw new AssertionError("Unexpected state API: " + method.getName());
                });
    }

    static final class Subscription implements Flow.Subscription {
        final List<Long> requests = new java.util.concurrent.CopyOnWriteArrayList<>();
        final AtomicInteger cancels = new AtomicInteger();
        @Override public void request(long n) { requests.add(n); }
        @Override public void cancel() { cancels.incrementAndGet(); }
    }

    static final class Session implements VaultSession {
        final AtomicInteger closes = new AtomicInteger();
        final AtomicInteger refreshes = new AtomicInteger();
        final CountDownLatch closeEntered = new CountDownLatch(1);
        final CountDownLatch allowClose;
        final Subscription subscription = new Subscription();
        Flow.Subscriber<? super VaultState> subscriber;
        boolean failSubscribe;
        boolean failClose;
        volatile String closeThread;

        Session() { this(new CountDownLatch(0)); }
        Session(CountDownLatch allowClose) { this.allowClose = allowClose; }
        @Override public Flow.Publisher<VaultState> states() {
            return incoming -> {
                if (failSubscribe) {
                    throw new IllegalStateException("subscribe failed");
                }
                subscriber = incoming;
                incoming.onSubscribe(subscription);
            };
        }
        @Override public void requestRefresh() {
            Edt.require();
            refreshes.incrementAndGet();
        }
        @Override public void close() {
            assertFalse(SwingUtilities.isEventDispatchThread());
            closeThread = Thread.currentThread().getName();
            closes.incrementAndGet();
            closeEntered.countDown();
            await(allowClose);
            if (failClose) {
                throw new IllegalStateException("close failed");
            }
            if (subscriber != null) {
                subscriber.onComplete();
            }
        }
        @Override public VaultFingerprint fingerprint() { throw new AssertionError(); }
        @Override public VaultState state() { throw new AssertionError(); }
        @Override public PasswordChangeResult changePassword(char[] old, char[] next) { throw new AssertionError(); }
    }

    static final class Launcher implements LauncherView {
        CountDownLatch ready = new CountDownLatch(1);
        final CountDownLatch disposed = new CountDownLatch(1);
        final List<String> titles = new ArrayList<>();
        final List<String> messages = new ArrayList<>();
        Runnable open;
        Runnable create;
        Runnable close;
        Runnable duringMessage = () -> { };
        Runnable duringDirectory = () -> { };
        Runnable duringPassword = () -> { };
        Runnable duringEmptyConfirmation = () -> { };
        int shown;
        int hidden;
        int directories;
        Path chooserLocation;
        boolean choosingCreate;
        int disposals;
        Path passwordDirectory;
        final List<PasswordPromptContext> passwordContexts = new ArrayList<>();
        boolean allowEmptyPassword;
        int emptyConfirmations;
        boolean busy;
        char[] password = {'p'};
        PasswordPromptResult.Action passwordAction;
        Path directory = Path.of("existing-directory");
        @Override public void actions(Runnable open, Runnable create, Runnable close) {
            Edt.require(); this.open = open; this.create = create; this.close = close;
        }
        @Override public Path chooseDirectory(Path initialLocation, boolean create) {
            Edt.require(); directories++; chooserLocation = initialLocation; choosingCreate = create;
            duringDirectory.run(); return directory;
        }
        @Override public PasswordPromptResult password(Path directory, boolean create, PasswordPromptContext context) {
            Edt.require(); passwordDirectory = directory; passwordContexts.add(context); duringPassword.run();
            if (passwordAction != null) { return PasswordPromptResult.dismissed(passwordAction); }
            return password == null ? PasswordPromptResult.dismissed(create
                    ? PasswordPromptResult.Action.CANCEL : PasswordPromptResult.Action.EXIT)
                    : PasswordPromptResult.submitted(password);
        }
        @Override public boolean confirmEmptyPassword() {
            Edt.require(); assertTrue(busy); emptyConfirmations++;
            duringEmptyConfirmation.run(); return allowEmptyPassword;
        }
        @Override public void busy(String text, boolean value) {
            Edt.require(); busy = value;
            if (!value) { ready.countDown(); }
        }
        @Override public void message(String title, String text) {
            Edt.require(); titles.add(title); messages.add(text); duringMessage.run();
        }
        @Override public void showWindow() { Edt.require(); shown++; }
        @Override public void hideWindow() { Edt.require(); hidden++; }
        @Override public void dispose() { Edt.require(); disposals++; disposed.countDown(); }
    }

    static class Window implements VaultView {
        final CountDownLatch disposed = new CountDownLatch(1);
        final List<VaultState> rendered = new ArrayList<>();
        Runnable refresh;
        Runnable changeVault;
        Runnable close;
        Runnable quit;
        int disposals;
        boolean closing;
        boolean failShow;
        int failures;
        @Override public void actions(Runnable refresh, Runnable close) {
            Edt.require(); this.refresh = refresh; this.close = () -> {
                if (quit != null) { quit.run(); } else { close.run(); }
            };
        }
        @Override public void quitAction(Runnable action) { Edt.require(); quit = action; }
        @Override public void changeVaultAction(Runnable action) { Edt.require(); changeVault = action; }
        @Override public void render(VaultState state) { Edt.require(); rendered.add(state); }
        @Override public void closing() { Edt.require(); closing = true; }
        @Override public void failure() { Edt.require(); failures++; }
        @Override public void showWindow() {
            Edt.require();
            if (failShow) { throw new IllegalStateException("show failed"); }
        }
        @Override public void dispose() { Edt.require(); disposals++; disposed.countDown(); }
    }
}
