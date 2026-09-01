# Item 20 — Multiline composer: requirements

- Key bindings (user-selected): Enter SENDS the prompt; Shift+Enter inserts a
  newline. The Shift+Enter binding is EXPLICIT (DefaultEditorKit.insertBreakAction),
  not an accident of the missing modifier match.
- Growth (user-selected): the composer grows with content up to a cap of
  MAX_COMPOSER_LINES = 10 lines, then scrolls internally. Height is measured
  with the View API (same technique as the transcript reflow in item 17b) and
  clamped: contentHeight.coerceIn(lineHeight, lineHeight * 10).
- Pasted multi-line text keeps every line break (JTextArea verbatim paste) and
  the composer grows to fit (up to the cap).
- The @-mention picker, mention insertions, and the item-19 style snapshot
  (applyStyle → composer font) stay intact; font changes and width changes
  re-measure the composer height.
- Pure height-clamp arithmetic is extracted into a testable function.
- Host follow-up (2026-09-01): the editor looked too narrow — (1) the border
  between the editor and the tab row is REMOVED (strip the platform
  DarculaScrollPaneBorder from the input scroll pane); (2) the focus frame
  surrounds the WHOLE composer block — editor + tab row — not the editor
  alone (Kilo PromptPanel.surface + paintChildren pattern: a ComposerShell
  paints the JBUI focus ring around itself when the input has focus; the ring
  width is reserved in the shell's border so the layout never jumps).
- Host follow-up (2026-09-01): the borderless editor gets a PLACEHOLDER hint —
  "Type here; use / or @, drop / paste files; ⏎ send, ⇧⏎ newline" — via the
  platform empty-text API with TextComponentEmptyText.setupPlaceholderVisibility
  so it stays visible while focused.
