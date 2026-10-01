package dev.totipo.desktop.ui;

import dev.totipo.*;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.time.Clock;
import java.util.List;
import javax.swing.*;

/** One list row per logical token; selection identity survives replacement projections. */
public final class TokenBrowserPanel extends JPanel {
    private static final long serialVersionUID = 1L;
    private final DefaultListModel<TokenPresentation.Row> rows = new DefaultListModel<>();
    private final JList<TokenPresentation.Row> list = new JList<>(rows);
    private final JTextArea detail = new JTextArea("Select a token to view details.");
    private final JPanel codes = new JPanel(new GridLayout(0, 1));
    private final transient TotpDisplay totp;
    private transient VaultState latest;
    private transient TokenId selected;
    private boolean rebuilding;
    private boolean closed;
    private final JButton edit = new JButton("Edit Token");
    private final JButton resolve = new JButton("Resolve Conflict…");
    private transient VaultView.MergeAction mergeAction;
    private transient VaultView.EditAction editAction;
    private boolean writeAvailable = true;
    private final JComboBox<String> alternatives = new JComboBox<>();

    public TokenBrowserPanel(Clock clock) {
        Edt.require();
        setLayout(new BorderLayout());
        totp = new TotpDisplay(clock, this::renderCodes);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer(new LiteralRenderer());
        list.getAccessibleContext().setAccessibleName("Logical tokens");
        detail.setEditable(false); // JTextArea always renders stored text literally, never as HTML.
        detail.getAccessibleContext().setAccessibleName("Selected token details");
        JPanel right = new JPanel(new BorderLayout());
        right.add(codes, BorderLayout.NORTH);
        right.add(new JScrollPane(detail), BorderLayout.CENTER);
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, new JScrollPane(list), right);
        split.setResizeWeight(0.3);
        split.setPreferredSize(new Dimension(960, 480));
        add(split, BorderLayout.CENTER);
        JPanel editing = new JPanel(new GridLayout(0, 1));
        editing.add(alternatives);
        editing.add(edit); editing.add(resolve); resolve.setVisible(false);
        resolve.addActionListener(event -> {
            if (!closed && writeAvailable && mergeAction != null && latest != null && selected != null) {
                TokenState token = latest.token(selected).orElse(null);
                if (token != null && token.hasConflict() && token.alternatives().size() >= 2) { mergeAction.open(latest, token); }
            }
        });
        add(editing, BorderLayout.SOUTH);
        edit.setEnabled(false);
        alternatives.setVisible(false);
        alternatives.addActionListener(event -> writeAvailability(writeAvailable));
        edit.addActionListener(event -> editSelected());
        list.addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting() && !rebuilding && !closed) {
                selected = list.getSelectedValue() == null ? null : list.getSelectedValue().id();
                renderSelection();
            }
        });
    }

    private static final class LiteralRenderer extends DefaultListCellRenderer {
        private static final long serialVersionUID = 1L;
        LiteralRenderer() { putClientProperty("html.disable", Boolean.TRUE); }
        @Override public Component getListCellRendererComponent(JList<?> source, Object value, int index,
                                                                boolean selected, boolean focus) {
            return super.getListCellRendererComponent(source, value, index, selected, focus);
        }
    }

    public void render(VaultState state) {
        Edt.require();
        if (closed) { return; }
        totp.clear();
        latest = state;
        rebuilding = true;
        try {
            rows.clear();
            int selectedIndex = -1;
            for (TokenState token : state.tokens()) {
                if (token.id().equals(selected)) { selectedIndex = rows.size(); }
                rows.addElement(TokenPresentation.row(token));
            }
            list.setSelectedIndex(selectedIndex);
            if (selectedIndex == -1) { selected = null; }
        } finally { rebuilding = false; }
        renderSelection();
    }

    private void renderSelection() {
        totp.clear();
        TokenState token = selected == null || latest == null ? null : latest.token(selected).orElse(null);
        if (token == null) {
            selected = null;
            detail.setText("Select a token to view details.");
        } else {
            detail.setText(TokenPresentation.detail(token));
            totp.select(latest, token);
        }
        detail.setCaretPosition(0);
        edit.setText(token != null && token.hasConflict() ? "Edit Alternative…" : "Edit Token");
        alternatives.removeAllItems();
        alternatives.setVisible(token != null && token.hasConflict());
        if (token != null && token.hasConflict()) {
            for (int i = 0; i < token.alternatives().size(); i++) { alternatives.addItem(TokenPresentation.label(i)); }
            alternatives.setSelectedIndex(-1);
            detail.append("\nTo edit, explicitly select an Alternative below. This updates only that alternative; "
                    + "it does not resolve the other concurrent alternatives.\n");
        }
        writeAvailability(writeAvailable);
    }

    public void onMerge(VaultView.MergeAction action) { Edt.require(); mergeAction = action; }
    public void onEdit(VaultView.EditAction action) { Edt.require(); editAction = action; }
    public void writeAvailability(boolean available) {
        Edt.require(); writeAvailable = available;
        TokenState token = selected == null || latest == null ? null : latest.token(selected).orElse(null);
        resolve.setVisible(token != null && token.hasConflict() && token.alternatives().size() >= 2);
        resolve.setEnabled(!closed && available && resolve.isVisible());
        alternatives.setEnabled(!closed && available);
        edit.setEnabled(!closed && available && token != null && !token.alternatives().isEmpty()
                && (!token.hasConflict() || alternatives.getSelectedIndex() >= 0));
    }
    private void editSelected() {
        if (closed || !writeAvailable || editAction == null || selected == null) { return; }
        VaultState base = latest;
        TokenState token = base.token(selected).orElse(null);
        if (token == null || token.alternatives().isEmpty()) { return; }
        int index = 0;
        String explanation = "This edit is based on the token value observed when the editor was opened. "
                + "Later concurrent changes are not automatically included.";
        if (token.hasConflict()) {
            index = alternatives.getSelectedIndex();
            if (index < 0) { return; }
            explanation = "This token currently has competing alternatives. You are editing " + TokenPresentation.label(index)
                    + " only. Saving this update does not resolve the other alternatives. " + explanation;
        }
        editAction.open(base, token.alternatives().get(index), explanation);
    }

    private void renderCodes(List<TotpDisplay.Display> displays) {
        if (codes.getComponentCount() != displays.size() * 2) {
            // Clear text even on detached components when retiring an old selection.
            for (Component component : codes.getComponents()) {
                if (component instanceof JLabel label) { label.setText(""); }
            }
            codes.removeAll();
            for (int i = 0; i < displays.size(); i++) {
                JLabel code = new JLabel();
                code.putClientProperty("html.disable", Boolean.TRUE);
                codes.add(code);
                JProgressBar remaining = new JProgressBar(0, 1000);
                remaining.setStringPainted(true);
                codes.add(remaining);
            }
            codes.revalidate();
        }
        for (int i = 0; i < displays.size(); i++) {
            TotpDisplay.Display display = displays.get(i);
            JLabel code = (JLabel) codes.getComponent(2 * i);
            String text = display.label() + ": " + display.code();
            if (!text.equals(code.getText())) { code.setText(text); }
            JProgressBar remaining = (JProgressBar) codes.getComponent(2 * i + 1);
            remaining.setValue(display.remaining());
            remaining.setString(display.countdown());
        }
        codes.repaint();
    }

    public void closing() {
        Edt.require();
        closed = true;
        edit.setEnabled(false); resolve.setEnabled(false);
        totp.clear();
        latest = null;
        selected = null;
        list.setEnabled(false);
        rows.clear();
        detail.setText("Closing…");
    }
}
