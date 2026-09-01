# Item 19 — Kilo-style typography: requirements

- Scope (user-selected): FULL Kilo model — transcript, composer input, tool
  cards, popup lists, dialogs (Question/Permission/PlanReview); DshDiffDialog
  already renders via platform diff components (nothing to align).
- Font rule (user-selected: match Kilo exactly): the transcript body uses the
  UI font FAMILY at the EDITOR font SIZE while code/tool content keeps the
  editor font family+size. Headers use JBFont.h4 bold; secondary text uses
  JBFont.small; tool args/raw meta use the editor (mono) font.
- Platform note (bytecode evidence, ideaIC 2025.1.3, 2026-09-01): Cmd/Ctrl+wheel
  zoom is a PER-EDITOR local override (EditorComponentImpl → EditorImpl.setFontSize
  → MyColorSchemeDelegate.myFontSize + PropertyChange "fontSize") — it never
  touches the global scheme, so the chat CANNOT see it (Kilo Code has the same
  limitation). The chat follows GLOBAL font-size changes: Settings → Editor →
  Font → Size → Apply, which goes through EditorColorsManagerImpl.setGlobalScheme
  → syncPublisher(EditorColorsManager.TOPIC). The handler therefore hops to the
  EDT (syncPublisher can deliver off the EDT) and we also subscribe
  LafManagerListener.TOPIC for theme changes (Kilo parity).
- Central snapshot: a DshEditorStyle object derives all fonts from the global
  editor color scheme; it is re-snapshotted on scheme change
  (EditorColorsManager.TOPIC) and applied to registered style targets without
  rebuilding Swing nodes (Kilo SessionEditorStyle / SessionEditorStyleTarget
  model, reimplemented).
- Typography roles:
  - transcript body (assistant text, user text, notices base, composer input):
    UI family, plain, editor size.
  - code surfaces (tool args, raw meta): editor family+size.
  - card headers (assistant/tool/user name): JBFont.h4 bold.
  - secondary text (status line, usage, thinking preview, popup detail,
    notice text): JBFont.small (notices keep italic/bold flavor).
- Popup lists (item 18 windows) read the snapshot at show time: bold primary
  labels use the platform bold font, details the small font, group labels
  small-bold gray.
- Tests: the font derivation is a pure function (no platform needed) and gets
  pure-JVM unit tests.
