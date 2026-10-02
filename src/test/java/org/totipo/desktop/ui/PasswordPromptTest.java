package org.totipo.desktop.ui;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PasswordPromptTest {
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
