package dev.totipo.desktop.ui;

import dev.totipo.*;
import java.util.List;
import java.util.stream.Collectors;

/** Text derived only from the public projection; labels have no semantic priority. */
final class TokenPresentation {
    private TokenPresentation() { }

    record Row(TokenId id, String text) {
        @Override public String toString() { return text; }
    }

    static Row row(TokenState token) {
        TokenCompetition values = token.competingValues();
        String text = token.alternatives().isEmpty()
                ? "Incomplete token observation — " + token.id().hex().substring(0, 12)
                : single(values.issuer(), "issuer") + " — " + single(values.account(), "account");
        if (token.hasConflict()) { text += "   CONFLICT"; }
        if (!token.unresolvedReferences().isEmpty()) { text += "   UNRESOLVED"; }
        if (values.status().values().size() == 1
                && values.status().values().get(0).value() == TokenStatus.TOMBSTONED) {
            text += "   TOMBSTONED";
        }
        return new Row(token.id(), text);
    }

    private static String single(CompetingField<?> field, String name) {
        return switch (field.values().size()) {
            case 0 -> "[unavailable " + name + "]";
            case 1 -> field.values().get(0).value().toString();
            default -> "[conflicting " + name + "]";
        };
    }

    static String label(int index) { return "Alternative " + (index + 1); }

    static String detail(TokenState token) {
        StringBuilder out = new StringBuilder("Token ID: " + token.id().hex() + "\n");
        out.append(token.hasConflict() ? "CONFLICT — distinct complete token values\n" : "No semantic conflict observed\n");
        List<TokenAlternative> alternatives = token.alternatives();
        if (alternatives.isEmpty()) {
            out.append("No complete token value is currently available from the local observation.\n");
        } else if (!token.hasConflict() && alternatives.size() == 1 && token.heads().size() > 1) {
            out.append(token.heads().size()).append(" current causal heads carry the same token value.\n");
        }
        out.append("Alternative labels are for presentation only; they do not indicate preference.\n");
        for (int i = 0; i < alternatives.size(); i++) {
            TokenAlternative alternative = alternatives.get(i);
            TokenDescriptor d = alternative.descriptor();
            out.append('\n').append(label(i)).append('\n')
                    .append("Status: ").append(d.status()).append("\nIssuer: ").append(d.issuer())
                    .append("\nAccount: ").append(d.account()).append("\nAlgorithm: ").append(d.algorithm())
                    .append("\nDigits: ").append(d.digits()).append("\nPeriod: ").append(d.period()).append('\n');
            out.append("Carried by current causal heads:\n");
            alternative.heads().forEach(head -> out.append(head.revision().hex()).append('\n'));
        }
        TokenCompetition c = token.competingValues();
        out.append("\nField competition\n");
        field(out, "Status", c.status(), alternatives);
        field(out, "Issuer", c.issuer(), alternatives);
        field(out, "Account", c.account(), alternatives);
        field(out, "Algorithm", c.algorithm(), alternatives);
        field(out, "Digits", c.digits(), alternatives);
        field(out, "Period", c.period(), alternatives);
        int groups = c.secret().groups().size();
        out.append("\nSecret equality\n");
        if (groups == 0) { out.append("No complete observed secret value.\n"); }
        else if (groups == 1) { out.append("All current alternatives use the same secret.\n"); }
        else { out.append("Current alternatives contain ").append(groups).append(" distinct secret values.\n"); }
        for (int i = 0; i < groups; i++) {
            out.append("Secret group ").append(i + 1).append(" — ")
                    .append(labels(c.secret().groups().get(i).alternatives(), alternatives)).append('\n');
        }
        out.append("\nTechnical details — current causal heads\n");
        for (TokenHead head : token.heads()) {
            out.append(head.revision().hex()).append('\n');
            head.metadata().clientName().ifPresent(name -> out.append("Client-provided name: ").append(name).append('\n'));
            head.metadata().clientTimeBits().ifPresent(bits -> out.append("Client-provided raw unsigned time: ")
                    .append(Long.toUnsignedString(bits)).append('\n'));
        }
        out.append("\nUnresolved causal references: ").append(token.unresolvedReferences().size()).append('\n');
        token.unresolvedReferences().forEach(ref -> out.append("Child: ").append(ref.child().hex())
                .append("\nReferenced parent: ").append(ref.parent().hex()).append('\n'));
        return out.toString();
    }

    private static void field(StringBuilder out, String name, CompetingField<?> field,
                              List<TokenAlternative> all) {
        out.append(name).append(field.disagrees() ? " (competing):\n" : ":\n");
        if (field.values().isEmpty()) { out.append("No complete observed value\n"); }
        field.values().forEach(value -> out.append(value.value()).append(" — ")
                .append(labels(value.alternatives(), all)).append('\n'));
    }

    private static String labels(List<TokenAlternative> members, List<TokenAlternative> all) {
        return members.stream().map(member -> label(all.indexOf(member))).collect(Collectors.joining(", "));
    }
}
