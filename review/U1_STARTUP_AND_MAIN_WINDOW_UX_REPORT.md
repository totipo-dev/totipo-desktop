# U1 — startup flow and main-window usability cleanup

## Baseline and scope

Baseline commit: `98bac56f906564358d8f49175eecc2592b0e6959` (after D1).
Before editing, `git status --short` was empty. Baseline `./gradlew clean test build`
passed, including `verifyMavenBoundary`, Java 17 bytecode verification,
qualification compilation, and distribution verification. Baseline test count:
**187**, with zero failures, errors, or skips. The baseline printed a fontconfig
configuration warning; the build and tests still succeeded.

Final desktop test count: **203**, with zero failures, errors, or skips (**+16**).
All changes remain uncommitted. Nothing was published, tagged, or released.

The checked-in D1 dependency boundary is Totipo Java **0.1.1**, rather than the
0.1.0 coordinates mentioned in the milestone instructions. U1 preserves the
actual baseline coordinates exactly; no version was changed or rolled back.

## Remembered vault and startup

`VaultPreferences` is a small desktop-owned interface with `lastVault`,
`setLastVault`, and `clearLastVault`. `JdkVaultPreferences` uses only JDK APIs:

- Preferences API: `java.util.prefs.Preferences.userRoot().node("/org/totipo/desktop")`.
- Exact node: `/org/totipo/desktop`.
- Exact key: `lastVault`.
- Only stored value: `path.toAbsolutePath().normalize().toString()`.
- No password, key, secret, or TOTP material is stored.

Preferences use the JDK's normal persistence and flushing behavior. Preference
access is best effort: an unavailable/denied preference service does not prevent
vault use. Invalid path syntax is discarded. Values exceeding the JDK Preferences
value limit cannot be remembered and do not prevent opening a vault.

The remembered location is updated only after Opened/Created returns a session,
a window/controller accepts ownership, and controller startup succeeds. Merely
choosing a directory never writes the preference. Authentication failure,
unavailable/invalid/absent vault, cancellation, unexpected operation failure,
window construction failure, failed window startup, and shutdown before session
acceptance do not replace the previous successful location.

Startup behavior:

| Remembered state | Result |
| --- | --- |
| No stored path | Compact launcher, with Open Vault and Create Vault |
| Existing readable/searchable directory | Launcher is skipped; password prompt for that path |
| Missing, non-directory, or inaccessible path | Clear preference, show launcher, and explain that the previously used vault could not be found |
| Direct password prompt cancelled | Return to launcher, preserving the remembered location |
| Directory usable but vault open fails | Existing result presentation, then launcher; preserve remembered location |

Directory metadata checks run on the existing application executor. Password
prompts, preference access, and all Swing updates run on the EDT. No new vault
filesystem I/O runs on the EDT. Startup shutdown is guarded so a pending path
check cannot open a vault after shutdown.

## Password/open flow

The password prompt shows the full absolute normalized vault path in a labeled,
read-only text field. The text remains selectable/copyable, and a long path can
be scrolled within the field; the underlying displayed value is not abbreviated.
The password input is labeled Password. Open uses an **Open** submit button;
creation uses **Create** and retains password confirmation.

The password field requests initial focus when the dialog opens. The root default
button and password-field Enter actions submit. Cancel, Escape, and window close
cancel. Existing character-array ownership, confirmation matching, cancellation,
and best-effort password clearing remain intact. The dialog is owned by the
launcher window. The open vault window title remains `Totipo — /path/to/vault`.

## Main window

The menu structure is deliberately small:

```text
Vault
  Change Vault…
  Change Password…
```

The menu and items have mnemonics and use standard Swing menu traversal. Change
Password reuses the existing controller/dialog callback and mutation availability
rules. It is absent from the primary content area. Both menu actions are disabled
when the window begins closing.

Change Vault shows the launcher and immediately starts the directory/password
flow. The current window and session remain available while choosing/opening the
replacement. Cancellation or failure hides the launcher and retains the current
window, session, and preference. Once the replacement successfully opens and
starts, the remembered path updates and the previous controller closes through
its existing cleanup path. The old session is closed exactly once on its session
executor; editor retirement and clipboard/session cleanup remain controller-owned.
There is no in-place session replacement or general controller refactor. Closing
the last vault window returns to the compact launcher.

Refresh and Create Token now share a leading-aligned `FlowLayout` row with a
10-pixel gap. Existing shortcuts and action availability remain unchanged.
Main content uses 16-pixel `EmptyBorder` padding, 12-pixel gaps between sections,
and 8–10-pixel gaps between adjacent controls. Empty notification, status, and
save-decision containers are hidden rather than padded with blank labels. The
permanent lower diagnostics area has been removed, leaving more space for the
existing token browser. The launcher retains its compact existing layout.

The vault frame consumes the panel's explicit **900 × 600** minimum-size contract.
Its preferred/initial target is **1000 × 700**, bounded by available screen space
through the existing sizing helper. It remains resizable and is not maximized.
Native window-manager enforcement and small-screen usability require manual checks.

`ApplicationFonts.install()` runs centrally on the EDT before UI construction.
Each existing Look & Feel font is derived with **+2 logical points** and retained
as a `FontUIResource`. Font styles and the active Look & Feel are preserved. There
is no hard-coded family, per-control font assignment, or new dependency.

## Diagnostics and notifications

The permanent Local observation diagnostics section and its raw code text area
are absent. The immutable state and controller diagnostic data remain available
internally. No protocol/storage diagnostics were deleted or reclassified.

`NotificationBanner` is a compact wrapping warning area at the top of the main
content. It is invisible when empty and reserves no visible banner space.
Warnings are driven by existing evidence and results:

- State diagnostics or Finished.hasDiagnostics: “Some vault data could not be read. Refresh to try again.”
- Any token's existing semantic `hasConflict()` flag: “A token has conflicting versions and needs attention.”
- Existing additional-conflict decision: “The vault changed while saving. Review the token before continuing.”
- Uncertain or abandoned save: a reminder that the change may already have been saved.
- Classified operation/preparation/cleanup failures: concise warning text that preserves known nonpublication versus an undetermined save outcome.

`VaultView.writeWarning(detail, userMessage)` preserves internal detail for
nonvisual views while the Swing frame renders only the user-facing message.
The existing inconsistent-draft case has a separate desktop presentation outcome
so its definite nonpublication warning does not depend on parsing diagnostic text.
No protocol result type or operation behavior was changed.

Routine reading progress uses “Reading vault…” and a progress bar, then disappears
when finished. Processed-object counts and finished-observation announcements are
omitted from normal UI. Successful save acknowledgements become unobtrusive
“Saved.” text; they never activate the warning banner. Pending original-resolution
saves use “Saving…”. Diagnostics/conflict warnings update with new state; abandoned
save warnings survive refresh and later successful saves. Operation warnings remain
until a subsequent routine save status replaces them. Save-decision explanations
use ordinary save language while retaining the exact-change retry, no re-read or
adjustment, non-undo, and duplicate-create caveats and existing actions.

The token-detail layout, alternatives, raw ID presentation, issuer/account
hierarchy, and selected-token hierarchy were not redesigned. All alternatives
and conflict-resolution actions remain present.

## Production sources

| Source | Change |
| --- | --- |
| `DesktopApplication.java` | Inject preference store; asynchronous startup path check; remember only successful opens; hide/return to launcher; replacement flow |
| `VaultPreferences.java` (new) | Desktop-owned remembered-location boundary |
| `JdkVaultPreferences.java` (new) | Exact Preferences node/key and normalized-path implementation |
| `TotipoDesktop.java` | Install application fonts before creating UI |
| `VaultWindowController.java` | Return startup success while retaining existing failure cleanup |
| `TokenWriteController.java` | Route existing warning outcomes to presentation with user-facing wording; distinguish known draft nonpublication for presentation |
| `ui/LauncherView.java`, `ui/LauncherFrame.java` | Path-aware password prompt and hide-window presentation hook |
| `ui/PasswordPrompt.java` | Read-only path, Open/Create default buttons, explicit focus/Enter/Escape wiring |
| `ui/VaultView.java` | Change Vault and classified warning presentation hooks |
| `ui/VaultFrame.java` | Install menu; minimum/preferred sizing; route menu and warning callbacks |
| `ui/VaultPanel.java` | Horizontal actions, consistent spacing, menu contents, no diagnostics pane, semantic warnings and unobtrusive status |
| `ui/NotificationBanner.java` (new) | Hidden-when-empty wrapping warning component |
| `ui/ApplicationFonts.java` (new) | Central Look & Feel-derived font enlargement |

## Tests

- `RememberedVaultTest` (new, 12 tests): empty preference; direct remembered-password flow; missing/non-directory stale paths; normalized success write after handoff; authentication/invalid/unavailable/absent failures; directory/password cancellations; startup password cancellation; Change Vault cancellation/failure preserving old session; successful replacement closing old session exactly once; failed window startup; isolated JDK store and cleanup; shutdown during startup.
- `ApplicationFontsTest` (new): derives fonts from current defaults, adds two points once, retains style, restores UI defaults; no fixed family-name assumptions.
- `PasswordPromptTest`: full read-only selectable path, labels/accessibility, field cleanup; existing matching/wiping checks retained.
- `VaultPanelTest`: progress behavior, routine completion hidden, banner visibility and clearing, menu actions/mnemonics, absence of content-area Change Password, shared horizontal action parent, minimum-size contract consumed by the frame, semantic conflict, additional-conflict, uncertain/abandoned-save and operation warning behavior.
- `PasswordBrowserTest`: password menu item retains the original reservation/availability behavior while selection, TOTP, refresh, and editing guards remain live.
- `PublicationPanelTest`: existing retry/stop behavior and sticky uncertain-save notice, with updated plain-language assertions.
- `UsabilityTest`: diagnostics pane absent, warning visible, token-detail assertions target the browser rather than the new banner; existing accessibility, shortcut, scrolling, and conflict behavior retained.
- `TestSupport`: deterministic launcher visibility, prompt path, directory selection and Change Vault callback observations.

Normal application-flow tests inject an in-memory store (older lifecycle tests use
a no-op test store). The JDK-store test uses only a random isolated
`/org/totipo/desktop-tests/<UUID>` node and removes the node and flushes the root in `finally`; tests do
not read or write the production preference node. Swing tests are headless
structure/state tests, not screenshot tests. They do not establish subjective
visual quality or native window behavior.

## Validation

| Command | Result |
| --- | --- |
| Baseline `./gradlew clean test build` | PASS; 187 tests, zero failures/errors/skips; Maven boundary green |
| Final `./gradlew clean test build` | PASS; 203 tests, zero failures/errors/skips; Maven boundary green; bytecode, qualification compilation and distribution checks green |
| `./gradlew --no-daemon --no-build-cache --rerun-tasks clean test` | PASS; 203 tests, zero failures/errors/skips; compilation and tests rerun |
| `./gradlew --offline --no-daemon --no-build-cache --rerun-tasks clean test` | PASS; 203 tests, zero failures/errors/skips; compilation and tests rerun offline |
| `./gradlew installDist verifyDistribution verifyDistributionArchives` | PASS; installed distribution and both archives verified |
| `./gradlew verifyMavenBoundary` | PASS; external Totipo Maven compile/runtime boundary verified again |
| `git diff --check` | PASS |
| Diff check of build, settings, Gradle wrapper/locks, dependency metadata/cache, Maven configuration and Nix/package files | No changes |

The runtime inventory remains exactly the desktop JAR, `totipo-core-0.1.1.jar`,
`totipo-storage-nio-0.1.1.jar`, and `bcprov-jdk18on-1.86.jar`. The dependency graph,
released Java coordinates, BC version, wrapper, repositories, locks, verification
metadata, and `package-deps.json` are unchanged. No dependency-update machinery or
cache regeneration was run. No Nix commands were run: the existing package source
filter already includes the entire `src` tree, including all new Java files.

No Totipo protocol behavior, storage/vault semantics, vault persistence format,
password-change semantics, token search/selection/code/countdown/copy/clipboard
clearing, create/edit/merge behavior, or session cleanup semantics were changed.
The new Preferences value is desktop application state, separate from vault data.
Maven/Nix architecture and all dependency/package configuration remain unchanged.

## Manual operator checks — still required

These checks have not been performed. Automated tests do not establish subjective
UI quality. Use a disposable vault and verify:

1. First run shows launcher.
2. Successful vault open remembers that vault.
3. Restart goes directly to password dialog.
4. Password dialog shows exact vault path.
5. Change Vault works.
6. Cancelling Change Vault keeps current vault.
7. Change Password is in menu.
8. Refresh/Create Token are side-by-side.
9. Spacing feels reasonable.
10. Font size is slightly larger.
11. Diagnostics pane is gone.
12. Warning banner only appears for actual warnings/errors.
13. Main window cannot collapse below a usable size.

Also verify initial password focus, Enter submission, Escape/window-close
cancellation, menu keyboard traversal, full-path copying/scrolling, screen-reader
announcements, and long-warning wrapping in the target desktop environment.

## Working-tree snapshot

The following snapshots include this untracked report. `git diff --stat` is the
literal Git command output and excludes untracked files; new sources/tests are
listed above and visible in `git status --short`. No files are staged.

`git status --short`:

```text
 M src/main/java/org/totipo/desktop/DesktopApplication.java
 M src/main/java/org/totipo/desktop/TokenWriteController.java
 M src/main/java/org/totipo/desktop/TotipoDesktop.java
 M src/main/java/org/totipo/desktop/VaultWindowController.java
 M src/main/java/org/totipo/desktop/ui/LauncherFrame.java
 M src/main/java/org/totipo/desktop/ui/LauncherView.java
 M src/main/java/org/totipo/desktop/ui/PasswordPrompt.java
 M src/main/java/org/totipo/desktop/ui/VaultFrame.java
 M src/main/java/org/totipo/desktop/ui/VaultPanel.java
 M src/main/java/org/totipo/desktop/ui/VaultView.java
 M src/test/java/org/totipo/desktop/TestSupport.java
 M src/test/java/org/totipo/desktop/ui/PasswordBrowserTest.java
 M src/test/java/org/totipo/desktop/ui/PasswordPromptTest.java
 M src/test/java/org/totipo/desktop/ui/PublicationPanelTest.java
 M src/test/java/org/totipo/desktop/ui/UsabilityTest.java
 M src/test/java/org/totipo/desktop/ui/VaultPanelTest.java
?? review/U1_STARTUP_AND_MAIN_WINDOW_UX_REPORT.md
?? src/main/java/org/totipo/desktop/JdkVaultPreferences.java
?? src/main/java/org/totipo/desktop/VaultPreferences.java
?? src/main/java/org/totipo/desktop/ui/ApplicationFonts.java
?? src/main/java/org/totipo/desktop/ui/NotificationBanner.java
?? src/test/java/org/totipo/desktop/RememberedVaultTest.java
?? src/test/java/org/totipo/desktop/ui/ApplicationFontsTest.java
```

`git diff --stat`:

```text
 .../org/totipo/desktop/DesktopApplication.java     |  73 +++++++++-
 .../org/totipo/desktop/TokenWriteController.java   |  23 +--
 .../java/org/totipo/desktop/TotipoDesktop.java     |   5 +-
 .../org/totipo/desktop/VaultWindowController.java  |   3 +-
 .../java/org/totipo/desktop/ui/LauncherFrame.java  |   3 +-
 .../java/org/totipo/desktop/ui/LauncherView.java   |   3 +-
 .../java/org/totipo/desktop/ui/PasswordPrompt.java |  72 +++++++---
 .../java/org/totipo/desktop/ui/VaultFrame.java     |   7 +-
 .../java/org/totipo/desktop/ui/VaultPanel.java     | 159 ++++++++++++++-------
 src/main/java/org/totipo/desktop/ui/VaultView.java |   3 +
 src/test/java/org/totipo/desktop/TestSupport.java  |  15 +-
 .../org/totipo/desktop/ui/PasswordBrowserTest.java |   4 +-
 .../org/totipo/desktop/ui/PasswordPromptTest.java  |  12 ++
 .../totipo/desktop/ui/PublicationPanelTest.java    |  10 +-
 .../java/org/totipo/desktop/ui/UsabilityTest.java  |  12 +-
 .../java/org/totipo/desktop/ui/VaultPanelTest.java | 120 +++++++++-------
 16 files changed, 364 insertions(+), 160 deletions(-)
```
