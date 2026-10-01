package dev.totipo.desktop;

import dev.totipo.PasswordChangeResult;
import dev.totipo.SessionClosedException;
import dev.totipo.VaultSession;
import dev.totipo.desktop.ui.Edt;
import dev.totipo.desktop.ui.PasswordChangePanel;
import dev.totipo.desktop.ui.VaultView;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import javax.swing.SwingUtilities;

/** Password-wrapper lifecycle only; no token publication capability or password cache. */
final class PasswordChangeController {
    private final VaultSession session;
    private final Executor executor;
    private final VaultView view;
    private final MutationGate gate;
    private final Consumer<String> retireSession;
    private PasswordChangePanel panel;
    private boolean pending;
    private boolean closing;
    private PasswordChangeResult lastResult; // Non-secret knowledge, including completion during close.
    private boolean internalFailure;

    PasswordChangeController(VaultSession session, Executor executor, VaultView view,
                             MutationGate gate, Consumer<String> retireSession) {
        this.session = session; this.executor = executor; this.view = view;
        this.gate = gate; this.retireSession = retireSession;
    }

    void open() {
        Edt.require();
        if (closing || panel != null || !gate.acquire(this)) { return; }
        try {
            panel = new PasswordChangePanel(this::submit, this::cancel);
            view.editPassword(panel);
        } catch (RuntimeException unexpected) {
            retireSession.accept("The password form could not be opened. This session will be closed.");
        }
    }

    private void submit(PasswordChangeSubmission submission) {
        Edt.require();
        if (closing || pending || panel == null) { submission.close(); return; }
        pending = true;
        try {
            executor.execute(() -> {
                PasswordChangeResult result = null;
                boolean failed = false;
                boolean sessionClosed = false;
                try { result = submission.execute(session); }
                catch (SessionClosedException closed) { sessionClosed = true; }
                catch (RuntimeException unexpected) { failed = true; }
                PasswordChangeResult outcome = result;
                boolean unexpected = failed;
                boolean closed = sessionClosed;
                SwingUtilities.invokeLater(() -> accept(outcome, unexpected, closed));
            });
        } catch (RuntimeException rejected) {
            submission.close();
            accept(null, true, false);
        }
    }

    private void accept(PasswordChangeResult result, boolean unexpected, boolean sessionClosed) {
        Edt.require();
        // Record before any UI cleanup, even when close has already begun.
        lastResult = result;
        internalFailure = unexpected;
        pending = false;
        if (closing) { return; }
        if (sessionClosed) { retireSession.accept(null); return; }
        if (unexpected || result == null) {
            retireSession.accept("An unexpected internal error occurred during password change. "
                    + "Totipo cannot infer the password-change outcome from that error. "
                    + "This session will be closed; reopen the vault before continuing.");
            return;
        }
        try {
            switch (result) {
                case CHANGED -> {
                    retirePanel();
                    gate.release(this);
                    view.writeMessage("Vault password change acknowledged.");
                }
                case AUTHENTICATION_FAILED -> panel.busy(false,
                        "Authentication with the supplied current password did not succeed for the observed vault data. "
                        + "This does not prove that the password was merely mistyped. "
                        + "The password was not changed by this attempt.");
                case FAILED -> panel.busy(false,
                        "The password change could not be completed because required vault observation or staging failed. "
                        + "The password was not changed by this attempt.");
                case STALE -> retireSession.accept(
                        "The observed canonical vault changed before the password replacement. "
                        + "This attempt did not perform the replacement. This session is stale and will be closed. "
                        + "Reopen the vault before continuing.");
                case UNCERTAIN -> retireSession.accept(
                        "Totipo could not determine whether the vault password replacement received durable acknowledgement. "
                        + "Either the previous or the new password may be canonical. "
                        + "This session will be closed; reopen the vault and re-observe it.");
            }
        } catch (RuntimeException cleanupFailure) {
            // Presentation failure cannot reclassify the already recorded protocol result.
            retireSession.accept("An internal presentation cleanup error occurred. This session will be closed.");
        }
    }

    private void cancel() {
        Edt.require();
        if (!closing && !pending) {
            try { retirePanel(); }
            finally { gate.release(this); }
        }
    }

    private void retirePanel() {
        PasswordChangePanel owned = panel;
        panel = null;
        if (owned != null) {
            try { owned.retire(); }
            finally { view.retirePassword(); }
        }
    }

    void closing() {
        Edt.require(); closing = true;
        try { retirePanel(); }
        finally { gate.release(this); }
    }

    PasswordChangeResult lastResult() { Edt.require(); return lastResult; }
    boolean internalFailure() { Edt.require(); return internalFailure; }
}
