# Native GUI smoke — UNQUALIFIED until executed

Record date, commit, VERSION, artifact checksum/store path, OS/distribution,
architecture, desktop/window manager (including X11/Wayland), runtime and display
scaling. Use disposable test vaults. Mark each item PASS/FAIL/UNQUALIFIED with
non-secret observations. A headless component test or Xvfb is not native qualification.
Run this with each package route claimed; see PACKAGED_LAUNCH.md.

- [ ] Application launches; launcher layout fits and Create/Open are visible.
- [ ] Create a disposable vault, close it, open it; correct window/session ownership.
- [ ] Search issuer/account/token ID, including conflicting alternatives; counts and empty states are clear.
- [ ] Keyboard navigation, Ctrl/Cmd+F, Escape search, F5, Ctrl/Cmd+N, arrows/Page/Home/End work.
- [ ] Normal TOTP rollover updates displayed codes/intervals without copying.
- [ ] Conflict display shows all alternatives without implying a winner.
- [ ] Create token and edit a selected alternative; validation and cancellation work.
- [ ] Merge uses two steps: deliberate alternative selection, then explicit field/secret choices.
- [ ] AdditionalConflict decision presentation is inspected using a controlled harness/fake if available; record method, otherwise UNQUALIFIED.
- [ ] Password dialog validates confirmation, cancels safely, and clears fields; successful change/reopen works.
- [ ] Stale/uncertain workflows require appropriate explicit decisions and do not imply freshness.
- [ ] Resize down/up, high DPI, long issuer/account/conflict text: controls remain reachable; scroll works.
- [ ] Focus remains visible/predictable across refresh, rollover, search, dialogs and cancellation.
- [ ] Window close affects only its session; application shutdown closes all sessions.
- [ ] No EDT freeze during create/open, writes, password change, observation or shutdown.

Do not record passwords, secrets, live TOTP codes or production vault paths.
