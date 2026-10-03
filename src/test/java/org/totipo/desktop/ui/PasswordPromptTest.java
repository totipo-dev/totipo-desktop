package org.totipo.desktop.ui;

import org.junit.jupiter.api.Test;
import java.awt.event.KeyEvent;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.JComponent;
import javax.swing.JRootPane;
import javax.swing.KeyStroke;
import org.totipo.desktop.ui.PasswordPromptResult.Action;
import static org.junit.jupiter.api.Assertions.*;

class PasswordPromptTest {
    @Test void openPromptShowsOrderedAccessibleActionsAndOpenDefault() throws Exception {
        org.totipo.desktop.TestSupport.edt(() -> {
            var form = new PasswordPrompt.Form(Path.of("remembered vault"), false, PasswordPromptContext.REMEMBERED_STARTUP, () -> { });
            var root = new JRootPane(); form.install(root);
            assertEquals(java.util.List.of("Exit", "Change Vault…", "Open"),
                    java.util.Arrays.stream(form.buttons.getComponents())
                            .map(component -> ((javax.swing.JButton) component).getText()).toList());
            assertSame(form.submit, root.getDefaultButton());
            assertEquals("Exit", form.dismiss.getAccessibleContext().getAccessibleName());
            assertEquals("Change Vault…", form.changeVault.getAccessibleContext().getAccessibleName());
            assertEquals("Open", form.submit.getAccessibleContext().getAccessibleName());
            assertTrue(form.fields.primary.isFocusable());
            assertTrue(form.dismiss.isFocusable()); assertTrue(form.changeVault.isFocusable());
            assertTrue(form.submit.isFocusable());
        });
    }

    @Test void exitEscapeAndWindowCloseProduceSameExitDecisionAndClearPassword() throws Exception {
        org.totipo.desktop.TestSupport.edt(() -> {
            for (int trigger = 0; trigger < 3; trigger++) {
                var disposals = new AtomicInteger();
                var form = new PasswordPrompt.Form(Path.of("vault"), false, PasswordPromptContext.EXPLICIT, disposals::incrementAndGet);
                var root = new JRootPane(); form.install(root);
                form.fields.primary.setText("private");
                if (trigger == 0) { form.dismiss.doClick(0); }
                else if (trigger == 1) { escape(root); }
                else { form.lifecycle.windowClosing(null); }
                assertEquals(Action.EXIT, form.decision);
                assertEquals(0, form.fields.primary.getPassword().length);
                form.lifecycle.windowClosing(null); form.submit.doClick(0);
                assertEquals(Action.EXIT, form.decision); assertEquals(1, disposals.get());
            }
        });
    }

    @Test void changeVaultRetiresPromptClearsAttemptAndKeepsDistinctDecision() throws Exception {
        org.totipo.desktop.TestSupport.edt(() -> {
            var disposals = new AtomicInteger();
            var form = new PasswordPrompt.Form(Path.of("vault"), false, PasswordPromptContext.EXPLICIT, disposals::incrementAndGet);
            form.fields.primary.setText("private"); form.changeVault.doClick(0);
            assertEquals(Action.CHANGE_VAULT, form.decision);
            assertEquals(0, form.fields.primary.getPassword().length);
            form.lifecycle.windowClosing(null);
            assertEquals(Action.CHANGE_VAULT, form.decision); assertEquals(1, disposals.get());
        });
    }

    @Test void enterAndDefaultOpenSubmitCurrentPassword() throws Exception {
        org.totipo.desktop.TestSupport.edt(() -> {
            for (boolean enter : new boolean[] {true, false}) {
                var form = new PasswordPrompt.Form(Path.of("vault"), false, PasswordPromptContext.EXPLICIT, () -> { });
                var root = new JRootPane(); form.install(root);
                form.fields.primary.setText("private");
                if (enter) { form.fields.primary.postActionEvent(); }
                else { root.getDefaultButton().doClick(0); }
                assertEquals(Action.SUBMIT, form.decision);
                assertArrayEquals("private".toCharArray(), form.fields.primary.getPassword());
                form.fields.clear();
            }
        });
    }

    @Test void createRetainsCancelCreateConfirmationAndCancellationKeys() throws Exception {
        org.totipo.desktop.TestSupport.edt(() -> {
            for (int trigger = 0; trigger < 3; trigger++) {
                var form = new PasswordPrompt.Form(Path.of("new vault"), true, PasswordPromptContext.EXPLICIT, () -> { });
                var root = new JRootPane(); form.install(root);
                assertEquals("Cancel", form.dismiss.getText()); assertEquals("Create", form.submit.getText());
                assertNull(form.changeVault); assertEquals(2, form.buttons.getComponentCount());
                assertEquals(6, form.fields.getComponentCount()); assertSame(form.submit, root.getDefaultButton());
                assertEquals("Create a vault in:", ((javax.swing.JLabel) form.fields.getComponent(0)).getText());
                form.fields.primary.setText("private"); form.fields.confirmation.setText("private");
                if (trigger == 0) { form.dismiss.doClick(0); }
                else if (trigger == 1) { escape(root); }
                else { form.lifecycle.windowClosing(null); }
                assertEquals(Action.CANCEL, form.decision);
                assertEquals(0, form.fields.primary.getPassword().length);
                assertEquals(0, form.fields.confirmation.getPassword().length);
            }
        });
    }

    private static void escape(JRootPane root) {
        Object binding = root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .get(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0));
        assertNotNull(binding);
        root.getActionMap().get(binding).actionPerformed(null);
    }

    @Test void openFieldsUseOriginWordingAboveFullReadOnlyPathAndAccessiblePasswordInput() throws Exception {
        org.totipo.desktop.TestSupport.edt(() -> {
            var path = java.nio.file.Path.of("vault/../vault with spaces").toAbsolutePath().normalize();
            for (var context : PasswordPromptContext.values()) {
                var fields = new PasswordPrompt.Fields(path, false, context);
                String expected = context == PasswordPromptContext.REMEMBERED_STARTUP
                        ? "Welcome back. Enter the password for:" : "Enter the password for:";
                assertEquals(path.toString(), fields.path.getText());
                assertFalse(fields.path.isEditable()); assertTrue(fields.path.isFocusable());
                var introduction = (javax.swing.JLabel) fields.getComponent(0);
                assertEquals(expected, introduction.getText());
                assertSame(fields.path, introduction.getLabelFor());
                assertSame(fields.path, fields.getComponent(1));
                assertEquals(expected, fields.path.getAccessibleContext().getAccessibleName());
                assertEquals("Password", fields.primary.getAccessibleContext().getAccessibleName());
                assertEquals(4, fields.getComponentCount());
                fields.primary.setText("private"); fields.clear(); assertEquals(0, fields.primary.getPassword().length);
            }
        });
    }
    @Test void matchingConfirmationIsWipedImmediatelyAndPrimaryIsRetainedForHandoff() {
        char[] primary = {'p', 'é'};
        char[] confirmation = primary.clone();
        assertTrue(PasswordPrompt.matches(primary, confirmation));
        assertArrayEquals(new char[2], confirmation);
        assertArrayEquals(new char[] {'p', 'é'}, primary);
    }

    @Test void mismatchWipesBothOwnedArrays() {
        char[] primary = {'p'};
        char[] confirmation = {'q'};
        assertFalse(PasswordPrompt.matches(primary, confirmation));
        assertArrayEquals(new char[1], primary);
        assertArrayEquals(new char[1], confirmation);
    }
}
