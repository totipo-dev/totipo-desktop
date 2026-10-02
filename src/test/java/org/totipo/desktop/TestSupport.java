package org.totipo.desktop;

import org.totipo.*;
import org.totipo.desktop.ui.Edt;
import org.totipo.desktop.ui.LauncherView;
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
        final CountDownLatch ready = new CountDownLatch(1);
        final CountDownLatch disposed = new CountDownLatch(1);
        final List<String> titles = new ArrayList<>();
        final List<String> messages = new ArrayList<>();
        Runnable open;
        Runnable create;
        Runnable close;
        Runnable duringMessage = () -> { };
        Runnable duringDirectory = () -> { };
        Runnable duringPassword = () -> { };
        boolean busy;
        char[] password = {'p'};
        Path directory = Path.of("existing-directory");
        @Override public void actions(Runnable open, Runnable create, Runnable close) {
            Edt.require(); this.open = open; this.create = create; this.close = close;
        }
        @Override public Path chooseDirectory() { Edt.require(); duringDirectory.run(); return directory; }
        @Override public char[] password(boolean create) { Edt.require(); duringPassword.run(); return password; }
        @Override public void busy(String text, boolean value) {
            Edt.require(); busy = value;
            if (!value) { ready.countDown(); }
        }
        @Override public void message(String title, String text) {
            Edt.require(); titles.add(title); messages.add(text); duringMessage.run();
        }
        @Override public void showWindow() { Edt.require(); }
        @Override public void dispose() { Edt.require(); disposed.countDown(); }
    }

    static class Window implements VaultView {
        final CountDownLatch disposed = new CountDownLatch(1);
        final List<VaultState> rendered = new ArrayList<>();
        Runnable refresh;
        Runnable close;
        boolean closing;
        boolean failShow;
        int failures;
        @Override public void actions(Runnable refresh, Runnable close) {
            Edt.require(); this.refresh = refresh; this.close = close;
        }
        @Override public void render(VaultState state) { Edt.require(); rendered.add(state); }
        @Override public void closing() { Edt.require(); closing = true; }
        @Override public void failure() { Edt.require(); failures++; }
        @Override public void showWindow() {
            Edt.require();
            if (failShow) { throw new IllegalStateException("show failed"); }
        }
        @Override public void dispose() { Edt.require(); disposed.countDown(); }
    }
}
