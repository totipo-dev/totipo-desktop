package dev.totipo.desktop.ui;

import dev.totipo.*;
import java.lang.reflect.Proxy;
import java.time.*;
import java.util.*;
import java.util.function.Function;

final class TokenFixtures {
    private TokenFixtures() { }
    static TokenId id(int n) { return new TokenId(String.format("%064x", n)); }
    static RevisionId revision(int n) { return new RevisionId(String.format("%064x", n)); }
    static TokenHead head(int n, ClientMetadata metadata) {
        return new TokenHead() {
            public TokenId tokenId() { return id(1); }
            public RevisionId revision() { return TokenFixtures.revision(n); }
            public ClientMetadata metadata() { return metadata; }
        };
    }
    static TokenAlternative alternative(TokenStatus status, String issuer, String account, TotpAlgorithm algorithm,
                                        int digits, int period, TokenHead... heads) {
        return new TokenAlternative() {
            public TokenDescriptor descriptor() {
                return new TokenDescriptor(status, issuer, account, algorithm, digits, Duration.ofSeconds(period));
            }
            public List<TokenHead> heads() { return List.of(heads); }
        };
    }
    static TokenAlternative active(String issuer) {
        return alternative(TokenStatus.ACTIVE, issuer, "account", TotpAlgorithm.SHA1, 6, 30);
    }
    // Test fixture only: construct complete public grouping data, never used by production.
    static <T> CompetingField<T> field(List<TokenAlternative> all, Function<TokenDescriptor, T> get) {
        Map<T, List<TokenAlternative>> groups = new LinkedHashMap<>();
        all.forEach(a -> groups.computeIfAbsent(get.apply(a.descriptor()), key -> new ArrayList<>()).add(a));
        return new CompetingField<>(groups.entrySet().stream()
                .map(e -> new CompetingField.Value<>(e.getKey(), e.getValue())).toList());
    }
    static TokenState token(int id, List<TokenAlternative> all, List<SecretGroup> secrets,
                            List<TokenHead> heads, List<UnresolvedReference> unresolved, boolean conflict) {
        return new TokenState() {
            public TokenId id() { return TokenFixtures.id(id); }
            public List<TokenAlternative> alternatives() { return all; }
            public List<TokenHead> heads() { return heads; }
            public List<UnresolvedReference> unresolvedReferences() { return unresolved; }
            public boolean hasConflict() { return conflict; }
            public TokenCompetition competingValues() {
                return new TokenCompetition(field(all, TokenDescriptor::status), field(all, TokenDescriptor::issuer),
                        field(all, TokenDescriptor::account), field(all, TokenDescriptor::algorithm),
                        field(all, TokenDescriptor::digits), field(all, TokenDescriptor::period), new CompetingSecret(secrets));
            }
        };
    }
    static TokenState token(int id, TokenAlternative... alternatives) {
        List<TokenAlternative> all = List.of(alternatives);
        return token(id, all, all.isEmpty() ? List.of() : List.of(new SecretGroup(all)),
                all.stream().flatMap(a -> a.heads().stream()).toList(), List.of(), all.size() > 1);
    }
    record Call(TokenAlternative alternative, Instant now) { }
    static final class State {
        final List<Call> calls = new ArrayList<>();
        boolean fail;
        final VaultState value;
        State(TokenState... tokens) {
            value = (VaultState) Proxy.newProxyInstance(VaultState.class.getClassLoader(), new Class<?>[]{VaultState.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "tokens" -> List.of(tokens);
                        case "token" -> Arrays.stream(tokens).filter(t -> t.id().equals(args[0])).findFirst();
                        case "observation" -> new ObservationProgress.Finished(0, false);
                        case "diagnostics" -> List.of();
                        case "generateTotp" -> {
                            Edt.require();
                            Instant now = (Instant) args[1];
                            calls.add(new Call((TokenAlternative) args[0], now));
                            if (fail) { throw new IllegalStateException("Private failure details"); }
                            // Deliberately unrelated to descriptor periods or wall-clock modulo boundaries.
                            yield new TotpCode("001234", now.minusSeconds(2), now.plusSeconds(8));
                        }
                        default -> throw new AssertionError("Forbidden API: " + method.getName());
                    });
        }
    }
    static final class MutableClock extends Clock {
        Instant now = Instant.parse("2026-01-01T00:00:07Z");
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return this; }
        public Instant instant() { return now; }
    }
}
