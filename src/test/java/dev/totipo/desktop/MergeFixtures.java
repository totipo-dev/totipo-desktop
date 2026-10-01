package dev.totipo.desktop;

import dev.totipo.*;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;
import javax.swing.SwingUtilities;
import static org.junit.jupiter.api.Assertions.*;

/** Public-interface fakes; no protocol/storage implementation involved. */
final class MergeFixtures {
    private MergeFixtures() { }
    static final TokenId ID = new TokenId("ab".repeat(32));
    static TokenAlternative alternative(int n) {
        return new TokenAlternative() {
            public TokenDescriptor descriptor() {
                return new TokenDescriptor(n == 0 ? TokenStatus.ACTIVE : TokenStatus.TOMBSTONED,
                        "issuer " + n, "account " + n, TotpAlgorithm.values()[n % 3], 6 + n % 3, Duration.ofSeconds(30 + n));
            }
            public List<TokenHead> heads() { return List.of(); }
        };
    }
    static <T> CompetingField<T> field(List<TokenAlternative> alternatives, Function<TokenDescriptor, T> get) {
        Map<T, List<TokenAlternative>> groups = new LinkedHashMap<>();
        alternatives.forEach(a -> groups.computeIfAbsent(get.apply(a.descriptor()), k -> new ArrayList<>()).add(a));
        return new CompetingField<>(groups.entrySet().stream().map(e -> new CompetingField.Value<>(e.getKey(), e.getValue())).toList());
    }
    static TokenState token(List<TokenAlternative> alternatives, List<SecretGroup> secrets) {
        return new TokenState() {
            public TokenId id() { return ID; }
            public List<TokenAlternative> alternatives() { return alternatives; }
            public List<TokenHead> heads() { return List.of(); }
            public List<UnresolvedReference> unresolvedReferences() { return List.of(); }
            public boolean hasConflict() { return alternatives.size() > 1; }
            public TokenCompetition competingValues() {
                return new TokenCompetition(field(alternatives, TokenDescriptor::status), field(alternatives, TokenDescriptor::issuer),
                        field(alternatives, TokenDescriptor::account), field(alternatives, TokenDescriptor::algorithm),
                        field(alternatives, TokenDescriptor::digits), field(alternatives, TokenDescriptor::period), new CompetingSecret(secrets));
            }
        };
    }
    static final class Recording {
        final List<String> calls = new CopyOnWriteArrayList<>();
        final List<Thread> threads = new CopyOnWriteArrayList<>();
        final List<Object> factories = new CopyOnWriteArrayList<>();
        final Queue<SaveResult> results = new ConcurrentLinkedQueue<>();
        final Map<String, Object> values = new ConcurrentHashMap<>();
        final List<List<MergeSecretChoice>> issued = new CopyOnWriteArrayList<>();
        final List<MergeSecretChoice> used = new CopyOnWriteArrayList<>();
        final CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(0);
        final TokenState token;
        final VaultState state;
        boolean closeFailure;
        boolean missingSecret;
        boolean ambiguousSecret;
        boolean unresolved;
        byte[] ownedSecret;
        Recording() { this(List.of(alternative(0), alternative(1), alternative(2)), false); }
        Recording(List<TokenAlternative> alternatives, boolean agreedSecret) {
            token = token(alternatives, agreedSecret ? List.of(new SecretGroup(alternatives))
                    : alternatives.stream().map(a -> new SecretGroup(List.of(a))).toList());
            state = (VaultState) Proxy.newProxyInstance(VaultState.class.getClassLoader(), new Class<?>[]{VaultState.class},
                    (p, m, a) -> switch (m.getName()) {
                        case "tokens" -> List.of(token);
                        case "token" -> { assertEquals(ID, a[0]); yield Optional.of(token); }
                        case "observation" -> new ObservationProgress.Finished(0, false);
                        case "diagnostics" -> List.of();
                        case "merge" -> { record("merge"); factories.add(a[0]); yield builder(a[0]); }
                        default -> throw new AssertionError("Unexpected API " + m.getName());
                    });
        }
        void record(String call) {
            assertFalse(SwingUtilities.isEventDispatchThread()); calls.add(call); threads.add(Thread.currentThread());
        }
        private MergeToken builder(Object selection) {
            List<TokenAlternative> selected = selection instanceof TokenId ? token.alternatives()
                    : ((Collection<?>) selection).stream().map(TokenAlternative.class::cast).toList();
            TokenCompetition competition = MergeInputs.capture(state, token).select(selected).selectedCompetition();
            List<MergeSecretChoice> choices = new ArrayList<>();
            if (!missingSecret) {
                for (SecretGroup group : competition.secret().groups()) { choices.add(group::alternatives); }
                if (ambiguousSecret) { choices.add(choices.get(0)); }
            }
            issued.add(choices);
            return (MergeToken) Proxy.newProxyInstance(MergeToken.class.getClassLoader(), new Class<?>[]{MergeToken.class},
                    (p, m, args) -> {
                        String call = m.getName(); record(call);
                        return switch (call) {
                            case "competingValues" -> competition;
                            case "secretChoices" -> choices;
                            case "unresolvedFields" -> unresolved ? List.of("issuer") : List.of();
                            case "save" -> { entered.countDown(); TestSupport.await(release); yield results.remove(); }
                            case "close" -> { if (closeFailure) { throw new IllegalStateException(); } yield null; }
                            case "secret" -> {
                                if (args[0] instanceof MergeSecretChoice choice) {
                                    assertTrue(choices.stream().anyMatch(c -> c == choice)); used.add(choice);
                                } else {
                                    assertInstanceOf(NewSecret.class, args[0]);
                                    assertArrayEquals(new byte[ownedSecret.length], ownedSecret);
                                    values.put("secret", args[0]);
                                }
                                yield p;
                            }
                            case "status", "issuer", "account", "algorithm", "digits", "period" -> { values.put(call, args[0]); yield p; }
                            default -> throw new AssertionError("Unexpected builder API " + call);
                        };
                    });
        }
        MergeInputs inputs() { return MergeInputs.capture(state, token); }
        MergeDraft draft(MergeInputs inputs) {
            return new MergeDraft(inputs, TokenWritesTest.FIELDS, inputs.selected().get(0), null);
        }
    }
    static final class Partial implements PartialResolution {
        PartialSaveResult result = TokenWritesTest.saved();
        int saves;
        int closes;
        boolean closeFailure;
        final CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(0);
        final List<Thread> threads = new CopyOnWriteArrayList<>();
        public PartialSaveResult save() {
            assertFalse(SwingUtilities.isEventDispatchThread()); threads.add(Thread.currentThread());
            assertEquals(0, saves++); entered.countDown(); TestSupport.await(release); return result;
        }
        public void close() {
            assertFalse(SwingUtilities.isEventDispatchThread()); threads.add(Thread.currentThread());
            closes++; if (closeFailure) { throw new IllegalStateException(); }
        }
    }
}
