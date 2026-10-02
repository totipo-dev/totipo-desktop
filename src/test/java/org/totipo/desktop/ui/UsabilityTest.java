package org.totipo.desktop.ui;

import org.totipo.*;
import java.awt.*;
import java.awt.event.*;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import static org.totipo.desktop.TestSupport.edt;
import static org.totipo.desktop.ui.TokenFixtures.*;
import static org.totipo.desktop.ui.TokenBrowserTest.find;
import static org.junit.jupiter.api.Assertions.*;

class UsabilityTest {
    @Test void matchingIsLiteralAndLimitedToIdIssuerAccountAcrossAlternatives() {
        var conflict = token(171, active("École ISSUER"), alternative(TokenStatus.ACTIVE,
                "<html>.*", "OtherAccount", TotpAlgorithm.SHA1, 6, 30));
        for (String query : List.of("", "AB", "école issuer", "OTHERaccount", ".*", "<html>")) {
            assertTrue(TokenSearch.matches(conflict, query), query);
        }
        for (String query : List.of("SHA1", "ACTIVE", "Alternative", "absent", "^.*$")) {
            assertFalse(TokenSearch.matches(conflict, query), query);
        }
        assertTrue(TokenSearch.matches(token(171), "AB"));
        assertTrue(TokenSearch.matches(token(171), ""));
        assertFalse(TokenSearch.matches(token(171), "issuer"));
    }

    @Test void normalizationDoesNotDependOnDefaultLocale() {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            assertTrue(TokenSearch.matches(token(1, active("École ISSUER")), "école issuer"));
        } finally { Locale.setDefault(previous); }
    }

    @Test void filterKeepsQueryOrderAndHonestSelectionAcrossEmissions() throws Exception {
        edt(() -> {
            var panel = new TokenBrowserPanel(new MutableClock());
            var state = new State(token(3, active("match")), token(1, active("other")), token(2, active("match"), active("else")));
            try {
                panel.render(state.value);
                JList<?> list = find(panel, JList.class);
                assertEquals("3 tokens", panel.resultCount.getText());
                panel.search.setText("MATCH");
                assertEquals("2 of 3 tokens", panel.resultCount.getText());
                assertEquals(id(3), ((TokenPresentation.Row) list.getModel().getElementAt(0)).id());
                assertEquals(id(2), ((TokenPresentation.Row) list.getModel().getElementAt(1)).id());
                assertTrue(list.getModel().getElementAt(1).toString().contains("CONFLICT"));
                assertTrue(state.calls.isEmpty());
                list.setSelectedIndex(0);
                panel.render(new State(token(2, active("match")), token(3, active("match newest"))).value);
                assertEquals("MATCH", panel.search.getText());
                assertEquals(1, list.getSelectedIndex());
                panel.search.setText("absent");
                assertEquals(-1, list.getSelectedIndex());
                assertEquals("No tokens match this search.", find(panel, JTextArea.class).getText());
                invoke(panel.search, JComponent.WHEN_FOCUSED, KeyStroke.getKeyStroke("ESCAPE"));
                assertEquals("", panel.search.getText());
                assertEquals(-1, list.getSelectedIndex());
                panel.render(new State().value);
                assertEquals("No tokens are currently observed.", find(panel, JTextArea.class).getText());
            } finally { panel.closing(); }
        });
    }

    static Action binding(JComponent target, int condition, KeyStroke stroke) {
        Object key = target.getInputMap(condition).get(stroke);
        assertNotNull(key); return target.getActionMap().get(key);
    }
    static void invoke(JComponent target, int condition, KeyStroke stroke) {
        Action action = binding(target, condition, stroke);
        action.actionPerformed(new ActionEvent(target, ActionEvent.ACTION_PERFORMED, "test"));
    }
    @Test void shortcutsShareActionsAndDisabledCreateCannotRun() throws Exception {
        edt(() -> {
            VaultPanel panel = new VaultPanel();
            AtomicInteger creates = new AtomicInteger(); AtomicInteger refreshes = new AtomicInteger();
            panel.tokenActions(creates::incrementAndGet, (s, a, e) -> fail());
            panel.onRefresh(refreshes::incrementAndGet);
            assertSame(panel.refreshAction, binding(panel, JComponent.WHEN_IN_FOCUSED_WINDOW, KeyStroke.getKeyStroke("F5")));
            KeyStroke create = KeyStroke.getKeyStroke(KeyEvent.VK_N, SwingUsability.menuMask());
            assertSame(panel.createAction, binding(panel, JComponent.WHEN_IN_FOCUSED_WINDOW, create));
            invoke(panel, JComponent.WHEN_IN_FOCUSED_WINDOW, create); assertEquals(0, creates.get());
            panel.render(new State().value);
            invoke(panel, JComponent.WHEN_IN_FOCUSED_WINDOW, create); assertEquals(1, creates.get());
            panel.writeAvailability(false);
            invoke(panel, JComponent.WHEN_IN_FOCUSED_WINDOW, create); assertEquals(1, creates.get());
            invoke(panel, JComponent.WHEN_IN_FOCUSED_WINDOW, KeyStroke.getKeyStroke("F5")); assertEquals(1, refreshes.get());
            JTextField search = find(panel, JTextField.class); search.setText("query");
            invoke(panel, JComponent.WHEN_IN_FOCUSED_WINDOW, KeyStroke.getKeyStroke(KeyEvent.VK_F, SwingUsability.menuMask()));
            assertEquals("query", search.getSelectedText());
            assertEquals(3, panel.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).keys().length);
            panel.closing();
        });
    }

    @Test void editorDefaultsAndEscapeUseGuardedCleanupWithoutSecretMetadata() throws Exception {
        edt(() -> {
            AtomicInteger cancelled = new AtomicInteger();
            TokenEditorPanel editor = new TokenEditorPanel(null, "Create", d -> fail(), cancelled::incrementAndGet);
            JRootPane root = new JRootPane(); root.setContentPane(editor); editor.installDialog(root);
            assertSame(editor.save, root.getDefaultButton());
            editor.secret.setText("JBSWY3DPEHPK3PXP");
            assertFalse(editor.secret.getAccessibleContext().getAccessibleName().contains("JBSWY"));
            assertNull(editor.secret.getAccessibleContext().getAccessibleDescription());
            assertLabels(editor);
            editor.busy(true, "Saving…"); invoke(root, JComponent.WHEN_IN_FOCUSED_WINDOW, KeyStroke.getKeyStroke("ESCAPE"));
            assertEquals(0, cancelled.get());
            editor.busy(false, ""); invoke(root, JComponent.WHEN_IN_FOCUSED_WINDOW, KeyStroke.getKeyStroke("ESCAPE"));
            assertEquals(1, cancelled.get()); assertEquals(0, editor.secret.getPassword().length);
            PasswordChangePanel password = new PasswordChangePanel(s -> fail(), cancelled::incrementAndGet);
            root.setContentPane(password); password.installDialog(root);
            assertSame(password.change, root.getDefaultButton()); assertLabels(password);
            password.current.setText("private-password"); password.next.setText("private-password");
            password.confirmation.setText("private-password");
            for (JPasswordField field : List.of(password.current, password.next, password.confirmation)) {
                assertFalse(field.getAccessibleContext().getAccessibleName().contains("private-password"));
                assertNull(field.getAccessibleContext().getAccessibleDescription());
            }
            password.busy(true, "Changing…"); invoke(root, JComponent.WHEN_IN_FOCUSED_WINDOW, KeyStroke.getKeyStroke("ESCAPE"));
            assertEquals(1, cancelled.get()); password.busy(false, "");
            invoke(root, JComponent.WHEN_IN_FOCUSED_WINDOW, KeyStroke.getKeyStroke("ESCAPE"));
            assertEquals(2, cancelled.get());
            for (JPasswordField field : List.of(password.current, password.next, password.confirmation)) { assertEquals(0, field.getPassword().length); }
        });
    }
    private static void assertLabels(Container parent) {
        for (Component component : parent.getComponents()) {
            if (component instanceof JLabel label && !label.getText().isBlank()) { assertNotNull(label.getLabelFor(), label.getText()); }
            if (component instanceof Container child) { assertLabels(child); }
        }
    }

    @Test void mergeStepsHaveNormalDefaultsAndEscapeRetiresSecret() throws Exception {
        edt(() -> {
            var token = token(1, active("one"), active("two"), active("three"));
            var state = new State(token);
            AtomicInteger cancelled = new AtomicInteger();
            var panel = new MergeEditorPanel(org.totipo.desktop.MergeInputs.capture(state.value, token),
                    draft -> fail(), cancelled::incrementAndGet);
            JRootPane root = new JRootPane(); root.setContentPane(panel); panel.installDialog(root);
            assertEquals("Continue", root.getDefaultButton().getText());
            root.getDefaultButton().doClick(); assertEquals("Save", root.getDefaultButton().getText());
            assertLabels(panel); assertNotNull(find(panel, JScrollPane.class));
            JPasswordField secret = find(panel, JPasswordField.class); secret.setText("MY");
            panel.busy(true, "Saving merge…");
            invoke(root, JComponent.WHEN_IN_FOCUSED_WINDOW, KeyStroke.getKeyStroke("ESCAPE"));
            assertEquals(0, cancelled.get());
            panel.busy(false, "");
            invoke(root, JComponent.WHEN_IN_FOCUSED_WINDOW, KeyStroke.getKeyStroke("ESCAPE"));
            assertEquals(1, cancelled.get()); assertEquals(0, secret.getPassword().length);
        });
    }

    @Test void manyHeadsReferencesDiagnosticsAndCodesRemainAccessibleAndScrollable() throws Exception {
        edt(() -> {
            List<TokenHead> heads = java.util.stream.IntStream.range(1, 100).mapToObj(i ->
                    head(i, new ClientMetadata(java.util.Optional.of("long client ".repeat(100)), java.util.Optional.empty()))).toList();
            List<UnresolvedReference> refs = java.util.stream.IntStream.range(101, 200)
                    .mapToObj(i -> new UnresolvedReference(revision(i), revision(i + 1))).toList();
            var a = alternative(TokenStatus.ACTIVE, "long issuer ".repeat(1000), "long account ".repeat(1000),
                    TotpAlgorithm.SHA1, 6, 30, heads.toArray(TokenHead[]::new));
            var token = token(1, List.of(a), List.of(new SecretGroup(List.of(a))), heads, refs, false);
            var panel = new TokenBrowserPanel(new MutableClock());
            panel.render(new State(token).value); find(panel, JList.class).setSelectedIndex(0);
            String detail = find(panel, JTextArea.class).getText();
            assertTrue(detail.contains("Unresolved causal references: 99"));
            assertTrue(detail.contains(revision(99).hex()));
            JProgressBar remaining = find(panel, JProgressBar.class);
            assertEquals("TOTP time remaining", remaining.getAccessibleContext().getAccessibleName());
            JLabel code = all(panel).stream().filter(JLabel.class::isInstance).map(JLabel.class::cast)
                    .filter(l -> l.getText().contains("001234")).findFirst().orElseThrow();
            assertTrue(code.getAccessibleContext().getAccessibleName().contains("001234"));
            assertFalse(code.getAccessibleContext().getAccessibleDescription().contains("001234"));
            panel.closing(); assertEquals("", code.getText());
            assertFalse(code.getAccessibleContext().getAccessibleName().contains("001234"));
            VaultPanel vault = new VaultPanel();
            String[] diagnostics = new String[200]; java.util.Arrays.fill(diagnostics, "DIAGNOSTIC");
            vault.render(org.totipo.desktop.TestSupport.state(new ObservationProgress.Finished(200, true), diagnostics));
            JTextArea diagnosticArea = all(vault).stream().filter(JTextArea.class::isInstance).map(JTextArea.class::cast)
                    .filter(area -> "Local observation diagnostics".equals(area.getAccessibleContext().getAccessibleName()))
                    .findFirst().orElseThrow();
            assertEquals(200, diagnosticArea.getText().lines().count());
            assertInstanceOf(JViewport.class, diagnosticArea.getParent()); vault.closing();
        });
    }
    private static List<Component> all(Container parent) {
        var result = new java.util.ArrayList<Component>();
        for (Component child : parent.getComponents()) {
            result.add(child); if (child instanceof Container container) { result.addAll(all(container)); }
        }
        return result;
    }

    @Test void decisionsHaveSafeDefaultsAndLongContentRemainsScrollable() throws Exception {
        edt(() -> {
            VaultPanel panel = new VaultPanel(); JRootPane root = new JRootPane(); root.setContentPane(panel);
            panel.additionalConflict(() -> {}, () -> fail(), () -> {});
            assertEquals("Review latest and merge again", root.getDefaultButton().getText());
            panel.publicationUncertain(true, false, () -> fail(), () -> fail()); assertNull(root.getDefaultButton());
            panel.clearUncertainty();
            var longValue = "<html>long issuer account ".repeat(1000);
            panel.render(new State(token(1, active(longValue), active(longValue + "second"), active("third"))).value);
            assertNotNull(find(panel, JScrollPane.class));
            assertNotNull(find(panel, JList.class).getAccessibleContext().getAccessibleName());
            find(panel, JList.class).setSelectedIndex(0);
            assertTrue(find(panel, JTextArea.class).getText().contains("CONFLICT"));
            assertTrue(find(panel, JTextArea.class).getLineWrap());
            panel.closing();
        });
    }
}
