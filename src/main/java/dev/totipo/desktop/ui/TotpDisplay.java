package dev.totipo.desktop.ui;

import dev.totipo.*;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.Timer;

/** EDT-only ephemeral code lifetime. The Swing timer merely invokes tick(). */
final class TotpDisplay {
    record Display(String label, String code, int remaining, String countdown) { }
    private final Clock clock;
    private final Consumer<List<Display>> render;
    private final Timer timer;
    private final List<Entry> entries = new ArrayList<>();
    private VaultState state;

    private static final class Entry {
        private final TokenAlternative alternative;
        private final String label;
        private TotpCode code;
        private boolean failed;
        Entry(TokenAlternative alternative, String label) { this.alternative = alternative; this.label = label; }
    }

    TotpDisplay(Clock clock, Consumer<List<Display>> render) {
        this.clock = clock;
        this.render = render;
        timer = new Timer(250, event -> tick());
        timer.setCoalesce(true);
    }

    void select(VaultState current, TokenState token) {
        Edt.require();
        clear();
        state = current;
        for (int i = 0; i < token.alternatives().size(); i++) {
            TokenAlternative alternative = token.alternatives().get(i);
            if (alternative.descriptor().status() == TokenStatus.ACTIVE) {
                entries.add(new Entry(alternative, TokenPresentation.label(i)));
            }
        }
        tick();
        if (entries.stream().anyMatch(entry -> !entry.failed)) { timer.start(); }
    }

    void tick() {
        Edt.require();
        if (state == null || entries.isEmpty()) { return; }
        Instant now = clock.instant();
        List<Display> displays = new ArrayList<>();
        for (Entry entry : entries) {
            if (!entry.failed) {
                try {
                    if (entry.code == null || now.isBefore(entry.code.validFrom())
                            || !now.isBefore(entry.code.validUntil())) {
                        entry.code = state.generateTotp(entry.alternative, now);
                    }
                    TotpCode code = entry.code;
                    double span = seconds(Duration.between(code.validFrom(), code.validUntil()));
                    double left = seconds(Duration.between(now, code.validUntil()));
                    if (span <= 0 || now.isBefore(code.validFrom()) || !now.isBefore(code.validUntil())) {
                        throw new IllegalStateException("Invalid code interval");
                    }
                    displays.add(new Display(entry.label, code.code(),
                            (int) Math.max(0, Math.min(1000, 1000 * left / span)),
                            (long) Math.ceil(left) + " seconds remaining"));
                } catch (RuntimeException unavailable) {
                    entry.code = null;
                    entry.failed = true;
                }
            }
            if (entry.failed) { displays.add(new Display(entry.label, "Code unavailable", 0, "")); }
        }
        if (entries.stream().noneMatch(entry -> !entry.failed)) { timer.stop(); }
        render.accept(List.copyOf(displays));
    }

    private static double seconds(Duration duration) { return duration.getSeconds() + duration.getNano() / 1e9; }

    void clear() {
        Edt.require();
        timer.stop();
        entries.clear();
        state = null;
        render.accept(List.of());
    }

    boolean running() { return timer.isRunning(); }
}
