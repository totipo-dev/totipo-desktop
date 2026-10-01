package dev.totipo.desktop;

/** Validates human input without allocating an encoded copy or retaining characters. */
final class PasswordInput {
    private PasswordInput() { }

    static boolean valid(char[] password) {
        // Saturate the count but continue validating UTF-16 throughout the input.
        int bytes = 0;
        for (int i = 0; i < password.length; i++) {
            char ch = password[i];
            int width;
            if (Character.isHighSurrogate(ch)) {
                if (++i == password.length || !Character.isLowSurrogate(password[i])) {
                    return false;
                }
                width = 4;
            } else if (Character.isLowSurrogate(ch)) {
                return false;
            } else {
                width = ch <= 0x7f ? 1 : ch <= 0x7ff ? 2 : 3;
            }
            bytes = Math.min(1025, bytes + width);
        }
        return bytes <= 1024;
    }
}
