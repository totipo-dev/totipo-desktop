package org.totipo.desktop.ui;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PasswordPromptTest {
    @Test void passwordFieldsShowFullReadOnlyPathAndAccessiblePasswordInput() throws Exception {
        org.totipo.desktop.TestSupport.edt(() -> {
            var path = java.nio.file.Path.of("vault/../vault with spaces").toAbsolutePath().normalize();
            var fields = new PasswordPrompt.Fields(path, false);
            assertEquals(path.toString(), fields.path.getText());
            assertFalse(fields.path.isEditable()); assertTrue(fields.path.isFocusable());
            assertEquals("Vault", fields.path.getAccessibleContext().getAccessibleName());
            assertEquals("Password", fields.primary.getAccessibleContext().getAccessibleName());
            assertEquals(4, fields.getComponentCount());
            fields.primary.setText("private"); fields.clear(); assertEquals(0, fields.primary.getPassword().length);
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
