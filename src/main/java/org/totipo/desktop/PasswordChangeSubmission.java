package org.totipo.desktop;

import org.totipo.PasswordChangeResult;
import org.totipo.VaultSession;
import java.util.Arrays;

/** Exclusive, short-lived caller-buffer ownership; never a retry recipe. */
public final class PasswordChangeSubmission implements AutoCloseable {
    private char[] current;
    private char[] next;

    private PasswordChangeSubmission(char[] current, char[] next) {
        this.current = current;
        this.next = next;
    }

    /** Takes all arrays even on validation failure; confirmation never escapes this call. */
    public static PasswordChangeSubmission prepare(char[] current, char[] next, char[] confirmation) {
        boolean accepted = false;
        try {
            if (!PasswordInput.valid(current) || !PasswordInput.valid(next)) {
                throw new IllegalArgumentException("Enter valid Unicode using at most 1024 UTF-8 bytes for each password.");
            }
            boolean matches;
            try { matches = Arrays.equals(next, confirmation); }
            finally { Arrays.fill(confirmation, '\0'); }
            if (!matches) { throw new IllegalArgumentException("New passwords do not match."); }
            PasswordChangeSubmission submission = new PasswordChangeSubmission(current, next);
            accepted = true;
            return submission;
        } finally {
            Arrays.fill(confirmation, '\0');
            if (!accepted) {
                Arrays.fill(current, '\0');
                Arrays.fill(next, '\0');
            }
        }
    }

    PasswordChangeResult execute(VaultSession session) {
        try { return session.changePassword(current, next); }
        finally { close(); }
    }

    @Override public void close() {
        if (current != null) { Arrays.fill(current, '\0'); current = null; }
        if (next != null) { Arrays.fill(next, '\0'); next = null; }
    }
}
