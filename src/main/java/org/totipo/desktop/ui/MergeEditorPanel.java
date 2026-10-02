package org.totipo.desktop.ui;

import org.totipo.*;
import org.totipo.desktop.MergeDraft;
import org.totipo.desktop.MergeInputs;
import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/** Modeless two-step desktop editor. No builder or publication capability enters this panel. */
public final class MergeEditorPanel extends JPanel {
    private static final long serialVersionUID = 1L;
    private final transient MergeInputs captured;
    private transient MergeInputs frozen;
    private final transient Consumer<MergeDraft> submit;
    private final transient Runnable abandon;
    private final JPanel body = new JPanel(new GridLayout(0, 1, 6, 6));
    private final transient List<JCheckBox> selections = new ArrayList<>();
    private final JTextArea message = literal("");
    private final JButton next = new JButton("Continue");
    private final JButton back = new JButton("Back");
    private final JButton save = new JButton("Save");
    private final JButton cancel = new JButton("Cancel");
    private final JPasswordField secret = new JPasswordField();
    private JComboBox<String> secretChoice;
    private transient List<SecretGroup> secretGroups = List.of();
    private transient Field<TokenStatus> status;
    private transient Field<String> issuer;
    private transient Field<String> account;
    private transient Field<TotpAlgorithm> algorithm;
    private transient Field<Integer> digits;
    private transient Field<Duration> period;
    private javax.swing.JRootPane dialogRoot;
    private boolean busy;
    private boolean retired;

    public MergeEditorPanel(MergeInputs captured, Consumer<MergeDraft> submit, Runnable abandon) {
        Edt.require(); this.captured = captured; this.submit = submit; this.abandon = abandon;
        setLayout(new BorderLayout(8, 8));
        add(literal("This resolution is based on the token state observed when the merge editor was opened. "
                + "Totipo will check for newly relevant information before publication. Alternative labels imply no priority."), BorderLayout.NORTH);
        add(new JScrollPane(body), BorderLayout.CENTER);
        JPanel footer = new JPanel(new BorderLayout());
        JPanel buttons = new JPanel();
        for (JButton button : List.of(back, next, save, cancel)) { buttons.add(button); }
        footer.add(message, BorderLayout.CENTER); footer.add(buttons, BorderLayout.SOUTH); add(footer, BorderLayout.SOUTH);
        next.addActionListener(event -> resolve());
        back.addActionListener(event -> { secret.setText(""); inputStep(); });
        save.setMnemonic('S'); cancel.setMnemonic('C'); next.setMnemonic('N'); back.setMnemonic('B');
        message.getAccessibleContext().setAccessibleName("Merge status");
        secret.getAccessibleContext().setAccessibleName("Replacement TOTP secret");
        cancel.addActionListener(event -> { if (canCancel()) { retire(); abandon.run(); } });
        save.addActionListener(event -> save());
        secret.getDocument().addDocumentListener(listener(this::validateForm));
        inputStep();
    }
    private static JTextArea literal(String text) {
        JTextArea area = new JTextArea(text); area.setEditable(false); area.setLineWrap(true); area.setWrapStyleWord(true);
        return area;
    }
    private void inputStep() {
        List<TokenAlternative> previous = frozen == null ? captured.captured() : frozen.selected();
        frozen = null; body.removeAll(); selections.clear();
        status = null; issuer = null; account = null; algorithm = null; digits = null; period = null;
        secretChoice = null; secretGroups = List.of();
        for (TokenAlternative alternative : captured.captured()) {
            JCheckBox selected = new JCheckBox(captured.labels(List.of(alternative)), previous.contains(alternative));
            selections.add(selected); body.add(selected);
            TokenDescriptor d = alternative.descriptor();
            body.add(literal(d.status() + " | Issuer: " + d.issuer() + " | Account: " + d.account()
                    + " | " + d.algorithm() + " | " + d.digits() + " digits | " + d.period().getSeconds()
                    + " seconds\nHeads: " + alternative.heads().stream().map(h -> h.revision().toString()).toList()));
            selected.addActionListener(event -> selectionChanged());
        }
        back.setVisible(false); save.setVisible(false); next.setVisible(true); selectionChanged(); refresh();
    }
    private void selectionChanged() {
        long count = selections.stream().filter(JCheckBox::isSelected).count();
        next.setEnabled(!busy && !retired && count >= 2);
        message.setText(count < 2 ? "Select at least two alternatives. Use Edit Alternative to change one value."
                : count < selections.size() ? "This resolution will parent only the selected alternatives. Unselected current alternatives are not included and may remain competing values."
                : "All captured complete alternatives are selected for a full-frontier merge.");
    }
    private void resolve() {
        if (busy || retired || !next.isEnabled()) { return; }
        List<TokenAlternative> selected = new ArrayList<>();
        for (int i = 0; i < selections.size(); i++) { if (selections.get(i).isSelected()) { selected.add(captured.captured().get(i)); } }
        frozen = captured.select(selected); body.removeAll();
        TokenCompetition c = frozen.selectedCompetition();
        status = field("Status", c.status(), List.of(TokenStatus.values()), null, Enum::name);
        issuer = field("Issuer", c.issuer(), List.of(), Function.identity(), Function.identity());
        account = field("Account", c.account(), List.of(), Function.identity(), Function.identity());
        algorithm = field("Algorithm", c.algorithm(), List.of(TotpAlgorithm.values()), null, Enum::name);
        digits = field("Digits (6–8)", c.digits(), List.of(), Integer::valueOf, Object::toString);
        period = field("Period (whole seconds, 1–4294967295)", c.period(), List.of(),
                text -> Duration.ofSeconds(Long.parseLong(text)), value -> Long.toString(value.getSeconds()));
        secretGroups = c.secret().groups(); secretChoice = new JComboBox<>();
        for (int i = 0; i < secretGroups.size(); i++) {
            secretChoice.addItem("Existing secret group " + (i + 1) + " — " + frozen.labels(secretGroups.get(i).alternatives()));
        }
        secretChoice.addItem("Replace with new secret");
        secretChoice.setSelectedIndex(secretGroups.size() == 1 ? 0 : -1);
        body.add(SwingUsability.label("Secret", secretChoice)); body.add(secretChoice); body.add(SwingUsability.label("New Base32 secret", secret)); body.add(secret);
        secret.setEnabled(false);
        secretChoice.addActionListener(event -> {
            boolean replacement = secretChoice.getSelectedIndex() == secretGroups.size();
            if (!replacement) { secret.setText(""); }
            secret.setEnabled(replacement && !busy && !retired); validateForm();
        });
        next.setVisible(false); back.setVisible(true); save.setVisible(true);
        message.setText(frozen.fullFrontier() ? "Resolve each disagreeing field explicitly. Agreed fields may also be changed."
                : "This resolution parents only selected alternatives; omitted alternatives may remain competing values.");
        validateForm(); refresh();
    }
    private <T> Field<T> field(String label, CompetingField<T> competition, List<T> allowed,
                               Function<String, T> parse, Function<T, String> format) {
        Field<T> field = new Field<>(competition, allowed, parse, format);
        body.add(SwingUsability.label(label, field.choice));
        if (parse != null) { field.panel.add(SwingUsability.label("Custom " + label, field.custom), 1); }
        body.add(field.panel); return field;
    }
    private final class Field<T> {
        final JPanel panel = new JPanel(new GridLayout(0, 1));
        final JComboBox<String> choice = new JComboBox<>();
        final JTextField custom = new JTextField();
        final List<T> values = new ArrayList<>();
        final Function<String, T> parse;
        Field(CompetingField<T> competition, List<T> allowed, Function<String, T> parse, Function<T, String> format) {
            this.parse = parse;
            DefaultListCellRenderer renderer = new DefaultListCellRenderer();
            renderer.putClientProperty("html.disable", Boolean.TRUE); choice.setRenderer(renderer);
            for (var value : competition.values()) {
                values.add(value.value()); choice.addItem(format.apply(value.value()) + " — " + frozen.labels(value.alternatives()));
            }
            for (T value : allowed) { if (!values.contains(value)) { values.add(value); choice.addItem(format.apply(value)); } }
            if (parse != null) { choice.addItem("Other…"); }
            choice.setSelectedIndex(competition.values().size() == 1 ? 0 : -1);
            panel.add(choice);
            if (parse != null) { panel.add(custom); custom.setEnabled(false); }
            choice.addActionListener(event -> {
                custom.setEnabled(!busy && !retired && choice.getSelectedIndex() == values.size()); validateForm();
            });
            custom.getDocument().addDocumentListener(listener(MergeEditorPanel.this::validateForm));
        }
        T value() {
            int index = choice.getSelectedIndex();
            if (index < 0) { throw new IllegalArgumentException("Resolve every field."); }
            return index < values.size() ? values.get(index) : parse.apply(custom.getText());
        }
    }
    private TokenDescriptor fields() {
        return new TokenDescriptor(status.value(), issuer.value(), account.value(), algorithm.value(), digits.value(), period.value());
    }
    private void validateForm() {
        boolean valid = false;
        if (frozen != null && period != null && secretChoice != null) {
            try {
                fields(); int index = secretChoice.getSelectedIndex();
                valid = index >= 0;
                if (index == secretGroups.size()) {
                    char[] chars = secret.getPassword();
                    try { valid = chars.length > 0; } finally { java.util.Arrays.fill(chars, '\0'); }
                }
            } catch (RuntimeException invalid) { valid = false; }
        }
        save.setEnabled(valid && !busy && !retired);
    }
    private void save() {
        if (busy || retired || !save.isEnabled()) { return; }
        try {
            TokenDescriptor fields = fields();
            int index = secretChoice.getSelectedIndex();
            byte[] decoded = null;
            if (index == secretGroups.size()) {
                char[] chars = secret.getPassword();
                try { decoded = TokenEditorPanel.decodeAndWipe(chars); } finally { secret.setText(""); }
            }
            MergeDraft draft = new MergeDraft(frozen, fields,
                    index < secretGroups.size() ? secretGroups.get(index).alternatives().get(0) : null, decoded);
            busy(true, "Saving merge…"); submit.accept(draft);
        } catch (IllegalArgumentException invalid) { message.setText("Resolve all fields with valid values and a valid Base32 secret if replacing."); }
    }
    public void busy(boolean value, String text) {
        Edt.require(); busy = value; enable(body, !value && !retired);
        back.setEnabled(!value && !retired); cancel.setEnabled(!value && !retired);
        next.setEnabled(!value && !retired); message.setText(text);
        if (!value && !retired && frozen != null) {
            for (var field : List.of(status, issuer, account, algorithm, digits, period)) {
                field.custom.setEnabled(field.choice.getSelectedIndex() == field.values.size());
            }
            secret.setEnabled(secretChoice.getSelectedIndex() == secretGroups.size());
        }
        validateForm();
    }
    private static void enable(java.awt.Container root, boolean value) {
        for (var child : root.getComponents()) { child.setEnabled(value); if (child instanceof java.awt.Container c) { enable(c, value); } }
    }
    public boolean canCancel() { return !busy && !retired; }
    public void cancel() { if (canCancel()) { cancel.doClick(); } }
    public void retire() { Edt.require(); retired = true; secret.setText(""); busy(true, " "); }
    void installDialog(JRootPane root) { dialogRoot = root; SwingUsability.dialog(root, next, this::cancel); }
    private void refresh() { if (dialogRoot != null) { dialogRoot.setDefaultButton(frozen == null ? next : save); } body.revalidate(); body.repaint(); }
    private static DocumentListener listener(Runnable changed) {
        return new DocumentListener() {
            public void insertUpdate(DocumentEvent event) { changed.run(); }
            public void removeUpdate(DocumentEvent event) { changed.run(); }
            public void changedUpdate(DocumentEvent event) { changed.run(); }
        };
    }
}
