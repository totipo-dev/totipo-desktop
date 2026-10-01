package dev.totipo.desktop.ui;

import dev.totipo.ObservationProgress;
import dev.totipo.VaultState;
import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.time.Clock;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
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
    private transient Runnable refreshCallback = () -> { };
    private transient Runnable createCallback = () -> { };
    final transient javax.swing.Action refreshAction = SwingUsability.action("Refresh", () -> refreshCallback.run());
    final transient javax.swing.Action createAction = SwingUsability.action("Create Token", () -> createCallback.run());
    private final JButton refresh = new JButton(refreshAction);
    private final TokenBrowserPanel browser = new TokenBrowserPanel(Clock.systemUTC());
    private final JButton create = new JButton(createAction);
    private final JButton changePassword = new JButton("Change Password…");
    private final JLabel writeMessage = new JLabel(" ");
    private final JLabel abandoned = new JLabel(" ");
    private final JPanel uncertainty = new JPanel(new BorderLayout(4, 4));
    private boolean writeAvailable = true;
    private boolean observed;

    public VaultPanel() {
        Edt.require();
        setLayout(new BorderLayout(12, 12));
        SwingUsability.bind(this, WHEN_IN_FOCUSED_WINDOW, javax.swing.KeyStroke.getKeyStroke("F5"), "refresh", refreshAction);
        SwingUsability.bind(this, WHEN_IN_FOCUSED_WINDOW, javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_N, SwingUsability.menuMask()), "create", createAction);
        SwingUsability.bind(this, WHEN_IN_FOCUSED_WINDOW, javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_F, SwingUsability.menuMask()), "find", SwingUsability.action("Find", browser::focusSearch));
        refresh.setMnemonic('R'); create.setMnemonic('N'); changePassword.setMnemonic('P');
        refresh.setToolTipText("Refresh (F5)"); create.setToolTipText("Create Token (menu shortcut + N)");
        status.getAccessibleContext().setAccessibleDescription("Local observation status");
        progress.getAccessibleContext().setAccessibleName("Local observation progress");
        diagnostics.getAccessibleContext().setAccessibleName("Local observation diagnostics");
        uncertainty.getAccessibleContext().setAccessibleName("Publication decision");
        setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        JPanel heading = new JPanel();
        heading.setLayout(new BoxLayout(heading, BoxLayout.Y_AXIS));
        heading.add(new JLabel("Vault — local observation"));
        heading.add(status);
        heading.add(progress);
        heading.add(refresh);
        heading.add(create);
        heading.add(changePassword);
        createAction.setEnabled(false);
        heading.add(writeMessage);
        heading.add(abandoned);
        heading.add(uncertainty);
        add(heading, BorderLayout.NORTH);
        diagnostics.setEditable(false);
        add(browser, BorderLayout.CENTER);
        JPanel lower = new JPanel(new BorderLayout());
        lower.add(new JLabel("Local observation diagnostics"), BorderLayout.NORTH);
        lower.add(new JScrollPane(diagnostics), BorderLayout.CENTER);
        add(lower, BorderLayout.SOUTH);
        progress.setIndeterminate(true);
    }

    public void focusSearch() { browser.focusSearch(); }

    public void passwordAction(Runnable action) {
        Edt.require(); changePassword.addActionListener(event -> action.run());
    }
    public void mergeAction(VaultView.MergeAction action) { browser.onMerge(action); }
    public void tokenActions(Runnable action, VaultView.EditAction edit) {
        Edt.require(); createCallback = action; browser.onEdit(edit);
    }
    public void writeAvailability(boolean available) {
        Edt.require(); writeAvailable = available;
        changePassword.setEnabled(available);
        createAction.setEnabled(available && observed); browser.writeAvailability(available);
    }
    public void writeMessage(String text) { Edt.require(); writeMessage.setText(text); }
    public void abandonedPublication(boolean value) {
        Edt.require(); abandoned.setText(value
                ? "One or more earlier token publications have unresolved persistence status." : " ");
    }
    public void clearUncertainty() {
        Edt.require();
        if (getRootPane() != null) { getRootPane().setDefaultButton(null); }
        uncertainty.removeAll(); uncertainty.revalidate(); uncertainty.repaint();
    }
    public void publicationUncertain(boolean isCreate, boolean busy, Runnable retry, Runnable stop) {
        clearUncertainty();
        JTextArea text = new JTextArea("Totipo could not determine whether this exact token operation received a durable acknowledgement. "
                + "It may already be present in the vault.\nRetry republishes the exact same frozen operation. It does not re-read or rebase the token."
                + "\nStop retrying releases the retry capability; it does not undo a publication or prove it failed."
                + (isCreate ? "\nStarting Create Token again later creates a new operation/token; it is not a retry and could create two logical tokens." : ""));
        text.setEditable(false); text.setLineWrap(true); text.setWrapStyleWord(true);
        text.setRows(isCreate ? 5 : 4);
        uncertainty.add(text, BorderLayout.CENTER);
        JButton retryButton = new JButton(busy ? "Retrying / releasing exact publication…" : "Retry exact publication");
        JButton stopButton = new JButton("Stop retrying");
        retryButton.setEnabled(!busy); stopButton.setEnabled(!busy);
        retryButton.addActionListener(event -> retry.run()); stopButton.addActionListener(event -> stop.run());
        JPanel choices = new JPanel(new GridLayout(1, 2, 4, 4));
        choices.add(retryButton); choices.add(stopButton);
        uncertainty.add(choices, BorderLayout.SOUTH);
        uncertainty.revalidate(); uncertainty.repaint();
    }

    public void additionalConflict(Runnable review, Runnable publish, Runnable cancel) {
        clearUncertainty();
        JTextArea text = new JTextArea("New relevant token information was observed before this merge could be published. Nothing from this merge has been published.");
        text.setEditable(false); text.setLineWrap(true); text.setWrapStyleWord(true); text.setRows(2);
        uncertainty.add(text, BorderLayout.CENTER);
        JPanel choices = new JPanel(new GridLayout(0, 1));
        String[] labels = {"Review latest and merge again", "Publish original resolution anyway", "Cancel"};
        Runnable[] actions = {review, publish, cancel};
        for (int i = 0; i < labels.length; i++) {
            JButton button = new JButton(labels[i]); Runnable action = actions[i];
            button.addActionListener(event -> action.run()); choices.add(button);
        }
        uncertainty.add(choices, BorderLayout.SOUTH); uncertainty.revalidate(); uncertainty.repaint();
        JButton reviewButton = (JButton) choices.getComponent(0);
        if (getRootPane() != null) { getRootPane().setDefaultButton(reviewButton); }
        reviewButton.requestFocusInWindow();
    }
    public void mergePublicationUncertain(boolean original, boolean busy, Runnable retry, Runnable stop) {
        publicationUncertain(false, busy, retry, stop);
        JTextArea text = (JTextArea) ((BorderLayout) uncertainty.getLayout()).getLayoutComponent(BorderLayout.CENTER);
        text.setText((original
                ? "Totipo could not determine whether the original frozen merge resolution received durable acknowledgement."
                : "Totipo could not determine whether this exact merge publication received durable acknowledgement.")
                + "\nIt may already have persisted. Retry republishes the exact frozen bytes. Stop retrying does not undo publication or prove it failed.");
    }

    public void onRefresh(Runnable action) {
        Edt.require();
        refreshCallback = action;
    }

    public void render(VaultState state) {
        Edt.require();
        observed = true;
        createAction.setEnabled(writeAvailable);
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
        writeAvailability(false);
        browser.closing();
        refreshAction.setEnabled(false);
        status.setText("Closing…");
        progress.setIndeterminate(false);
    }
}
