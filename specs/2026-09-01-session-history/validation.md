# Item 22 — validation

## Automatic
- `./gradlew test` green; `./gradlew buildPlugin` green; artifact path
  reported.
- Pure-JVM HistoryStore tests: line round-trip, index newest-first, title set
  once, hasContent, empty/missing file tolerance.

## Manual (host machine)
- The tool-window TITLE BAR shows icon-only New Session → History buttons at
  the level of the built-in vertical-… (Options) button; the vertical-… menu
  contains Settings, which opens the settings page; the panel header keeps
  only Stop + PLAN badge.
- The History button swaps the chat for the IN-WINDOW history view (Back
  button + search field + list); Back restores the chat; no popup appears.
- Each history row has Rename (pencil) and Delete (trash) icon buttons:
  rename edits inline (Enter/blur commits, Escape cancels, blank ignored);
  delete confirms via dialog and removes the session from the list and disk.
- The header New session button stops a running turn, clears the transcript
  and composer, and the NEXT send creates a fresh history entry (the old one
  stays in the list).
- Run a chat; restart the IDE; the empty chat shows the past session in
  Recent sessions (title + relative time, newest first).
- Opening it replays the transcript (tool cards still terse) at the top of
  the scroll.
- Sending after a replay continues the SAME history entry (no new duplicate
  entry, updated time moves to the top).
- Multiple sessions are listed; the header History button opens the same
  list and opens sessions from it.
- No Continue button is offered (documented limitation: the pinned SDK server
  cannot resume harness memory).

## Verification rounds (host)
1. Round 1 (2026-09-01): blocking feedback — needed a way to start a new
   conversation → New session button added.
2. Round 2 (2026-09-01): New session left the old session's title visible on
   top → Recent list now shows only on a freshly opened panel (clean chat
   after New session).
3. Round 3 (2026-09-01): Kilo-parity follow-up — icon-only buttons, history
   INSIDE the main window (not a popup), per-item Rename (inline) + Delete
   (confirmed dialog) → implemented (AllIcons.General.Add / Vcs.History /
   Actions.Back / Actions.Edit / Actions.GC).
4. Round 4 (2026-09-01): the icon buttons must sit in the tool-window TITLE
   BAR at the level of the vertical-… Options button → DshToolWindowFactory
   uses setTitleActions (New Session, History) + setAdditionalGearActions
   (Settings). Verified ✓. Artifact
   build/distributions/DeepSeekHarnessForJB-0.1.0.202609020819.zip.
