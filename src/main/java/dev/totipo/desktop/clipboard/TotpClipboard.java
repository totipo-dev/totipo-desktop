package dev.totipo.desktop.clipboard;

import dev.totipo.desktop.ui.Edt;
import java.awt.HeadlessException;
import java.awt.Toolkit;
import java.awt.datatransfer.*;
import java.io.IOException;
import java.time.Duration;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

/** One application-wide, EDT-owned lease; never retains vault objects or previous clipboard data. */
public final class TotpClipboard {
    public static final String COPIED = "Code copied. Totipo will try to clear it when it expires or within 30 seconds.";
    public static final String UNAVAILABLE = "Clipboard unavailable; code was not copied.";

    @FunctionalInterface public interface Copy {
        String copy(String code, Instant validFrom, Instant validUntil, Instant now);
    }
    interface Access {
        Transferable current();
        void set(Transferable value, ClipboardOwner owner);
    }
    @FunctionalInterface interface Scheduler { Runnable after(int milliseconds, Runnable action); }
    private final Access access;
    private final Scheduler scheduler;
    private final Clock clock;
    private Lease active;
    private boolean stopped;

    private static final class Lease {
        final String marker;
        final Object origin;
        Runnable cancel = () -> { };
        boolean clearing;
        Instant retryUntil;
        Lease(String marker, Object origin) { this.marker = marker; this.origin = origin; }
    }

    public TotpClipboard() {
        this(new Access() {
            public Transferable current() { return Toolkit.getDefaultToolkit().getSystemClipboard().getContents(null); }
            public void set(Transferable value, ClipboardOwner owner) {
                Toolkit.getDefaultToolkit().getSystemClipboard().setContents(value, owner);
            }
        }, (milliseconds, action) -> {
            Timer timer = new Timer(milliseconds, event -> action.run());
            timer.setRepeats(false); timer.start(); return timer::stop;
        }, Clock.systemUTC());
    }

    TotpClipboard(Access access, Scheduler scheduler, Clock clock) {
        this.access = access; this.scheduler = scheduler; this.clock = clock;
    }

    public String copy(Object origin, String code, Instant from, Instant until, Instant now) {
        Edt.require();
        if (stopped || now.isBefore(from) || !now.isBefore(until)) { return UNAVAILABLE; }
        String marker = UUID.randomUUID().toString();
        TotpClipboardPayload payload = new TotpClipboardPayload(code, marker,
                () -> SwingUtilities.invokeLater(() -> ownershipLost(marker)));
        try {
            access.set(payload, payload);
        } catch (HeadlessException | SecurityException | IllegalStateException unavailable) {
            return UNAVAILABLE;
        }
        retire();
        Lease lease = new Lease(marker, origin);
        active = lease;
        Instant cap = now.plusSeconds(30);
        Instant deadline = until.isBefore(cap) ? until : cap;
        // Round up to avoid an early clear for sub-millisecond intervals.
        Duration delay = Duration.between(clock.instant(), deadline);
        int milliseconds = (int) Math.max(0, Math.min(30_000, Math.ceil(delay.toNanos() / 1_000_000.0)));
        lease.cancel = scheduler.after(milliseconds, () -> clear(lease, 0));
        return COPIED;
    }

    private void ownershipLost(String marker) {
        Edt.require();
        if (active != null && active.marker.equals(marker)) { retire(); }
    }

    public void originClosing(Object origin) {
        Edt.require();
        if (active != null && active.origin == origin && !active.clearing) { clear(active, 0); }
    }

    /** Does not wait for clipboard availability or session work. Retries are bounded Swing one-shots. */
    public void shutdown() {
        Edt.require();
        stopped = true;
        if (active != null && !active.clearing) { clear(active, 0); }
    }

    private void clear(Lease lease, int retry) {
        Edt.require();
        if (active != lease) { return; }
        lease.cancel.run();
        if (!lease.clearing) { lease.retryUntil = clock.instant().plusSeconds(5); }
        lease.clearing = true;
        if (retry > 0 && clock.instant().isAfter(lease.retryUntil)) { retire(); return; }
        try {
            Transferable current = access.current();
            if (current != null && current.isDataFlavorSupported(TotpClipboardPayload.MARKER)
                    && lease.marker.equals(current.getTransferData(TotpClipboardPayload.MARKER))) {
                access.set(new StringSelection(""), null);
            }
            retire();
        } catch (HeadlessException | SecurityException | IllegalStateException
                 | UnsupportedFlavorException | IOException unavailable) {
            if (retry < 20 && clock.instant().isBefore(lease.retryUntil)) {
                lease.cancel = scheduler.after(250, () -> clear(lease, retry + 1));
            }
            else { retire(); }
        }
    }

    private void retire() {
        if (active != null) { active.cancel.run(); active = null; }
    }
}
