package dev.totipo.desktop.clipboard;

import java.awt.datatransfer.Transferable;

/** Cross-package integration fixture; never touches the host clipboard. */
public final class ClipboardProbe {
    private final TotpClipboardTest.Harness harness = new TotpClipboardTest.Harness();
    public TotpClipboard manager() { return harness.manager; }
    public Transferable payload() { return harness.memory.value; }
    public int writes() { return harness.memory.writes; }
    public void unavailable() { harness.memory.readFailure = new IllegalStateException(); }
    public void expire() { harness.timer.last().fire(); }
    public void assertEmpty() { TotpClipboardTest.textIs(payload(), ""); }
    public String copy() { return harness.copy(harness.a, 8); }
}
