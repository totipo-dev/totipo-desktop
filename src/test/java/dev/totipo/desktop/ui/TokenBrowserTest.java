package dev.totipo.desktop.ui;

import dev.totipo.*;
import java.awt.Component;
import java.awt.Container;
import java.util.*;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import static dev.totipo.desktop.TestSupport.edt;
import static dev.totipo.desktop.ui.TokenFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

class TokenBrowserTest {
    static <T> T find(Container root, Class<T> type) {
        for (Component child : root.getComponents()) {
            if (type.isInstance(child)) { return type.cast(child); }
            if (child instanceof Container container) {
                T found = find(container, type);
                if (found != null) { return found; }
            }
        }
        return null;
    }

    @Test void logicalRowsOrderLiteralTextAndSelectionFollowLatestIdentity() throws Exception {
        edt(() -> {
            TokenBrowserPanel panel = new TokenBrowserPanel(new MutableClock());
            try {
                TokenState first = token(1, alternative(TokenStatus.ACTIVE, "<html>issuer", "<html>account",
                        TotpAlgorithm.SHA1, 6, 30));
                TokenState second = token(2, active("other"), active("third"));
                State old = new State(first, second);
                panel.render(old.value);
                JList<?> list = find(panel, JList.class);
                JTextArea detail = find(panel, JTextArea.class);
                assertEquals(2, list.getModel().getSize());
                assertEquals(-1, list.getSelectedIndex());
                assertEquals("Select a token to view details.", detail.getText());
                assertEquals(id(1), ((TokenPresentation.Row) list.getModel().getElementAt(0)).id());
                assertTrue(list.getModel().getElementAt(0).toString().startsWith("<html>issuer"));
                assertTrue(list.getModel().getElementAt(1).toString().contains("[conflicting issuer]"));
                assertLiteralRenderer(list);
                list.setSelectedIndex(0);
                assertTrue(detail.getText().contains("Issuer: <html>issuer\nAccount: <html>account"));
                assertEquals(1, old.calls.size());
                TokenAlternative replacement = active("replacement");
                State next = new State(second, token(1, replacement));
                panel.render(next.value);
                assertEquals(1, list.getSelectedIndex());
                assertTrue(detail.getText().contains("Issuer: replacement"));
                assertSame(replacement, next.calls.get(0).alternative());
                list.setSelectedIndex(0);
                assertEquals(3, next.calls.size());
                panel.render(new State(first).value);
                assertEquals(-1, list.getSelectedIndex());
                assertEquals("Select a token to view details.", detail.getText());
                panel.closing();
                assertFalse(list.isEnabled());
                panel.render(old.value);
                assertEquals(0, list.getModel().getSize());
                assertNull(find(panel, JProgressBar.class));
            } finally { panel.closing(); }
        });
    }

    private static <T> void assertLiteralRenderer(JList<T> list) {
        Component rendered = list.getCellRenderer().getListCellRendererComponent(list,
                list.getModel().getElementAt(0), 0, false, false);
        JLabel label = assertInstanceOf(JLabel.class, rendered);
        assertEquals(Boolean.TRUE, label.getClientProperty("html.disable"));
        assertNull(label.getClientProperty("html"));
        assertTrue(label.getText().startsWith("<html>issuer"));
        assertTrue(label.getText().contains("<html>account"));
    }

    @Test void equalHeadsAreOneSemanticValueAndRawMetadataIsLiteral() {
        TokenHead one = head(10, ClientMetadata.empty());
        TokenHead two = head(11, new ClientMetadata(Optional.of("<html>client"), Optional.of(-1L)));
        TokenAlternative a = alternative(TokenStatus.ACTIVE, "<html>issuer", "<html>account",
                TotpAlgorithm.SHA1, 6, 30, one, two);
        TokenState token = token(1, a);
        String detail = TokenPresentation.detail(token);
        assertFalse(TokenPresentation.row(token).text().contains("CONFLICT"));
        assertTrue(detail.contains("2 current causal heads carry the same token value."));
        assertTrue(detail.contains("Client-provided raw unsigned time: 18446744073709551615"));
        assertTrue(detail.contains("Client-provided name: <html>client"));
        assertTrue(detail.contains("Account: <html>account"));
        assertTrue(detail.contains(one.revision().hex()));
        assertTrue(detail.contains(two.revision().hex()));
    }

    @Test void everyCompetingFieldMapsToAllAlternativesAndSecretGroups() {
        TokenAlternative a = alternative(TokenStatus.ACTIVE, "issuer A", "account A", TotpAlgorithm.SHA1, 6, 30);
        TokenAlternative b = alternative(TokenStatus.TOMBSTONED, "issuer B", "account B", TotpAlgorithm.SHA256, 8, 45);
        TokenAlternative c = alternative(TokenStatus.ACTIVE, "issuer A", "account A", TotpAlgorithm.SHA1, 6, 30);
        TokenState token = token(1, List.of(a, b, c), List.of(new SecretGroup(List.of(a, c)), new SecretGroup(List.of(b))),
                List.of(), List.of(), true);
        String text = TokenPresentation.detail(token);
        for (String value : List.of("ACTIVE", "issuer A", "account A", "SHA1", "6", "PT30S")) {
            assertTrue(text.contains(value + " — Alternative 1, Alternative 3"), value);
        }
        for (String value : List.of("TOMBSTONED", "issuer B", "account B", "SHA256", "8", "PT45S")) {
            assertTrue(text.contains(value + " — Alternative 2"), value);
        }
        assertTrue(text.contains("Secret group 1 — Alternative 1, Alternative 3"));
        assertTrue(text.contains("Secret group 2 — Alternative 2"));
        assertTrue(TokenPresentation.row(token).text().contains("[conflicting issuer]"));
        assertTrue(TokenPresentation.row(token).text().contains("[conflicting account]"));
    }

    @Test void secretOnlyConflictAndAgreedSecretAreCoreProjected() {
        TokenAlternative a = active("same");
        TokenAlternative b = active("same");
        assertEquals(a.descriptor(), b.descriptor());
        TokenState different = token(1, List.of(a, b), List.of(new SecretGroup(List.of(a)), new SecretGroup(List.of(b))),
                List.of(), List.of(), true);
        assertTrue(TokenPresentation.row(different).text().contains("CONFLICT"));
        assertTrue(TokenPresentation.detail(different).contains("2 distinct secret values"));
        assertTrue(TokenPresentation.detail(different).contains("Alternative 2"));
        assertTrue(TokenPresentation.detail(token(1, a, b)).contains("All current alternatives use the same secret."));
    }

    @Test void incompleteAndUnresolvedRemainVisibleWithoutInventedConflict() throws Exception {
        edt(() -> {
            var ref = new UnresolvedReference(revision(20), revision(21));
            TokenState incomplete = token(1, List.of(), List.of(), List.of(head(20, ClientMetadata.empty())), List.of(ref), false);
            String row = TokenPresentation.row(incomplete).text();
            String detail = TokenPresentation.detail(incomplete);
            assertTrue(row.contains("Incomplete token observation"));
            assertTrue(row.contains("UNRESOLVED"));
            assertFalse(row.contains("CONFLICT"));
            assertTrue(detail.contains("No complete token value"));
            assertTrue(detail.contains("Child: " + ref.child().hex()));
            assertTrue(detail.contains("Referenced parent: " + ref.parent().hex()));
            for (String field : List.of("Status", "Issuer", "Account", "Algorithm", "Digits", "Period")) {
                assertTrue(detail.contains(field + ":\nNo complete observed value"));
            }
            State state = new State(incomplete);
            TokenBrowserPanel panel = new TokenBrowserPanel(new MutableClock());
            try {
                panel.render(state.value);
                find(panel, JList.class).setSelectedIndex(0);
                assertTrue(find(panel, JTextArea.class).getText().contains("Unresolved causal references: 1"));
                assertEquals(0, state.calls.size());
            } finally { panel.closing(); }
            TokenState partial = token(2, List.of(active("available")), List.of(), List.of(), List.of(ref), false);
            assertTrue(TokenPresentation.row(partial).text().contains("UNRESOLVED"));
            assertFalse(TokenPresentation.row(partial).text().contains("CONFLICT"));
        });
    }
}
