package org.totipo.desktop;

import org.totipo.*;
import java.util.List;

/** Immutable descriptive capture. Filtering retains core grouping and opaque alternative identity. */
public record MergeInputs(VaultState base, TokenState token, List<TokenAlternative> captured,
                          TokenCompetition competition, List<TokenAlternative> selected) {
    public MergeInputs {
        captured = List.copyOf(captured); selected = List.copyOf(selected);
        if (selected.size() < 2 || !captured.containsAll(selected)
                || selected.stream().distinct().count() != selected.size()) {
            throw new IllegalArgumentException("Select at least two distinct captured alternatives.");
        }
    }
    public static MergeInputs capture(VaultState base, TokenState token) {
        if (!token.hasConflict() || token.alternatives().size() < 2) {
            throw new IllegalArgumentException("No complete semantic conflict.");
        }
        return new MergeInputs(base, token, token.alternatives(), token.competingValues(), token.alternatives());
    }
    public MergeInputs select(List<TokenAlternative> inputs) {
        return new MergeInputs(base, token, captured, competition, inputs);
    }
    public boolean fullFrontier() { return selected.size() == captured.size(); }
    public TokenCompetition selectedCompetition() {
        return new TokenCompetition(filter(competition.status()), filter(competition.issuer()),
                filter(competition.account()), filter(competition.algorithm()), filter(competition.digits()),
                filter(competition.period()), new CompetingSecret(competition.secret().groups().stream()
                .map(group -> new SecretGroup(members(group.alternatives())))
                .filter(group -> !group.alternatives().isEmpty()).toList()));
    }
    private List<TokenAlternative> members(List<TokenAlternative> members) {
        return members.stream().filter(selected::contains).toList();
    }
    private <T> CompetingField<T> filter(CompetingField<T> field) {
        return new CompetingField<>(field.values().stream()
                .map(value -> new CompetingField.Value<>(value.value(), members(value.alternatives())))
                .filter(value -> !value.alternatives().isEmpty()).toList());
    }
    public String labels(List<TokenAlternative> members) {
        return members.stream().map(a -> "Alternative " + (captured.indexOf(a) + 1))
                .collect(java.util.stream.Collectors.joining(", "));
    }
}
