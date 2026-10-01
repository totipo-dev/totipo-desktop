# Desktop architecture

M2b adds explicit field-oriented merge and frozen-resolution decisions to M2a
manual token create/update and explicit publication retry/abandonment, building on
M1a session/window lifecycle, observation/diagnostics and refresh, and M1b
logical-token/TOTP browsing. The single-project
Swing bootstrap and pinned composite dependency from M0 remain intact. Production
code consumes only `dev.totipo.*` and `dev.totipo.storage.nio.NioTotipo`, never the
storage SPI or implementation internals. See [the dependency pin](TOTIPO_JAVA_PIN.md).

## Ownership

```text
TotipoDesktop: invokeLater
  -> DesktopApplication (EDT ownership)
       -> LauncherFrame / LauncherPanel
       -> one serialized application lifecycle executor
       -> zero or more VaultWindowController
            -> one VaultSession
            -> one VaultFrame / VaultPanel
            -> one StateSubscriber / states() subscription
            -> one serialized session executor
            -> at most the latest immutable VaultState reference
            -> one TokenWriteController (create/update OR merge OR capability decision)
                 -> desktop TokenEditorPanel / owned modeless TokenEditDialog
                 -> captured base/alternative for an open editor
                 -> MergeEditorPanel with captured MergeInputs and submitted MergeDraft
                 -> at most one executor-owned PartialResolution OR PublicationRetry
                 -> sticky non-secret abandoned-publication boolean
```

There is no global current vault. Independent windows have independent controllers,
subscriptions and executors, even if the user selects the same directory twice.
`DesktopApplication` owns the launcher and tracks controllers until session close
finishes. Frames render and emit actions; they do not own protocol behavior.
`LauncherView` and `VaultView` are small presentation interfaces for testing these
owners without constructing top-level windows. They are not a general UI framework.

## Threading and entry points

All ownership transitions, user actions and Swing changes occur on the EDT. The
small `Edt` guard checks those boundaries. `TotipoDesktop` only schedules creation
of `DesktopApplication` and shows its launcher.

One ordinary JDK single-thread executor, named `totipo-application`, runs blocking
open/create and closes returned sessions that cannot be transferred to a controller.
The package-private `VaultAccess` seam has only `open(Path, char[])` and
`create(Path, char[])`; `NioVaultAccess` delegates directly to `NioTotipo`. Tests
substitute lifecycle outcomes without KDF or filesystem work. No protocol or
storage-provider behavior is reproduced in this seam.

Each controller owns a separate single-thread executor named `totipo-session-N`.
It runs token builder factory/setters/save/close, publication retry/capability
cleanup, and session close. Blocking open, create and close never run on the EDT.
Executors are explicitly shut down; no shutdown interrupts an in-flight operation.
The application uses neither SwingWorker nor virtual threads nor reactive libraries.
Production code remains compatible with Java 17.

## Launcher and passwords

Open and Create choose an existing directory with Swing's directory chooser.
Desktop code does not create directories, recursively inspect them, or canonicalize
paths. The NIO result is authoritative. Only one launcher operation is accepted at
a time; both buttons stay disabled across prompts, work, result presentation and
unclaimed-session cleanup. Modal dialogs can run nested EDT loops, so shutdown is
checked after prompting as well as when the background result reaches the EDT.

Passwords come from `JPasswordField.getPassword()` as desktop-owned `char[]` values.
No password is converted to String, logged, or included in presentation/diagnostics.
Create compares password and confirmation arrays directly. Confirmation is wiped
immediately after comparison; a mismatch also wipes the primary array. Fields are
cleared when dismissed. Cancellation/rejection wipes any retrieved primary array.

`PasswordInput` validates UTF-16 and counts UTF-8 bytes without an encoded copy.
It rejects unpaired surrogates and more than 1024 UTF-8 bytes. Empty input and all
other valid Unicode within the limit are accepted; there is no extra password policy.
Once accepted for background work, the operation owns the primary array and wipes
it in `finally`, whether NIO returns a session, another outcome, or throws. This is
best-effort JVM secret hygiene, not a secure-erasure guarantee. No passwords are
remembered for retries or subsequent Open actions. Unexpected exceptions use generic
application/session failures; exception text and stack traces are not displayed or
logged because they could contain caller-controlled data.

## Lifecycle outcomes

Every public open/create result has distinct handling:

| Result | Desktop behavior |
| --- | --- |
| Opened / Created | Transfer the session once to a registered controller, then start its window/subscription. If construction fails before transfer or shutdown has begun, close the session on the application executor. After transfer, the controller owns all cleanup, including start/subscription failure. |
| Absent | No canonical Totipo vault was observed; never implicitly create. |
| Unavailable | Directory/vault could not be reliably accessed; no password/corruption diagnosis. |
| InvalidVault | Observed vault data could not be used as a valid vault; no repair/replacement. |
| AuthenticationFailed | Authentication did not succeed; this does not prove the password is incorrect, and authenticated data may have changed or become unusable. |
| AlreadyExists | A vault was observed and nothing was overwritten; Open remains an explicit later action. |
| Failed | Definite failure of this create operation; no automatic retry. |
| Uncertain | Creation may have succeeded; canonical outcome cannot be asserted. Do not blindly retry; explicitly use Open Vault to re-observe. |

Unexpected runtime exceptions are application failures, never converted into a
protocol outcome. `Error` is not caught or swallowed. There is no automatic open,
create, observation or lifecycle retry.

## State and replay-latest delivery

`VaultState` is authoritative immutable application state. A controller retains
its latest received reference, clears it on close, and does not maintain a
shadow vault model or state history. An editor separately captures its exact
historical operation base. Swing rendering models are rebuilt from the
current state, not merged into a desktop protocol representation.

The desktop application must not maintain a mutable shadow copy of vault/token state.

`StateSubscriber` requests exactly one item on subscription. `onNext` enqueues an
EDT runnable, which stores/renders the state only while the controller remains
active. Only after rendering does it request one further item. Demand therefore
stays zero while the EDT is pending, allowing the core's replay-latest coalescing
to discard intermediate observations. There is no desktop state queue or unbounded
demand. Cancellation drops the subscription reference and suppresses pending UI
application. Terminal callbacks are also handed to the EDT and handled once.

A stream error marks the session unusable, disables interaction, presents a generic
session failure distinct from observation diagnostics, and begins close. Unexpected
completion retires the session/window cleanly. Explicit close cancels first, so
completion cannot start another close lifecycle. Rendering/runtime failures use
the same terminal cleanup path.

## Local observation presentation

`VaultPanel` delegates token browsing to `TokenBrowserPanel` and reads observation
and diagnostics for the following presentation:

- Enumerating: “Observing local vault — discovered N objects”, with an indeterminate
  bar. The count is not described as final.
- Processing: “Processing local observation — X of Y”, with exact long counts.
  Only the visual bar is scaled to 0–1000 to avoid Swing integer-range overflow.
- Finished: “Local observation finished — N objects processed”, optionally followed
  by “diagnostics present”. This means only that the local pass ended.

The current diagnostic codes replace the previous text, preserving order and
repeated entries. Codes receive no invented causal explanations and are not a
persistent event log. Neither progress nor diagnostics establish freshness,
complete history, remote synchronization, rollback resistance or absence of conflicts.

Refresh invokes `VaultSession.requestRefresh()` directly on the EDT because the
API guarantees a non-blocking request. It is disabled during close. There is no
polling, filesystem watcher, automatic refresh loop or remote sync behavior.

## Close and shutdown

Both frames use `DO_NOTHING_ON_CLOSE`; owners control disposal. A window close
request idempotently marks its controller closing, cancels the subscriber, drops
the latest reference, disables Refresh and displays “Closing…”. Its session executor
calls `session.close()`. After that call finishes, the executor is shut down and an
EDT callback disposes the frame and removes the controller from the application.
A runtime close failure is reported generically and still retires the executor and
window, without retry or a claim that unexpected core cleanup failure was repaired.
No frame disposal substitutes for session close.

Launcher close marks the application shutting down, disables Open/Create and asks
every live controller to close. No new vault window can be created thereafter.
An already-running open/create is allowed to finish without interruption. A session
returned after shutdown is explicitly closed on the application executor; passwords
are still wiped normally. The launcher remains owned until no launcher operation
(including prompts, result presentation or unclaimed cleanup) is pending and all
controllers have reported closed. Then the application executor shuts down and the
launcher is disposed. The EDT never waits for background work. There is no
`System.exit()` shortcut.

## Preserved principles and later work

1. Protocol/application state belongs to `VaultSession` and `VaultState`.
2. Presentation state includes selection, window state and dialogs.
3. Operation state includes editor drafts, captured bases, and outstanding result capabilities.

The desktop must not collapse conflicts into arbitrary winners, partial observation
into completeness, uncertainty into success/failure, local publication into remote
synchronization, or equal causal heads into semantic conflict. No automatic merge,
retry or invented synchronization semantics are introduced.

Later work includes search/filter/sort, password change, QR/URI import,
secret export, clipboard, keychain, remembered
passwords, recent history, tray, shortcuts, theming, watchers, remote providers,
installers and release publishing.

## Verification

Headless tests construct panels and drive lifecycle owners on the EDT, using narrow
fake views/access/sessions and controlled publishers/latches. They test lifecycle
outcomes, password validation/wiping, subscriber backpressure, terminal/cancel
races, observation-only rendering, refresh, independent session close and shutdown
during open/create. M1a fake states reject unexpected token/editing/TOTP methods;
M2a adds public-interface write fakes; M2b adds merge/partial fakes and deterministic
close barriers. Real-NIO temporary-directory tests cover
create/close/open/close, TOTP intervals, desktop token create/update, and a real
conflict created by historical concurrent updates then explicitly merged. Build/test also
verify every production class is Java 17 classfile version 61. No new production or
test dependencies were needed.

## Logical-token browser and TOTP (M1b)

One row represents one logical token. Complete semantic alternatives, including
secret-only differences, determine conflict; equal-valued multiple heads do not.
Selection follows TokenId across immutable projections. Alternative labels express
no preference. Incomplete observations retain their unresolved evidence and never
invent an editable value. Stored issuer/account/metadata render literally.

TOTP uses the public state/alternative operation and core-returned half-open time
intervals. A coalescing Swing timer updates the display; opening an editor does
not stop it. Latest states continue rendering during editing and uncertainty.
No success result directly changes a row, selection, descriptor or code.

## Desktop drafts and secret ingress (M2a)

`TokenEditorPanel` is headlessly testable; `TokenEditDialog` is only an owned
modeless shell. Neither holds a core builder. Create defaults are ACTIVE, empty
issuer/account, SHA1, six digits, thirty seconds, and a required secret. Update
prefills the selected descriptor, permits ACTIVE/TOMBSTONED, and requires an
explicit Replace secret checkbox to accept replacement ingress. TOMBSTONED is
not permanent deletion. No metadata is supplied or copied from parents.

Non-secret input is validated against public TokenDescriptor bounds. No extra
issuer/account policy or hidden wire-format validation is duplicated. A Save
reads `JPasswordField.getPassword()` into a temporary char array and decodes that
array directly. `Base32` accepts RFC 4648 upper/lowercase, padded or unpadded text,
and ignores ASCII space/tab/CR/LF/hyphen. It rejects invalid alphabet, Unicode
lookalikes, 0/1 substitutions, misplaced/incorrect padding, impossible lengths,
nonzero trailing bits, and decoded lengths outside 1–128 bytes. Error messages
never contain input. Temporary character arrays are wiped in finally; fields
clear after decoding, cancellation, retirement, and disabling replacement.

A submitted `TokenDraft` exclusively owns its decoded byte array and immutable
non-secret descriptor. Ownership transfers to one executor task. `TokenWrites`
creates `base.createToken()` or `base.update(capturedAlternative)` there, applies
all fields, saves, and closes the builder on that same thread. A try-with-resources
draft scope wipes bytes even when factory/setter/save fails. After
`NewSecret.copyOf` defensively copies ingress, the desktop array is immediately
wiped; `builder.secret` synchronously copies the wrapper, which closes immediately.
No wrapper or plaintext draft is retained for retries. Without replacement, no
secret ingress call is made. All wiping is best-effort JVM hygiene, not secure
erasure. A definite failure retains editable non-secret form fields, but requires
fresh secret input when the operation needs it.

## Captured update basis and serialized workflow

The browser offers Edit Token for one complete semantic alternative, including
multiple equal heads. A conflicted token offers an initially unselected
Alternative N selector and Edit Alternative…; explanatory text appears before
opening the editor and in the editor. No complete alternative means no edit.

Opening captures the exact receiving VaultState and TokenAlternative. New state
emissions neither rewrite the draft nor replace this basis. Historical same-session
references are valid. Ordinary update uses only the deterministic equal-valued
heads selected by that receiving state (or the alternative's captured heads).
It does not parent unrelated alternatives, invoke a merge gate or resolve conflict.
No head-level update, rebasing, winner selection or merge exists in this path.

`TokenWriteController` admits one write workflow per window, including merge and
partial-resolution decisions. Create/Edit/Resolve disable throughout it; Refresh and ordinary state/TOTP delivery stay
available. Save validation occurs on EDT, then controls disable before one task
is submitted to the existing session executor. Repeated clicks cannot queue more
writes. There is no extra executor, subscription, polling or automatic retry.

## Publication results and capability ownership

| Result | Desktop behavior |
| --- | --- |
| Saved | Retire editor/uncertainty; acknowledge token publication; restore write actions. This is configured-store durability acknowledgement, not sync, conflict resolution or global freshness. |
| Failed(PREPARATION_FAILED) | Explain definite non-publication; leave non-secret fields editable for an explicit new builder operation from the same base. |
| Failed(OBSERVATION_UNAVAILABLE) | State that required local observation was unavailable and the operation was not published; retain editable form. |
| Failed(UNRESOLVED_FIELDS) | State definite non-publication without inventing missing values; retain editable form. |
| Failed(SESSION_CLOSING) | Begin orderly window/session close; no retry choice. |
| PublicationUncertain | Retire editor; keep exact retry capability on executor; show Retry exact publication / Stop retrying. May already be present in the vault. |
| AdditionalConflict from create/update | Defensive invariant failure only: close the returned PartialResolution on executor, show generic internal failure, never publish it or expose merge choices. |

The pinned publication attempt requests local refresh after Saved.
The desktop does not request an additional refresh and never uses observation as
acknowledgement. Runtime exceptions produce a generic internal-operation message,
not a fabricated SaveResult; exception details, drafts and codes are not logged.

Only the session executor accesses the retry field. EDT receives classification
and callbacks, never a capability. An explicit retry takes and clears the old
handle, invokes `retryPublication()` on the session executor using narrow
`RetryResult`, installs the successor on uncertainty, and closes the consumed
old handle. There is never a second builder or semantic reconstruction. Saved
drops uncertainty with no abandonment notice; successive uncertain results own
successive independent handles. Both actions disable during an attempt.

Stop closes the handle on the executor and sets a sticky session presentation
boolean. It does not undo publication or prove failure. Later observation,
Refresh, or unrelated Saved operations cannot clear it. The only retained history
is this non-secret boolean. Create explains that starting again makes a distinct
token and may lead to two logical tokens if an earlier uncertain create persisted.
An unexpected retry exception retires the unusable handle and also preserves the
uncertainty history with a generic internal failure message.

## Close versus editor/save/retry

Close marks the controller closing on EDT, cancels its one state subscription,
retires the editor and clears its field, disables all write entry points, and
clears uncertainty presentation. It queues capability cleanup followed by session
close on the same executor. It never interrupts a save/retry or waits on EDT.
An in-flight task may install a returned successor on the executor; queued cleanup
then releases it before session close. Late EDT callbacks check closing and never
reopen an editor, reinstall uncertainty UI, or start another retry. Session close
still invalidates core-owned handles, even if an unexpected cleanup failure occurs.


## Explicit merge resolution (M2b)

Resolve Conflict… is separate from Edit Alternative… and appears only for
`hasConflict()` with at least two complete semantic alternatives. Equal heads,
diagnostics, or unresolved references alone do not enable it. Unresolved references
do not veto a real semantic conflict; the existing technical warning remains visible.
Tombstoned alternatives are complete merge inputs, without any status preference.

`MergeInputs` captures the exact receiving `VaultState`, `TokenState`, token ID
through that token, complete alternative list and descriptive `TokenCompetition`
when the workflow opens. Later state emissions continue to drive the browser/TOTP
but never rewrite the capture. Input selection initially includes every captured
alternative, with literal descriptors and head IDs. Alternative numbers express no
priority. At least two must be selected; changing a single alternative belongs to
ordinary Edit Alternative.

Continue freezes a copied list of selected alternatives. All-selected Save calls
exactly `capturedBase.merge(tokenId)` for the receiving state's full frontier.
A deliberately selected strict subset calls exactly
`capturedBase.merge(selectedAlternatives)` with those captured references. Omitted
alternatives are not silently included, and the form warns they may remain competing.
The desktop never treats an omitted member of the original frontier as new information.

`MergeInputs.selectedCompetition()` intersects each core field-value membership and
secret-group membership with the selection, discarding empty intersections. It
retains the core's values and equality groups; descriptors, heads, metadata time and
TOTP codes do not establish equality or voting priority. Going Back discards field
choices and clears new-secret input; the input step preserves the checked subset
for deliberate changes and builds a fresh resolution form on Continue.

The modeless `MergeEditorPanel` resolves status, issuer, account, algorithm, digits,
period and secret independently. Agreed fields are prefilled and remain changeable;
disagreeing fields start without a selection and disable Save until explicitly
resolved. Existing choices include their Alternative memberships. Status and
algorithm permit all pinned enum values. Issuer/account offer literal Other… text,
with no added string policy. Digits and integral-second period use the same public
`TokenDescriptor` validation as M2a (6–8 and 1–4294967295). Controls and renderers
render stored strings literally, without Swing HTML.

Secret groups expose only Alternative memberships. One agreed group defaults to
keeping it; multiple groups have no default. Replacement accepts the M2a Base32
caller-secret ingress. The submitted `MergeDraft` owns immutable non-secret fields,
`MergeInputs`, a captured representative alternative for an existing secret group,
or newly decoded owned bytes. It contains no builder, secret choice, partial or
retry capability. It delegates field application/secret ingress to the small M2a
`TokenDraft` helper without changing ordinary create/update semantics.

`MergeWrites` constructs, inspects (`competingValues`, `secretChoices`,
`unresolvedFields`), populates, saves and closes a short-lived `MergeToken` entirely
on the session executor. A descriptive `SecretGroup` is never passed to it. The
representative alternative must match exactly one choice issued by this exact
builder; missing/ambiguous mappings publish nothing. All intended fields are applied,
and an unexpectedly nonempty unresolved list prevents save. `NewSecret.copyOf`
occurs there, immediately wipes desktop bytes, synchronously supplies the builder,
and closes promptly. Draft scope also wipes on factory/setter/save failure.
Metadata remains at the public API default.

The core alone owns the normal merge fresh-observation/new-information gate,
including causal relevance of new heads with already-known semantic values. There
is no second desktop freshness check or latest-state substitution.

## Merge results, partial ownership and renewed review

Normal merge Saved says “Merge publication acknowledged.” The editor retires; only
emitted states change browser rows. This promises neither global conflict freedom
nor peer observation. All definite Failed reasons retain editable non-secret form
state for a deliberate new Save from the same base, except SESSION_CLOSING, which
starts close. OBSERVATION_UNAVAILABLE explicitly states that nothing was published;
it is not a conflict result. Consumed replacement secrets must be re-entered.

AdditionalConflict is definite non-publication, with newly relevant information
and an independently owned frozen original resolution. The builder is terminal.
The controller retires the form, keeps `PartialResolution` only in executor-owned
operation state, and presents Review latest and merge again (the normal/default
action), Publish original resolution anyway, and Cancel. Neither publication nor
review happens automatically. Subsequent state emissions cannot alter that partial.

Review Latest or Cancel takes and clears the owned partial, then closes it on the
session executor without save. Cleanup errors produce a generic cleanup message,
not persistence uncertainty; session close is the final invalidation backstop.
After cleanup, Review looks up the full TokenId in `AdditionalConflict.latest()`.
A remaining conflict opens a fresh input-selection workflow using exactly that state,
with all its alternatives selected and no prior resolutions, subset or new secret.
Otherwise the workflow ends with an explanation. The supplied state never replaces
the browser's latest emitted state. Repeated AdditionalConflict/Review cycles are
unbounded human decisions, each retiring the previous partial.

Publish Original requires a second explicit confirmation explaining that it uses
the exact original selected inputs and resolution, omits newly observed information,
and skips the merge new-information check. It can leave competing alternatives.
Only the executor calls `PartialResolution.save()`; it constructs no new merge or
update and performs no desktop semantic gate. The narrowed `PartialSaveResult`
permits only Saved, Failed, and PublicationUncertain:

- Saved consumes/retires the partial and says “Original merge resolution publication
  acknowledged.” It does not claim the new conflict was resolved.
- Failed retires/closes the partial, states definite non-publication, and restores
  write actions (or closes for SESSION_CLOSING). It never saves the same partial again.
- PublicationUncertain retires the partial and transfers sole publication authority
  to the independently returned `PublicationRetry`.

Normal and partial merge uncertainty reuse M2a's exact-byte retry/stop machinery,
with operation-specific wording. There is no Review Latest action while a retry
is owned, no plaintext recipe retained for retry, and no merge reconstruction.
Stop remains sticky across observations and later acknowledged writes.

## Merge close races and cleanup knowledge

The one-write-workflow slot covers input selection, field editing, normal merge,
AdditionalConflict decisions, partial save and publication retry. Refresh, state
rendering and TOTP remain independent until close. Closing clears unsaved secret
input and disables the modeless editor without creating a builder. An in-flight
merge, partial save or retry is never interrupted. Executor cleanup is queued
behind that work and ahead of session close, including capabilities returned while
closing. No late completion may reopen decision or retry UI; unpublished partials
are closed without save, without creating sticky uncertainty solely from abandonment.

Once a save returns a semantic result, subsequent builder/handle cleanup cannot
erase it. AdditionalConflict keeps its independent partial despite builder-close
failure; partial Saved/Failed/PublicationUncertain survive partial-close failure,
including ownership of a usable retry successor. Cleanup logs are generic and
redacted. Tests use public fakes and deterministic latches for these races; the
real NIO smoke does not attempt to manufacture AdditionalConflict with filesystem races.
