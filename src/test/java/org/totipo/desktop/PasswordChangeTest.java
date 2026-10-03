package org.totipo.desktop;

import org.totipo.*;
import org.totipo.desktop.ui.*;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.*;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.totipo.desktop.TestSupport.*;
import static org.totipo.desktop.TokenWriteControllerTest.*;
import static org.junit.jupiter.api.Assertions.*;

class PasswordChangeTest {
    static final class PasswordSession implements VaultSession {
        final Session lifecycle = new Session();
        final CountDownLatch entered = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        PasswordChangeResult result = PasswordChangeResult.CHANGED;
        RuntimeException failure;
        char[] current;
        char[] next;
        int calls;
        int subscriptions;
        String thread;
        @Override public PasswordChangeResult changePassword(char[] old, char[] replacement) {
            assertFalse(SwingUtilities.isEventDispatchThread());
            current = old; next = replacement; calls++; thread = Thread.currentThread().getName();
            entered.countDown(); await(release);
            if (failure != null) { throw failure; }
            return result;
        }
        @Override public void close() {
            if (current != null) { wiped(current); wiped(next); }
            lifecycle.close();
        }
        @Override public void requestRefresh() { lifecycle.requestRefresh(); }
        @Override public Flow.Publisher<VaultState> states() { subscriptions++; return lifecycle.states(); }
        @Override public VaultState state() { throw new AssertionError(); }
        @Override public VaultFingerprint fingerprint() { throw new AssertionError(); }
    }
    static final class PasswordView extends Window {
        Runnable password;
        Runnable create;
        EditAction edit;
        MergeAction merge;
        PasswordChangePanel panel;
        int forms;
        int tokenForms;
        TokenEditorPanel tokenEditor;
        Runnable retry;
        Runnable stop;
        final BlockingQueue<String> tokenEvents = new LinkedBlockingQueue<>();
        boolean available = true;
        boolean sticky = true;
        String message;
        String retirement;
        final CountDownLatch finished = new CountDownLatch(1);
        @Override public void passwordAction(Runnable action) { password = action; }
        @Override public void tokenActions(Runnable action, EditAction editAction) { create = action; edit = editAction; }
        @Override public void mergeAction(MergeAction action) { merge = action; }
        @Override public void editToken(TokenEditorPanel editor, boolean createToken) { tokenForms++; tokenEditor = editor; }
        @Override public void retireEditor() { tokenEditor = null; }
        @Override public void publicationUncertain(boolean createToken, boolean busy, Runnable retryAction, Runnable stopAction) {
            retry = retryAction; stop = stopAction; if (!busy) { tokenEvents.add("uncertain"); }
        }
        @Override public void clearUncertainty() { retry = null; stop = null; }
        @Override public void editMerge(MergeEditorPanel editor) { tokenForms++; }
        @Override public void editPassword(PasswordChangePanel editor) {
            panel = editor; forms++;
            button(panel, "Change").addPropertyChangeListener("enabled", event -> {
                if (Boolean.TRUE.equals(event.getNewValue())) { finished.countDown(); }
            });
        }
        @Override public void retirePassword() { panel = null; }
        @Override public void writeAvailability(boolean value) { available = value; if (value) { tokenEvents.add("available"); } }
        @Override public void abandonedPublication(boolean value) { sticky = value; }
        @Override public void writeMessage(String value) { message = value; finished.countDown(); }
        @Override public void retirementMessage(String value) {
            assertEquals(0, disposed.getCount()); assertTrue(closing); retirement = value;
        }
    }
    static final class PasswordHarness implements AutoCloseable {
        final PasswordSession session = new PasswordSession();
        final PasswordView view = new PasswordView();
        final CountDownLatch retired = new CountDownLatch(1);
        final VaultWindowController controller;
        PasswordHarness() throws Exception {
            controller = onEdt(() -> new VaultWindowController(session, view, 93, ignored -> retired.countDown()));
            edt(controller::start);
        }
        void open() throws Exception { edt(view.password); }
        void submit() throws Exception { edt(() -> enterAndSubmit(view.panel)); await(session.entered); }
        @Override public void close() {
            session.release.countDown();
            try { edt(controller::close); await(retired); }
            catch (Exception failure) { throw new AssertionError(failure); }
        }
    }
    static List<JPasswordField> fields(PasswordChangePanel panel) {
        return components(panel).stream().filter(JPasswordField.class::isInstance).map(JPasswordField.class::cast).toList();
    }
    static void enterAndSubmit(PasswordChangePanel panel) {
        fields(panel).get(0).setText("current"); fields(panel).get(1).setText("replacement"); fields(panel).get(2).setText("replacement");
        button(panel, "Change").doClick();
        for (var field : fields(panel)) { assertEquals(0, field.getDocument().getLength()); }
    }
    static void wiped(char[] input) {
        // Never put secret contents in assertion failure output.
        assertTrue(input != null && java.util.stream.IntStream.range(0, input.length).allMatch(i -> input[i] == '\0'),
                "Caller buffer must be wiped");
    }
    static String message(PasswordChangePanel panel) {
        return components(panel).stream().filter(JTextArea.class::isInstance).map(JTextArea.class::cast)
                .filter(area -> "Password change status".equals(area.getAccessibleContext().getAccessibleName()))
                .findFirst().orElseThrow().getText();
    }

    @ParameterizedTest @EnumSource(PasswordChangeResult.class)
    void exactResultsWipeAndPreserveSessionBoundaries(PasswordChangeResult result) throws Exception {
        try (PasswordHarness h = new PasswordHarness()) {
            h.session.result = result; h.open();
            PasswordChangePanel panel = onEdt(() -> h.view.panel);
            h.submit();
            edt(() -> { assertFalse(h.view.available); button(panel, "Change").doClick(); panel.cancel(); h.view.password.run(); });
            h.session.release.countDown();
            boolean retiring = result == PasswordChangeResult.STALE || result == PasswordChangeResult.UNCERTAIN;
            await(retiring ? h.retired : h.view.finished);
            edt(() -> {
                assertEquals(1, h.session.calls); assertEquals(1, h.view.forms); assertTrue(h.view.sticky);
                assertEquals(0, h.session.lifecycle.refreshes.get());
                if (retiring) {
                    assertTrue(h.view.closing); assertNull(h.view.panel); assertFalse(h.view.available);
                    assertTrue(h.view.retirement.contains("Reopen") || h.view.retirement.contains("reopen"));
                    assertEquals(result == PasswordChangeResult.UNCERTAIN, h.view.retirement.contains("Either the previous"));
                    if (result == PasswordChangeResult.STALE) { assertTrue(h.view.retirement.contains("did not perform")); }
                    h.view.password.run(); h.view.create.run(); assertEquals(0, h.view.tokenForms);
                } else {
                    assertFalse(h.view.closing); assertEquals(0, h.session.lifecycle.closes.get());
                    if (result == PasswordChangeResult.CHANGED) {
                        assertNull(h.view.panel); assertTrue(h.view.available);
                        assertEquals("Vault password change acknowledged.", h.view.message);
                    } else {
                        assertSame(panel, h.view.panel); assertTrue(button(panel, "Change").isEnabled());
                        assertFalse(h.view.available); assertTrue(message(panel).contains("not changed by this attempt"));
                        assertFalse(message(panel).contains("uncertain"));
                        if (result == PasswordChangeResult.AUTHENTICATION_FAILED) {
                            assertTrue(message(panel).contains("observed vault data"));
                            assertTrue(message(panel).contains("does not prove"));
                        }
                    }
                    h.view.refresh.run(); assertEquals(1, h.session.lifecycle.refreshes.get());
                }
            });
            wiped(h.session.current); wiped(h.session.next); assertEquals("totipo-session-93", h.session.thread);
        }
    }

    @ParameterizedTest @EnumSource(PasswordChangeResult.class)
    void closeDuringEveryResultNeverRestoresUiAndSerializesClose(PasswordChangeResult result) throws Exception {
        try (PasswordHarness h = new PasswordHarness()) {
            h.session.result = result; h.open(); h.submit();
            edt(() -> { h.controller.close(); h.controller.close(); assertTrue(h.view.closing); assertNull(h.view.panel); });
            assertEquals(0, h.session.lifecycle.closes.get());
            h.session.release.countDown(); await(h.retired);
            edt(() -> { assertFalse(h.view.available); assertNull(h.view.message); assertNull(h.view.retirement); assertEquals(1, h.view.forms); });
            assertEquals(1, h.session.lifecycle.closes.get()); assertEquals(h.session.thread, h.session.lifecycle.closeThread);
            wiped(h.session.current); wiped(h.session.next);
        }
    }

    @Test void unexpectedExceptionRetiresWithRedactedUnclassifiedOutcome() throws Exception {
        try (PasswordHarness h = new PasswordHarness()) {
            h.session.failure = new IllegalStateException("private diagnostic marker");
            h.open(); h.submit(); h.session.release.countDown(); await(h.retired);
            edt(() -> {
                assertTrue(h.view.retirement.contains("cannot infer"));
                assertFalse(h.view.retirement.contains("private diagnostic marker"));
                assertFalse(h.view.retirement.contains("Either")); assertFalse(h.view.available);
            });
            wiped(h.session.current); wiped(h.session.next); assertEquals(1, h.session.calls);
        }
    }

    @Test void closedSessionUsesCloseLifecycleWithoutPasswordError() throws Exception {
        try (PasswordHarness h = new PasswordHarness()) {
            h.session.failure = new SessionClosedException();
            h.open(); h.submit(); h.session.release.countDown(); await(h.retired);
            edt(() -> { assertNull(h.view.retirement); assertNull(h.view.message); assertNull(h.view.panel); });
            wiped(h.session.current); wiped(h.session.next);
        }
    }

    @Test void unsentCancelAndWindowCloseClearDocumentsWithoutCoreCalls() throws Exception {
        for (boolean close : List.of(false, true)) {
            try (PasswordHarness h = new PasswordHarness()) {
                h.open();
                edt(() -> {
                    PasswordChangePanel panel = h.view.panel;
                    for (var field : fields(panel)) { field.setText("unsent"); }
                    if (close) { h.controller.close(); } else { panel.cancel(); }
                    for (var field : fields(panel)) { assertEquals(0, field.getDocument().getLength()); }
                    assertNull(h.view.panel); assertEquals(!close, h.view.available);
                });
                assertEquals(0, h.session.calls);
            }
        }
    }

    @Test void passwordSlotBlocksEveryTokenEntryAndAllowsStateAndRefresh() throws Exception {
        try (PasswordHarness h = new PasswordHarness()) {
            var recording = new MergeFixtures.Recording();
            h.open();
            for (boolean submitted : List.of(false, true)) {
                if (submitted) { h.submit(); }
                edt(() -> {
                    h.session.lifecycle.subscriber.onNext(recording.state);
                    h.view.create.run(); h.view.edit.open(recording.state, recording.token.alternatives().get(0), "");
                    h.view.merge.open(recording.state, recording.token); h.view.password.run(); h.view.refresh.run();
                    assertFalse(h.view.available); assertEquals(0, h.view.tokenForms); assertEquals(1, h.view.forms);
                });
                edt(() -> assertSame(recording.state, h.view.rendered.getLast()));
            }
            assertEquals(2, h.session.lifecycle.refreshes.get());
            assertEquals(0, h.session.lifecycle.subscription.cancels.get());
            assertEquals(3, h.session.lifecycle.subscription.requests.size());
            assertEquals(1, h.session.subscriptions);
        }
    }

    @Test void definiteFailuresAllowOnlyFreshExplicitSubmission() throws Exception {
        for (PasswordChangeResult outcome : List.of(PasswordChangeResult.AUTHENTICATION_FAILED, PasswordChangeResult.FAILED)) {
            try (PasswordHarness h = new PasswordHarness()) {
                h.session.result = outcome; h.open(); h.submit(); h.session.release.countDown(); await(h.view.finished);
                edt(() -> {
                    for (var field : fields(h.view.panel)) { assertEquals(0, field.getDocument().getLength()); }
                    assertEquals(1, h.session.calls);
                    h.session.result = PasswordChangeResult.CHANGED;
                    enterAndSubmit(h.view.panel);
                });
                // Queue a close behind the second operation; it must still run and wipe.
                edt(h.controller::close); await(h.retired); assertEquals(2, h.session.calls);
                wiped(h.session.current); wiped(h.session.next);
            }
        }
    }

    @ParameterizedTest @EnumSource(PasswordChangeResult.class)
    void resultKnowledgeSurvivesClose(PasswordChangeResult result) throws Exception {
        PasswordSession session = new PasswordSession(); session.result = result;
        var executor = Executors.newSingleThreadExecutor();
        PasswordView view = new PasswordView();
        var gate = onEdt(() -> new MutationGate(view::writeAvailability));
        var controller = onEdt(() -> new PasswordChangeController(session, executor, view, gate, ignored -> {}));
        try {
            edt(() -> { controller.open(); enterAndSubmit(view.panel); controller.closing(); });
            session.release.countDown(); executor.submit(() -> {}).get(10, TimeUnit.SECONDS);
            edt(() -> { assertEquals(result, controller.lastResult()); assertFalse(controller.internalFailure()); });
        } finally { session.release.countDown(); executor.shutdown(); }
    }

    @Test void confirmationAndRejectedBuffersAreWipedWithoutCoreCall() {
        char[] old = {'a'}, next = {'b'}, confirmation = {'b'};
        try (var submission = PasswordChangeSubmission.prepare(old, next, confirmation)) {
            assertNotNull(submission);
            wiped(confirmation); assertTrue(old[0] != 0 && next[0] != 0);
        }
        wiped(old); wiped(next);
        char[] a = {'a'}, b = {'b'}, mismatch = {'c'};
        assertThrows(IllegalArgumentException.class, () -> PasswordChangeSubmission.prepare(a, b, mismatch));
        wiped(a); wiped(b); wiped(mismatch);
    }
    @Test void abandonedTokenCapabilityAllowsPasswordChangeWithoutClearingStickyHistory() throws Exception {
        try (PasswordHarness h = new PasswordHarness()) {
            var recording = new TokenWritesTest.Recording();
            var retry = new TokenWriteControllerTest.Handle();
            recording.results.add(new SaveResult.PublicationUncertain(retry));
            edt(() -> { h.view.sticky = false; h.session.lifecycle.subscriber.onNext(recording.state); });
            edt(() -> {
                h.view.create.run(); h.view.password.run(); assertNull(h.view.panel);
                password(h.view.tokenEditor).setText("MY"); button(h.view.tokenEditor, "Save").doClick();
            });
            assertEquals("uncertain", h.view.tokenEvents.poll(10, TimeUnit.SECONDS));
            edt(() -> { h.view.password.run(); assertNull(h.view.panel); h.view.stop.run(); });
            assertEquals("available", h.view.tokenEvents.poll(10, TimeUnit.SECONDS));
            edt(() -> assertTrue(h.view.sticky));
            h.open(); h.submit(); h.session.release.countDown(); await(h.view.finished);
            edt(() -> { assertTrue(h.view.sticky); assertNull(h.view.panel); assertTrue(h.view.available); });
            assertEquals(0, retry.calls); assertEquals(1, retry.closes);
        }
    }

    @ParameterizedTest @EnumSource(PasswordChangeResult.class)
    void applicationShutdownWaitsWithoutReopeningOrResultUi(PasswordChangeResult result) throws Exception {
        PasswordSession session = new PasswordSession(); session.result = result;
        PasswordView view = new PasswordView(); Launcher launcher = new Launcher();
        java.util.concurrent.atomic.AtomicInteger opens = new java.util.concurrent.atomic.AtomicInteger();
        VaultAccess access = new VaultAccess() {
            public OpenResult open(java.nio.file.Path path, char[] password) { opens.incrementAndGet(); return new OpenResult.Opened(session); }
            public CreateVaultResult create(java.nio.file.Path path, char[] password) { throw new AssertionError(); }
        };
        DesktopApplication app = onEdt(() -> new DesktopApplication(access, launcher, path -> view));
        try {
            edt(() -> app.begin(java.nio.file.Path.of("test-vault"), new char[0], false)); await(launcher.ready);
            edt(() -> { view.password.run(); enterAndSubmit(view.panel); }); await(session.entered);
            edt(() -> { app.shutdown(); assertTrue(view.closing); assertFalse(app.executorShutdown()); });
            assertEquals(0, session.lifecycle.closes.get()); assertEquals(1, launcher.disposed.getCount());
            session.release.countDown(); await(launcher.disposed);
            edt(() -> { assertNull(view.panel); assertFalse(view.available); assertNull(view.message); assertTrue(launcher.messages.isEmpty()); });
            assertEquals(1, opens.get()); assertEquals(1, session.calls); assertEquals(1, session.lifecycle.closes.get());
            assertEquals(session.thread, session.lifecycle.closeThread); wiped(session.current); wiped(session.next);
        } finally { session.release.countDown(); edt(app::shutdown); await(launcher.disposed); }
    }

    @Test void updateEditorAndPartialSaveAlsoExcludePasswordWorkflow() throws Exception {
        try (TokenWriteControllerTest.Harness h = new TokenWriteControllerTest.Harness()) {
            edt(() -> h.view.edit.open(h.recording.state, MergeFixtures.alternative(0), ""));
            h.event("editor"); edt(h.view::passwordBlocked);
        }
        try (MergeControllerTest.Harness h = new MergeControllerTest.Harness()) {
            var partial = new MergeFixtures.Partial(); partial.result = TokenWritesTest.saved();
            partial.release = new CountDownLatch(1);
            try {
                h.decision(partial, new MergeFixtures.Recording());
                edt(h.view.publish); h.event("confirm"); edt(h.view.confirm); await(partial.entered);
                edt(h.view::passwordBlocked);
            } finally { partial.release.countDown(); }
            h.event("finished");
        }
    }
    @ParameterizedTest @EnumSource(PasswordChangeResult.class)
    void typedKnowledgeSurvivesDialogCleanupFailure(PasswordChangeResult result) throws Exception {
        PasswordSession session = new PasswordSession(); session.result = result;
        var executor = Executors.newSingleThreadExecutor();
        java.util.concurrent.atomic.AtomicReference<PasswordChangePanel> panel = new java.util.concurrent.atomic.AtomicReference<>();
        VaultView view = new Window() {
            @Override public void editPassword(PasswordChangePanel value) { panel.set(value); }
            @Override public void retirePassword() { throw new IllegalStateException("private cleanup marker"); }
        };
        var controller = onEdt(() -> new PasswordChangeController(session, executor, view,
                new MutationGate(view::writeAvailability), ignored -> {}));
        try {
            edt(() -> { controller.open(); enterAndSubmit(panel.get()); });
            session.release.countDown(); executor.submit(() -> {}).get(10, TimeUnit.SECONDS);
            edt(() -> {
                if (result != PasswordChangeResult.CHANGED) { assertThrows(IllegalStateException.class, controller::closing); }
                assertEquals(result, controller.lastResult()); assertFalse(controller.internalFailure());
            });
            wiped(session.current); wiped(session.next);
        } finally { session.release.countDown(); executor.shutdown(); }
    }

    @Test void unexpectedFailureHasNoTypedResultEvenDuringClose() throws Exception {
        for (boolean closeFirst : List.of(false, true)) {
            PasswordSession session = new PasswordSession(); session.failure = new IllegalStateException("private marker");
            var executor = Executors.newSingleThreadExecutor(); PasswordView view = new PasswordView();
            var controller = onEdt(() -> new PasswordChangeController(session, executor, view,
                    new MutationGate(view::writeAvailability), ignored -> {}));
            try {
                edt(() -> { controller.open(); enterAndSubmit(view.panel); if (closeFirst) { controller.closing(); } });
                session.release.countDown(); executor.submit(() -> {}).get(10, TimeUnit.SECONDS);
                edt(() -> { assertNull(controller.lastResult()); assertTrue(controller.internalFailure()); controller.closing(); });
                wiped(session.current); wiped(session.next);
            } finally { session.release.countDown(); executor.shutdown(); }
        }
    }

    @ParameterizedTest @EnumSource(value = PasswordChangeResult.class, names = {"STALE", "UNCERTAIN"})
    void retirementMessageReachesLauncherAndReopenIsExplicitNewSession(PasswordChangeResult result) throws Exception {
        PasswordSession first = new PasswordSession(); first.result = result;
        PasswordSession second = new PasswordSession();
        Launcher launcher = new Launcher();
        java.util.List<PasswordView> views = new java.util.ArrayList<>();
        var opens = new java.util.concurrent.atomic.AtomicInteger();
        CountDownLatch message = new CountDownLatch(1);
        CountDownLatch reopened = new CountDownLatch(1);
        VaultAccess access = new VaultAccess() {
            public OpenResult open(java.nio.file.Path path, char[] password) {
                return new OpenResult.Opened(opens.getAndIncrement() == 0 ? first : second);
            }
            public CreateVaultResult create(java.nio.file.Path path, char[] password) { throw new AssertionError(); }
        };
        DesktopApplication app = onEdt(() -> new DesktopApplication(access, launcher, path -> {
            var view = new PasswordView(); views.add(view); if (views.size() == 2) { reopened.countDown(); } return view;
        }));
        try {
            edt(() -> {
                launcher.duringMessage = () -> {
                    assertEquals(1, first.lifecycle.closes.get()); assertEquals(0, views.getFirst().disposed.getCount());
                    message.countDown();
                };
                app.begin(java.nio.file.Path.of("same-directory"), new char[0], false);
            });
            await(launcher.ready);
            edt(() -> { views.getFirst().password.run(); enterAndSubmit(views.getFirst().panel); });
            first.release.countDown(); await(message);
            edt(() -> {
                assertEquals(1, opens.get()); assertEquals(1, views.size());
                assertTrue(launcher.messages.getFirst().contains("reopen") || launcher.messages.getFirst().contains("Reopen"));
                app.begin(java.nio.file.Path.of("same-directory"), new char[]{'f', 'r', 'e', 's', 'h'}, false);
            });
            await(reopened);
            edt(() -> { assertEquals(2, opens.get()); assertFalse(views.getLast().closing); assertTrue(views.getLast().rendered.isEmpty()); });
            assertEquals(1, second.subscriptions); assertEquals(0, second.calls);
            wiped(first.current); wiped(first.next);
        } finally { first.release.countDown(); second.release.countDown(); edt(app::shutdown); await(launcher.disposed); }
    }
}
