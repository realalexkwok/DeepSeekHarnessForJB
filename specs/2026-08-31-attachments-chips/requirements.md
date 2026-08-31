# Item 15 (replanned) — Kilo-style context attachment chips: requirements

- A context-chips bar sits above the composer: one compact chip per active
  context source — Current file, AGENTS.md, and each `@`-mention in the input.
- Chips are collapsed by default; clicking a chip toggles a preview (title +
  truncated content); the X on a chip removes that context (mentions: the
  `@path` token is stripped from the input; file/AGENTS.md: their context
  checkbox is unchecked).
- The prompt still carries the resolved content for the agent (the chips are
  the user-facing compact view — Kilo's prompt-attachment model).
- Mention chips reflect the live input (added/removed as tokens change).
- KNOWN LIMITATION (host-verified, closed as cosmetic 2026-08-31): the initial
  Current-file chip does not appear until the first keystroke/focus event — the
  construction-time and displayability-time refreshes still lose the race with
  the tool-window layout. Recorded as unsolved; revisit with the 17b transcript
  rebuild.
