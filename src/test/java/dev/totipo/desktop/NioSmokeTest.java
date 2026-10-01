package dev.totipo.desktop;

import dev.totipo.CreateVaultResult;
import dev.totipo.OpenResult;
import dev.totipo.NewSecret;
import dev.totipo.SaveResult;
import dev.totipo.VaultState;
import dev.totipo.storage.nio.NioTotipo;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class NioSmokeTest {
    @TempDir Path directory;

    @Test void desktopBoundaryCreatesUpdatesAndObservesBeforeTotp() throws Exception {
        char[] password = {'m', '2', 'a'};
        byte[] input = {102};
        var executor = java.util.concurrent.Executors.newSingleThreadExecutor(r -> new Thread(r, "totipo-session-smoke"));
        try (var session = assertInstanceOf(CreateVaultResult.Created.class,
                NioTotipo.create(directory, password)).session()) {
            var initial = session.state();
            var fields = new dev.totipo.TokenDescriptor(dev.totipo.TokenStatus.ACTIVE, "Desktop", "before",
                    dev.totipo.TotpAlgorithm.SHA1, 6, java.time.Duration.ofSeconds(30));
            var saved = assertInstanceOf(SaveResult.Saved.class, executor.submit(() ->
                    TokenWrites.save(initial, null, new TokenDraft(fields, input))).get(10, TimeUnit.SECONDS));
            assertArrayEquals(new byte[1], input);
            VaultState first = observe(session, saved.tokenId(), "before");
            var alternative = first.token(saved.tokenId()).orElseThrow().alternatives().get(0);
            var updated = new dev.totipo.TokenDescriptor(dev.totipo.TokenStatus.ACTIVE, "Desktop", "after",
                    dev.totipo.TotpAlgorithm.SHA256, 8, java.time.Duration.ofSeconds(45));
            assertInstanceOf(SaveResult.Saved.class, executor.submit(() ->
                    TokenWrites.save(first, alternative, new TokenDraft(updated, null))).get(10, TimeUnit.SECONDS));
            VaultState next = observe(session, saved.tokenId(), "after");
            var selected = next.token(saved.tokenId()).orElseThrow().alternatives().get(0);
            assertEquals(updated, selected.descriptor());
            assertEquals(8, next.generateTotp(selected, Instant.parse("2026-01-01T00:00:07Z")).code().length());
        } finally {
            Arrays.fill(password, '\0'); Arrays.fill(input, (byte) 0); executor.shutdown();
        }
    }

    private static VaultState observe(dev.totipo.VaultSession session, dev.totipo.TokenId id, String account) throws Exception {
        CompletableFuture<VaultState> observed = new CompletableFuture<>();
        session.states().subscribe(new Flow.Subscriber<>() {
            private Flow.Subscription subscription;
            public void onSubscribe(Flow.Subscription value) { subscription = value; value.request(Long.MAX_VALUE); }
            public void onNext(VaultState state) {
                if (state.token(id).stream().flatMap(token -> token.alternatives().stream())
                        .anyMatch(alternative -> alternative.descriptor().account().equals(account))) {
                    observed.complete(state); subscription.cancel();
                }
            }
            public void onError(Throwable failure) { observed.completeExceptionally(failure); }
            public void onComplete() { observed.completeExceptionally(new IllegalStateException("Closed before observation")); }
        });
        return observed.get(10, TimeUnit.SECONDS);
    }

    @Test void realCoreTotpUsesReturnedHalfOpenInterval() throws Exception {
        char[] password = {'t', 'e', 's', 't'};
        try (var session = assertInstanceOf(CreateVaultResult.Created.class,
                NioTotipo.create(directory, password)).session()) {
            SaveResult.Saved saved;
            // Mutation is isolated to test setup, using only the public application API.
            try (var secret = NewSecret.copyOf(new byte[]{1, 2, 3, 4});
                 var create = session.state().createToken()) {
                saved = assertInstanceOf(SaveResult.Saved.class,
                        create.issuer("Integration").account("test").secret(secret).save());
            }
            CompletableFuture<VaultState> observed = new CompletableFuture<>();
            session.states().subscribe(new Flow.Subscriber<>() {
                private Flow.Subscription subscription;
                public void onSubscribe(Flow.Subscription value) { subscription = value; value.request(Long.MAX_VALUE); }
                public void onNext(VaultState state) {
                    if (state.token(saved.tokenId()).isPresent()) { observed.complete(state); subscription.cancel(); }
                }
                public void onError(Throwable failure) { observed.completeExceptionally(failure); }
                public void onComplete() { observed.completeExceptionally(new IllegalStateException("Closed before observation")); }
            });
            VaultState state = observed.get(10, TimeUnit.SECONDS);
            var alternative = state.token(saved.tokenId()).orElseThrow().alternatives().get(0);
            Instant now = Instant.parse("2026-01-01T00:00:07Z");
            var code = state.generateTotp(alternative, now);
            assertFalse(now.isBefore(code.validFrom()));
            assertTrue(now.isBefore(code.validUntil()));
            assertEquals(code, state.generateTotp(alternative, code.validUntil().minusNanos(1)));
            var next = state.generateTotp(alternative, code.validUntil());
            assertEquals(code.validUntil(), next.validFrom());
            assertTrue(next.validUntil().isAfter(next.validFrom()));
            assertEquals(alternative.descriptor().digits(), code.code().length());
        } finally { Arrays.fill(password, '\0'); }
    }

    @Test void createCloseOpenCloseInExistingDirectory() {
        char[] password = {'s', 'm', 'o', 'k', 'e'};
        try {
            CreateVaultResult.Created created = assertInstanceOf(CreateVaultResult.Created.class,
                    NioTotipo.create(directory, password));
            created.session().close();
            OpenResult.Opened opened = assertInstanceOf(OpenResult.Opened.class,
                    NioTotipo.open(directory, password));
            opened.session().close();
        } finally {
            Arrays.fill(password, '\0');
        }
    }
}
