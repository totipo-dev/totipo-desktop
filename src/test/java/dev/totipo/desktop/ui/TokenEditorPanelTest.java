package dev.totipo.desktop.ui;

import dev.totipo.*;
import dev.totipo.desktop.TokenDraft;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static dev.totipo.desktop.TestSupport.edt;
import static org.junit.jupiter.api.Assertions.*;

class TokenEditorPanelTest {
    @Test void defaultsRequiredSecretValidationAndSingleSubmission() throws Exception {
        edt(() -> {
            AtomicInteger submits = new AtomicInteger();
            var panel = new TokenEditorPanel(null, "Create", draft -> { submits.incrementAndGet(); draft.close(); }, () -> {});
            assertEquals("", panel.issuer.getText()); assertEquals("", panel.account.getText());
            assertEquals(TotpAlgorithm.SHA1, panel.algorithm.getSelectedItem());
            assertEquals("6", panel.digits.getText()); assertEquals("30", panel.period.getText());
            assertEquals(TokenStatus.ACTIVE, panel.status.getSelectedItem());
            panel.save.doClick(); assertEquals(0, submits.get()); assertTrue(panel.save.isEnabled());
            panel.secret.setText("MY"); panel.digits.setText("9"); panel.save.doClick();
            assertEquals(0, submits.get());
            panel.digits.setText("6"); panel.period.setText("1.5"); panel.save.doClick(); assertEquals(0, submits.get());
            panel.period.setText("4294967296"); panel.save.doClick(); assertEquals(0, submits.get());
            panel.period.setText("0"); panel.save.doClick(); assertEquals(0, submits.get());
            panel.period.setText("4294967295"); panel.save.doClick(); panel.save.doClick();
            assertEquals(1, submits.get()); assertEquals(0, panel.secret.getPassword().length);
            assertFalse(panel.canCancel()); panel.retire();
        });
    }
    @Test void updatePrefillReplacementOptInAndCloseClearing() throws Exception {
        edt(() -> {
            var descriptor = new TokenDescriptor(TokenStatus.TOMBSTONED, "<html>issuer", "account", TotpAlgorithm.SHA512, 8, Duration.ofSeconds(60));
            var panel = new TokenEditorPanel(descriptor, "Update", TokenDraft::close, () -> {});
            assertEquals(descriptor.issuer(), panel.issuer.getText()); assertEquals("account", panel.account.getText());
            assertEquals(TokenStatus.TOMBSTONED, panel.status.getSelectedItem());
            assertEquals(TotpAlgorithm.SHA512, panel.algorithm.getSelectedItem());
            assertEquals("8", panel.digits.getText()); assertEquals("60", panel.period.getText());
            assertFalse(panel.secret.isEnabled());
            panel.replace.doClick(); assertTrue(panel.secret.isEnabled()); panel.secret.setText("MY");
            panel.replace.doClick(); assertEquals(0, panel.secret.getPassword().length);
            panel.secret.setText("invalid"); // Disabled input must not be read or decoded.
            try (TokenDraft draft = panel.draft()) { assertNotNull(draft); }
            panel.retire(); assertEquals(0, panel.secret.getPassword().length);
        });
    }
    @Test void temporaryCopiesWipedOnSuccessAndFailureAndCancelClearsField() throws Exception {
        char[] valid = {'M', 'Y'};
        byte[] decoded = TokenEditorPanel.decodeAndWipe(valid);
        assertArrayEquals(new char[2], valid); java.util.Arrays.fill(decoded, (byte) 0);
        char[] bad = {'M', '0'};
        assertThrows(IllegalArgumentException.class, () -> TokenEditorPanel.decodeAndWipe(bad));
        assertArrayEquals(new char[2], bad);
        edt(() -> {
            AtomicInteger cancels = new AtomicInteger();
            var panel = new TokenEditorPanel(null, "", TokenDraft::close, cancels::incrementAndGet);
            panel.secret.setText("MY"); panel.cancel.doClick();
            assertEquals(1, cancels.get()); assertEquals(0, panel.secret.getPassword().length);
        });
    }
}
