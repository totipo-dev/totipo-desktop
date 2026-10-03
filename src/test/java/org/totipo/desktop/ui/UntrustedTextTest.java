package org.totipo.desktop.ui;

import org.junit.jupiter.api.Test;
import org.totipo.*;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.totipo.desktop.ui.TokenFixtures.*;

class UntrustedTextTest {
    @Test void controlAndDirectionCharactersCannotForgeDetailLinesAndModelsRemainExact() {
        String issuer = " <html>e\u0301\nStatus: TOMBSTONED\0\\ ";
        String account = " account\r\t\u202E ";
        String client = "client\nAccount: forged\u007F";
        var head = head(1, new ClientMetadata(Optional.of(client), Optional.empty()));
        var alternative = alternative(TokenStatus.ACTIVE, issuer, account, TotpAlgorithm.SHA1, 6, 30, head);
        var token = token(1, alternative);
        String text = TokenPresentation.detail(token);
        assertTrue(text.contains("Issuer:  <html>e\u0301\\u{000A}Status: TOMBSTONED\\u{0000}\\\\ "));
        assertTrue(text.contains("Account:  account\\u{000D}\\u{0009}\\u{202E} "));
        assertTrue(text.contains("Client-provided name: client\\u{000A}Account: forged\\u{007F}"));
        assertFalse(text.contains("\nAccount: forged"));
        assertTrue(TokenPresentation.row(token).text().contains("\\u{000A}"));
        assertEquals(issuer, alternative.descriptor().issuer());
        assertEquals(account, alternative.descriptor().account());
        assertEquals(client, head.metadata().clientName().orElseThrow());
    }
}
