package dev.totipo.desktop;

import dev.totipo.ObservationProgress;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static dev.totipo.desktop.TestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class VaultWindowControllerTest {
    @Test void refreshAndIdempotentCloseHaveCorrectLifetime() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch retired = new CountDownLatch(1);
        Session session = new Session(release);
        Window window = new Window();
        AtomicInteger notifications = new AtomicInteger();
        VaultWindowController controller = onEdt(() -> new VaultWindowController(session, window, 7, closed -> {
            assertTrue(closed.executorShutdown());
            notifications.incrementAndGet();
            retired.countDown();
        }));
        try {
            edt(() -> {
                controller.start();
                window.refresh.run();
                assertEquals(1, session.refreshes.get());
                session.subscriber.onNext(state(new ObservationProgress.Enumerating(2)));
            });
            edt(() -> {
                assertEquals(1, window.rendered.size());
                window.close.run();
                assertTrue(window.closing);
                window.refresh.run();
                window.close.run();
                assertEquals(1, session.refreshes.get());
                assertEquals(1, session.subscription.cancels.get());
                session.subscriber.onNext(state(new ObservationProgress.Enumerating(3)));
            });
            await(session.closeEntered);
            edt(() -> assertEquals(1, window.rendered.size()));
            assertEquals(1, window.disposed.getCount());
            assertFalse(controller.executorShutdown());
        } finally {
            release.countDown();
            edt(controller::close);
            await(retired);
        }
        assertEquals(1, session.closes.get());
        assertEquals("totipo-session-7", session.closeThread);
        assertEquals(1, notifications.get());
        assertEquals(0, window.disposed.getCount());
    }

    @Test void terminalStreamEventsRetireSessionAndWindow() throws Exception {
        for (boolean error : new boolean[] {true, false}) {
            Session session = new Session();
            Window window = new Window();
            CountDownLatch retired = new CountDownLatch(1);
            VaultWindowController controller = onEdt(() -> new VaultWindowController(session, window, 1,
                    closed -> retired.countDown()));
            edt(controller::start);
            if (error) { session.subscriber.onError(new IllegalStateException()); }
            else { session.subscriber.onComplete(); }
            session.subscriber.onComplete();
            await(retired);
            edt(() -> {
                assertTrue(window.closing);
                assertEquals(error ? 1 : 0, window.failures);
                controller.close();
            });
            assertEquals(1, session.closes.get());
            assertTrue(controller.executorShutdown());
        }
    }

    @Test void startFailureAfterTransferStillClosesSession() throws Exception {
        for (boolean showFailure : new boolean[] {true, false}) {
            Session session = new Session();
            Window window = new Window();
            window.failShow = showFailure;
            session.failSubscribe = !showFailure;
            CountDownLatch retired = new CountDownLatch(1);
            VaultWindowController controller = onEdt(() -> new VaultWindowController(session, window, 1,
                    closed -> retired.countDown()));
            edt(controller::start);
            await(retired);
            assertEquals(1, session.closes.get());
            assertEquals(1, window.failures);
            assertTrue(controller.executorShutdown());
        }
    }

    @Test void runtimeCloseFailureStillRetiresExecutorAndNotifiesOwner() throws Exception {
        Session session = new Session();
        session.failClose = true;
        Window window = new Window();
        CountDownLatch retired = new CountDownLatch(1);
        VaultWindowController controller = onEdt(() -> new VaultWindowController(session, window, 1,
                closed -> retired.countDown()));
        edt(() -> { controller.start(); controller.close(); });
        await(retired);
        assertEquals(1, session.closes.get());
        assertEquals(1, window.failures);
        assertTrue(controller.executorShutdown());
    }
}
