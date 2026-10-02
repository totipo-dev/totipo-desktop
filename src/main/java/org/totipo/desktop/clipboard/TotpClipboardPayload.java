package org.totipo.desktop.clipboard;

import java.awt.datatransfer.*;
import java.io.IOException;

/** Only paste text and a random per-copy identity cross the clipboard boundary. */
final class TotpClipboardPayload implements Transferable, ClipboardOwner {
    static final DataFlavor MARKER = new DataFlavor("application/x-totipo-copy-marker;class=java.lang.String",
            "Totipo copy marker");
    private final String code;
    private final String marker;
    private final Runnable lost;

    TotpClipboardPayload(String code, String marker, Runnable lost) {
        this.code = code; this.marker = marker; this.lost = lost;
    }
    @Override public DataFlavor[] getTransferDataFlavors() {
        return new DataFlavor[]{DataFlavor.stringFlavor, MARKER};
    }
    @Override public boolean isDataFlavorSupported(DataFlavor flavor) {
        return DataFlavor.stringFlavor.equals(flavor) || MARKER.equals(flavor);
    }
    @Override public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException, IOException {
        if (DataFlavor.stringFlavor.equals(flavor)) { return code; }
        if (MARKER.equals(flavor)) { return marker; }
        throw new UnsupportedFlavorException(flavor);
    }
    @Override public void lostOwnership(Clipboard clipboard, Transferable contents) { lost.run(); }
}
