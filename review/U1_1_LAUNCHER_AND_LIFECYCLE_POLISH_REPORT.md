# U1.1 — launcher, vault selection, and application lifecycle polish

## Baseline and scope

Baseline commit (committed U1): `efa7d446c7338ae1fa0b1f0ccb19d846702a412e`.
Before editing, `git status --short` was empty. Baseline
`./gradlew clean test build` passed, including `verifyMavenBoundary`, Java 17
bytecode verification, qualification compilation, and distribution verification.
Baseline: **203 tests**, zero failures, errors, or skips. The baseline printed a
fontconfig configuration warning without failing the build.

After: **209 tests**, zero failures, errors, or skips (**+6**).
All changes are uncommitted. Nothing was published, tagged, or released.

Production changes are confined to launcher layout, password prompt copy,
directory chooser setup, menu construction, and application quit wiring.
`VaultWindowController` and protocol-facing operation code are unchanged.
The main token browser/detail area and U1 main-window layout remain unchanged.

## Launcher

The window title remains **Totipo**; the redundant Totipo content heading was
removed. The explanatory text now reads **Open an existing vault or create a new
one.** Actions are **Open Existing Vault…** and **Create New Vault…**.

The panel targets 560 × 280 pixels, growing when its font/layout metrics need
more room. Its minimum is 500 × 250. Outer padding is 28 pixels vertically and
32 horizontally, with 16 pixels between rows and comfortable button margins.
The frame packs the content, uses the existing screen-fit helper to respect
available screen space, and caps its minimum to that fitted size.

Open is the root default button. Open/Create have O/C mnemonics; their existing
action order and busy-state disabling are preserved. Restoring the ready state
after cancellation uses the new explanatory text.

## Password dialog

Open retains the **Open Vault** title and now presents:

```text
Enter the password for:
/absolute/path/to/vault
Password
[password field]
[Cancel] [Open]
```

Create uses **Create a vault in:** above the path and retains its password
confirmation and Create action. The complete normalized path remains in a
non-editable, focusable text field; it is selectable and horizontally scrollable.
The wording also supplies the path's accessible label.

Initial password focus, default-button/Enter submission, Escape/Cancel/window
close cancellation, password validation, confirmation matching, character-array
ownership, and clearing are unchanged. No protocol terminology was added.

## Directory chooser

`VaultDirectoryChooser` is a small standard `JFileChooser` subclass shared by
Open and Create. No custom filesystem browser or native chooser was introduced.

| Configuration | Before | After |
| --- | --- | --- |
| Selection mode | `DIRECTORIES_ONLY` | `DIRECTORIES_ONLY`, preserved |
| Multiple selection | Disabled | Disabled, preserved |
| All Files filter | Default enabled | Disabled; no file-type filters added |
| Open title | Look-and-feel default | Select Vault Folder |
| Create title | Same generic open chooser | Select New Vault Folder |
| Approve action | Default Open | Select Folder |
| Preferred content size | Look-and-feel default | 800 × 550 |
| Initial location | Default | Current/remembered vault parent when available; otherwise default |

Approve tooltips identify whether the selected folder will be opened or used for
creation. The chooser's accessible name matches its title. Standard directory
navigation, location controls, and selected-folder display remain available;
directory-only handling and disabled All Files filtering remove file-type
choices without manipulating look-and-feel internals.

The application passes the current vault location for Change Vault and remembers
the last successfully opened location in memory for subsequent chooser use.
Startup also supplies the remembered location, including a stale location whose
existing parent may still be useful. The chooser starts in the nearest existing
parent. Open preselects the current vault when it exists. Create does not
preselect an existing vault. Without a useful location, JFileChooser's normal
default remains in effect. No user-specific location is hard-coded and no new
preference is stored.

JFileChooser packs its preferred size through `createDialog`; the existing
screen-fit helper then clamps the window, including decorations, to available
screen bounds before centering relative to its owner. No fixed screen
coordinates, maximization, or forced minimum chooser size is used. Smaller-screen
and look-and-feel usability remain manual checks.

## Menus and application lifecycle

```text
File
  Exit
Vault
  Change Vault…
  Change Password…
```

Menus use normal Swing `JMenu`/`JMenuItem` APIs. File/Vault/Exit have F/V/X
mnemonics; existing Vault action mnemonics remain.

`DesktopApplication` installs its existing `shutdown` callback through a small
`VaultView.quitAction` presentation hook. `VaultFrame` routes both its
window-closing event and File → Exit to that same callback. Controller session
retirement retains its existing close path; standalone controller views can
still use that path without an application owner.

Shutdown remains EDT-owned and idempotent. It marks the application shutting
down before invoking the existing clipboard cleanup and closing all owned
controllers. Each controller's close guard cancels its subscription, retires
edit/password UI and existing session-related resources, and closes its one
VaultSession on its session executor exactly once. That executor shuts down,
then the vault frame is disposed on the EDT. Owned dialogs are retired/disposed
through existing cleanup. The application waits for all controllers and any
pending launcher operation, shuts down its executor, and disposes the launcher
once. Shutdown guards prevent launcher redisplay. No `System.exit` is used;
normal executor shutdown and Swing disposal allow natural JVM termination.

Change Vault remains separate: the current session/window stays alive during
selection and password entry. Directory/password cancellation or failed open
returns to the existing vault without shutting down the application. Only a
successful new controller startup updates the remembered preference and closes
the previous controller. Cancelling the remembered-vault password prompt during
startup still returns to the launcher, preserving the remembered location.
Stale remembered locations still produce the existing warning and launcher.

## Tests added and updated

- `ApplicationQuitTest`: two tests exercise the actual File menu panel and a
  headless window boundary wired to the application quit callback. A latch holds
  session close pending; repeated Exit/window-close/launcher-close callbacks
  verify one session close, one subscription cancel, one window disposal, one
  launcher disposal, executor shutdown, and no launcher redisplay. No test exits
  the JVM.
- `LauncherPanelTest`: one new test checks clear actions, removal of the redundant
  heading, meaningful padding, minimum/preferred dimensions, row spacing, button
  margins, mnemonics, and the default Open action.
- `VaultDirectoryChooserTest`: three new tests check Open/Create configuration,
  titles, approve text, directory-only mode, disabled All Files filtering,
  preferred size, default location, current-vault parent/preselection, and stale
  path fallback. No chooser dialog or native UI is launched.
- `VaultPanelTest`: existing menu test now verifies File/Exit, Vault actions,
  mnemonics, and the retained U1 action row and minimum dimensions.
- `PasswordPromptTest`: updated accessible path-label expectation; existing path
  and password clearing checks remain.
- `DesktopApplicationTest`: main-window close now waits for application disposal
  and confirms executor shutdown.
- `RememberedVaultTest`: Change Vault cancellation additionally confirms the
  application remains alive and receives the current chooser location. Existing
  remembered startup, stale warning, cancellation/failure preference, and
  successful replacement tests remain green.
- `ClipboardLifecycleTest`: independent session retirement now uses publisher
  completion, preserving its clipboard ownership assertions after user
  window-close acquired application quit semantics. Clipboard code is unchanged.
- Headless launcher/window fixtures record chooser purpose/location and disposal
  counts and expose the application quit hook.

Automated checks cover behavior and configuration; they do not establish
subjective visual quality or actual desktop process termination.

## Validation

| Command | Result |
| --- | --- |
| Baseline `./gradlew clean test build` | PASS; 203 tests |
| Final `./gradlew clean test build` | PASS; 209 tests; Maven boundary, bytecode, qualification, distribution checks passed |
| `./gradlew --no-daemon --no-build-cache --rerun-tasks clean test` | PASS; 209 tests |
| `./gradlew --offline --no-daemon --no-build-cache --rerun-tasks clean test` | PASS; 209 tests |
| `./gradlew installDist verifyDistribution verifyDistributionArchives` | PASS; runtime inventory, notices, launch scripts, Java 17 classes, ZIP/TAR contents verified |
| `git diff --check` | PASS; rechecked after report completion |

Intermediate validation found a serialization lint warning on the quit callback
and an existing clipboard test's former window-close assumption. The callback
is now transient and the test uses session completion; the full build and fresh
tests subsequently passed.

No protocol, storage, password-validation, token, clipboard, or conflict semantics
changed. No Totipo Java dependency or Maven/Nix architecture changed. The
dependency graph is unchanged: `org.totipo:totipo-storage-nio:0.1.1` directly,
`org.totipo:totipo-core:0.1.1` transitively, and BC 1.86 at runtime. Build files,
lockfiles, verification metadata, `package-deps.json`, and Nix files have no diff.
No Nix dependency cache was regenerated and no dependency was added.

## Operator checklist — manual execution pending

Use disposable vaults and the existing isolated Preferences guidance when
testing fresh startup. These items have not been marked complete by automation.

1. [ ] Fresh startup launcher is comfortably sized and clear, with one Totipo
   title, an explanation, and two comfortable actions.
2. [ ] Open Existing Vault opens a directory-specific chooser titled Select
   Vault Folder, with Select Folder as its approve action.
3. [ ] Chooser is large enough to navigate comfortably, including on a smaller
   display; navigating into folders before selecting one works.
4. [ ] Chooser does not show confusing All Files/file-selection behavior; the
   current location and selected folder are clear.
5. [ ] Password prompt clearly identifies which location is being unlocked;
   long paths remain selectable/scrollable and password has initial focus.
6. [ ] File → Exit is present and reachable by keyboard.
7. [ ] Vault → Change Vault… and Change Password… remain present and reachable.
8. [ ] Closing the main window exits Totipo after normal cleanup; the process
   terminates naturally.
9. [ ] Closing the main window does not reopen the launcher.
10. [ ] File → Exit performs the same quit/cleanup behavior, including with an
    owned token/password editor open.
11. [ ] Change Vault directory/password cancellation returns to the existing
    vault; failed open also retains it; successful replacement opens the new
    window and closes the old session.
12. [ ] Restart with a remembered valid vault still skips the launcher.
13. [ ] Cancelling the startup remembered-vault password prompt returns to the
    launcher; a stale remembered location shows the existing warning.
14. [ ] Create New Vault uses Select New Vault Folder and Select Folder with
    directory-only navigation and comfortable dimensions; confirmation and
    empty-password handling retain their existing behavior.
15. [ ] Enter submits and Escape/Cancel cancels password dialogs; launcher
    default action, focus order, and mnemonics work. U1 main-window padding,
    larger font, warning banner, action row, hidden diagnostics, and minimum
    size remain intact.

## Review state

`git status --short` and `git diff --stat` are recorded below after validation.
The standard diff stat excludes untracked files, which are listed in status.

```text
$ git status --short
 M src/main/java/org/totipo/desktop/DesktopApplication.java
 M src/main/java/org/totipo/desktop/ui/LauncherFrame.java
 M src/main/java/org/totipo/desktop/ui/LauncherPanel.java
 M src/main/java/org/totipo/desktop/ui/LauncherView.java
 M src/main/java/org/totipo/desktop/ui/PasswordPrompt.java
 M src/main/java/org/totipo/desktop/ui/VaultFrame.java
 M src/main/java/org/totipo/desktop/ui/VaultPanel.java
 M src/main/java/org/totipo/desktop/ui/VaultView.java
 M src/test/java/org/totipo/desktop/ClipboardLifecycleTest.java
 M src/test/java/org/totipo/desktop/DesktopApplicationTest.java
 M src/test/java/org/totipo/desktop/RememberedVaultTest.java
 M src/test/java/org/totipo/desktop/TestSupport.java
 M src/test/java/org/totipo/desktop/ui/LauncherPanelTest.java
 M src/test/java/org/totipo/desktop/ui/PasswordPromptTest.java
 M src/test/java/org/totipo/desktop/ui/VaultPanelTest.java
?? review/U1_1_LAUNCHER_AND_LIFECYCLE_POLISH_REPORT.md
?? src/main/java/org/totipo/desktop/ui/VaultDirectoryChooser.java
?? src/test/java/org/totipo/desktop/ApplicationQuitTest.java
?? src/test/java/org/totipo/desktop/ui/VaultDirectoryChooserTest.java

$ git diff --stat
 .../org/totipo/desktop/DesktopApplication.java     | 10 +++++++--
 .../java/org/totipo/desktop/ui/LauncherFrame.java  | 10 +++++----
 .../java/org/totipo/desktop/ui/LauncherPanel.java  | 26 ++++++++++++++++------
 .../java/org/totipo/desktop/ui/LauncherView.java   |  2 +-
 .../java/org/totipo/desktop/ui/PasswordPrompt.java |  2 +-
 .../java/org/totipo/desktop/ui/VaultFrame.java     |  8 ++++++-
 .../java/org/totipo/desktop/ui/VaultPanel.java     |  6 +++++
 src/main/java/org/totipo/desktop/ui/VaultView.java |  2 ++
 .../org/totipo/desktop/ClipboardLifecycleTest.java | 11 ++++++---
 .../org/totipo/desktop/DesktopApplicationTest.java |  3 ++-
 .../org/totipo/desktop/RememberedVaultTest.java    |  2 ++
 src/test/java/org/totipo/desktop/TestSupport.java  | 19 ++++++++++++----
 .../org/totipo/desktop/ui/LauncherPanelTest.java   | 20 +++++++++++++++++
 .../org/totipo/desktop/ui/PasswordPromptTest.java  |  2 +-
 .../java/org/totipo/desktop/ui/VaultPanelTest.java | 10 ++++++++-
 15 files changed, 107 insertions(+), 26 deletions(-)
```
