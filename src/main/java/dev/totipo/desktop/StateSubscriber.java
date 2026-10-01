package dev.totipo.desktop;

import dev.totipo.VaultState;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import javax.swing.SwingUtilities;

/** One pending EDT rendering at a time; the publisher owns replay-latest coalescing. */
final class StateSubscriber implements Flow.Subscriber<VaultState> {
    private final Consumer<VaultState> render;
    private final Runnable failure;
    private final Runnable complete;
    private final AtomicReference<Flow.Subscription> subscription = new AtomicReference<>();
    private final AtomicBoolean retired = new AtomicBoolean();
    private final AtomicBoolean terminal = new AtomicBoolean();

    StateSubscriber(Consumer<VaultState> render, Runnable failure, Runnable complete) {
        this.render = render;
        this.failure = failure;
        this.complete = complete;
    }

    @Override public void onSubscribe(Flow.Subscription incoming) {
        if (retired.get() || terminal.get() || !subscription.compareAndSet(null, incoming)) {
            incoming.cancel();
            return;
        }
        if (retired.get() || terminal.get()) {
            cancel();
        } else {
            incoming.request(1);
        }
    }

    @Override public void onNext(VaultState state) {
        if (retired.get() || terminal.get()) {
            return;
        }
        SwingUtilities.invokeLater(() -> {
            if (retired.get()) {
                return;
            }
            try {
                render.accept(state);
                Flow.Subscription current = subscription.get();
                if (!retired.get() && !terminal.get() && current != null) {
                    current.request(1);
                }
            } catch (RuntimeException unexpected) {
                onError(unexpected);
            }
        });
    }

    @Override public void onError(Throwable error) {
        // Never log exception messages/causes: caller-provided text might contain secrets.
        terminate(failure);
    }

    @Override public void onComplete() {
        terminate(complete);
    }

    private void terminate(Runnable action) {
        if (retired.get() || !terminal.compareAndSet(false, true)) {
            return;
        }
        Flow.Subscription current = subscription.getAndSet(null);
        if (current != null) {
            current.cancel();
        }
        SwingUtilities.invokeLater(() -> {
            if (!retired.getAndSet(true)) {
                action.run();
            }
        });
    }

    void cancel() {
        retired.set(true);
        Flow.Subscription current = subscription.getAndSet(null);
        if (current != null) {
            current.cancel();
        }
    }
}
