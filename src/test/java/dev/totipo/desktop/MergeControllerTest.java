package dev.totipo.desktop;

import dev.totipo.*;
import dev.totipo.desktop.ui.*;
import java.util.List;
import java.util.concurrent.*;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import static dev.totipo.desktop.MergeFixtures.*;
import static dev.totipo.desktop.TestSupport.*;
import static dev.totipo.desktop.TokenWriteControllerTest.button;
import static dev.totipo.desktop.TokenWriteControllerTest.components;
import static dev.totipo.desktop.TokenWriteControllerTest.password;
import static dev.totipo.desktop.TokenWritesTest.saved;
import static org.junit.jupiter.api.Assertions.*;

class MergeControllerTest {
    static final class View extends Window {
        Runnable passwordAction;
        int passwordForms;
        @Override public void passwordAction(Runnable action) { passwordAction = action; }
        @Override public void editPassword(PasswordChangePanel panel) { passwordForms++; }
        void passwordBlocked() { passwordAction.run(); assertEquals(0, passwordForms); }
        MergeAction merge;
        Runnable create;
        EditAction edit;
        MergeEditorPanel editor;
        Runnable review;
        Runnable publish;
        Runnable cancel;
        Runnable confirm;
        Runnable retry;
        Runnable stop;
        boolean available;
        boolean sticky;
        String message;
        final BlockingQueue<String> events = new LinkedBlockingQueue<>();
        @Override public void mergeAction(MergeAction action) { merge = action; }
        @Override public void tokenActions(Runnable create, EditAction edit) { this.create = create; this.edit = edit; }
        @Override public void editMerge(MergeEditorPanel panel) {
            Edt.require(); editor = panel; events.add("editor");
        }
        @Override public void retireEditor() { Edt.require(); editor = null; }
        @Override public void writeAvailability(boolean value) {
            Edt.require(); available = value; if (value) { events.add("finished"); }
        }
        @Override public void additionalConflict(Runnable review, Runnable publish, Runnable cancel) {
            Edt.require(); this.review = review; this.publish = publish; this.cancel = cancel; events.add("decision");
        }
        @Override public void confirmOriginalResolution(Runnable action) { confirm = action; events.add("confirm"); }
        @Override public void mergePublicationUncertain(boolean original, boolean busy, Runnable retry, Runnable stop) {
            passwordBlocked();
            Edt.require(); this.retry = retry; this.stop = stop; if (!busy) { events.add("uncertain"); }
        }
        @Override public void clearUncertainty() { review = null; publish = null; cancel = null; confirm = null; retry = null; stop = null; }
        @Override public void abandonedPublication(boolean abandoned) { sticky = abandoned; }
        @Override public void writeMessage(String value) { message = value; }
    }
    static final class Harness implements AutoCloseable {
        final Session session = new Session();
        final View view = new View();
        final Recording fake = new Recording();
        final CountDownLatch retired = new CountDownLatch(1);
        final VaultWindowController controller;
        Harness() throws Exception {
            controller = onEdt(() -> new VaultWindowController(session, view, 92, c -> retired.countDown()));
            edt(() -> { controller.start(); session.subscriber.onNext(fake.state); }); edt(() -> {});
        }
        void event(String expected) throws Exception {
            assertEquals(expected, view.events.poll(10, TimeUnit.SECONDS));
            if (!expected.equals("finished")) { edt(view::passwordBlocked); }
        }
        void open() throws Exception { edt(() -> view.merge.open(fake.state, fake.token)); event("editor"); }
        void save() throws Exception {
            edt(() -> {
                button(view.editor, "Continue").doClick();
                for (var box : boxes(view.editor)) { box.setSelectedIndex(0); }
                assertTrue(button(view.editor, "Save").isEnabled()); button(view.editor, "Save").doClick();
                view.passwordBlocked();
            });
        }
        void decision(Partial partial, Recording latest) throws Exception {
            fake.results.add(new SaveResult.AdditionalConflict(latest.state, partial)); open(); save(); event("decision");
        }
        @Override public void close() {
            try { fake.release.countDown(); edt(controller::close); await(retired); edt(() -> {
                assertNull(view.editor); assertNull(view.review); assertNull(view.retry); assertFalse(view.available);
            }); } catch (Exception failure) { throw new AssertionError(failure); }
        }
    }
    static List<JComboBox<?>> boxes(java.awt.Container panel) {
        return components(panel).stream().filter(JComboBox.class::isInstance).<JComboBox<?>>map(c -> (JComboBox<?>) c).toList();
    }
    @Test void laterEmissionsDoNotRebaseEditorOrMutateBrowserOnAcknowledgement() throws Exception {
        try (Harness h = new Harness()) {
            h.fake.results.add(saved()); h.open();
            Recording later = new Recording();
            edt(() -> h.session.subscriber.onNext(later.state)); edt(() -> {});
            h.save(); h.event("finished");
            assertEquals(List.of(ID), h.fake.factories); assertTrue(later.factories.isEmpty());
            edt(() -> { assertSame(later.state, h.view.rendered.getLast()); assertEquals(2, h.view.rendered.size()); });
        }
    }
    @Test void definiteMergeFailuresKeepCapturedEditorAndRequireExplicitSave() throws Exception {
        for (SaveResult.Reason reason : SaveResult.Reason.values()) {
            try (Harness h = new Harness()) {
                h.fake.results.add(new SaveResult.Failed(reason)); h.open();
                CountDownLatch editable = new CountDownLatch(1);
                edt(() -> button(h.view.editor, "Save").addPropertyChangeListener("enabled", event -> {
                    if (Boolean.TRUE.equals(event.getNewValue()) && h.fake.calls.contains("save")) { editable.countDown(); }
                }));
                h.save();
                if (reason == SaveResult.Reason.SESSION_CLOSING) { await(h.retired); }
                else {
                    await(editable);
                    edt(() -> {
                        assertNotNull(h.view.editor); assertNull(h.view.retry); assertFalse(h.view.sticky);
                        assertTrue(MergeEditorTest.text(h.view.editor).contains("published"));
                    });
                    assertEquals(1, h.fake.factories.size());
                    h.fake.results.add(saved()); edt(() -> button(h.view.editor, "Save").doClick()); h.event("finished");
                    assertEquals(List.of(ID, ID), h.fake.factories);
                }
            }
        }
    }
    @Test void submittedNewSecretIsGoneAtDecisionAndFreshReviewDoesNotReuseIt() throws Exception {
        try (Harness h = new Harness()) {
            Partial partial = new Partial(); Recording latest = new Recording();
            h.fake.ownedSecret = new byte[1]; h.fake.results.add(new SaveResult.AdditionalConflict(latest.state, partial));
            h.open(); MergeEditorPanel old = onEdt(() -> h.view.editor);
            edt(() -> {
                button(old, "Continue").doClick(); boxes(old).forEach(box -> box.setSelectedIndex(0));
                boxes(old).getLast().setSelectedIndex(3); password(old).setText("MY"); button(old, "Save").doClick();
            });
            h.event("decision");
            assertThrows(IllegalStateException.class, () -> ((NewSecret) h.fake.values.get("secret")).copy());
            edt(() -> assertEquals(0, password(old).getPassword().length));
            edt(h.view.review); h.event("finished"); h.event("editor");
            edt(() -> {
                button(h.view.editor, "Continue").doClick(); assertEquals(-1, boxes(h.view.editor).getLast().getSelectedIndex());
                assertEquals(0, password(h.view.editor).getPassword().length);
            });
            assertEquals(0, partial.saves); assertEquals(1, partial.closes);
        }
    }

    @Test void missingSecretMappingIsInternalNonpublicationWithoutUncertainty() throws Exception {
        try (Harness h = new Harness()) {
            h.fake.missingSecret = true; h.open(); h.save(); h.event("finished");
            edt(() -> { assertTrue(h.view.message.contains("Nothing was published")); assertFalse(h.view.sticky); assertNull(h.view.retry); });
            assertFalse(h.fake.calls.contains("save")); assertTrue(h.fake.calls.contains("close"));
        }
    }
    @Test void subsetOmissionIsNotDesktopAdditionalConflictButLaterCoreConflictIsHonored() throws Exception {
        for (boolean laterConflict : List.of(false, true)) {
            try (Harness h = new Harness()) {
                var abc = h.fake.token.alternatives();
                Recording abcd = new Recording(List.of(abc.get(0), abc.get(1), abc.get(2), alternative(3)), false);
                Partial partial = new Partial();
                h.fake.results.add(laterConflict ? new SaveResult.AdditionalConflict(abcd.state, partial) : saved());
                h.open();
                edt(() -> components(h.view.editor).stream().filter(JCheckBox.class::isInstance)
                        .map(JCheckBox.class::cast).toList().get(2).doClick());
                h.save(); h.event(laterConflict ? "decision" : "finished");
                assertEquals(List.of(abc.get(0), abc.get(1)), h.fake.factories.get(0));
                assertEquals(0, partial.saves);
                if (laterConflict) {
                    edt(h.view.review); h.event("finished"); h.event("editor");
                    edt(() -> assertEquals(4, components(h.view.editor).stream().filter(JCheckBox.class::isInstance).count()));
                    assertEquals(1, partial.closes);
                }
            }
        }
    }

    @Test void additionalConflictRetiresEditorAndOwnsOnlyOneUnpublishedPartial() throws Exception {
        try (Harness h = new Harness()) {
            Partial partial = new Partial(); h.fake.closeFailure = true; h.decision(partial, new Recording());
            edt(() -> {
                assertNull(h.view.editor); assertFalse(h.view.available); assertFalse(h.view.sticky);
                h.view.create.run(); h.view.edit.open(h.fake.state, h.fake.token.alternatives().get(0), "");
                h.view.merge.open(h.fake.state, h.fake.token); assertNull(h.view.editor);
                h.session.subscriber.onNext(new Recording().state);
            });
            assertEquals(0, partial.saves); assertEquals(0, partial.closes); assertEquals(1, h.fake.factories.size());
            edt(h.view.cancel); h.event("finished");
            assertEquals(1, partial.closes); assertEquals(0, partial.saves); edt(() -> assertFalse(h.view.sticky));
        }
    }
    @Test void reviewUsesSuppliedLatestResetsSubsetChoicesAndRepeatsWithoutLimit() throws Exception {
        try (Harness h = new Harness()) {
            Recording b = new Recording(), c = new Recording(); Partial ab = new Partial(), bc = new Partial();
            h.fake.results.add(new SaveResult.AdditionalConflict(b.state, ab)); h.open();
            MergeEditorPanel original = onEdt(() -> h.view.editor);
            edt(() -> components(original).stream().filter(JCheckBox.class::isInstance).map(JCheckBox.class::cast).toList().get(2).setSelected(false));
            h.save(); h.event("decision"); assertEquals(2, ((List<?>) h.fake.factories.get(0)).size());
            edt(() -> h.session.subscriber.onNext(c.state)); edt(() -> {});
            edt(h.view.review); h.event("finished"); h.event("editor");
            assertEquals(1, ab.closes); assertEquals(0, ab.saves);
            edt(() -> {
                assertSame(c.state, h.view.rendered.getLast());
                assertTrue(components(h.view.editor).stream().filter(JCheckBox.class::isInstance).map(JCheckBox.class::cast).allMatch(JCheckBox::isSelected));
                assertEquals(0, password(original).getPassword().length);
            });
            b.results.add(new SaveResult.AdditionalConflict(c.state, bc)); h.save(); h.event("decision");
            assertEquals(List.of(ID), b.factories); edt(h.view.review); h.event("finished"); h.event("editor");
            assertEquals(1, bc.closes); assertEquals(0, bc.saves);
            c.results.add(saved()); h.save(); h.event("finished"); assertEquals(List.of(ID), c.factories);
            edt(() -> assertEquals("Merge publication acknowledged.", h.view.message));
        }
    }
    @Test void reviewAndCancelCleanupErrorsRemainDefiniteNonpublication() throws Exception {
        for (boolean review : List.of(true, false)) {
            try (Harness h = new Harness()) {
                Partial partial = new Partial(); partial.closeFailure = true; h.decision(partial, new Recording());
                edt(review ? h.view.review : h.view.cancel); h.event("finished"); if (review) { h.event("editor"); }
                edt(() -> { assertFalse(h.view.sticky); assertNull(h.view.retry); assertTrue(h.view.message.contains("cleanup error")); });
                assertEquals(0, partial.saves); assertEquals(1, partial.closes);
            }
        }
    }
    @Test void reviewWithNoRemainingConflictDoesNotManufactureMerge() throws Exception {
        try (Harness h = new Harness()) {
            Partial partial = new Partial(); Recording latest = new Recording(List.of(alternative(0)), true);
            h.decision(partial, latest); edt(h.view.review); h.event("finished");
            edt(() -> { assertNull(h.view.editor); assertTrue(h.view.message.contains("no longer")); });
            assertTrue(latest.factories.isEmpty()); assertEquals(1, partial.closes);
        }
    }
    @Test void publishOriginalRequiresConfirmationAndPreservesAllResultsDespiteCleanupFailure() throws Exception {
        for (int variant = 0; variant < 3; variant++) {
            try (Harness h = new Harness()) {
                Partial partial = new Partial(); partial.closeFailure = true;
                TokenWriteControllerTest.Handle retry = new TokenWriteControllerTest.Handle(); retry.outcomes.add(saved());
                partial.result = variant == 0 ? saved() : variant == 1 ? new SaveResult.Failed(SaveResult.Reason.PREPARATION_FAILED)
                        : new SaveResult.PublicationUncertain(retry);
                h.decision(partial, new Recording()); edt(h.view.publish); h.event("confirm");
                assertEquals(0, partial.saves); assertEquals(0, partial.closes);
                edt(h.view.confirm); h.event(variant == 2 ? "uncertain" : "finished");
                assertEquals(1, partial.saves); assertEquals(1, partial.closes); assertEquals(1, h.fake.factories.size());
                if (variant == 2) {
                    assertEquals(0, retry.calls); edt(() -> assertNull(h.view.review));
                    edt(h.view.retry); h.event("finished"); assertEquals(1, retry.calls);
                }
                int resultVariant = variant;
                edt(() -> {
                    assertFalse(h.view.sticky); assertNull(h.view.editor); assertTrue(h.view.available);
                    assertTrue(h.view.message.contains(resultVariant == 1 ? "Nothing was published" : "Original merge resolution publication acknowledged"));
                });
                assertEquals(1, partial.threads.stream().distinct().count());
                assertEquals("totipo-session-92", partial.threads.getFirst().getName());
            }
        }
    }
    @Test void everyPartialFailureRetiresHandleAndSessionClosingFollowsCloseLifecycle() throws Exception {
        for (SaveResult.Reason reason : SaveResult.Reason.values()) {
            try (Harness h = new Harness()) {
                Partial partial = new Partial(); partial.result = new SaveResult.Failed(reason);
                h.decision(partial, new Recording()); edt(h.view.publish); h.event("confirm"); edt(h.view.confirm);
                if (reason == SaveResult.Reason.SESSION_CLOSING) { await(h.retired); }
                else { h.event("finished"); edt(() -> { assertTrue(h.view.available); assertFalse(h.view.sticky); }); }
                assertEquals(1, partial.saves); assertEquals(1, partial.closes); assertEquals(1, h.fake.factories.size());
            }
        }
    }
    @Test void partialAndNormalUncertaintyReuseExactRetryAndStickyHistory() throws Exception {
        for (boolean partialSave : List.of(false, true)) {
            try (Harness h = new Harness()) {
                TokenWriteControllerTest.Handle retry = new TokenWriteControllerTest.Handle();
                if (partialSave) {
                    Partial partial = new Partial(); partial.result = new SaveResult.PublicationUncertain(retry);
                    h.decision(partial, new Recording()); edt(h.view.publish); h.event("confirm"); edt(h.view.confirm);
                } else { h.fake.results.add(new SaveResult.PublicationUncertain(retry)); h.open(); h.save(); }
                h.event("uncertain"); edt(h.view.stop); h.event("finished");
                assertEquals(0, retry.calls); assertEquals(1, retry.closes);
                edt(() -> h.session.subscriber.onNext(h.fake.state)); h.fake.results.add(saved()); h.open(); h.save(); h.event("finished");
                edt(() -> assertTrue(h.view.sticky)); assertEquals(2, h.fake.factories.size());
            }
        }
    }
    @Test void closeUnsavedEditorClearsNewSecretWithoutCreatingBuilder() throws Exception {
        try (Harness h = new Harness()) {
            h.open(); MergeEditorPanel panel = onEdt(() -> h.view.editor);
            edt(() -> { button(panel, "Continue").doClick(); var secret = boxes(panel).getLast(); secret.setSelectedIndex(secret.getItemCount() - 1);
                password(panel).setText("MY"); h.controller.close(); assertEquals(0, password(panel).getPassword().length); });
            await(h.retired); assertTrue(h.fake.calls.isEmpty());
        }
    }
    @Test void closeDuringMergeSerializesEveryResultWithoutLateUi() throws Exception {
        for (int variant = 0; variant < 4; variant++) {
            try (Harness h = new Harness()) {
                Partial partial = new Partial(); TokenWriteControllerTest.Handle retry = new TokenWriteControllerTest.Handle();
                h.fake.results.add(switch (variant) {
                    case 0 -> saved(); case 1 -> new SaveResult.AdditionalConflict(h.fake.state, partial);
                    case 2 -> new SaveResult.PublicationUncertain(retry);
                    default -> new SaveResult.Failed(SaveResult.Reason.PREPARATION_FAILED);
                });
                h.fake.release = new CountDownLatch(1); h.open(); h.save(); await(h.fake.entered);
                edt(h.controller::close); assertEquals(0, h.session.closes.get()); h.fake.release.countDown(); await(h.retired);
                edt(() -> { assertNull(h.view.editor); assertNull(h.view.review); assertNull(h.view.retry); assertTrue(h.view.events.isEmpty()); });
                assertEquals(variant == 1 ? 1 : 0, partial.closes); assertEquals(0, partial.saves);
                assertEquals(variant == 2 ? 1 : 0, retry.closes);
            }
        }
    }
    @Test void closeDuringDecisionDiscardsWithoutPublicationOrStickyHistory() throws Exception {
        try (Harness h = new Harness()) {
            Partial partial = new Partial(); h.decision(partial, new Recording()); edt(h.controller::close); await(h.retired);
            assertEquals(1, partial.closes); assertEquals(0, partial.saves); edt(() -> assertFalse(h.view.sticky));
        }
    }
    @Test void closeDuringPartialSaveSerializesEveryResultWithoutLateUi() throws Exception {
        for (int variant = 0; variant < 3; variant++) {
            try (Harness h = new Harness()) {
                Partial partial = new Partial(); TokenWriteControllerTest.Handle retry = new TokenWriteControllerTest.Handle();
                partial.result = variant == 0 ? saved() : variant == 1 ? new SaveResult.Failed(SaveResult.Reason.PREPARATION_FAILED)
                        : new SaveResult.PublicationUncertain(retry);
                partial.release = new CountDownLatch(1); h.decision(partial, new Recording());
                edt(h.view.publish); h.event("confirm"); edt(h.view.confirm); await(partial.entered);
                try { edt(h.controller::close); assertEquals(0, h.session.closes.get()); } finally { partial.release.countDown(); }
                await(h.retired); assertEquals(1, partial.saves); assertEquals(1, partial.closes);
                assertEquals(variant == 2 ? 1 : 0, retry.closes);
                edt(() -> { assertNull(h.view.retry); assertTrue(h.view.events.isEmpty()); });
            }
        }
    }
    @Test void closeDuringMergeRetryRetiresSuccessorWithoutReconstructingMerge() throws Exception {
        try (Harness h = new Harness()) {
            TokenWriteControllerTest.Handle a = new TokenWriteControllerTest.Handle(), b = new TokenWriteControllerTest.Handle();
            a.release = new CountDownLatch(1); a.outcomes.add(new SaveResult.PublicationUncertain(b));
            h.fake.results.add(new SaveResult.PublicationUncertain(a)); h.open(); h.save(); h.event("uncertain");
            edt(h.view.retry); await(a.entered);
            try { edt(h.controller::close); assertEquals(0, h.session.closes.get()); } finally { a.release.countDown(); }
            await(h.retired); assertEquals(1, a.closes); assertEquals(1, b.closes); assertEquals(0, b.calls);
            assertEquals(1, h.fake.factories.size()); edt(() -> assertTrue(h.view.events.isEmpty()));
        }
    }
}
