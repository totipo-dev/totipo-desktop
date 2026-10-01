package dev.totipo.desktop.ui;

import dev.totipo.ObservationProgress;
import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import static dev.totipo.desktop.TestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class PublicationPanelTest {
    @Test void uncertaintyWordingActionsAndStickyNoticeSurviveObservation() throws Exception {
        edt(() -> {
            VaultPanel panel = new VaultPanel();
            try {
                AtomicInteger retries = new AtomicInteger(); AtomicInteger stops = new AtomicInteger();
                panel.publicationUncertain(true, false, retries::incrementAndGet, stops::incrementAndGet);
                String text = all(panel).stream().filter(JTextArea.class::isInstance).map(JTextArea.class::cast)
                        .map(JTextArea::getText).filter(value -> value.contains("durable acknowledgement")).findFirst().orElseThrow();
                assertTrue(text.contains("may already be present")); assertTrue(text.contains("exact same frozen operation"));
                assertTrue(text.contains("does not re-read or rebase")); assertTrue(text.contains("two logical tokens"));
                assertFalse(text.contains("Save failed")); assertFalse(text.contains("Not saved"));
                button(panel, "Retry exact publication").doClick(); assertEquals(1, retries.get());
                button(panel, "Stop retrying").doClick(); assertEquals(1, stops.get());
                panel.publicationUncertain(true, true, retries::incrementAndGet, stops::incrementAndGet);
                assertFalse(button(panel, "Stop retrying").isEnabled());
                panel.clearUncertainty(); panel.abandonedPublication(true);
                panel.render(state(new ObservationProgress.Finished(0, false)));
                panel.writeMessage("Token publication acknowledged.");
                assertTrue(all(panel).stream().filter(JLabel.class::isInstance).map(JLabel.class::cast)
                        .anyMatch(label -> label.getText().contains("unresolved persistence status")));
            } finally { panel.closing(); }
        });
    }
    private static List<Component> all(Container root) {
        List<Component> result = new ArrayList<>();
        for (Component child : root.getComponents()) {
            result.add(child); if (child instanceof Container container) { result.addAll(all(container)); }
        }
        return result;
    }
    private static JButton button(Container root, String text) {
        return all(root).stream().filter(JButton.class::isInstance).map(JButton.class::cast)
                .filter(button -> button.getText().equals(text)).findFirst().orElseThrow();
    }
}
