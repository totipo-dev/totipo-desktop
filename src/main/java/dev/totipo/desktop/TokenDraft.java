package dev.totipo.desktop;

import dev.totipo.NewSecret;
import dev.totipo.TokenDescriptor;
import dev.totipo.TokenEditor;
import java.util.Arrays;
import java.util.Objects;

/** Exclusive ownership transfers from submitter to task. Never shared with the form. */
public final class TokenDraft implements AutoCloseable {
    private final TokenDescriptor fields;
    private byte[] secret;

    /** Takes ownership of replacementSecret, including responsibility for wiping it. */
    public TokenDraft(TokenDescriptor fields, byte[] replacementSecret) {
        this.fields = Objects.requireNonNull(fields);
        secret = replacementSecret;
    }

    void apply(TokenEditor<?> builder) {
        builder.status(fields.status());
        builder.issuer(fields.issuer());
        builder.account(fields.account());
        builder.algorithm(fields.algorithm());
        builder.digits(fields.digits());
        builder.period(fields.period());
        if (secret != null) {
            try (NewSecret ingress = NewSecret.copyOf(secret)) {
                close(); // Core wrapper now owns a defensive copy.
                builder.secret(ingress); // Synchronous builder copy; wrapper closes immediately.
            }
        }
    }

    @Override public void close() {
        if (secret != null) { Arrays.fill(secret, (byte) 0); secret = null; }
    }
}
