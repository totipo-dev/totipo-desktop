package org.totipo.desktop;

import org.totipo.desktop.clipboard.TotpClipboard;

import org.totipo.VaultSession;
import org.totipo.VaultState;
import org.totipo.desktop.ui.Edt;
import org.totipo.desktop.ui.VaultView;
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
    private final PasswordChangeController passwords;
    private final MutationGate gate;
    private final Consumer<String> retirementMessage;
    private String retirementReason;
    private VaultState latest;
    private boolean closing;
    private final Object clipboardOrigin = new Object();
    private final TotpClipboard clipboard;

    // Ownership transfers on successful construction, before start() touches the view/publisher.
    VaultWindowController(VaultSession session, VaultView view, int number,
                          Consumer<VaultWindowController> closed) {
        this(session, view, number, closed, view::retirementMessage);
    }

    VaultWindowController(VaultSession session, VaultView view, int number,
                          Consumer<VaultWindowController> closed, Consumer<String> retirementMessage) {
        this(session, view, number, closed, retirementMessage, null);
    }

    VaultWindowController(VaultSession session, VaultView view, int number,
                          Consumer<VaultWindowController> closed, Consumer<String> retirementMessage,
                          TotpClipboard clipboard) {
        Edt.require();
        this.clipboard = clipboard;
        this.retirementMessage = retirementMessage;
        this.session = session;
        this.view = view;
        this.closed = closed;
        executor = Executors.newSingleThreadExecutor(task -> new Thread(task, "totipo-session-" + number));
        gate = new MutationGate(view::writeAvailability);
        writes = new TokenWriteController(executor, view, this::close, gate);
        passwords = new PasswordChangeController(session, executor, view, gate, reason -> {
            if (!closing) { retirementReason = reason; close(); }
        });
        subscriber = new StateSubscriber(this::render, () -> close(true), this::close);
    }

    void start() {
        Edt.require();
        try {
            view.actions(this::refresh, this::close);
            if (clipboard != null) {
                view.copyAction((code, from, until, now) -> closing
                        ? TotpClipboard.UNAVAILABLE
                        : clipboard.copy(clipboardOrigin, code, from, until, now));
            }
            view.tokenActions(() -> writes.open(latest, null,
                    "A new Create makes a distinct token; it is not a retry of an earlier uncertain publication."), writes::open);
            view.mergeAction(writes::openMerge);
            view.passwordAction(passwords::open);
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
            if (clipboard != null) { clipboard.originClosing(clipboardOrigin); }
            gate.closing();
            try { passwords.closing(); }
            finally {
                try { writes.closing(); }
                finally { view.closing(); }
            }
            if (failed) {
                view.failure();
            }
        } catch (RuntimeException cleanupFailure) {
            // Presentation cleanup must not prevent this or other application sessions closing.
            System.err.println("Totipo: session presentation cleanup failure (details redacted).");
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
                                try {
                                    if (retirementReason != null) { retirementMessage.accept(retirementReason); }
                                } finally { closed.accept(this); }
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
