package org.totipo.desktop.ui;

/** Display-only escaping; callers retain the original model values for storage/editing. */
final class UntrustedText {
    private UntrustedText() { }

    static String display(String value) {
        StringBuilder out = new StringBuilder();
        value.codePoints().forEach(point -> {
            if (point == '\\') { out.append("\\\\"); }
            else if (Character.isISOControl(point) || Character.getType(point) == Character.FORMAT
                    || point == 0x2028 || point == 0x2029) {
                out.append(String.format(java.util.Locale.ROOT, "\\u{%04X}", point));
            } else { out.appendCodePoint(point); }
        });
        return out.toString();
    }
}
