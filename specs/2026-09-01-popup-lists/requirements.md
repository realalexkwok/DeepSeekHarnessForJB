# Item 18 — Kilo-style popup lists for the composer tabs: requirements

- The Context / Action / Model bottom-tab popups become Kilo-style LIST
  popups instead of plain menus: rows with a name + gray description, check
  (Context) or radio (Action/Model) state glyphs, icons, a search field that
  filters as you type, Enter applies, Escape closes, clicking outside closes.
- Context rows: Current file / AGENTS.md (check state mirrors the toggles).
- Action rows: Ask / Code / Plan / Debug (2026-09-01 rename: Execute→Code,
  Fix→Debug) with advisory descriptions and the current action marked; the
  mode names are BOLD.
- Model rows: a bold **Model** group label over the model list, a bold
  **Effort** group label (Off/Low/High/Max) and bold **Settings…** as its OWN
  third group label — not a row under Effort (host feedback 2026-09-01);
  Model and Effort labels are not pickable, Settings… still opens the
  settings page when picked; current selections marked with a tick that lives
  in its OWN fixed column so row content stays aligned; picking applies the
  existing model/effort selection paths.
- Same visual language as the @-mention picker (JWindow-based, above the
  composer).
