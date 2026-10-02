package org.totipo.qualification;

import org.totipo.*;
import org.totipo.storage.nio.NioTotipo;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

/** Operator-run, disposable filesystem qualification using only the public API. */
public final class FilesystemQualification {
    private FilesystemQualification() { }

    public static void main(String[] args) throws Exception {
        require(args.length == 1 && !args[0].isBlank(), "An explicit qualification root is required");
        Path root = Path.of(args[0]).toRealPath();
        require(Files.isDirectory(root) && Files.isWritable(root), "Root must be an existing writable directory");
        require(root.getParent() != null && !root.equals(Path.of(System.getProperty("user.home")).toRealPath()),
                "Filesystem root and home directory are not qualification roots");
        var store = Files.getFileStore(root);
        System.out.printf("Qualification time=%s OS=%s version=%s architecture=%s JVM=%s %s filesystem=%s POSIX=%s%n",
                Instant.now(), System.getProperty("os.name"), System.getProperty("os.version"),
                System.getProperty("os.arch"), System.getProperty("java.vendor"), System.getProperty("java.version"),
                store.type(), store.supportsFileAttributeView("posix"));
        Path directory = Files.createTempDirectory(root, "totipo-qualification-");
        try {
            workflow(directory);
        } finally {
            try {
                // No FOLLOW_LINKS: remove only the created tree, never a symlink target.
                Files.walkFileTree(directory, new SimpleFileVisitor<>() {
                    @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                        Files.delete(file); return FileVisitResult.CONTINUE;
                    }
                    @Override public FileVisitResult postVisitDirectory(Path dir, IOException failure) throws IOException {
                        if (failure != null) { throw failure; }
                        Files.delete(dir); return FileVisitResult.CONTINUE;
                    }
                });
            } catch (IOException failure) {
                System.err.println("Qualification cleanup failed; manually remove only: " + directory);
                throw failure;
            }
        }
        System.out.println("PASS: workflow and cleanup; only this exact filesystem/environment was tested.");
    }

    private static void workflow(Path directory) throws Exception {
        char[] oldPassword = {'q', 'u', 'a', 'l', '-', 'o', 'l', 'd'};
        char[] newPassword = {'q', 'u', 'a', 'l', '-', 'n', 'e', 'w'};
        byte[] input = {1, 2, 3, 4};
        Instant time = Instant.parse("2026-01-01T00:00:07Z");
        try {
            VaultFingerprint fingerprint;
            try (var session = expect(CreateVaultResult.Created.class, NioTotipo.create(directory, oldPassword)).session()) {
                fingerprint = session.fingerprint();
            }
            TokenId id;
            String code;
            try (var session = open(directory, oldPassword)) {
                require(fingerprint.equals(session.fingerprint()), "Root changed after creation");
                try (var secret = NewSecret.copyOf(input); var builder = session.state().createToken()) {
                    id = expect(SaveResult.Saved.class, builder.status(TokenStatus.ACTIVE).issuer("Qualification")
                            .account("created").algorithm(TotpAlgorithm.SHA1).digits(6)
                            .period(Duration.ofSeconds(30)).secret(secret).save()).tokenId();
                }
                var state = observe(session, s -> hasAccount(s, id, "created"));
                code = state.generateTotp(alternative(state, id), time).code();
            }
            try (var session = open(directory, oldPassword)) {
                var state = observe(session, s -> hasAccount(s, id, "created"));
                require(code.equals(state.generateTotp(alternative(state, id), time).code()), "TOTP changed on reopen");
                update(state, id, "ordinary");
                var historicalBase = observe(session, s -> hasAccount(s, id, "ordinary"));
                update(historicalBase, id, "branch-one");
                observe(session, s -> hasAccount(s, id, "branch-one"));
                update(historicalBase, id, "branch-two");
                var conflict = observe(session, s -> s.token(id).map(t -> t.hasConflict() && t.alternatives().size() == 2).orElse(false));
                try (var merge = conflict.merge(id)) {
                    expect(SaveResult.Saved.class, merge.status(TokenStatus.ACTIVE).issuer("Qualification")
                            .account("merged").algorithm(TotpAlgorithm.SHA1).digits(6).period(Duration.ofSeconds(30))
                            .secret(merge.secretChoices().get(0)).save());
                }
                var merged = observe(session, s -> hasAccount(s, id, "merged") && !s.token(id).orElseThrow().hasConflict());
                require(code.equals(merged.generateTotp(alternative(merged, id), time).code()), "Merge changed preserved TOTP");
            }
            try (var session = open(directory, oldPassword)) {
                var merged = observe(session, s -> hasAccount(s, id, "merged"));
                require(merged.tokens().size() == 1 && merged.token(id).orElseThrow().alternatives().size() == 1,
                        "Merged token state did not survive reopen");
                require(code.equals(merged.generateTotp(alternative(merged, id), time).code()), "Merged TOTP changed on reopen");
                require(fingerprint.equals(session.fingerprint()), "Root changed before password change");
                require(session.changePassword(oldPassword, newPassword) == PasswordChangeResult.CHANGED, "Password change failed");
                require(fingerprint.equals(session.fingerprint()), "Password change altered root");
            }
            var rejected = NioTotipo.open(directory, oldPassword);
            if (rejected instanceof OpenResult.Opened opened) { opened.session().close(); }
            expect(OpenResult.AuthenticationFailed.class, rejected);
            try (var session = open(directory, newPassword)) {
                require(fingerprint.equals(session.fingerprint()), "Rewrap changed root");
                var state = observe(session, s -> hasAccount(s, id, "merged"));
                require(state.tokens().size() == 1 && state.token(id).orElseThrow().alternatives().size() == 1,
                        "Rewrap changed token state");
                require(code.equals(state.generateTotp(alternative(state, id), time).code()), "Rewrap changed TOTP");
            }
        } finally {
            Arrays.fill(oldPassword, '\0'); Arrays.fill(newPassword, '\0'); Arrays.fill(input, (byte) 0);
        }
    }

    private static VaultSession open(Path directory, char[] password) {
        return expect(OpenResult.Opened.class, NioTotipo.open(directory, password)).session();
    }
    private static TokenAlternative alternative(VaultState state, TokenId id) {
        return state.token(id).orElseThrow().alternatives().get(0);
    }
    private static boolean hasAccount(VaultState state, TokenId id, String account) {
        return state.token(id).stream().flatMap(t -> t.alternatives().stream())
                .anyMatch(a -> a.descriptor().account().equals(account));
    }
    private static void update(VaultState state, TokenId id, String account) {
        try (var builder = state.update(alternative(state, id))) {
            expect(SaveResult.Saved.class, builder.account(account).save());
        }
    }
    private static VaultState observe(VaultSession session, Predicate<VaultState> condition) throws Exception {
        var result = new CompletableFuture<VaultState>();
        var subscriber = new Flow.Subscriber<VaultState>() {
            private volatile Flow.Subscription subscription;
            @Override public void onSubscribe(Flow.Subscription value) { subscription = value; value.request(Long.MAX_VALUE); }
            @Override public void onNext(VaultState state) { if (condition.test(state)) { result.complete(state); cancel(); } }
            @Override public void onError(Throwable failure) { result.completeExceptionally(new IllegalStateException("Observation failed")); }
            @Override public void onComplete() { result.completeExceptionally(new IllegalStateException("Observation closed")); }
            void cancel() { if (subscription != null) { subscription.cancel(); } }
        };
        session.states().subscribe(subscriber);
        try { return result.get(15, TimeUnit.SECONDS); }
        finally { subscriber.cancel(); }
    }
    private static <T> T expect(Class<T> type, Object result) {
        require(type.isInstance(result), "Unexpected qualification outcome; expected " + type.getSimpleName());
        return type.cast(result);
    }
    private static void require(boolean condition, String message) {
        if (!condition) { throw new IllegalStateException(message); }
    }
}
