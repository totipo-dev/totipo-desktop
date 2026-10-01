package dev.totipo.desktop;

import dev.totipo.*;
import dev.totipo.desktop.ui.*;
import java.awt.Component;
import java.awt.Container;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import static dev.totipo.desktop.TestSupport.*;
import static dev.totipo.desktop.TokenWritesTest.*;
import static org.junit.jupiter.api.Assertions.*;

class TokenWriteControllerTest {
    static final class View extends Window {
        Runnable passwordAction;
        int passwordForms;
        @Override public void passwordAction(Runnable action) { passwordAction = action; }
        @Override public void editPassword(PasswordChangePanel panel) { passwordForms++; }
        void passwordBlocked() { passwordAction.run(); assertEquals(0, passwordForms); }
        Runnable create;
        EditAction edit;
        TokenEditorPanel editor;
        Runnable retry;
        Runnable stop;
        boolean available;
        boolean sticky;
        String message;
        final BlockingQueue<String> events = new LinkedBlockingQueue<>();
        @Override public void tokenActions(Runnable create, EditAction edit) { this.create = create; this.edit = edit; }
        @Override public void editToken(TokenEditorPanel editor, boolean create) {
            Edt.require(); this.editor = editor; events.add("editor");
            button(editor, "Save").addPropertyChangeListener("enabled", event -> {
                if (Boolean.TRUE.equals(event.getNewValue())) { events.add("editable"); }
            });
        }
        @Override public void retireEditor() { Edt.require(); editor = null; }
        @Override public void writeAvailability(boolean value) {
            Edt.require(); available = value; if (value) { events.add("finished"); }
        }
        @Override public void publicationUncertain(boolean create, boolean busy, Runnable retry, Runnable stop) {
            passwordBlocked();
            Edt.require(); this.retry = retry; this.stop = stop;
            if (!busy) { events.add("uncertain"); }
        }
        @Override public void clearUncertainty() { Edt.require(); retry = null; stop = null; }
        @Override public void abandonedPublication(boolean abandoned) { Edt.require(); sticky = abandoned; }
        @Override public void writeMessage(String value) { Edt.require(); message = value; }
    }
    static final class Handle implements PublicationRetry {
        final QueueResult outcomes = new QueueResult();
        int calls;
        int closes;
        final List<Thread> threads = new ArrayList<>();
        final CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(0);
        boolean fail;
        boolean closeFailure;
        @Override public RetryResult retryPublication() {
            assertFalse(SwingUtilities.isEventDispatchThread()); threads.add(Thread.currentThread()); calls++;
            assertEquals(1, calls, "Consumed handle reused"); entered.countDown(); await(release);
            if (fail) { throw new IllegalStateException(); }
            return outcomes.remove();
        }
        @Override public void close() {
            assertFalse(SwingUtilities.isEventDispatchThread()); threads.add(Thread.currentThread()); closes++;
            if (closeFailure) { throw new IllegalStateException("private cleanup details"); }
        }
    }
    static final class QueueResult extends java.util.concurrent.ConcurrentLinkedQueue<RetryResult> {
        private static final long serialVersionUID = 1L;
    }
    static final class Harness implements AutoCloseable {
        final Session session = new Session();
        final View view = new View();
        final Recording recording = new Recording();
        final CountDownLatch retired = new CountDownLatch(1);
        final VaultWindowController controller;
        Harness() throws Exception {
            controller = onEdt(() -> new VaultWindowController(session, view, 91, owner -> retired.countDown()));
            edt(() -> { controller.start(); session.subscriber.onNext(recording.state); });
            edt(() -> {});
        }
        void open() throws Exception { edt(view.create); event("editor"); }
        void save() throws Exception {
            edt(() -> { password(view.editor).setText("MY"); button(view.editor, "Save").doClick(); view.passwordBlocked(); });
        }
        void event(String expected) throws Exception {
            assertEquals(expected, view.events.poll(10, TimeUnit.SECONDS));
            if (!expected.equals("finished")) { edt(view::passwordBlocked); }
        }
        @Override public void close() {
            try {
                recording.release.countDown(); edt(controller::close); await(retired);
                edt(() -> { assertNull(view.editor); assertNull(view.retry); assertFalse(view.available); });
            } catch (Exception failure) { throw new AssertionError(failure); }
        }
    }
    static List<Component> components(Container root) {
        List<Component> all = new ArrayList<>();
        for (Component child : root.getComponents()) {
            all.add(child); if (child instanceof Container container) { all.addAll(components(container)); }
        }
        return all;
    }
    static JButton button(Container root, String label) {
        return components(root).stream().filter(c -> c instanceof JButton b && b.getText().equals(label))
                .map(JButton.class::cast).findFirst().orElseThrow();
    }
    static JPasswordField password(Container root) {
        return components(root).stream().filter(JPasswordField.class::isInstance).map(JPasswordField.class::cast).findFirst().orElseThrow();
    }
    @Test void savedUsesSessionExecutorAndRepeatedClicksCannotQueue() throws Exception {
        try (Harness h = new Harness()) {
            h.recording.results.add(saved()); h.recording.release = new CountDownLatch(1);
            h.open(); TokenEditorPanel panel = onEdt(() -> h.view.editor); h.save(); await(h.recording.entered);
            edt(() -> { button(panel, "Save").doClick(); h.view.create.run(); assertFalse(h.view.available); });
            h.recording.release.countDown(); h.event("finished");
            edt(() -> { assertNull(h.view.editor); assertTrue(h.view.available); assertFalse(h.view.sticky); });
            assertEquals(1, h.recording.calls.stream().filter("createToken"::equals).count());
            assertTrue(h.recording.threads.stream().allMatch(t -> t.getName().equals("totipo-session-91")));
        }
    }
    @Test void everyDefiniteFailureHasDeliberateBehaviorAndExplicitNewSave() throws Exception {
        for (SaveResult.Reason reason : SaveResult.Reason.values()) {
            try (Harness h = new Harness()) {
                h.recording.results.add(new SaveResult.Failed(reason)); h.open(); h.save();
                if (reason == SaveResult.Reason.SESSION_CLOSING) {
                    await(h.retired); edt(() -> { assertNull(h.view.editor); assertFalse(h.view.available); });
                } else {
                    h.event("editable");
                    edt(() -> {
                        assertNotNull(h.view.editor); assertNull(h.view.retry);
                        assertTrue(components(h.view.editor).stream().filter(JTextArea.class::isInstance)
                                .map(JTextArea.class::cast).anyMatch(label -> label.getText().contains("published")));
                    });
                    h.recording.results.add(saved()); h.save(); h.event("finished");
                    assertEquals(2, h.recording.calls.stream().filter("createToken"::equals).count());
                }
            }
        }
    }
    @Test void savedRemainsAcknowledgedWhenBuilderCloseThrows() throws Exception {
        try (Harness h = new Harness()) {
            h.recording.closeFailure = true; h.recording.results.add(saved());
            h.open(); h.save(); h.event("finished");
            edt(() -> {
                assertEquals("Token publication acknowledged.", h.view.message);
                assertFalse(h.view.sticky); assertNull(h.view.editor); assertNull(h.view.retry);
                assertTrue(h.view.available);
            });
            assertEquals(1, h.recording.calls.stream().filter("close"::equals).count());
        }
    }
    @Test void uncertainRetainsIndependentRetryWhenBuilderCloseThrows() throws Exception {
        try (Harness h = new Harness()) {
            Handle handle = new Handle(); handle.outcomes.add(saved());
            h.recording.closeFailure = true;
            h.recording.results.add(new SaveResult.PublicationUncertain(handle));
            h.open(); h.save(); h.event("uncertain");
            edt(() -> {
                assertNotNull(h.view.retry); assertNull(h.view.editor); assertFalse(h.view.available);
                assertFalse(h.view.sticky); // Still live, not abandoned.
            });
            assertEquals(0, handle.calls); assertEquals(0, handle.closes);
            edt(h.view.retry); h.event("finished");
            edt(() -> { assertEquals("Token publication acknowledged.", h.view.message); assertFalse(h.view.sticky); });
            assertEquals(1, handle.calls); assertEquals(1, handle.closes);
            assertEquals(1, h.recording.calls.stream().filter("createToken"::equals).count());
        }
    }
    @Test void definiteFailureRemainsDefiniteWhenBuilderCloseThrows() throws Exception {
        try (Harness h = new Harness()) {
            h.recording.closeFailure = true;
            h.recording.results.add(new SaveResult.Failed(SaveResult.Reason.PREPARATION_FAILED));
            h.open(); h.save(); h.event("editable");
            edt(() -> {
                assertNotNull(h.view.editor); assertNull(h.view.retry); assertFalse(h.view.sticky);
                assertTrue(components(h.view.editor).stream().filter(JTextArea.class::isInstance)
                        .map(JTextArea.class::cast).anyMatch(text -> text.getText().contains("Nothing was published.")));
            });
        }
    }
    @Test void stopRemainsStickyWhenCapabilityCloseThrows() throws Exception {
        try (Harness h = new Harness()) {
            Handle handle = new Handle(); handle.closeFailure = true;
            h.recording.results.add(new SaveResult.PublicationUncertain(handle));
            h.open(); h.save(); h.event("uncertain"); edt(h.view.stop); h.event("finished");
            edt(() -> {
                assertTrue(h.view.sticky); assertNull(h.view.retry); assertTrue(h.view.available);
                assertEquals("Internal operation cleanup failure; publication remains uncertain.", h.view.message);
            });
            assertEquals(0, handle.calls); assertEquals(1, handle.closes);
            assertTrue(handle.threads.stream().allMatch(thread -> thread == h.recording.threads.get(0)));
            h.recording.results.add(saved()); h.open(); h.save(); h.event("finished");
            edt(() -> assertTrue(h.view.sticky));
        }
    }
    @Test void retryResultSurvivesConsumedCapabilityCloseFailure() throws Exception {
        try (Harness h = new Harness()) {
            Handle a = new Handle(); Handle b = new Handle();
            a.closeFailure = true; b.closeFailure = true;
            a.outcomes.add(new SaveResult.PublicationUncertain(b)); b.outcomes.add(saved());
            h.recording.results.add(new SaveResult.PublicationUncertain(a));
            h.open(); h.save(); h.event("uncertain");
            edt(h.view.retry); h.event("uncertain");
            assertEquals(1, a.closes); assertEquals(0, b.closes);
            edt(h.view.retry); h.event("finished");
            edt(() -> { assertEquals("Token publication acknowledged.", h.view.message); assertFalse(h.view.sticky); });
            assertEquals(1, b.calls); assertEquals(1, b.closes);
        }
    }
    @Test void exactRetryTransfersThroughTwoSuccessorsThenSavedWithoutStickyNotice() throws Exception {
        try (Harness h = new Harness()) {
            Handle a = new Handle(); Handle b = new Handle(); Handle c = new Handle();
            a.outcomes.add(new SaveResult.PublicationUncertain(b)); b.outcomes.add(new SaveResult.PublicationUncertain(c));
            c.outcomes.add(saved()); h.recording.results.add(new SaveResult.PublicationUncertain(a));
            h.open(); h.save(); h.event("uncertain"); assertEquals(0, a.calls);
            edt(() -> { assertNull(h.view.editor); assertFalse(h.view.available); h.session.subscriber.onNext(h.recording.state); });
            edt(() -> { assertNotNull(h.view.retry); assertFalse(h.view.sticky); }); assertEquals(0, a.calls);
            edt(h.view.retry); h.event("uncertain"); assertEquals(1, a.calls); assertEquals(0, b.calls);
            edt(h.view.retry); h.event("uncertain"); assertEquals(1, b.calls); assertEquals(0, c.calls);
            edt(h.view.retry); h.event("finished");
            edt(() -> { assertNull(h.view.retry); assertFalse(h.view.sticky); });
            assertEquals(1, h.recording.calls.stream().filter("createToken"::equals).count());
            for (Handle handle : List.of(a, b, c)) {
                assertEquals(1, handle.calls); assertEquals(1, handle.closes);
                assertTrue(handle.threads.stream().allMatch(t -> t == h.recording.threads.get(0)));
            }
        }
    }
    @Test void stopAbandonsAndLaterObservationRefreshAndSavedDoNotClearWarning() throws Exception {
        try (Harness h = new Harness()) {
            Handle handle = new Handle(); h.recording.results.add(new SaveResult.PublicationUncertain(handle));
            h.open(); h.save(); h.event("uncertain"); edt(h.view.stop); h.event("finished");
            assertEquals(0, handle.calls); assertEquals(1, handle.closes);
            edt(() -> { assertTrue(h.view.sticky); h.view.refresh.run(); h.session.subscriber.onNext(h.recording.state); });
            h.recording.results.add(saved()); h.open(); h.save(); h.event("finished");
            edt(() -> assertTrue(h.view.sticky));
        }
    }
    @Test void capturedUpdateIgnoresNewStateAndRetainsSelectedAlternativeWithoutSecretReplacement() throws Exception {
        try (Harness h = new Harness()) {
            var fields = new TokenDescriptor(TokenStatus.TOMBSTONED, "old", "account", TotpAlgorithm.SHA1, 6, java.time.Duration.ofSeconds(30));
            TokenAlternative selected = (TokenAlternative) Proxy.newProxyInstance(TokenAlternative.class.getClassLoader(),
                    new Class<?>[]{TokenAlternative.class}, (p, m, a) -> fields);
            h.recording.expected = selected; h.recording.results.add(saved());
            edt(() -> h.view.edit.open(h.recording.state, selected, "Alternative 2 only; does not resolve the other alternatives."));
            h.event("editor");
            edt(() -> h.session.subscriber.onNext(state(new ObservationProgress.Finished(0, false))));
            edt(() -> {
                components(h.view.editor).stream().filter(JComboBox.class::isInstance).map(JComboBox.class::cast)
                        .filter(box -> box.getSelectedItem() == TokenStatus.TOMBSTONED).findFirst().orElseThrow().setSelectedItem(TokenStatus.ACTIVE);
                button(h.view.editor, "Save").doClick();
            });
            h.event("finished"); assertEquals("update", h.recording.calls.get(0));
            assertEquals(TokenStatus.ACTIVE, h.recording.values.get("status")); assertFalse(h.recording.calls.contains("secret"));
        }
    }
    @Test void unsavedCloseClearsFieldsWithoutBuilder() throws Exception {
        try (Harness h = new Harness()) {
            h.open(); TokenEditorPanel panel = onEdt(() -> h.view.editor);
            edt(() -> { password(panel).setText("MY"); h.controller.close(); assertEquals(0, password(panel).getPassword().length); });
            await(h.retired); assertTrue(h.recording.calls.isEmpty());
        }
    }
    @Test void cancelRestoresActionsAndDoesNotCreateBuilder() throws Exception {
        try (Harness h = new Harness()) {
            h.open(); TokenEditorPanel panel = onEdt(() -> h.view.editor);
            edt(() -> { password(panel).setText("MY"); button(panel, "Cancel").doClick(); });
            h.event("finished");
            edt(() -> { assertNull(h.view.editor); assertTrue(h.view.available); assertEquals(0, password(panel).getPassword().length); });
            assertTrue(h.recording.calls.isEmpty());
        }
    }
    @Test void closeDuringSaveSuppressesLateSavedAndUncertainAndCleansHandle() throws Exception {
        for (boolean uncertain : new boolean[]{false, true}) {
            try (Harness h = new Harness()) {
                Handle handle = new Handle();
                h.recording.results.add(uncertain ? new SaveResult.PublicationUncertain(handle) : saved());
                h.recording.release = new CountDownLatch(1); h.open(); h.save(); await(h.recording.entered);
                edt(h.controller::close); assertEquals(0, h.session.closes.get());
                h.recording.release.countDown(); await(h.retired);
                edt(() -> { assertNull(h.view.editor); assertNull(h.view.retry); assertTrue(h.view.events.isEmpty()); });
                assertEquals(uncertain ? 1 : 0, handle.closes); assertEquals(0, handle.calls);
                assertEquals("totipo-session-91", h.session.closeThread);
            }
        }
    }
    @Test void closeDuringRetryRetiresSuccessorWithoutUiOrInterruption() throws Exception {
        try (Harness h = new Harness()) {
            Handle a = new Handle(); Handle b = new Handle();
            a.release = new CountDownLatch(1); a.outcomes.add(new SaveResult.PublicationUncertain(b));
            h.recording.results.add(new SaveResult.PublicationUncertain(a));
            h.open(); h.save(); h.event("uncertain"); edt(h.view.retry); await(a.entered);
            try {
                edt(h.controller::close); assertEquals(0, h.session.closes.get());
            } finally { a.release.countDown(); }
            await(h.retired);
            assertEquals(1, a.calls); assertEquals(1, a.closes); assertEquals(0, b.calls); assertEquals(1, b.closes);
            edt(() -> { assertNull(h.view.retry); assertTrue(h.view.events.isEmpty()); });
        }
    }
    @Test void defensiveAdditionalConflictOnlyClosesCapability() throws Exception {
        try (Harness h = new Harness()) {
            CountDownLatch closed = new CountDownLatch(1);
            PartialResolution resolution = (PartialResolution) Proxy.newProxyInstance(PartialResolution.class.getClassLoader(),
                    new Class<?>[]{PartialResolution.class}, (p, method, args) -> {
                        assertEquals("close", method.getName()); assertFalse(SwingUtilities.isEventDispatchThread());
                        closed.countDown(); return null;
                    });
            h.recording.results.add(new SaveResult.AdditionalConflict(h.recording.state, resolution));
            h.open(); h.save(); h.event("finished"); await(closed);
            edt(() -> { assertNull(h.view.retry); assertEquals("Internal token operation failure.", h.view.message); });
        }
    }
    @Test void unexpectedRuntimeDoesNotEscapeEdtAndRetryExceptionPreservesUncertaintyHistory() throws Exception {
        try (Harness h = new Harness()) {
            h.recording.runtimeFailure = true; h.open(); h.save(); h.event("finished");
            edt(() -> assertTrue(h.view.message.startsWith("Internal")));
            h.recording.runtimeFailure = false;
            Handle handle = new Handle(); handle.fail = true;
            h.recording.results.add(new SaveResult.PublicationUncertain(handle));
            h.open(); h.save(); h.event("uncertain"); edt(h.view.retry); h.event("finished");
            edt(() -> assertTrue(h.view.sticky)); assertEquals(1, handle.closes);
        }
    }
}
