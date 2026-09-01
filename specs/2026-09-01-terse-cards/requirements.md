# Item 21 — Terse input/output rendering: requirements

- Collapse scope (user-selected): tool args AND result collapse together
  behind the card header by default; one click expands the whole body.
- Collapsed view (user-selected): ONE gray preview line — the first non-blank
  line of the result, falling back to the pretty-printed args — truncated
  (160 chars + ellipsis).
- Auto-expand (user-selected): NEVER. Cards always start collapsed; a change
  of the tool NAME re-collapses an expanded card; streaming updates keep the
  current state.
- Header affordance: ▸/▾ arrow before the tool name; the whole header block
  (name + preview) is the click target with a hand cursor.
- Preserved: Diff button, raw-meta toggle, error label, assistant streaming
  text (untouched), item-19 typography roles (preview = small gray).
- Preview-line extraction is a pure function with pure-JVM tests.
