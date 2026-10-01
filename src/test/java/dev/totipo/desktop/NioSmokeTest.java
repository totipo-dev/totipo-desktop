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

    @Test void realConcurrentUpdatesMergeThroughDesktopBoundaryAndAuthoritativeObservation() throws Exception {
        char[] password = {'m', '2', 'b'};
        var executor = java.util.concurrent.Executors.newSingleThreadExecutor(r -> new Thread(r, "totipo-session-merge-smoke"));
        try (var session = assertInstanceOf(CreateVaultResult.Created.class, NioTotipo.create(directory, password)).session()) {
            var fields = new dev.totipo.TokenDescriptor(dev.totipo.TokenStatus.ACTIVE, "Issuer A", "base",
                    dev.totipo.TotpAlgorithm.SHA1, 6, java.time.Duration.ofSeconds(30));
            var saved = assertInstanceOf(SaveResult.Saved.class, executor.submit(() ->
                    TokenWrites.save(session.state(), null, new TokenDraft(fields, new byte[]{102}))).get(10, TimeUnit.SECONDS));
            VaultState base = observe(session, saved.tokenId(), "base");
            var ancestor = base.token(saved.tokenId()).orElseThrow().alternatives().get(0);
            var b = new dev.totipo.TokenDescriptor(dev.totipo.TokenStatus.ACTIVE, "Issuer B", "branch B",
                    dev.totipo.TotpAlgorithm.SHA256, 7, java.time.Duration.ofSeconds(42));
            var c = new dev.totipo.TokenDescriptor(dev.totipo.TokenStatus.TOMBSTONED, "Issuer C", "branch C",
                    dev.totipo.TotpAlgorithm.SHA512, 8, java.time.Duration.ofSeconds(60));
            assertInstanceOf(SaveResult.Saved.class, executor.submit(() -> TokenWrites.save(base, ancestor, new TokenDraft(b, null))).get());
            observe(session, saved.tokenId(), "branch B");
            assertInstanceOf(SaveResult.Saved.class, executor.submit(() -> TokenWrites.save(base, ancestor, new TokenDraft(c, null))).get());
            VaultState conflicted = observe(session, saved.tokenId(), "branch C");
            var token = conflicted.token(saved.tokenId()).orElseThrow();
            assertTrue(token.hasConflict()); assertEquals(2, token.alternatives().size());
            var inputs = MergeInputs.capture(conflicted, token);
            var combined = new dev.totipo.TokenDescriptor(dev.totipo.TokenStatus.ACTIVE, b.issuer(), "merged",
                    c.algorithm(), c.digits(), b.period());
            var representative = inputs.selectedCompetition().secret().groups().get(0).alternatives().get(0);
            assertInstanceOf(SaveResult.Saved.class, executor.submit(() ->
                    MergeWrites.save(new MergeDraft(inputs, combined, representative, null))).get(10, TimeUnit.SECONDS));
            VaultState observed = observe(session, saved.tokenId(), "merged");
            var merged = observed.token(saved.tokenId()).orElseThrow();
            assertFalse(merged.hasConflict()); assertEquals(1, merged.alternatives().size());
            assertEquals(combined, merged.alternatives().get(0).descriptor());
            assertEquals(8, observed.generateTotp(merged.alternatives().get(0), Instant.parse("2026-01-01T00:00:07Z")).code().length());
        } finally { Arrays.fill(password, '\0'); executor.shutdown(); }
    }

    static VaultState observe(dev.totipo.VaultSession session, dev.totipo.TokenId id, String account) throws Exception {
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
