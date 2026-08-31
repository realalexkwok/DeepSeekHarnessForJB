# Item 14 — Composer @-mentions: requirements

- Typing `@` in the composer opens a file picker popup listing project files
  by their FULL workspace-relative path (Kilo look & feel, kilocode's
  KiloPromptCompletionProvider — MIT); the typed prefix filters (name or path),
  name matches rank first; Enter/click insert `@<relative-path> ` with a
  trailing space; Escape dismisses; ↑/↓ navigate.
- Fix rounds (2026-08-31, host-verified): the popup is a non-focusable JWindow
  (JPopupMenu stole keyboard focus); the trigger scans the whole text for `@`
  (caret lags behind the first typed character); insertion captures the span
  BEFORE hiding the picker and uses the PROJECT-ROOT-relative path; the picker
  is a FLAT Kilo-style completion (full relative paths, prefix filter, name
  matches ranked first — the earlier hierarchical browser showed only names and
  made the agent search for files).
- At send time the prompt assembly resolves each `@<path>` token to the file's
  content and appends a `[File: <path>]` context section per mention (same
  shape as the existing current-file context); the section tells the model the
  content is already provided so it does not search for the file again.
- Bundled-carrier fix (with item 12): extraction marks EVERY artifact
  executable — the ripgrep sidecar came out of the jar without the exec bit,
  which broke the harness glob tool ("ripgrep launch failed") on the bundled
  carrier.
- Safety: mentions are workspace-confined via canonical-path checks, capped at
  10 files and 50 KB per file.
- Pure parts are unit-tested (token parsing + assembly); the popup is manual.
