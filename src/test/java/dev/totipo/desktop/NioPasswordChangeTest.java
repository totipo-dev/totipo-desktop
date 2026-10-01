package dev.totipo.desktop;

import dev.totipo.*;
import dev.totipo.storage.nio.NioTotipo;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class NioPasswordChangeTest {
    @TempDir Path directory;

    @Test void acknowledgedRewrapPreservesRootTokenAndFixedInstantTotp() throws Exception {
        char[] old = {'o', 'l', 'd'}, next = {'n', 'e', 'w'};
        byte[] input = {1, 2, 3, 4};
        VaultFingerprint fingerprint;
        TokenId token;
        String code;
        Instant instant = Instant.parse("2026-01-01T00:00:07Z");
        try {
            try (var session = assertInstanceOf(CreateVaultResult.Created.class, NioTotipo.create(directory, old)).session()) {
                fingerprint = session.fingerprint();
                try (var secret = NewSecret.copyOf(input); var builder = session.state().createToken()) {
                    token = assertInstanceOf(SaveResult.Saved.class, builder.account("password-change").secret(secret).save()).tokenId();
                }
                var state = NioSmokeTest.observe(session, token, "password-change");
                var alternative = state.token(token).orElseThrow().alternatives().get(0);
                code = state.generateTotp(alternative, instant).code();
                assertEquals(PasswordChangeResult.CHANGED, session.changePassword(old, next));
                assertEquals(fingerprint, session.fingerprint());
                assertEquals(code, session.state().generateTotp(alternative, instant).code());
                assertTrue(session.state().token(token).isPresent());
            }
            assertInstanceOf(OpenResult.AuthenticationFailed.class, NioTotipo.open(directory, old));
            try (var reopened = assertInstanceOf(OpenResult.Opened.class, NioTotipo.open(directory, next)).session()) {
                assertEquals(fingerprint, reopened.fingerprint());
                var state = NioSmokeTest.observe(reopened, token, "password-change");
                var alternative = state.token(token).orElseThrow().alternatives().get(0);
                assertEquals(code, state.generateTotp(alternative, instant).code());
            }
        } finally { Arrays.fill(old, '\0'); Arrays.fill(next, '\0'); Arrays.fill(input, (byte) 0); }
    }

    @Test void authenticationFailureKeepsSessionUsableAndOldPasswordOpens() {
        char[] old = {'o', 'l', 'd'}, wrong = {'o', 't', 'h', 'e', 'r'}, next = {'n', 'e', 'w'};
        try {
            VaultFingerprint fingerprint;
            try (var session = assertInstanceOf(CreateVaultResult.Created.class, NioTotipo.create(directory, old)).session()) {
                fingerprint = session.fingerprint();
                assertEquals(PasswordChangeResult.AUTHENTICATION_FAILED, session.changePassword(wrong, next));
                assertEquals(fingerprint, session.fingerprint());
                assertNotNull(session.state()); session.requestRefresh();
                try (var builder = session.state().createToken()) { assertNotNull(builder); }
            }
            try (var reopened = assertInstanceOf(OpenResult.Opened.class, NioTotipo.open(directory, old)).session()) {
                assertEquals(fingerprint, reopened.fingerprint());
            }
        } finally { Arrays.fill(old, '\0'); Arrays.fill(wrong, '\0'); Arrays.fill(next, '\0'); }
    }
}
