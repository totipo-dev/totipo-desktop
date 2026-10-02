package org.totipo.desktop;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Base32Test {
    @Test void rfc4648AndHumanGrouping() {
        String[][] examples = {{"MY======", "f"}, {"MZXQ====", "fo"}, {"MZXW6===", "foo"},
                {"MZXW6YQ=", "foob"}, {"MZXW6YTB", "fooba"}, {"MZXW6YTBOI======", "foobar"},
                {"mzxw6ytboi", "foobar"}, {" MZ-XW\t6Y\r\nTB OI ", "foobar"}};
        for (String[] example : examples) {
            assertArrayEquals(example[1].getBytes(StandardCharsets.US_ASCII), Base32.decode(example[0].toCharArray()));
        }
    }
    @Test void rejectsMalformedAndAmbiguousInput() {
        for (String bad : new String[]{"", " -\t\r\n", "========", "M", "MZX", "MZXW6Y", "M0", "M1",
                "M!", "МY", "MY=MY", "MY=====", "MY=======", "MZXW6YTB=", "MZ", "MZ======",
                "MZXR", "MZXW7", "MZXW6YR", "MY\u00a0"}) {
            assertThrows(IllegalArgumentException.class, () -> Base32.decode(bad.toCharArray()), bad);
        }
    }
    @Test void enforcesDecodedBounds() {
        assertEquals(1, Base32.decode("AA".toCharArray()).length);
        assertEquals(128, Base32.decode("A".repeat(205).toCharArray()).length);
        assertThrows(IllegalArgumentException.class, () -> Base32.decode("A".repeat(207).toCharArray()));
    }
}
