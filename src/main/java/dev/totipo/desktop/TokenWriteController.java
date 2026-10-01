package dev.totipo.desktop;

import dev.totipo.*;
import dev.totipo.desktop.ui.*;
import java.util.concurrent.Executor;
import javax.swing.SwingUtilities;

/** EDT workflow owner. The retry field is exclusively accessed by the session executor. */
final class TokenWriteController {
    private enum Outcome { SAVED, UNCERTAIN, FAILED, INTERNAL_FAILURE }
    private final Executor executor;
    private final VaultView view;
    private final Runnable closeSession;
    private TokenEditorPanel editor;
    private MergeEditorPanel mergeEditor;
    private boolean merge;
    private boolean original;
    private boolean decision;
    private TokenId mergeId;
    private PartialResolution partial; // Session executor only.
    private VaultState reviewBase; // EDT descriptive state, never browser authority.
    private boolean active;
    private boolean pending;
    private boolean closing;
    private boolean abandoned;
    private boolean create;
    private PublicationRetry retry; // Session executor only, including cleanup.

    TokenWriteController(Executor executor, VaultView view, Runnable closeSession) {
        this.executor = executor; this.view = view; this.closeSession = closeSession;
    }

    void open(VaultState base, TokenAlternative alternative, String explanation) {
        Edt.require();
        if (closing || active || base == null) { return; }
        active = true;
        merge = false; original = false;
        create = alternative == null;
        editor = new TokenEditorPanel(create ? null : alternative.descriptor(), explanation,
                draft -> submit(base, alternative, draft), this::cancel);
        view.writeAvailability(false);
        view.editToken(editor, create);
    }

    void openMerge(VaultState base, TokenState token) {
        Edt.require();
        if (closing || active || base == null || token == null || !token.hasConflict()
                || token.alternatives().size() < 2) { return; }
        active = true; merge = true; original = false; create = false;
        mergeId = token.id();
        mergeEditor = new MergeEditorPanel(MergeInputs.capture(base, token), this::submitMerge, this::cancel);
        view.writeAvailability(false); view.editMerge(mergeEditor);
    }

    private void submitMerge(MergeDraft draft) {
        Edt.require();
        if (closing || pending || !active) { draft.close(); return; }
        pending = true;
        executor.execute(() -> {
            try {
                SaveResult result = MergeWrites.save(draft);
                if (result instanceof SaveResult.AdditionalConflict conflict) {
                    partial = conflict.resolution();
                    VaultState latest = conflict.latest();
                    SwingUtilities.invokeLater(() -> {
                        if (closing) { return; }
                        pending = false; decision = true; reviewBase = latest;
                        retireEditor();
                        view.additionalConflict(this::reviewLatest, this::confirmOriginal, this::discardPartial);
                    });
                } else { acceptPartialResult((PartialSaveResult) result); }
            } catch (MergeWrites.InconsistentDraft mismatch) {
                deliver(Outcome.INTERNAL_FAILURE, null, "Internal merge draft problem. Nothing was published.");
            } catch (RuntimeException unexpected) {
                deliver(Outcome.INTERNAL_FAILURE, null, "Internal merge operation problem; publication outcome could not be determined.");
            }
        });
    }
    private void acceptPartialResult(PartialSaveResult result) {
        if (result instanceof SaveResult.Saved) { deliver(Outcome.SAVED, null, null); }
        else if (result instanceof SaveResult.PublicationUncertain uncertain) {
            retry = uncertain.retry(); deliver(Outcome.UNCERTAIN, null, null);
        } else if (result instanceof SaveResult.Failed failed) { deliver(Outcome.FAILED, failed.reason(), null); }
    }
    private void reviewLatest() { releasePartial(true); }
    private void discardPartial() { releasePartial(false); }
    private void releasePartial(boolean review) {
        Edt.require();
        if (closing || pending || !decision) { return; }
        pending = true; decision = false; view.clearUncertainty();
        VaultState base = reviewBase; TokenId id = mergeId; reviewBase = null;
        executor.execute(() -> {
            boolean cleaned = cleanupPartial();
            SwingUtilities.invokeLater(() -> {
                if (closing) { return; }
                finish();
                if (review) {
                    TokenState token = base.token(id).orElse(null);
                    if (token != null && token.hasConflict() && token.alternatives().size() >= 2) { openMerge(base, token); }
                    else { view.writeMessage("The supplied reviewed state no longer has a complete semantic conflict."); }
                }
                if (!cleaned) { view.writeMessage("Internal resolution cleanup error. Nothing from the discarded merge was published."); }
            });
        });
    }
    private void confirmOriginal() {
        Edt.require();
        if (closing || pending || !decision) { return; }
        view.confirmOriginalResolution(this::publishOriginal);
    }
    private void publishOriginal() {
        Edt.require();
        if (closing || pending || !decision) { return; }
        pending = true; decision = false; original = true; reviewBase = null;
        view.clearUncertainty(); view.writeMessage("Publishing original merge resolution…");
        executor.execute(() -> {
            PartialResolution owned = partial; partial = null;
            try {
                PartialSaveResult result;
                try { result = owned.save(); }
                finally { closePartialQuietly(owned); }
                acceptPartialResult(result);
            } catch (RuntimeException unexpected) {
                deliver(Outcome.INTERNAL_FAILURE, null, "Internal original-resolution operation problem; publication outcome could not be determined.");
            }
        });
    }
    private static boolean closePartialQuietly(PartialResolution owned) {
        try { if (owned != null) { owned.close(); } return true; }
        catch (RuntimeException cleanupFailure) {
            System.err.println("Totipo: partial resolution cleanup failure (details redacted)."); return false;
        }
    }
    private boolean cleanupPartial() {
        PartialResolution owned = partial; partial = null;
        return closePartialQuietly(owned);
    }

    private void submit(VaultState base, TokenAlternative alternative, TokenDraft draft) {
        Edt.require();
        if (closing || pending || !active) { draft.close(); return; }
        pending = true;
        executor.execute(() -> {
            try {
                SaveResult result = TokenWrites.save(base, alternative, draft);
                if (result instanceof SaveResult.AdditionalConflict conflict) {
                    closePartialQuietly(conflict.resolution());
                    deliver(Outcome.INTERNAL_FAILURE, null, "Internal token operation failure.");
                } else if (result instanceof SaveResult.Saved) {
                    deliver(Outcome.SAVED, null, null);
                } else if (result instanceof SaveResult.PublicationUncertain uncertain) {
                    retry = uncertain.retry();
                    deliver(Outcome.UNCERTAIN, null, null);
                } else if (result instanceof SaveResult.Failed failed) {
                    deliver(Outcome.FAILED, failed.reason(), null);
                } else {
                    throw new IllegalStateException("Unsupported save result");
                }
            } catch (RuntimeException unexpected) {
                deliver(Outcome.INTERNAL_FAILURE, null, "Internal token operation failure; publication outcome could not be determined.");
            }
        });
    }

    // Only classification reaches EDT; result capabilities stay on the executor.
    private void deliver(Outcome outcome, SaveResult.Reason reason, String error) {
        SwingUtilities.invokeLater(() -> {
            if (closing) { return; }
            pending = false;
            if (outcome == Outcome.SAVED) {
                finish();
                view.writeMessage(merge ? (original ? "Original merge resolution publication acknowledged." : "Merge publication acknowledged.")
                        : create ? "Token publication acknowledged." : "Token update publication acknowledged.");
            } else if (outcome == Outcome.UNCERTAIN) {
                retireEditor();
                showUncertainty(false);
            } else if (reason == SaveResult.Reason.SESSION_CLOSING) {
                closeSession.run();
            } else if (reason != null) {
                String message = switch (reason) {
                    case PREPARATION_FAILED -> "The token operation could not be prepared for publication. Nothing was published.";
                    case OBSERVATION_UNAVAILABLE -> "The required local observation was unavailable; this operation was not published.";
                    case UNRESOLVED_FIELDS -> "Required token fields were unresolved; this operation was not published.";
                    case SESSION_CLOSING -> throw new IllegalStateException("Handled above");
                };
                if (original) { finish(); view.writeMessage(message + " Review the observed conflict to start a new merge."); }
                else if (merge) { mergeEditor.busy(false, message + " Re-enter a new secret if required before saving."); }
                else { editor.busy(false, message + " Re-enter a new secret if required before saving."); }
            } else {
                finish();
                view.writeMessage(error);
            }
        });
    }

    private void showUncertainty(boolean busy) {
        if (merge) { view.mergePublicationUncertain(original, busy, this::retry, this::stop); }
        else { view.publicationUncertain(create, busy, this::retry, this::stop); }
    }

    void retry() {
        Edt.require();
        if (closing || pending || !active || editor != null || mergeEditor != null || decision) { return; }
        pending = true;
        showUncertainty(true);
        executor.execute(() -> {
            PublicationRetry previous = retry;
            retry = null;
            try {
                RetryResult result;
                try {
                    result = previous.retryPublication();
                    if (result instanceof SaveResult.PublicationUncertain uncertain) { retry = uncertain.retry(); }
                }
                finally {
                    try { previous.close(); }
                    catch (RuntimeException cleanupFailure) {
                        // A consumed handle's cleanup must not overwrite the retry result
                        // or discard its independently owned successor capability.
                        System.err.println("Totipo: retry capability cleanup failure (details redacted).");
                    }
                }
                if (result instanceof SaveResult.PublicationUncertain) {
                    deliver(Outcome.UNCERTAIN, null, null);
                } else if (result instanceof SaveResult.Saved) {
                    deliver(Outcome.SAVED, null, null);
                } else {
                    throw new IllegalStateException("Unsupported retry result");
                }
            } catch (RuntimeException unexpected) {
                cleanupQuietly();
                SwingUtilities.invokeLater(() -> {
                    if (!closing) {
                        abandoned = true;
                        finish();
                        view.writeMessage("Internal retry operation failure. The earlier publication remains uncertain.");
                    }
                });
            }
        });
    }

    void stop() {
        Edt.require();
        if (closing || pending || !active || editor != null || mergeEditor != null || decision) { return; }
        pending = true;
        showUncertainty(true);
        executor.execute(() -> {
            boolean failed = false;
            try { cleanup(); } catch (RuntimeException unexpected) { failed = true; }
            boolean cleanupFailed = failed;
            SwingUtilities.invokeLater(() -> {
                if (!closing) {
                    abandoned = true;
                    finish();
                    if (cleanupFailed) { view.writeMessage("Internal operation cleanup failure; publication remains uncertain."); }
                }
            });
        });
    }

    private void cancel() {
        if (!closing && !pending) { finish(); }
    }
    private void retireEditor() {
        if (editor != null) { editor.retire(); editor = null; view.retireEditor(); }
        if (mergeEditor != null) { mergeEditor.retire(); mergeEditor = null; view.retireEditor(); }
    }
    private void finish() {
        retireEditor(); active = false; pending = false; decision = false; reviewBase = null;
        view.clearUncertainty();
        view.abandonedPublication(abandoned);
        view.writeAvailability(!closing);
    }
    void closing() {
        Edt.require(); closing = true; retireEditor(); active = false; pending = false; decision = false; reviewBase = null;
        view.clearUncertainty(); view.writeAvailability(false);
        executor.execute(this::cleanupQuietly);
    }
    private void cleanupQuietly() {
        try { cleanup(); }
        catch (RuntimeException unexpected) {
            System.err.println("Totipo: operation cleanup failure (details redacted).");
        }
    }
    private void cleanup() {
        cleanupPartial();
        PublicationRetry owned = retry; retry = null;
        if (owned != null) { owned.close(); }
    }
}
