package org.totipo.desktop.ui;

/** Presentation decision; submitted characters transfer to the application for clearing. */
public final class PasswordPromptResult {
    public enum Action { SUBMIT, EXIT, CHANGE_VAULT, CANCEL }

    private final Action action;
    private final char[] password;

    private PasswordPromptResult(Action action, char[] password) {
        this.action = action;
        this.password = password;
    }

    public static PasswordPromptResult submitted(char[] password) {
        return new PasswordPromptResult(Action.SUBMIT, java.util.Objects.requireNonNull(password));
    }

    public static PasswordPromptResult dismissed(Action action) {
        java.util.Objects.requireNonNull(action);
        if (action == Action.SUBMIT) { throw new IllegalArgumentException("Submission requires a password"); }
        return new PasswordPromptResult(action, null);
    }

    public Action action() { return action; }
    /** Caller-owned characters for SUBMIT; null for other actions. */
    public char[] password() { return password; }
}
