package org.totipo.desktop;

import org.totipo.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import static org.totipo.desktop.MergeFixtures.*;
import static org.totipo.desktop.TokenWritesTest.saved;
import static org.totipo.desktop.TokenWritesTest.FIELDS;
import static org.junit.jupiter.api.Assertions.*;

class MergeWritesTest {
    @Test void fullAndSubsetFactoriesUseFrozenReceivingStateAndAllCallsStayOnOneThread() throws Exception {
        for (boolean full : List.of(true, false)) {
            Recording fake = new Recording(); fake.results.add(saved());
            MergeInputs inputs = full ? fake.inputs() : fake.inputs().select(List.of(fake.token.alternatives().get(2), fake.token.alternatives().get(0)));
            try (var executor = Executors.newSingleThreadExecutor()) {
                assertInstanceOf(SaveResult.Saved.class, executor.submit(() -> MergeWrites.save(fake.draft(inputs))).get());
            }
            assertEquals(List.of("merge", "competingValues", "secretChoices", "unresolvedFields", "secret",
                    "status", "issuer", "account", "algorithm", "digits", "period", "unresolvedFields", "save", "close"), fake.calls);
            assertEquals(1, new HashSet<>(fake.threads).size());
            if (full) { assertSame(fake.token.id(), fake.factories.get(0)); }
            else { assertEquals(inputs.selected(), fake.factories.get(0)); assertEquals(2, ((List<?>) fake.factories.get(0)).size()); }
            assertSame(fake.issued.get(0).stream().filter(c -> c.alternatives().contains(inputs.selected().get(0))).findFirst().orElseThrow(), fake.used.get(0));
        }
    }
    @Test void choicesAreMappedFreshForEveryBuilderAndInvalidMappingNeverPublishes() throws Exception {
        Recording fake = new Recording();
        try (var executor = Executors.newSingleThreadExecutor()) {
            for (int i = 0; i < 2; i++) {
                fake.results.add(saved()); executor.submit(() -> MergeWrites.save(fake.draft(fake.inputs()))).get();
            }
            assertNotSame(fake.used.get(0), fake.used.get(1));
            for (int mode = 0; mode < 3; mode++) {
                Recording invalid = new Recording(); invalid.missingSecret = mode == 0;
                invalid.ambiguousSecret = mode == 1; invalid.unresolved = mode == 2;
                assertThrows(ExecutionException.class, () -> executor.submit(() -> MergeWrites.save(invalid.draft(invalid.inputs()))).get());
                assertFalse(invalid.calls.contains("save")); assertEquals("close", invalid.calls.getLast());
            }
        }
    }
    @Test void newSecretWipedBeforeBuilderCopyAndNoPlaintextSurvivesAdditionalConflictOrRetry() throws Exception {
        for (boolean uncertain : List.of(false, true)) {
            Recording fake = new Recording(); Partial partial = new Partial();
            var retry = new TokenWriteControllerTest.Handle();
            SaveResult expected = uncertain ? new SaveResult.PublicationUncertain(retry)
                    : new SaveResult.AdditionalConflict(fake.state, partial);
            fake.results.add(expected);
            byte[] secret = {102}; fake.ownedSecret = secret;
            try (var executor = Executors.newSingleThreadExecutor()) {
                var result = executor.submit(() -> MergeWrites.save(new MergeDraft(fake.inputs(), FIELDS, null, secret))).get();
                assertSame(expected, result);
            }
            assertArrayEquals(new byte[1], secret);
            assertThrows(IllegalStateException.class, () -> ((NewSecret) fake.values.get("secret")).copy());
            assertTrue(fake.used.isEmpty()); assertEquals(0, partial.closes); assertEquals(0, partial.saves);
            assertEquals(0, retry.calls); assertEquals(0, retry.closes);
        }
    }
    @Test void cleanupPreservesEveryReturnedResultIncludingIndependentlyOwnedPartial() throws Exception {
        Recording fake = new Recording(); Partial partial = new Partial(); fake.closeFailure = true;
        var retry = new TokenWriteControllerTest.Handle();
        List<SaveResult> results = List.of(saved(), new SaveResult.Failed(SaveResult.Reason.OBSERVATION_UNAVAILABLE),
                new SaveResult.AdditionalConflict(fake.state, partial), new SaveResult.PublicationUncertain(retry));
        try (var executor = Executors.newSingleThreadExecutor()) {
            for (SaveResult result : results) {
                fake.results.add(result); assertSame(result, executor.submit(() -> MergeWrites.save(fake.draft(fake.inputs()))).get());
            }
        }
        assertEquals(0, partial.closes); assertEquals(0, retry.closes);
    }
    @Test void filterUsesOnlyCoreMembershipEvenForIdenticalVisibleDescriptors() {
        TokenAlternative a = alternative(0), b = alternative(0), c = alternative(1);
        Recording fake = new Recording(List.of(a, b, c), false);
        var all = fake.inputs(); assertEquals(fake.token.competingValues(), all.selectedCompetition());
        var subset = all.select(List.of(b, a)); var competition = subset.selectedCompetition();
        assertEquals(1, competition.issuer().values().size());
        assertEquals(List.of(a, b), competition.issuer().values().get(0).alternatives());
        assertFalse(competition.issuer().disagrees());
        assertEquals(2, competition.secret().groups().size()); // Same descriptors do not imply equal secrets.
        assertEquals(List.of(a), competition.secret().groups().get(0).alternatives());
        assertEquals(List.of(b), competition.secret().groups().get(1).alternatives());
        var remaining = all.select(List.of(a, c)).selectedCompetition();
        assertTrue(remaining.issuer().disagrees()); assertEquals(2, remaining.secret().groups().size());
    }
    @Test void agreedSecretNeedsNoNewSecretAndDraftCancellationWipesIngress() throws Exception {
        Recording fake = new Recording(List.of(alternative(0), alternative(1)), true); fake.results.add(saved());
        try (var executor = Executors.newSingleThreadExecutor()) {
            executor.submit(() -> MergeWrites.save(fake.draft(fake.inputs()))).get();
        }
        assertEquals(1, fake.used.size()); assertFalse(fake.values.containsKey("secret"));
        byte[] bytes = {102}; new MergeDraft(fake.inputs(), FIELDS, null, bytes).close(); assertArrayEquals(new byte[1], bytes);
    }
}
