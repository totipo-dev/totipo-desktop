package dev.totipo.desktop;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PasswordInputTest {
    @Test void unicodeAndUtf8Boundaries() {
        assertTrue(PasswordInput.valid(new char[0]));
        assertTrue(PasswordInput.valid(new char[] {'a', 'Z', '9', '\0'}));
        assertTrue(PasswordInput.valid(new char[] {'é', '界'}));
        assertTrue(PasswordInput.valid(new char[] {'\uD83D', '\uDE00'}));
        assertFalse(PasswordInput.valid(new char[] {'\uD83D'}));
        assertFalse(PasswordInput.valid(new char[] {'\uD83D', 'a'}));
        assertFalse(PasswordInput.valid(new char[] {'\uDE00'}));
        assertTrue(PasswordInput.valid("a".repeat(1024).toCharArray()));
        assertFalse(PasswordInput.valid("a".repeat(1025).toCharArray()));
        assertTrue(PasswordInput.valid("é".repeat(512).toCharArray()));
        assertFalse(PasswordInput.valid(("é".repeat(512) + "a").toCharArray()));
        assertTrue(PasswordInput.valid(("界".repeat(341) + "a").toCharArray()));
        assertFalse(PasswordInput.valid(("界".repeat(341) + "é").toCharArray()));
        assertTrue(PasswordInput.valid("😀".repeat(256).toCharArray()));
        assertFalse(PasswordInput.valid(("😀".repeat(256) + "a").toCharArray()));
    }
}
