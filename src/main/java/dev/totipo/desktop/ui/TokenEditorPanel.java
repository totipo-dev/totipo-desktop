package dev.totipo.desktop.ui;

import dev.totipo.*;
import dev.totipo.desktop.Base32;
import dev.totipo.desktop.TokenDraft;
import java.awt.GridLayout;
import java.awt.BorderLayout;
import java.time.Duration;
import java.util.Arrays;
import java.util.function.Consumer;
import javax.swing.*;

/** Literal Swing fields own input, never a core builder or existing secret. */
public final class TokenEditorPanel extends JPanel {
    private static final long serialVersionUID = 1L;
    final JTextField issuer = new JTextField(24);
    final JTextField account = new JTextField(24);
    final JComboBox<TotpAlgorithm> algorithm = new JComboBox<>(TotpAlgorithm.values());
    final JComboBox<TokenStatus> status = new JComboBox<>(TokenStatus.values());
    final JTextField digits = new JTextField("6");
    final JTextField period = new JTextField("30");
    final JCheckBox replace = new JCheckBox("Replace secret");
    final JPasswordField secret = new JPasswordField();
    final JButton save = new JButton("Save");
    final JButton cancel = new JButton("Cancel");
    final JTextArea message = new JTextArea(" ");
    private final boolean create;
    private boolean busy;
    private boolean retired;

    public TokenEditorPanel(TokenDescriptor descriptor, String explanation,
                            Consumer<TokenDraft> submit, Runnable abandon) {
        Edt.require();
        create = descriptor == null;
        setLayout(new BorderLayout(8, 8));
        setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        JPanel fields = new JPanel(new GridLayout(0, 2, 8, 8));
        if (!create) {
            status.setSelectedItem(descriptor.status());
            issuer.setText(descriptor.issuer()); account.setText(descriptor.account());
            algorithm.setSelectedItem(descriptor.algorithm());
            digits.setText(Integer.toString(descriptor.digits()));
            period.setText(Long.toString(descriptor.period().getSeconds()));
            fields.add(SwingUsability.label("Status", status)); fields.add(status);
        }
        fields.add(SwingUsability.label("Issuer", issuer)); fields.add(issuer);
        fields.add(SwingUsability.label("Account", account)); fields.add(account);
        fields.add(SwingUsability.label("Algorithm", algorithm)); fields.add(algorithm);
        fields.add(SwingUsability.label("Digits (6–8)", digits)); fields.add(digits);
        fields.add(SwingUsability.label("Period (seconds, 1–4294967295)", period)); fields.add(period);
        if (!create) { fields.add(replace); fields.add(new JLabel(" ")); }
        fields.add(SwingUsability.label("New Base32 secret", secret)); fields.add(secret);
        add(new JScrollPane(fields), BorderLayout.CENTER);
        secret.setEnabled(create);
        replace.addActionListener(event -> {
            if (!replace.isSelected()) { secret.setText(""); }
            secret.setEnabled(replace.isSelected() && !busy && !retired);
        });
        JTextArea note = new JTextArea(explanation);
        note.setEditable(false); note.setLineWrap(true); note.setWrapStyleWord(true);
        note.setRows(5);
        add(note, BorderLayout.NORTH);
        message.setEditable(false); message.setLineWrap(true); message.setWrapStyleWord(true); message.setRows(3);
        JPanel footer = new JPanel(new BorderLayout(8, 8));
        JPanel buttons = new JPanel(); buttons.add(save); buttons.add(cancel);
        footer.add(message, BorderLayout.CENTER); footer.add(buttons, BorderLayout.SOUTH);
        add(footer, BorderLayout.SOUTH);
        save.addActionListener(event -> {
            if (busy || retired) { return; }
            TokenDraft draft;
            try { draft = draft(); }
            catch (IllegalArgumentException invalid) { message.setText(invalid.getMessage()); return; }
            busy(true, "Saving…");
            submit.accept(draft);
        });
        save.setMnemonic('S'); cancel.setMnemonic('C');
        secret.getAccessibleContext().setAccessibleName("Replacement TOTP secret");
        cancel.addActionListener(event -> { if (!busy && !retired) { retire(); abandon.run(); } });
    }

    void installDialog(JRootPane root) { SwingUsability.dialog(root, save, () -> { if (canCancel()) { cancel.doClick(); } }); }

    TokenDraft draft() {
        Edt.require();
        TokenDescriptor fields;
        try {
            fields = new TokenDescriptor(create ? TokenStatus.ACTIVE : (TokenStatus) status.getSelectedItem(),
                    issuer.getText(), account.getText(), (TotpAlgorithm) algorithm.getSelectedItem(),
                    Integer.parseInt(digits.getText()), Duration.ofSeconds(Long.parseLong(period.getText())));
        } catch (RuntimeException invalid) {
            throw new IllegalArgumentException("Choose valid status/algorithm, digits 6–8 and whole seconds 1–4294967295.");
        }
        byte[] decoded = null;
        if (create || replace.isSelected()) {
            char[] input = secret.getPassword();
            try { decoded = decodeAndWipe(input); }
            finally { secret.setText(""); }
        }
        return new TokenDraft(fields, decoded);
    }

    static byte[] decodeAndWipe(char[] input) {
        try { return Base32.decode(input); }
        finally { Arrays.fill(input, '\0'); }
    }

    public void busy(boolean value, String text) {
        Edt.require();
        busy = value;
        for (var control : new JComponent[]{issuer, account, algorithm, status, digits, period, replace, save, cancel}) {
            control.setEnabled(!value && !retired);
        }
        secret.setEnabled(!value && !retired && (create || replace.isSelected()));
        message.setText(text);
    }
    public void retire() {
        Edt.require(); retired = true; secret.setText(""); busy(true, " ");
    }
    public boolean canCancel() { Edt.require(); return !busy && !retired; }
}
