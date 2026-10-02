package org.totipo.desktop;

import org.totipo.*;
import java.util.Objects;

/** Submitted desktop values; owns caller secret ingress, never a core capability. */
public final class MergeDraft implements AutoCloseable {
    private final MergeInputs inputs;
    private final TokenAlternative secretRepresentative;
    private final TokenDraft values;

    public MergeDraft(MergeInputs inputs, TokenDescriptor fields, TokenAlternative representative, byte[] newSecret) {
        this.inputs = Objects.requireNonNull(inputs);
        if ((representative == null) == (newSecret == null)
                || (representative != null && !inputs.selected().contains(representative))) {
            if (newSecret != null) { java.util.Arrays.fill(newSecret, (byte) 0); }
            throw new IllegalArgumentException("Choose an existing secret group or enter a new secret.");
        }
        secretRepresentative = representative;
        values = new TokenDraft(fields, newSecret);
    }
    MergeInputs inputs() { return inputs; }
    void apply(MergeToken builder) {
        // Builder-issued descriptions/capabilities stay within this executor operation.
        Objects.requireNonNull(builder.competingValues());
        var choices = builder.secretChoices();
        builder.unresolvedFields();
        if (secretRepresentative != null) {
            var matches = choices.stream().filter(choice -> choice.alternatives().contains(secretRepresentative)).toList();
            if (matches.size() != 1) { throw new MergeWrites.InconsistentDraft(); }
            builder.secret(matches.get(0));
        }
        values.apply(builder);
        if (!builder.unresolvedFields().isEmpty()) { throw new MergeWrites.InconsistentDraft(); }
    }
    @Override public void close() { values.close(); }
}
