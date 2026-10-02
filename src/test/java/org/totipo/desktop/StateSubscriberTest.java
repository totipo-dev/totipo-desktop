package org.totipo.desktop;

import org.totipo.ObservationProgress;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import static org.totipo.desktop.TestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class StateSubscriberTest {
    @Test void demandWaitsForEdtRendering() throws Exception {
        Subscription subscription = new Subscription();
        AtomicInteger renders = new AtomicInteger();
        StateSubscriber subscriber = new StateSubscriber(state -> {
            assertTrue(SwingUtilities.isEventDispatchThread());
            assertEquals(List.of(1L), subscription.requests);
            renders.incrementAndGet();
        }, () -> fail("unexpected error"), () -> fail("unexpected completion"));
        subscriber.onSubscribe(subscription);
        assertEquals(List.of(1L), subscription.requests);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        SwingUtilities.invokeLater(() -> { entered.countDown(); await(release); });
        await(entered);
        try {
            subscriber.onNext(state(new ObservationProgress.Enumerating(2)));
            assertEquals(0, renders.get());
            assertEquals(List.of(1L), subscription.requests);
        } finally {
            release.countDown();
        }
        edt(() -> { });
        assertEquals(1, renders.get());
        assertEquals(List.of(1L, 1L), subscription.requests);
        subscriber.cancel();
    }

    @Test void cancellationSuppressesAlreadyQueuedRenderingAndLateSubscription() throws Exception {
        AtomicInteger renders = new AtomicInteger();
        Subscription subscription = new Subscription();
        StateSubscriber subscriber = new StateSubscriber(state -> renders.incrementAndGet(),
                () -> fail("cancelled error"), () -> fail("cancelled completion"));
        subscriber.onSubscribe(subscription);
        edt(() -> {
            subscriber.onNext(state(new ObservationProgress.Finished(0, false)));
            subscriber.cancel();
            subscriber.cancel();
        });
        subscriber.onError(new IllegalStateException());
        subscriber.onComplete();
        Subscription late = new Subscription();
        subscriber.onSubscribe(late);
        edt(() -> { });
        assertEquals(0, renders.get());
        assertEquals(List.of(1L), subscription.requests);
        assertEquals(1, subscription.cancels.get());
        assertEquals(1, late.cancels.get());
        assertTrue(late.requests.isEmpty());
    }

    @Test void terminalSignalsArriveOnEdtExactlyOnce() throws Exception {
        for (boolean error : new boolean[] {true, false}) {
            AtomicInteger failures = new AtomicInteger();
            AtomicInteger completions = new AtomicInteger();
            StateSubscriber subscriber = new StateSubscriber(state -> fail("terminal state"), () -> {
                assertTrue(SwingUtilities.isEventDispatchThread()); failures.incrementAndGet();
            }, () -> {
                assertTrue(SwingUtilities.isEventDispatchThread()); completions.incrementAndGet();
            });
            Subscription subscription = new Subscription();
            subscriber.onSubscribe(subscription);
            if (error) { subscriber.onError(new IllegalStateException()); }
            else { subscriber.onComplete(); }
            subscriber.onComplete();
            subscriber.onError(new IllegalStateException());
            subscriber.onNext(state(new ObservationProgress.Enumerating(1)));
            edt(() -> { });
            assertEquals(error ? 1 : 0, failures.get());
            assertEquals(error ? 0 : 1, completions.get());
            subscriber.cancel();
            assertEquals(1, subscription.cancels.get());
        }
    }

    @Test void duplicateSubscriptionIsCancelledAndRenderFailureTerminates() throws Exception {
        CountDownLatch failed = new CountDownLatch(1);
        StateSubscriber subscriber = new StateSubscriber(state -> { throw new IllegalStateException(); },
                failed::countDown, () -> fail("unexpected completion"));
        Subscription first = new Subscription();
        Subscription duplicate = new Subscription();
        subscriber.onSubscribe(first);
        subscriber.onSubscribe(duplicate);
        subscriber.onNext(state(new ObservationProgress.Enumerating(1)));
        await(failed);
        assertEquals(List.of(1L), first.requests);
        assertEquals(1, first.cancels.get());
        assertEquals(1, duplicate.cancels.get());
    }

    @Test void cancellationCanRetireQueuedTerminalHandling() throws Exception {
        StateSubscriber subscriber = new StateSubscriber(state -> fail("unexpected state"),
                () -> fail("cancelled error"), () -> fail("cancelled completion"));
        Subscription subscription = new Subscription();
        subscriber.onSubscribe(subscription);
        edt(() -> {
            subscriber.onError(new IllegalStateException());
            subscriber.cancel();
        });
        edt(() -> { });
        assertEquals(1, subscription.cancels.get());
    }
}
