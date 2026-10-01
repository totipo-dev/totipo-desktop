package dev.totipo.desktop;

import dev.totipo.VaultSession;
import dev.totipo.VaultState;
import dev.totipo.desktop.ui.Edt;
import dev.totipo.desktop.ui.VaultView;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import javax.swing.SwingUtilities;

/** EDT-owned lifetime of exactly one session and window. */
final class VaultWindowController {
    private final VaultSession session;
    private final VaultView view;
    private final Consumer<VaultWindowController> closed;
    private final ExecutorService executor;
    private final StateSubscriber subscriber;
    private final TokenWriteController writes;
    private VaultState latest;
    private boolean closing;

    // Ownership transfers on successful construction, before start() touches the view/publisher.
    VaultWindowController(VaultSession session, VaultView view, int number,
                          Consumer<VaultWindowController> closed) {
        Edt.require();
        this.session = session;
        this.view = view;
        this.closed = closed;
        executor = Executors.newSingleThreadExecutor(task -> new Thread(task, "totipo-session-" + number));
        writes = new TokenWriteController(executor, view, this::close);
        subscriber = new StateSubscriber(this::render, () -> close(true), this::close);
    }

    void start() {
        Edt.require();
        try {
            view.actions(this::refresh, this::close);
            view.tokenActions(() -> writes.open(latest, null,
                    "A new Create makes a distinct token; it is not a retry of an earlier uncertain publication."), writes::open);
            view.mergeAction(writes::openMerge);
            view.showWindow();
            session.states().subscribe(subscriber);
        } catch (RuntimeException unexpected) {
            close(true);
        }
    }

    private void render(VaultState state) {
        Edt.require();
        if (!closing) {
            latest = state;
            view.render(latest);
        }
    }

    void refresh() {
        Edt.require();
        if (!closing) {
            try {
                session.requestRefresh();
            } catch (RuntimeException unexpected) {
                close(true);
            }
        }
    }

    void close() {
        close(false);
    }

    private void close(boolean failed) {
        Edt.require();
        if (closing) {
            return;
        }
        closing = true;
        subscriber.cancel();
        latest = null;
        try {
            writes.closing();
            view.closing();
            if (failed) {
                view.failure();
            }
        } finally {
            executor.execute(() -> {
                boolean closeFailed = false;
                try {
                    session.close();
                } catch (RuntimeException unexpected) {
                    closeFailed = true;
                    System.err.println("Totipo: unexpected session close failure (details redacted).");
                } finally {
                    executor.shutdown();
                    boolean reportFailure = closeFailed && !failed;
                    SwingUtilities.invokeLater(() -> {
                        try {
                            if (reportFailure) {
                                view.failure();
                            }
                        } finally {
                            try {
                                view.dispose();
                            } finally {
                                closed.accept(this);
                            }
                        }
                    });
                }
            });
        }
    }

    boolean executorShutdown() {
        return executor.isShutdown();
    }
}
