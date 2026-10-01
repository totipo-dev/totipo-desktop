package dev.totipo.desktop.ui;

import dev.totipo.ObservationProgress;
import dev.totipo.VaultState;
import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.time.Clock;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

/** Renders observation evidence and tokens from the same immutable state. */
public final class VaultPanel extends JPanel {
    private static final long serialVersionUID = 1L;
    private final JLabel status = new JLabel("Waiting for local observation…");
    private final JProgressBar progress = new JProgressBar(0, 1000);
    private final JTextArea diagnostics = new JTextArea(8, 48);
    private final JButton refresh = new JButton("Refresh");
    private final TokenBrowserPanel browser = new TokenBrowserPanel(Clock.systemUTC());

    public VaultPanel() {
        Edt.require();
        setLayout(new BorderLayout(12, 12));
        setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        JPanel heading = new JPanel(new GridLayout(0, 1, 0, 8));
        heading.add(new JLabel("Vault — local observation"));
        heading.add(status);
        heading.add(progress);
        heading.add(refresh);
        add(heading, BorderLayout.NORTH);
        diagnostics.setEditable(false);
        add(browser, BorderLayout.CENTER);
        JPanel lower = new JPanel(new BorderLayout());
        lower.add(new JLabel("Local observation diagnostics"), BorderLayout.NORTH);
        lower.add(new JScrollPane(diagnostics), BorderLayout.CENTER);
        add(lower, BorderLayout.SOUTH);
        progress.setIndeterminate(true);
    }

    public void onRefresh(Runnable action) {
        Edt.require();
        refresh.addActionListener(event -> action.run());
    }

    public void render(VaultState state) {
        Edt.require();
        ObservationProgress observation = state.observation();
        if (observation instanceof ObservationProgress.Enumerating enumerating) {
            status.setText("Observing local vault — discovered " + enumerating.discovered() + " objects");
            progress.setIndeterminate(true);
        } else if (observation instanceof ObservationProgress.Processing processing) {
            status.setText("Processing local observation — " + processing.processed() + " of " + processing.total());
            progress.setIndeterminate(false);
            // Scale only the bar; the label preserves the exact long values.
            progress.setValue(processing.total() <= 0 ? 0
                    : (int) Math.max(0, Math.min(1000, 1000.0 * processing.processed() / processing.total())));
        } else if (observation instanceof ObservationProgress.Finished finished) {
            status.setText("Local observation finished — " + finished.processed() + " objects processed"
                    + (finished.hasDiagnostics() ? "; diagnostics present" : ""));
            progress.setIndeterminate(false);
            progress.setValue(1000);
        } else {
            throw new IllegalArgumentException("Unsupported observation progress");
        }
        StringBuilder codes = new StringBuilder();
        for (var diagnostic : state.diagnostics()) {
            codes.append(diagnostic.code()).append('\n');
        }
        diagnostics.setText(codes.toString());
        diagnostics.setCaretPosition(0);
        browser.render(state);
    }

    public void closing() {
        Edt.require();
        browser.closing();
        refresh.setEnabled(false);
        status.setText("Closing…");
        progress.setIndeterminate(false);
    }
}
