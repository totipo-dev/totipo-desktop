package dev.totipo.desktop;

import dev.totipo.*;
import javax.swing.SwingUtilities;

/** One synchronous operation; caller must use the owning session executor. */
final class TokenWrites {
    private TokenWrites() { }
    static SaveResult save(VaultState base, TokenAlternative alternative, TokenDraft draft) {
        if (SwingUtilities.isEventDispatchThread()) { throw new IllegalStateException("Write on EDT"); }
        SaveResult result = null;
        try {
            try (draft; TokenEditor<?> builder = alternative == null ? base.createToken() : base.update(alternative)) {
                draft.apply(builder);
                result = builder.save();
            }
            return result;
        } catch (RuntimeException failure) {
            if (result != null) {
                // Cleanup cannot erase publication knowledge. Returned capabilities are
                // independent of the builder and still transfer to the caller for handling.
                System.err.println("Totipo: builder cleanup failure (details redacted).");
                return result;
            }
            throw failure;
        }
    }
}
