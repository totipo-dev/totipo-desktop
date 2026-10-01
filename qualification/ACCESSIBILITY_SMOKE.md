# Accessibility/keyboard smoke — UNQUALIFIED until executed

Record the native environment and package as for GUI_SMOKE.md. This is a modest
manual check, not certification. If a screen reader is exercised, record its
name/version; otherwise screen-reader behavior remains UNQUALIFIED.

- [ ] Keyboard-only create/open/search/select/create/edit/merge/password/close workflows work.
- [ ] Focus is visible, including in scrolled dialogs and after cancellation.
- [ ] Labels and accessible names identify controls; status/conflict text is understandable.
- [ ] Tab order is usable; keyboard navigation is not trapped.
- [ ] Conflict alternatives and explicit merge decisions can be distinguished without color alone.
- [ ] Password/secret controls are labeled, masked where intended, and not exposed in diagnostic text.
- [ ] Current visible TOTP and corresponding Copy action are identifiable; stale/hidden codes are not announced as current.
- [ ] Dangerous actions and conflict winners are not defaulted; ordinary dialog defaults are safe.
- [ ] If tested, screen reader can navigate labels, state changes and conflict controls; record actual results.
