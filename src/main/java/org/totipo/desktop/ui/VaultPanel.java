package org.totipo.desktop.ui;

import org.totipo.desktop.clipboard.TotpClipboard;

import org.totipo.ObservationProgress;
import org.totipo.VaultState;
import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.time.Clock;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import java.awt.FlowLayout;
import javax.swing.JMenuBar;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JTextArea;

/** Renders observation evidence and tokens from the same immutable state. */
public final class VaultPanel extends JPanel {
    private static final long serialVersionUID = 1L;
    private final JLabel status = new JLabel("Reading vault…");
    private final JProgressBar progress = new JProgressBar(0, 1000);
    final NotificationBanner notification = new NotificationBanner();
    private String observationWarning = "";
    private String operationWarning = "";
    private boolean abandonedWarning;
    private boolean uncertainWarning;
    private boolean decisionWarning;
    private final JPanel reading = new JPanel(new BorderLayout(8, 8));
    private final JPanel messages = new JPanel(new BorderLayout(0, 12));
    private boolean conflictWarning;
    final JMenuItem changeVault = new JMenuItem("Change Vault…");
    final JMenuItem changePassword = new JMenuItem("Change Password…");
    final JMenuItem exit = new JMenuItem("Exit");
    static final java.awt.Dimension MINIMUM_SIZE = new java.awt.Dimension(900, 600);
    private transient Runnable refreshCallback = () -> { };
    private transient Runnable createCallback = () -> { };
    final transient javax.swing.Action refreshAction = SwingUsability.action("Refresh", () -> refreshCallback.run());
    final transient javax.swing.Action createAction = SwingUsability.action("Create Token", () -> createCallback.run());
    private final JButton refresh = new JButton(refreshAction);
    private final TokenBrowserPanel browser = new TokenBrowserPanel(Clock.systemUTC());
    private final JButton create = new JButton(createAction);
    private final JLabel writeMessage = new JLabel();
    private final JPanel uncertainty = new JPanel(new BorderLayout(4, 4));
    private boolean writeAvailable = true;
    private boolean observed;

    public VaultPanel() {
        Edt.require();
        setLayout(new BorderLayout(12, 12));
        setMinimumSize(new java.awt.Dimension(MINIMUM_SIZE));
        SwingUsability.bind(this, WHEN_IN_FOCUSED_WINDOW, javax.swing.KeyStroke.getKeyStroke("F5"), "refresh", refreshAction);
        SwingUsability.bind(this, WHEN_IN_FOCUSED_WINDOW, javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_N, SwingUsability.menuMask()), "create", createAction);
        SwingUsability.bind(this, WHEN_IN_FOCUSED_WINDOW, javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_F, SwingUsability.menuMask()), "find", SwingUsability.action("Find", browser::focusSearch));
        refresh.setMnemonic('R'); create.setMnemonic('N'); changePassword.setMnemonic('P');
        refresh.setToolTipText("Refresh (F5)"); create.setToolTipText("Create Token (menu shortcut + N)");
        status.getAccessibleContext().setAccessibleDescription("Vault reading status");
        progress.getAccessibleContext().setAccessibleName("Vault reading progress");
        uncertainty.getAccessibleContext().setAccessibleName("Save decision");
        setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        JPanel heading = new JPanel(new BorderLayout(0, 12));
        reading.add(status, BorderLayout.WEST);
        reading.add(progress, BorderLayout.CENTER);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEADING, 10, 0));
        actions.add(refresh);
        actions.add(create);
        JPanel controls = new JPanel(new BorderLayout(0, 12));
        controls.add(reading, BorderLayout.NORTH);
        controls.add(actions, BorderLayout.CENTER);
        writeMessage.setVisible(false);
        uncertainty.setVisible(false);
        messages.setVisible(false);
        messages.add(writeMessage, BorderLayout.NORTH);
        messages.add(uncertainty, BorderLayout.CENTER);
        controls.add(messages, BorderLayout.SOUTH);
        heading.add(notification, BorderLayout.NORTH);
        heading.add(controls, BorderLayout.CENTER);
        createAction.setEnabled(false);
        add(heading, BorderLayout.NORTH);
        add(browser, BorderLayout.CENTER);
        progress.setIndeterminate(true);
    }

    public JMenuBar menuBar() {
        Edt.require();
        JMenuBar bar = new JMenuBar();
        JMenu file = new JMenu("File"); file.setMnemonic('F');
        exit.setMnemonic('X'); file.add(exit); bar.add(file);
        JMenu vault = new JMenu("Vault"); vault.setMnemonic('V');
        changeVault.setMnemonic('V');
        vault.add(changeVault); vault.add(changePassword); bar.add(vault);
        return bar;
    }
    public void exitAction(Runnable action) {
        Edt.require(); exit.addActionListener(event -> action.run());
    }
    public void changeVaultAction(Runnable action) {
        Edt.require(); changeVault.addActionListener(event -> action.run());
    }

    public void focusSearch() { browser.focusSearch(); }
    public void copyAction(TotpClipboard.Copy action) { browser.copyAction(action); }

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
    public void writeMessage(String text) {
        Edt.require();
        operationWarning = ""; updateNotification();
        String statusText = switch (text) {
            case "Publishing original merge resolution…" -> "Saving…";
            case "Original merge resolution publication acknowledged.", "Merge publication acknowledged.",
                    "Token publication acknowledged.", "Token update publication acknowledged." -> "Saved.";
            default -> "";
        };
        writeMessage.setText(statusText); writeMessage.setVisible(!statusText.isEmpty()); updateMessages();
    }
    public void writeWarning(String message) {
        Edt.require(); operationWarning = message; writeMessage.setVisible(false); updateMessages(); updateNotification();
    }
    private void updateMessages() {
        messages.setVisible(writeMessage.isVisible() || uncertainty.isVisible());
        revalidate(); repaint();
    }
    public void abandonedPublication(boolean value) {
        Edt.require(); abandonedWarning = value; updateNotification();
    }
    private void updateNotification() {
        String warning = observationWarning;
        if (!operationWarning.isEmpty()) { warning += (warning.isEmpty() ? "" : " ") + operationWarning; }
        if (decisionWarning) { warning += (warning.isEmpty() ? "" : " ") + "The vault changed while saving. Review the token before continuing."; }
        if (conflictWarning) { warning += (warning.isEmpty() ? "" : " ") + "A token has conflicting versions and needs attention."; }
        if (uncertainWarning || abandonedWarning) {
            warning += (warning.isEmpty() ? "" : " ") + "A change may already have been saved. Review the save options before trying again.";
        }
        notification.message(warning);
    }
    public void clearUncertainty() {
        Edt.require();
        uncertainWarning = false; decisionWarning = false; updateNotification();
        if (getRootPane() != null) { getRootPane().setDefaultButton(null); }
        uncertainty.removeAll(); uncertainty.setVisible(false); updateMessages(); uncertainty.revalidate(); uncertainty.repaint();
    }
    public void publicationUncertain(boolean isCreate, boolean busy, Runnable retry, Runnable stop) {
        clearUncertainty();
        uncertainWarning = true; updateNotification(); uncertainty.setVisible(true); updateMessages();
        JTextArea text = new JTextArea("Totipo could not confirm whether this token change was saved. "
                + "It may already be present in the vault.\nRetry sends the exact same change again. It does not read newer token data or adjust the change."
                + "\nStop retrying gives up the option to retry this change; it does not undo a save or prove it failed."
                + (isCreate ? "\nStarting Create Token again later creates a separate token; it is not a retry and could create two tokens." : ""));
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
        decisionWarning = true; updateNotification(); uncertainty.setVisible(true); updateMessages();
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
                ? "Totipo could not confirm whether your original conflict resolution was saved."
                : "Totipo could not confirm whether this conflict resolution was saved.")
                + "\nIt may already be saved. Retry sends the exact same resolution again. Stop retrying does not undo a save or prove it failed.");
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
        if (observation instanceof ObservationProgress.Enumerating) {
            status.setText("Reading vault…");
            progress.setIndeterminate(true);
        } else if (observation instanceof ObservationProgress.Processing processing) {
            status.setText("Reading vault…");
            progress.setIndeterminate(false);
            // Scale long counts without truncation or overflow.
            progress.setValue(processing.total() <= 0 ? 0
                    : (int) Math.max(0, Math.min(1000, 1000.0 * processing.processed() / processing.total())));
        } else if (observation instanceof ObservationProgress.Finished) {
            status.setText("");
            progress.setIndeterminate(false);
            progress.setValue(1000);
        } else {
            throw new IllegalArgumentException("Unsupported observation progress");
        }
        boolean isReading = !(observation instanceof ObservationProgress.Finished);
        status.setVisible(isReading); progress.setVisible(isReading); reading.setVisible(isReading);
        observationWarning = !state.diagnostics().isEmpty()
                || observation instanceof ObservationProgress.Finished finished && finished.hasDiagnostics()
                ? "Some vault data could not be read. Refresh to try again." : "";
        conflictWarning = state.tokens().stream().anyMatch(org.totipo.TokenState::hasConflict);
        updateNotification();
        browser.render(state);
    }

    public void closing() {
        Edt.require();
        writeAvailability(false);
        changeVault.setEnabled(false);
        browser.closing();
        refreshAction.setEnabled(false);
        reading.setVisible(true); status.setVisible(true);
        status.setText("Closing…");
        progress.setIndeterminate(false);
    }
}
