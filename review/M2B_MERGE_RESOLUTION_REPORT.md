# M2b merge/conflict-resolution review

## Scope and baseline

Implemented explicit, manual conflict resolution on committed M2a `4cafa02`.
All changes remain uncommitted and unstaged. No tag, publication, release, submodule
update, or production dependency was created.

Before editing, inspected the clean M2a controller, desktop draft/ingress, builder
executor, uncertainty/retry ownership, window close ordering, browser, and tests.
Read the pinned public declarations for both `VaultState.merge` overloads,
`MergeToken`, `MergeSecretChoice`, competition/group types, `SaveResult`,
`PartialResolution`, `PartialSaveResult`, and `PublicationRetry`, plus
`vendor/totipo-java/API_DESIGN.md` covering captured bases, causal relevance,
persistence knowledge and thread confinement.

The gitlink, submodule HEAD, and `TOTIPO_JAVA_PIN.md` all remain
`3d97dca72604f39b6b475c0d5a0a8b076632cc42`. The submodule is clean.
Ordinary Update, normal Merge, and publication of a frozen PartialResolution
remain distinct public operations. There is no automatic merge or winner policy.

## Files and final structure

New production files:

| File | Responsibility |
| --- | --- |
| `MergeInputs.java` | Exact receiving state/token/competition capture, immutable selected alternatives, membership-only presentation filtering and Alternative labels. |
| `MergeDraft.java` | Submitted resolved fields and existing-secret representative or exclusively owned new-secret ingress. No live core capability. |
| `MergeWrites.java` | Synchronous session-executor merge builder operation, exact factory selection, cleanup-result preservation and draft-invariant classification. |
| `ui/MergeEditorPanel.java` | Headless-testable two-step input selection and field-resolution form; no live builder. |

Changed production files:

- `TokenWriteController.java`: admits merge in the same single-write slot, owns
  partial handles on the executor, handles Review/Publish/Cancel, and reuses the
  existing exact-publication retry and sticky abandonment machinery.
- `VaultWindowController.java`: installs the merge action; retains its one state
  subscription and serialized session close lifecycle.
- `ui/TokenBrowserPanel.java`: separate Resolve Conflict action and eligibility.
- `ui/VaultView.java`: presentation callbacks carry panels, descriptive state and
  user actions, never partial/retry capabilities.
- `ui/VaultFrame.java`: owned modeless merge dialog and explicit frozen-publication
  confirmation.
- `ui/VaultPanel.java`: AdditionalConflict choices, default Review action and
  operation-specific uncertainty text.

`TokenDraft`, `TokenWrites`, `TokenEditorPanel`, ordinary edit explanation, and
ordinary factory behavior remain unchanged. The create/update defensive
AdditionalConflict path still releases the unexpected partial without publishing
or offering merge choices. Its cleanup now uses the generic quiet partial cleanup
helper, so cleanup failure cannot change definite non-publication into uncertainty.

New tests: `MergeFixtures`, `MergeWritesTest`, `MergeEditorTest`, and
`MergeControllerTest`. Extended `NioSmokeTest` and `ui/TokenEditingBrowserTest`.
Updated `ARCHITECTURE.md` and `README.md`; this report is the remaining new file.

## Captured basis, input selection and field resolution

Resolve Conflict appears only when `hasConflict()` is true and there are at least
two complete semantic alternatives. Edit Alternative remains separately available
with its explanation that ordinary update does not resolve other alternatives.
Multiple equal heads alone, diagnostics, incomplete observation and unresolved
references do not create merge eligibility. Existing unresolved-reference warnings
remain visible, and a real semantic conflict with unresolved references is allowed
to reach the core gate. Tombstones remain complete valid inputs.

Opening captures the exact receiving `VaultState`, `TokenState`, its full TokenId,
complete alternative list and core `TokenCompetition`. Input selection defaults to
all captured alternatives and presents each status, issuer, account, algorithm,
digits, period and carrying head IDs literally. Labels imply no priority. Continue
requires two selected alternatives; otherwise it directs users to Edit Alternative.
A strict subset has an explicit warning that omitted alternatives may remain competing.

Continue freezes the selected list. All-selected uses exactly
`capturedBase.merge(tokenId)`, preserving the receiving state's full frontier.
Strict-subset uses exactly `capturedBase.merge(selectedAlternatives)` with the
captured references and no additions or head-level selection. It never substitutes
a later emitted state or same-looking alternatives. New state emissions still
update the main browser and TOTP. The core alone performs normal merge's causal
new-information check; semantic equality is never used to disregard a core result.

Filtering intersects core-provided field-value memberships and secret-group
memberships with selected alternatives, dropping empty intersections. No desktop
descriptor/secret comparison reconstructs those groups. Agreed-after-filter fields
are prefilled; disagreements start unselected. Back preserves the checked inputs
for deliberate changes but discards all field choices and clears new-secret input;
Continue constructs a fresh resolution form without remapping prior choices.

The result can combine independent fields from multiple alternatives. Status permits
ACTIVE/TOMBSTONED without a preference or permanent-deletion claim. Algorithm permits
every pinned `TotpAlgorithm`. Issuer/account offer existing values with memberships
and literal Other… input. Digits and period permit existing or custom values,
validated by the same public descriptor rules as M2a: 6–8 and integral seconds
1–4294967295. All disagreeing fields require an explicit choice; neither list order,
head count, majority nor metadata time selects a winner. Stored strings use plain
text areas and HTML-disabled combo renderers.

## Secret choice, ownership and thread confinement

One descriptive secret group defaults to keeping that agreed secret; replacement
is optional. Multiple groups begin unselected and require an existing group or a
new secret. Groups display only Alternative memberships, never secret bytes or
TOTP-derived equality. The draft represents a chosen group with one captured
selected `TokenAlternative`, not its presentation index or label.

On the session thread, `MergeWrites` creates the exact builder and `MergeDraft.apply`
inspects `competingValues()`, `secretChoices()` and `unresolvedFields()`. It locates
exactly one builder-issued secret choice containing the representative and passes
that precise capability to the same builder. Missing or ambiguous mapping is an
internal draft problem with definite non-publication; the builder closes. No
choice is reused across builders. After setters, unexpected unresolved fields also
prevent save rather than inventing a resolution.

New-secret ingress reuses M2a's character-array Base32 decoder. Retrieved characters
are wiped, the password field clears, and decoded bytes transfer exclusively to the
submitted draft. On the executor, the unchanged `TokenDraft.apply` creates
`NewSecret.copyOf`, immediately wipes desktop bytes, synchronously supplies the
builder, then closes the wrapper. Draft scope handles failure cleanup. No plaintext
secret remains for AdditionalConflict, PartialResolution or PublicationRetry.
JVM wiping retains M2a's best-effort limitation. Metadata remains at API defaults.

Factory, competition inspection, secret-choice inspection/mapping, every setter,
both unresolved-field inspections, save and close are recorded by public fakes on
one non-EDT session thread. No MergeToken, MergeSecretChoice, PartialResolution or
PublicationRetry is stored in Swing state or the desktop merge draft. Partial/retry
fields belong exclusively to the controller's session-executor operations.

## Result and decision semantics

| Normal merge result | Behavior |
| --- | --- |
| Saved | “Merge publication acknowledged.” Retire workflow; emitted state alone changes the browser. No global conflict-freedom claim. |
| Failed(UNRESOLVED_FIELDS) | Definite non-publication, explain missing required fields, retain editable non-secret form without inventing values. |
| Failed(OBSERVATION_UNAVAILABLE) | Required local observation was unavailable and the operation was not published. No conflict classification or automatic retry. |
| Failed(PREPARATION_FAILED) | Definite non-publication; retain non-secret choices and require re-entry of a consumed new secret. |
| Failed(SESSION_CLOSING) | Follow orderly close without retry choices. |
| AdditionalConflict | Nothing published. Retire editable form and transfer the independent frozen partial into executor ownership. Present Review Latest, Publish Original and Cancel. |
| PublicationUncertain | Transfer sole authority to the shared exact-publication retry workflow with merge-specific wording. |

A subsequent explicit Save after a definite normal failure creates a fresh builder
from the same captured base; it never silently rebases or automatically retries.

AdditionalConflict says new relevant information was observed before publication
and nothing from this merge was published. It is not reported as a failed save or
successful conflict resolution. Exactly one partial is owned; no automatic partial
save or remerge occurs. Review Latest is the default/normal decision action.

Review Latest first takes/clears/closes the partial on the executor, never saves it,
then looks up the full TokenId in exactly `AdditionalConflict.latest`. A remaining
semantic conflict starts a new editor at input selection, all latest alternatives
selected, with fresh field and secret choices. No remaining conflict produces an
explanation and restores normal actions. That supplied state never replaces the
browser's current emitted projection. Repeated B/C/etc. AdditionalConflict cycles
retire each previous partial and require new human resolution without an arbitrary cap.

Cancel releases the unpublished partial and restores writes without sticky
publication uncertainty. Review/Cancel cleanup exceptions produce a generic
cleanup error, drop the desktop reference and rely on session close as the final
invalidation backstop. They do not invent persistence uncertainty.

Publish Original requires a separate confirmation explaining exact original inputs,
exclusion of new information, absence of another new-information check, and possible
remaining competing alternatives. Only the confirmed callback queues
`PartialResolution.save()`; no builder, setters, rebase or desktop semantic gate runs.

| PartialSaveResult | Behavior |
| --- | --- |
| Saved | Retire/close handle; “Original merge resolution publication acknowledged.” Does not claim the newly observed conflict was resolved. |
| Failed | Definite non-publication; retire/close handle without a second save. Restore writes or close for SESSION_CLOSING. User may start a fresh merge manually. |
| PublicationUncertain | Retire partial and transfer independent successor to M2a PublicationRetry. No plaintext recipe is retained. |

There is no AdditionalConflict branch for partial save. Normal and original-resolution
uncertainty have distinct wording but identical Retry exact publication / Stop retrying
capability machinery. No Review action is offered with an active retry. A retry uses
only frozen publication bytes, never rebuilds merge. Success ends active uncertainty;
Stop leaves the existing session-wide sticky warning, which observations, Refresh
and later Saved operations do not clear.

## Cleanup preservation and closing

Returned semantic knowledge survives later cleanup exceptions: merge Saved remains
acknowledged, AdditionalConflict retains its independently owned partial, partial
Saved/Failed preserve acknowledgement/non-publication, and partial uncertainty retains
its usable retry successor. Generic redacted cleanup logging cannot erase results.

One write slot spans Create, Update, merge editing, AdditionalConflict, partial save
and retry. Create/Edit/Resolve remain disabled throughout. Refresh/state/TOTP can
continue until close. There is no extra executor or subscription and no optimistic
browser mutation after save.

Close retires an unsaved merge form and clears new-secret input without constructing
a builder. In-flight merge/partial/retry operations finish without interruption.
Cleanup queues behind them and ahead of session close, releasing any late-returned
partial/retry handles. AdditionalConflict after close never opens decision UI or
publishes the partial; partial uncertainty after close never opens retry UI. No late
result resurrects the window. Abandoning an unpublished partial during close does
not itself create a sticky uncertainty notice.

## Tests and integration evidence

Final suite: **97 passing desktop tests**, including all **67 existing M1/M2a tests**
and **30 added test methods**. Loop-based cases exercise multiple variants per method.
No graphical display is required, and race tests use latches/barriers rather than sleeps.

| Coverage | Added methods |
| --- | ---: |
| MergeWritesTest: exact full/subset receiving-state factories; all builder calls on one thread; fresh exact-builder choice mapping; missing/ambiguous choice and unresolved mismatch; new-secret wiping for AdditionalConflict/uncertainty; cleanup preserves every result; membership filtering and agreed-secret behavior | 6 |
| MergeEditorTest: explicit resolution of all six non-secret fields and secret; literal custom strings and numeric boundaries; Base32 entry; agreed-after-filter prefill; subset warning/minimum; Back/cancel wiping; literal rendering | 5 |
| MergeControllerTest: immutable base under emissions; all normal failure reasons; new-secret retirement/review; invariant failure; explicit A/B subset with omitted C and later core D result; partial ownership; repeated Review cycles; no-conflict review; explicit confirmation; every partial result/reason; cleanup exceptions; sticky retry; close in every merge/partial/retry phase | 17 |
| TokenEditingBrowserTest: real conflict eligibility, separate Edit/Resolve, shared availability, equal-head/no-alternative exclusion and unresolved-reference allowance | 1 |
| NioSmokeTest: real concurrent updates and merge through desktop boundary | 1 |

The NIO test creates a temporary vault/token, retains A, writes B and C from that
same historical base, observes two competing alternatives, explicitly resolves all
six fields to a combination of B/C plus a new account, chooses the exact builder's
existing secret through the desktop representative mapping, and saves a full-frontier
merge. It verifies the authoritative emitted descriptor, absence of the earlier
conflict, and TOTP for the active result. All resources close. No filesystem race
manufactures AdditionalConflict; deterministic public capabilities cover it.
Optional three-way real subset integration was not added; exact subset factories,
omitted-C/later-D decisions and resulting workflow are covered with deterministic
public fakes. The upstream protocol corpus was not duplicated or modified.

## Production source audit

Reviewed the changed production source and call sites explicitly:

- No MergeToken or MergeSecretChoice stored in Swing state/draft; builder objects and
  their inspections are local to the executor operation.
- No builder factory, inspection, setter, save or close on EDT; partial/retry blocking
  calls also run on the same session executor.
- No silent latest-state rebase; browser emissions and explicit Review bases remain separate.
- No automatic first-value winner, head-count vote or client-time freshness inference.
- No secret equality reconstructed from visible descriptors or TOTP; no descriptive
  SecretGroup passed as a mutation capability.
- No PartialResolution.save without the explicit confirmed choice; no automatic
  Review Latest, merge, partial publication or publication retry.
- AdditionalConflict remains definite non-publication, distinct from persistence failure.
- No impossible PartialSaveResult AdditionalConflict handling.
- No browser row mutation on Saved and no second state subscription.
- No storage SPI, implementation/internal API, secret export, or plaintext secret String.
- No metadata invention, automatic conflict policy or milestone non-goal feature.

## Reproducibility and validation

Production dependencies before and after are identical: direct `storage-nio`, pinned
transitive `core`, and core-owned `org.bouncycastle:bcprov-jdk18on:1.86`.
Java/Swing/AWT/time/concurrency remain JDK APIs. No production or test dependency was
added. Runtime dependency output contains only this graph. Locks and verification
metadata, Gradle settings, wrapper, Nix files, API pin and gitlink have no diff.

Gradle stays 9.8.0. The wrapper distribution SHA-256 remains
`bafd5ce9cfaea0fbccfdc8439a1ac42fbd4cd9c89dc9a988228d8a2639a58e6c`;
wrapper JAR SHA-256 remains
`238e777fcddd7e34f9708186085def2abd6e08e658505b38718d79d74c21abd5`.
Strict dependency verification and strict locking remain enabled. Compilation keeps
UTF-8, `-Xlint:all`, `-Werror`, JDK 25 build toolchain and production `--release 17`.
`verifyJava17Bytecode` checks every production class for major version 61 and no
preview bytecode. The environment supplies a full Nix-built OpenJDK 25.0.4.1,
including `java.desktop`.

Validation results and final working-tree snapshot follow below.

| Command / check | Final result |
| --- | --- |
| `git submodule status` | Exact documented pin; no dirty marker. Submodule porcelain status empty; gitlink unchanged. |
| `./gradlew clean test build` | PASS, final run 15 s; 97 tests, build/check/distributions completed. |
| `./gradlew --no-daemon --no-build-cache --rerun-tasks clean test` | PASS, final run 21 s; all nine tasks executed, 97 tests. |
| `./gradlew --offline --no-daemon --no-build-cache --rerun-tasks clean test` | PASS, final run 21 s; all nine tasks executed, 97 tests. |
| `./gradlew --offline dependencies --configuration runtimeClasspath` | PASS; unchanged storage-nio → core → Bouncy Castle 1.86 graph. |
| `git diff --check` | PASS. Supplemental whitespace check also covers all untracked Java/Markdown files. |
| Java 17 classfiles | PASS: independently inspected all 43 production class headers; every class has major 61/minor 0. Gradle bytecode verification also passed. |
| Test XML totals | 97 tests, 0 failures, 0 errors, 0 skipped. All existing 67 tests preserved and passing. |
| Build/dependency inputs against HEAD | No changes to build/settings files, wrapper/checksums, locks, verification metadata, Nix files, pin document or submodule. |

Commands used `--console=plain` for readable logs. Build logs for this work session
are `/tmp/totipo-m2b-clean-build.log`, `/tmp/totipo-m2b-rerun.log`,
`/tmp/totipo-m2b-offline.log`, and `/tmp/totipo-m2b-dependencies.log`.

Graphical smoke validation was unavailable: neither DISPLAY nor WAYLAND_DISPLAY
is configured. No top-level GUI was claimed to have been exercised. The two-step
editor, literal controls, actions and controller decisions are covered headlessly.
`nix develop` was unavailable because the Nix CLI is absent from this environment;
the supplied full Nix-built JDK 25 was used directly. The Nix shell definition was
preserved unchanged. An initial headless fontconfig diagnostic did not prevent
Swing tests; no GUI dependency was introduced into CI.

## Deviations and remaining validation

No material implementation-scope deviation is known. No requested production
feature is deferred. Environment-limited graphical and `nix develop` validation
remain unperformed, as stated above. The optional real three-alternative subset
integration test was omitted; its mandatory deterministic fake coverage is present.
Manual graphical review should check modeless layout/readability, decision focus,
and live browser/TOTP responsiveness on a real desktop before a release claim.

## Final working-tree snapshot

`git status --short` (all changes deliberately uncommitted/unstaged):

```text
 M ARCHITECTURE.md
 M README.md
 M src/main/java/dev/totipo/desktop/TokenWriteController.java
 M src/main/java/dev/totipo/desktop/VaultWindowController.java
 M src/main/java/dev/totipo/desktop/ui/TokenBrowserPanel.java
 M src/main/java/dev/totipo/desktop/ui/VaultFrame.java
 M src/main/java/dev/totipo/desktop/ui/VaultPanel.java
 M src/main/java/dev/totipo/desktop/ui/VaultView.java
 M src/test/java/dev/totipo/desktop/NioSmokeTest.java
 M src/test/java/dev/totipo/desktop/ui/TokenEditingBrowserTest.java
?? review/M2B_MERGE_RESOLUTION_REPORT.md
?? src/main/java/dev/totipo/desktop/MergeDraft.java
?? src/main/java/dev/totipo/desktop/MergeInputs.java
?? src/main/java/dev/totipo/desktop/MergeWrites.java
?? src/main/java/dev/totipo/desktop/ui/MergeEditorPanel.java
?? src/test/java/dev/totipo/desktop/MergeControllerTest.java
?? src/test/java/dev/totipo/desktop/MergeEditorTest.java
?? src/test/java/dev/totipo/desktop/MergeFixtures.java
?? src/test/java/dev/totipo/desktop/MergeWritesTest.java
```

`git diff --stat`:

```text
 ARCHITECTURE.md                                    | 147 +++++++++++++++++++--
 README.md                                          |  17 ++-
 .../dev/totipo/desktop/TokenWriteController.java   | 124 +++++++++++++++--
 .../dev/totipo/desktop/VaultWindowController.java  |   1 +
 .../dev/totipo/desktop/ui/TokenBrowserPanel.java   |  15 ++-
 .../java/dev/totipo/desktop/ui/VaultFrame.java     |  26 +++-
 .../java/dev/totipo/desktop/ui/VaultPanel.java     |  33 ++++-
 src/main/java/dev/totipo/desktop/ui/VaultView.java |   9 ++
 src/test/java/dev/totipo/desktop/NioSmokeTest.java |  34 +++++
 .../totipo/desktop/ui/TokenEditingBrowserTest.java |  36 +++++
 10 files changed, 416 insertions(+), 26 deletions(-)
```

Git's ordinary diff stat excludes the nine untracked additions shown above: four
production files, four test files and this report. They are present in the working
tree for review; they have deliberately not been staged to alter this snapshot.

## M3a readiness

The implementation is ready for review as the basis for **M3a: password change +
password-change uncertainty/stale-session handling**. Merge, frozen partial
publication and exact retry retain separate authority and lifetimes, while the
one-workflow slot, serialized close and sticky token-publication history remain
intact. M3a should design its own password-change outcome/stale-session behavior
against the pinned public API rather than reinterpret token retry capabilities.
This assessment does not claim graphical platform qualification or implement any
password-change functionality. Complete the noted graphical review when a display
is available; all executable validation available here passed.
