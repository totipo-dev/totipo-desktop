package dev.totipo.desktop.ui;

import dev.totipo.TokenState;
import java.util.Locale;

/** Literal presentation-only matching; never chooses an alternative. */
final class TokenSearch {
    private TokenSearch() { }
    static boolean matches(TokenState token, String query) {
        String needle = query.toLowerCase(Locale.ROOT);
        return token.id().hex().contains(needle) || token.alternatives().stream().anyMatch(a ->
                a.descriptor().issuer().toLowerCase(Locale.ROOT).contains(needle)
                || a.descriptor().account().toLowerCase(Locale.ROOT).contains(needle));
    }
}
