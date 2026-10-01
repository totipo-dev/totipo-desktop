package dev.totipo.desktop;

import dev.totipo.*;
import dev.totipo.desktop.ui.*;
import java.awt.Component;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import static dev.totipo.desktop.MergeFixtures.*;
import static dev.totipo.desktop.MergeControllerTest.boxes;
import static dev.totipo.desktop.TestSupport.edt;
import static dev.totipo.desktop.TokenWriteControllerTest.*;
import static org.junit.jupiter.api.Assertions.*;

class MergeEditorTest {
    @Test void everyDisagreeingFieldStartsUnresolvedAndRequiresExplicitSelection() throws Exception {
        edt(() -> {
            Recording fake = new Recording(); AtomicReference<MergeDraft> submitted = new AtomicReference<>();
            var panel = new MergeEditorPanel(fake.inputs(), submitted::set, () -> {});
            assertTrue(components(panel).stream().filter(JCheckBox.class::isInstance).map(JCheckBox.class::cast).allMatch(JCheckBox::isSelected));
            button(panel, "Continue").doClick();
            var fields = boxes(panel); assertEquals(7, fields.size());
            for (var field : fields) { assertEquals(-1, field.getSelectedIndex()); }
            assertFalse(button(panel, "Save").isEnabled());
            for (var field : fields) {
                field.setSelectedIndex(0);
                if (field != fields.getLast()) { assertFalse(button(panel, "Save").isEnabled()); }
            }
            assertTrue(button(panel, "Save").isEnabled());
            for (var field : fields) {
                field.setSelectedIndex(-1); assertFalse(button(panel, "Save").isEnabled());
                field.setSelectedIndex(1); assertTrue(button(panel, "Save").isEnabled());
            }
            assertTrue(fields.getLast().getItemAt(0).toString().contains("Alternative 1"));
            assertTrue(fields.getLast().getItemAt(1).toString().contains("Alternative 2"));
            button(panel, "Save").doClick(); assertNotNull(submitted.get());
            assertFalse(button(panel, "Save").isEnabled()); submitted.get().close(); panel.retire();
        });
    }
    @Test void customLiteralStringsNumericLimitsAndNewSecretIngress() throws Exception {
        edt(() -> {
            Recording fake = new Recording(); AtomicReference<MergeDraft> submitted = new AtomicReference<>();
            var panel = new MergeEditorPanel(fake.inputs(), submitted::set, () -> {});
            button(panel, "Continue").doClick(); var choices = boxes(panel);
            choices.forEach(box -> box.setSelectedIndex(0));
            var custom = components(panel).stream().filter(c -> c instanceof JTextField && !(c instanceof JPasswordField))
                    .map(JTextField.class::cast).toList();
            assertEquals(4, custom.size());
            for (int index : List.of(1, 2, 4, 5)) { choices.get(index).setSelectedIndex(choices.get(index).getItemCount() - 1); }
            custom.get(0).setText("<html>literal & issuer"); custom.get(1).setText("\u0001 account");
            custom.get(2).setText("8"); custom.get(3).setText("4294967295"); assertTrue(button(panel, "Save").isEnabled());
            for (String invalid : List.of("5", "9", "6.0", "")) {
                custom.get(2).setText(invalid); assertFalse(button(panel, "Save").isEnabled());
            }
            custom.get(2).setText("6");
            for (String invalid : List.of("0", "4294967296", "1.5", "-1")) {
                custom.get(3).setText(invalid); assertFalse(button(panel, "Save").isEnabled());
            }
            custom.get(3).setText("1");
            choices.getLast().setSelectedIndex(choices.getLast().getItemCount() - 1);
            assertFalse(button(panel, "Save").isEnabled()); password(panel).setText("!"); button(panel, "Save").doClick();
            assertNull(submitted.get()); assertEquals(0, password(panel).getPassword().length);
            password(panel).setText("MY"); button(panel, "Save").doClick(); assertNotNull(submitted.get());
            assertEquals(0, password(panel).getPassword().length); submitted.get().close(); panel.retire();
        });
    }
    @Test void subsetFilteringPrefillsAgreedFieldsAndBackDiscardsChoicesAndSecret() throws Exception {
        edt(() -> {
            Recording fake = new Recording(List.of(alternative(0), alternative(0), alternative(1)), true);
            var panel = new MergeEditorPanel(fake.inputs(), draft -> fail("Unexpected save"), () -> {});
            var selected = components(panel).stream().filter(JCheckBox.class::isInstance).map(JCheckBox.class::cast).toList();
            selected.get(2).doClick();
            assertTrue(text(panel).contains("Unselected current alternatives are not included"));
            selected.get(1).doClick(); assertFalse(button(panel, "Continue").isEnabled()); assertTrue(text(panel).contains("Edit Alternative"));
            selected.get(1).doClick(); button(panel, "Continue").doClick();
            assertTrue(boxes(panel).stream().allMatch(box -> box.getSelectedIndex() == 0));
            assertTrue(button(panel, "Save").isEnabled());
            var secretChoice = boxes(panel).getLast(); secretChoice.setSelectedIndex(secretChoice.getItemCount() - 1);
            JPasswordField old = password(panel); old.setText("MY");
            button(panel, "Back").doClick(); assertEquals(0, old.getPassword().length);
            var inputsAgain = components(panel).stream().filter(JCheckBox.class::isInstance).map(JCheckBox.class::cast).toList();
            assertFalse(inputsAgain.get(2).isSelected()); inputsAgain.get(2).doClick();
            button(panel, "Continue").doClick();
            assertEquals(-1, boxes(panel).get(1).getSelectedIndex()); // All inputs freshly selected, no prior resolution.
            assertEquals(0, boxes(panel).getLast().getSelectedIndex()); // Core agreed secret, not a prior choice.
            panel.retire(); assertFalse(button(panel, "Save").isEnabled());
        });
    }
    @Test void cancelBeforeSaveCreatesNoCapabilityAndClearsSecret() throws Exception {
        edt(() -> {
            Recording fake = new Recording(); int[] cancels = {0};
            var panel = new MergeEditorPanel(fake.inputs(), d -> fail("Unexpected save"), () -> cancels[0]++);
            button(panel, "Continue").doClick(); boxes(panel).getLast().setSelectedIndex(3); password(panel).setText("MY");
            panel.cancel(); assertEquals(1, cancels[0]); assertEquals(0, password(panel).getPassword().length);
            assertTrue(fake.calls.isEmpty()); assertFalse(panel.canCancel());
        });
    }
    @Test void storedValuesUseLiteralComboRenderer() throws Exception {
        edt(() -> {
            Recording fake = new Recording(); var panel = new MergeEditorPanel(fake.inputs(), MergeDraft::close, () -> {});
            button(panel, "Continue").doClick();
            for (var box : boxes(panel).subList(0, 6)) {
                Component renderer = box.getRenderer().getListCellRendererComponent(new JList<>(), null, 0, false, false);
                assertEquals(Boolean.TRUE, ((JComponent) renderer).getClientProperty("html.disable"));
            }
            panel.retire();
        });
    }
    static String text(java.awt.Container panel) {
        return components(panel).stream().filter(JTextArea.class::isInstance).map(JTextArea.class::cast)
                .map(JTextArea::getText).collect(java.util.stream.Collectors.joining("\n"));
    }
}
