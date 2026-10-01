# M3b usability and accessibility review

## Baseline and scope

Started from clean committed M3a, `a995984` (`implement m3a`). Inspected the browser,
presentation, TOTP lifetime, vault panel/frame, token/merge/password forms and owned
dialogs, view callbacks and MutationGate before editing. The gitlink and clean
submodule both remain `3d97dca72604f39b6b475c0d5a0a8b076632cc42`, exactly as recorded
in TOTIPO_JAVA_PIN.md. No protocol or controller source changed. All work remains
uncommitted; no commit, tag, publishing or release was performed.

## Implemented behavior and search architecture

Search tokens is a labeled single-line Swing field above the logical-token list.
The browser holds the latest VaultState, current field text and normal rows; there
is no second domain database or historical result set. A document change filters
the latest projection. A state emission retains the query and recalculates rows.

Matching is a literal case-insensitive substring, normalized with Locale.ROOT,
of full TokenId hex or issuer/account of any complete semantic alternative.
Empty query matches every token. Whitespace is literal too; no trimming, regex,
glob, HTML, fuzzy matching, ranking or transliteration is performed. TOTP codes,
secret groups/data, revisions, client metadata and diagnostics are not searched.
Incomplete tokens match only their ID (or empty query). Unresolved references do
not affect matching. Search text is never logged, persisted, sent out, included in
exception messages, diagnostics or window titles; close clears the field.

VaultState.tokens() order is preserved. Each conflicted token remains one row;
matching an alternative never selects a winner or changes detail semantics.
Secret-only conflicts remain ordinary logical rows with the conflict indicator.
Counts say `N tokens` or `M of N tokens`, counting logical identities. Empty vault
text is `No tokens are currently observed.`; an empty filtered result says
`No tokens match this search.`. Waiting/observation/diagnostics remain separate.

Selection is keyed by full TokenId and resolved against the newest state. A still
visible identity survives emissions. Hiding or removing it clears selection and
detail. Clearing the filter does not restore selection, and no opening, filtering
or emission automatically selects the first/replacement row. Query changes that
leave the selection visible do not regenerate its TOTP or reset its detail.

## Keyboard, availability and focus

- Ctrl/Cmd+F focuses Search and selects all current query text.
- Escape within Search clears it; ordinary select-all/delete remains available.
- F5 invokes the same non-blocking Refresh Action as the button.
- Ctrl/Cmd+N invokes the same Create Token Action as the button.
- Normal JList arrows, Page Up/Down and Home/End bindings are untouched.
- Refresh/Create/Change Password and form navigation have ordinary mnemonics.

The platform mask comes from Toolkit.getMenuShortcutKeyMaskEx(), with a headless
Ctrl fallback. Bindings apply only in the Swing window. Shared Actions guard direct
invocation when disabled and use the existing callbacks. Create's enabled state
still comes from observation plus MutationGate availability; controllers retain
reservation checks. There is no second availability model or hidden editor queue.
No shortcut was added for tombstoning, resolution publication, publish-original,
retry/stop decisions, password submission or bypassing normal window close.

Initial Search focus is requested after showing the actual vault, in a deferred
callback guarded by isShowing(). Find is user driven and does not select a token.
No state render requests focus. The existing Review Latest decision focus remains;
TOTP and replay-latest updates do not request focus. Native focus acquisition is
not required by headless tests.

## Accessibility and dialog behavior

Search, logical list, detail, alternative selector, observation progress,
diagnostics, publication decision container and form status areas have descriptive
accessible names or descriptions. Buttons retain meaningful visible-text names:
Refresh, Create, Edit Token/Alternative, Resolve, Change Password, retry/stop and
AdditionalConflict decisions. Observation status keeps its current visible text as
its accessible name, with a static description. Merge input checkboxes retain
explicit Alternative labels. Token, merge (including custom fields), password-change,
launcher password and Search labels use setLabelFor.

Secret/password names describe the input, never its content. No entered password,
Base32 secret or existing hidden secret is put into accessible metadata. Controls
remain JPasswordField. Conflict detail remains textual with complete-alternative
count, differing field memberships, secret equality groups, causal heads and
unresolved-reference counts/IDs; color is not required.

TOTP label text remains accessible as current visible authentication material.
Only its static description explains the display; no code is stored in an explicit
name/description. Remaining-time bars are named. Existing rollover/selection/close
clearing is preserved, including clearing detached code labels. No history/event
logger or custom accessibility framework was added. Saving, password-change,
observation and closing status remain visible component text.

Owned token dialogs default to Save. Merge defaults to Continue at input selection
and Save during field resolution, switching back with Back. Password forms default
to Change; busy state disables submission. Editable ordinary values remain
single-line fields. Dialog Escape calls the existing Cancel path, with its existing
busy/retired checks, wiping and controller reservation cleanup. Escape cannot
interrupt submitted work or claim cancellation. No separate cleanup path was added.

AdditionalConflict retains Review Latest as default. Publish Original is never
default; its separate confirmation retains Cancel as the normal choice. No
AdditionalConflict Escape binding was added. Publication uncertainty clears the
root default and requires deliberate retry/stop activation; neither has a shortcut.

## Layout

Vault initial size is bounded by its graphics configuration's usable screen area,
so pack cannot expand it based on stored text. Owned token and merge dialogs use
the same screen-bound sizing and remain relative to their owner. Password dialog
retains compact content-based sizing, capped to the usable screen.

Token detail wraps inside a scroll pane; list starts at a useful divider position
with a minimum width. Multiple TOTP alternatives scroll within a bounded code area.
Token fields scroll and issuer/account have bounded column preferences. Merge
retains its scrollable resolution body with navigation outside the scroll pane.
AdditionalConflict choices stack vertically to remain reachable at narrower widths.
Diagnostics remain scrollable. No authoritative detail is silently truncated;
long technical sections remain available by scrolling. No screen-resolution,
position persistence or theme dependency was introduced.

## Tests

Added UsabilityTest with 8 headless tests covering literal/locale-independent
matching, ID/incomplete/any-alternative behavior, order/count/empty states,
query and identity survival, hidden-selection clearing, no auto-selection, action
maps and disabled Create, Find select-all, search Escape, associated labels,
secret/password metadata, default buttons, busy Escape guards and cancellation
wiping, merge step defaults, safe decision defaults, long issuer/account/client
text, many heads/references/diagnostics, scroll containers and current-code
accessibility/retirement. Public projection fakes reject unexpected API calls.

Final count: **151 tests**, including all **143 pre-M3b tests**, unchanged.
Existing lifecycle, gate, replay-latest, password retirement, partial-resolution,
exact-publication retry and NIO regression tests remain passing.

An initial compilation caught Action fields needing transient under -Xlint:all;
this was corrected. One initial new layout test located the uncertainty text
instead of detail; the test now clears that decision panel before checking detail.
No existing tests were removed or weakened.

## Validation and reproducibility

Validation was run with the supplied full JDK 25 environment and repository Gradle
9.8.0 wrapper. Nix executable and graphical DISPLAY are unavailable in this
container; `nix develop` and the requested native graphical smoke (all 18 steps)
could not be performed. Focus indication, native tab traversal, actual monitor
placement and interactive resizing still need human graphical review. No graphical
requirement entered CI. A non-fatal fontconfig configuration warning appeared in an
initial test run; Swing headless tests nevertheless completed.

| Command/check | Result |
| --- | --- |
| `git submodule status` | Exact pin; no prefix indicating divergence/uninitialized checkout |
| `./gradlew clean test build` | PASS, 24s; 151 tests, no failures/errors/skips |
| `./gradlew --no-daemon --no-build-cache --rerun-tasks clean test` | PASS, 35s; all 9 tasks executed |
| `./gradlew --offline --no-daemon --no-build-cache --rerun-tasks clean test` | PASS, 36s; all 9 tasks executed |
| `./gradlew --offline dependencies --configuration runtimeClasspath` | PASS; unchanged dependency tree below |
| `git diff --check` | PASS |
| Production classfile inspection and `verifyJava17Bytecode` | PASS; all 54 classes have major version 61, no preview bytecode |
| Existing test files | No changes; all 143 remain passing |
| Submodule worktree / gitlink | Clean / unchanged |

Full environment reports OpenJDK 25.0.4.1 and Gradle 9.8.0. Build scripts retain
--release 17, UTF-8, -Xlint:all, -Werror, strict locking and strict dependency
verification. Wrapper checksums, metadata, locks, Nix inputs and pin are unchanged.
Byte comparison against M3a confirmed all inspected reproducibility inputs; the
Windows wrapper is unchanged under its existing `.gitattributes` CRLF checkout
rule (Git blob LF vs working-tree CRLF), not generated churn.

### Dependency inventory, before and after

Identical production/runtime tree: desktop → pinned `dev.totipo:storage-nio` →
pinned core → `org.bouncycastle:bcprov-jdk18on:1.86`. Other production facilities
are JDK 17 APIs, Swing/AWT, time and concurrency. Zero added dependencies.

Desktop tests retain JUnit BOM/Jupiter/Platform 6.1.3, apiguardian 1.1.2,
jspecify 1.0.0 and opentest4j 1.3.0, alongside that same production tree. Zero added
test dependencies; no lock or verification metadata modifications.

## Production source audit

- Exactly one production `session.states().subscribe(subscriber)`, in unchanged
  VaultWindowController. StateSubscriber demand/replay and close ordering unchanged.
- Search uses projection accessors only; no protocol mutation/refresh/subscription,
  persistence, query history, diagnostics output or network path. No forbidden
  search field, alternative winner or sorting introduced.
- Refresh/Create buttons and shortcuts share guarded Action instances and enabled
  state. MutationGate/controller ownership is unchanged. No destructive one-key
  action or global hotkey; password submission remains ordinary form submission.
- No password/secret in accessible metadata. TOTP current text remains accessible
  with existing ephemeral code lifetime and no historical-code metadata.
- No render-driven focus request; only initial showing, Find and the existing
  AdditionalConflict review decision request focus.
- Literal JTextArea/JTextField presentation and disabled-HTML list/merge renderers
  remain. No HTML generated from user text; no search highlighting markup.
- No clipboard API, tray, notification, filesystem watcher, automatic refresh,
  persistence/preferences, new lifecycle subsystem, storage SPI/internal API or
  dependency added. Controllers, capabilities, captured bases, PartialResolution,
  exact retry, password retirement and session ownership are unchanged.

## Files added and changed

Added:

- `src/main/java/dev/totipo/desktop/ui/TokenSearch.java`: small matching helper.
- `src/main/java/dev/totipo/desktop/ui/SwingUsability.java`: guarded Actions,
  bindings, labels, dialog wiring and usable-screen sizing helpers.
- `src/test/java/dev/totipo/desktop/ui/UsabilityTest.java`: 8 structural/headless tests.
- This review report.

Changed:

- TokenBrowserPanel: search, counts/empty states, selection filtering, focus action,
  wrapped/scrolled detail and code area, accessibility.
- TokenPresentation: explicit complete-alternative count.
- VaultPanel: shared Refresh/Create Actions, shortcuts, accessibility and stacked
  AdditionalConflict choices.
- VaultFrame: bounded sizing, merge dialog wiring, initial post-show focus.
- TokenEditorPanel / TokenEditDialog: labels, mnemonics, scrollable fields,
  bounded field sizing, default Save and guarded Escape.
- MergeEditorPanel: associated resolution/custom labels, mnemonics, step defaults,
  Escape forwarding and accessible status.
- PasswordChangePanel / PasswordChangeDialog: labels/status, mnemonics, default
  Change, guarded Escape and compact bounded size.
- PasswordPrompt: launcher password label associations.
- README.md / ARCHITECTURE.md: concrete capabilities and presentation invariants;
  existing protocol/security caveats retained.

## Deviations and remaining validation

No intended functional scope deviation. Optional menu-shortcut+R was omitted;
F5 supplies Refresh. AdditionalConflict Escape was not added; existing explicit
Cancel preserves its capability-discard path. No new framework or dependency was
needed. Native graphical smoke and `nix develop` remain unavailable as described
above; headless structural tests are not a claim about actual screen-reader or
native window-manager behavior. No accessibility certification is claimed.

## Review worktree snapshot

`git status --short`:

```text
 M ARCHITECTURE.md
 M README.md
 M src/main/java/dev/totipo/desktop/ui/MergeEditorPanel.java
 M src/main/java/dev/totipo/desktop/ui/PasswordChangeDialog.java
 M src/main/java/dev/totipo/desktop/ui/PasswordChangePanel.java
 M src/main/java/dev/totipo/desktop/ui/PasswordPrompt.java
 M src/main/java/dev/totipo/desktop/ui/TokenBrowserPanel.java
 M src/main/java/dev/totipo/desktop/ui/TokenEditDialog.java
 M src/main/java/dev/totipo/desktop/ui/TokenEditorPanel.java
 M src/main/java/dev/totipo/desktop/ui/TokenPresentation.java
 M src/main/java/dev/totipo/desktop/ui/VaultFrame.java
 M src/main/java/dev/totipo/desktop/ui/VaultPanel.java
?? review/M3B_USABILITY_ACCESSIBILITY_REPORT.md
?? src/main/java/dev/totipo/desktop/ui/SwingUsability.java
?? src/main/java/dev/totipo/desktop/ui/TokenSearch.java
?? src/test/java/dev/totipo/desktop/ui/UsabilityTest.java
```

`git diff --stat` (tracked modifications; new untracked files are listed above):

```text
 ARCHITECTURE.md                                    | 31 +++++++++++++++
 README.md                                          | 13 +++++-
 .../dev/totipo/desktop/ui/MergeEditorPanel.java    | 13 ++++--
 .../totipo/desktop/ui/PasswordChangeDialog.java    |  3 +-
 .../dev/totipo/desktop/ui/PasswordChangePanel.java | 10 +++--
 .../java/dev/totipo/desktop/ui/PasswordPrompt.java |  5 +--
 .../dev/totipo/desktop/ui/TokenBrowserPanel.java   | 46 ++++++++++++++++++++--
 .../dev/totipo/desktop/ui/TokenEditDialog.java     |  3 +-
 .../dev/totipo/desktop/ui/TokenEditorPanel.java    | 24 ++++++-----
 .../dev/totipo/desktop/ui/TokenPresentation.java   |  1 +
 .../java/dev/totipo/desktop/ui/VaultFrame.java     |  7 ++--
 .../java/dev/totipo/desktop/ui/VaultPanel.java     | 33 +++++++++++-----
 12 files changed, 151 insertions(+), 38 deletions(-)
```

Ready for review and **M3c: explicit TOTP clipboard policy and safe copy behavior**.
