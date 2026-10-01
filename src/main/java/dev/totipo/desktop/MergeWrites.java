package dev.totipo.desktop;

import dev.totipo.*;
import javax.swing.SwingUtilities;

/** All merge builder operations, including construction and cleanup, share the session thread. */
final class MergeWrites {
    private MergeWrites() { }
    static final class InconsistentDraft extends IllegalStateException {
        private static final long serialVersionUID = 1L;
        InconsistentDraft() { super("Merge draft does not match builder inputs"); }
    }
    static SaveResult save(MergeDraft draft) {
        if (SwingUtilities.isEventDispatchThread()) { throw new IllegalStateException("Merge on EDT"); }
        SaveResult result = null;
        MergeInputs inputs = draft.inputs();
        try {
            try (draft; MergeToken builder = inputs.fullFrontier()
                    ? inputs.base().merge(inputs.token().id()) : inputs.base().merge(inputs.selected())) {
                draft.apply(builder);
                result = builder.save();
            }
            return result;
        } catch (RuntimeException failure) {
            if (result == null) { throw failure; }
            System.err.println("Totipo: merge builder cleanup failure (details redacted).");
            return result;
        }
    }
}
