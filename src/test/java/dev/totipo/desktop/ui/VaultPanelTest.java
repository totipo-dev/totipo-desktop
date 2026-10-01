package dev.totipo.desktop.ui;

import dev.totipo.ObservationProgress;
import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JProgressBar;
import javax.swing.JTextArea;
import org.junit.jupiter.api.Test;
import static dev.totipo.desktop.TestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class VaultPanelTest {
    @Test void rendersAllProgressVariantsWithExactCountsAndReplacesDiagnostics() throws Exception {
        edt(() -> {
            VaultPanel panel = new VaultPanel();
            List<Component> components = descendants(panel);
            JLabel status = components.stream().filter(JLabel.class::isInstance).map(JLabel.class::cast)
                    .filter(label -> label.getText().startsWith("Waiting")).findFirst().orElseThrow();
            JProgressBar progress = first(components, JProgressBar.class);
            JTextArea diagnostics = components.stream().filter(JTextArea.class::isInstance).map(JTextArea.class::cast)
                    .filter(area -> area.getRows() == 8).findFirst().orElseThrow();
            JButton refresh = components.stream().filter(JButton.class::isInstance).map(JButton.class::cast)
                    .filter(button -> button.getText().equals("Refresh")).findFirst().orElseThrow();
            // These fake states have no tokens and reject editing/TOTP API calls.
            panel.render(state(new ObservationProgress.Enumerating(42), "CODE_A", "CODE_A"));
            assertEquals("Observing local vault — discovered 42 objects", status.getText());
            assertTrue(progress.isIndeterminate());
            assertEquals("CODE_A\nCODE_A\n", diagnostics.getText());
            panel.render(state(new ObservationProgress.Processing(3, 8), "CODE_B"));
            assertEquals("Processing local observation — 3 of 8", status.getText());
            assertFalse(progress.isIndeterminate());
            assertEquals(375, progress.getValue());
            assertEquals("CODE_B\n", diagnostics.getText());
            panel.render(state(new ObservationProgress.Processing(Long.MAX_VALUE - 1, Long.MAX_VALUE)));
            assertEquals("Processing local observation — 9223372036854775806 of 9223372036854775807", status.getText());
            assertTrue(progress.getValue() >= 999);
            assertEquals("", diagnostics.getText());
            panel.render(state(new ObservationProgress.Processing(0, 0)));
            assertEquals("Processing local observation — 0 of 0", status.getText());
            assertEquals(0, progress.getValue());
            panel.render(state(new ObservationProgress.Finished(25, false)));
            assertEquals("Local observation finished — 25 objects processed", status.getText());
            assertFalse(progress.isIndeterminate());
            panel.render(state(new ObservationProgress.Finished(26, true), "CODE_C"));
            assertEquals("Local observation finished — 26 objects processed; diagnostics present", status.getText());
            assertEquals("CODE_C\n", diagnostics.getText());
            assertTrue(refresh.isEnabled());
            panel.closing();
            assertEquals("Closing…", status.getText());
            assertFalse(refresh.isEnabled());
        });
    }

    private static List<Component> descendants(Container parent) {
        List<Component> result = new ArrayList<>();
        for (Component child : parent.getComponents()) {
            result.add(child);
            if (child instanceof Container container) { result.addAll(descendants(container)); }
        }
        return result;
    }

    private static <T> T first(List<Component> components, Class<T> type) {
        return components.stream().filter(type::isInstance).map(type::cast).findFirst().orElseThrow();
    }
}
