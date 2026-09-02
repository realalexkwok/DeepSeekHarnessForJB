# Item 22 — Session history: requirements

- Storage (user: "refer to Kilo Code"): Kilo keeps sessions PROJECT-scoped with
  a per-session log plus an index of metadata (id, title, created, updated).
  Our adaptation: a per-project store under `.idea/dsh/history/` — one
  append-only `<id>.jsonl` per session (prompt echoes + raw session.event
  JSON lines) and `index.json` with the session metadata. `.idea` is
  gitignored by convention, so history travels with the checkout but not VCS.
  One store, mode-agnostic — the future Agent mode shares it.
- Session identity: one history entry per CONVERSATION. A NEW SESSION
  button in the header is the user-facing trigger (host feedback 2026-09-01):
  it stops any running turn, clears the transcript + composer, and rotates to
  a fresh entry. The send-time rotation rule remains as a safety net (rotate
  only when the transcript is empty and the current entry has content);
  sending after a replay appends to the replayed entry (the log continues).
- UI (user: "refer to Kilo Code"): Kilo's empty-state RecentsList — when the
  transcript is empty and history exists, the chat shows a Recent sessions
  list (bold title = first prompt, gray relative time, newest first, limit 5,
  click to open).
  Host feedback 2026-09-01: the Recent list shows ONLY on a freshly opened
  panel; after New session the chat stays CLEAN (history stays reachable via
  the History button).
- Header buttons follow-up (host feedback 2026-09-01, Kilo parity): New
  session + History become ICON-ONLY buttons (AllIcons.General.Add /
  AllIcons.Vcs.History, tooltips "New Session"/"History"). Host feedback
  2026-09-01 (round 2): they must sit in the tool-window TITLE BAR at the
  level of the built-in vertical-… (Options) button — implemented exactly
  like Kilo: DshToolWindowFactory uses ToolWindow.setTitleActions(New Session,
  History) and ToolWindow.setAdditionalGearActions(Settings
  AllIcons.General.GearPlain → settings page); the panel's own header row
  keeps only Stop + the PLAN badge.
- History management follow-up (host feedback 2026-09-01, Kilo parity): the
  history list lives INSIDE the main window — the History button swaps the
  CENTER between the transcript and a history view (Back button
  AllIcons.Actions.Back + a search field + the list), never a popup; each row
  carries Rename (AllIcons.Actions.Edit) and Delete (AllIcons.Actions.GC)
  icon buttons — rename edits inline in the row (Enter/blur commits, Escape
  cancels; HistoryStore.rename updates the index title), delete confirms via
  a platform Yes/No dialog and removes the session log + index entry
  (HistoryStore.delete).
- Open = REPLAY: the stored lines re-fold through ChatTranscriptModel (prompt
  echoes + EventMapper over the raw event JSON) and render once, scrolled to
  the top; tool cards stay terse (item 21) because replay reuses the same
  widget pipeline.
- True Continue: NOT offered. Evidence (harness pinned checkout, 2026-09-01):
  the SDK server keeps sessions as in-memory AgentHandles
  (sdk/server getOrCreateSession/sessions Map); resume exists only via the
  agents-config `resumeSessionId` path, which the SDK JSON-RPC surface
  (initialize/session/prompt/shutdown) does not expose. Graceful degradation
  per the approved option: no misleading Continue button; sends after a
  replay continue the SAME history entry instead.
