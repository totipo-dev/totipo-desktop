package org.totipo.desktop;

import org.totipo.*;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TokenWritesTest {
    static final TokenDescriptor FIELDS = new TokenDescriptor(TokenStatus.ACTIVE, "issuer", "account",
            TotpAlgorithm.SHA256, 8, Duration.ofSeconds(42));
    static SaveResult.Saved saved() { return new SaveResult.Saved(new TokenId("01".repeat(32)), List.of()); }
    static final class Recording {
        final List<String> calls = new CopyOnWriteArrayList<>();
        final List<Thread> threads = new CopyOnWriteArrayList<>();
        final Map<String, Object> values = new ConcurrentHashMap<>();
        final Queue<SaveResult> results = new ConcurrentLinkedQueue<>();
        final CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(0);
        TokenAlternative expected;
        boolean runtimeFailure;
        boolean closeFailure;
        byte[] ownedSecret;
        void record(String name) {
            assertFalse(SwingUtilities.isEventDispatchThread()); calls.add(name); threads.add(Thread.currentThread());
        }
        final VaultState state = (VaultState) Proxy.newProxyInstance(VaultState.class.getClassLoader(),
                new Class<?>[]{VaultState.class}, (proxy, method, args) -> {
                    if (method.getName().equals("tokens")) { return List.of(); }
                    if (method.getName().equals("observation")) { return new ObservationProgress.Finished(0, false); }
                    if (method.getName().equals("diagnostics")) { return List.of(); }
                    String name = method.getName(); record(name);
                    if (name.equals("update")) { assertSame(expected, args[0]); assertInstanceOf(TokenAlternative.class, args[0]); }
                    else { assertEquals("createToken", name); }
                    Class<?> type = name.equals("update") ? UpdateToken.class : CreateToken.class;
                    return Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (builder, m, arguments) -> {
                        String call = m.getName(); record(call);
                        if (call.equals("close")) {
                            if (closeFailure) { throw new IllegalStateException("private cleanup details"); }
                            return null;
                        }
                        if (call.equals("save")) {
                            entered.countDown(); TestSupport.await(release);
                            if (runtimeFailure) { throw new IllegalStateException(); }
                            return results.remove();
                        }
                        assertNotEquals("metadata", call);
                        if (call.equals("secret")) {
                            if (ownedSecret != null) { assertArrayEquals(new byte[ownedSecret.length], ownedSecret); }
                            byte[] copy = ((NewSecret) arguments[0]).copy();
                            assertArrayEquals(new byte[]{102}, copy); Arrays.fill(copy, (byte) 0);
                            values.put("wrapper", arguments[0]);
                        } else { values.put(call, arguments[0]); }
                        return builder;
                    });
                });
    }
    @Test void everyBuilderCallConfinedAndSecretWipedAfterIngress() throws Exception {
        for (boolean update : new boolean[]{false, true}) {
            Recording fake = new Recording(); fake.results.add(saved());
            fake.expected = (TokenAlternative) Proxy.newProxyInstance(TokenAlternative.class.getClassLoader(),
                    new Class<?>[]{TokenAlternative.class}, (p, m, a) -> FIELDS);
            byte[] secret = {102};
            fake.ownedSecret = secret;
            ExecutorService executor = Executors.newSingleThreadExecutor(r -> new Thread(r, "totipo-session-test"));
            try {
                assertInstanceOf(SaveResult.Saved.class, executor.submit(() -> TokenWrites.save(fake.state,
                        update ? fake.expected : null, new TokenDraft(FIELDS, secret))).get(10, TimeUnit.SECONDS));
                assertEquals(List.of(update ? "update" : "createToken", "status", "issuer", "account", "algorithm", "digits", "period", "secret", "save", "close"), fake.calls);
                assertEquals(1, new HashSet<>(fake.threads).size());
                assertEquals("totipo-session-test", fake.threads.get(0).getName());
                assertArrayEquals(new byte[1], secret);
                assertThrows(IllegalStateException.class, () -> ((NewSecret) fake.values.get("wrapper")).copy());
                assertEquals(FIELDS.status(), fake.values.get("status"));
                assertEquals(FIELDS.issuer(), fake.values.get("issuer"));
                assertEquals(FIELDS.account(), fake.values.get("account"));
                assertEquals(FIELDS.algorithm(), fake.values.get("algorithm"));
                assertEquals(FIELDS.digits(), fake.values.get("digits"));
                assertEquals(FIELDS.period(), fake.values.get("period"));
            } finally { executor.shutdown(); }
        }
    }
    @Test void abandonedDraftWipesAndUpdateWithoutReplacementDoesNotSetSecret() throws Exception {
        byte[] secret = {102}; new TokenDraft(FIELDS, secret).close(); assertArrayEquals(new byte[1], secret);
        Recording fake = new Recording(); fake.results.add(saved());
        fake.expected = (TokenAlternative) Proxy.newProxyInstance(TokenAlternative.class.getClassLoader(),
                new Class<?>[]{TokenAlternative.class}, (p, m, a) -> FIELDS);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            executor.submit(() -> TokenWrites.save(fake.state, fake.expected, new TokenDraft(FIELDS, null))).get();
            assertFalse(fake.calls.contains("secret"));
        } finally { executor.shutdown(); }
    }
    @Test void factoryExceptionStillWipesTransferredSecret() throws Exception {
        byte[] secret = {102};
        VaultState broken = (VaultState) Proxy.newProxyInstance(VaultState.class.getClassLoader(),
                new Class<?>[]{VaultState.class}, (p, m, a) -> { throw new IllegalStateException(); });
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            assertThrows(ExecutionException.class, () -> executor.submit(() ->
                    TokenWrites.save(broken, null, new TokenDraft(FIELDS, secret))).get());
            assertArrayEquals(new byte[1], secret);
        } finally { executor.shutdown(); }
    }
}
