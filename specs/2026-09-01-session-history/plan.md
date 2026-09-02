# Item 22 — session history: plan

1. Spec files (this directory).
2. HistoryStore (io.dsh.jb.history): pure Jackson/file store — append-only
   JSONL per session (t/kind/payload lines), index.json metadata, thread-safe
   appends (EDT + runtime reader), bulk event appends per 150 ms flush.
3. DshChatPanel wiring: record prompt echoes at send(); record events in the
   batched flushTranscript (one multi-line write per flush); history id
   rotation rule; PopupRow gains an opaque tag for pick routing.
4. UI: empty-state Recent sessions list (limit 5, click to open) swapped into
   the CENTER when the transcript is empty and history exists; History button
   popup in the header; replaySession folds stored lines through the model
   and renders once at the top.
5. Pure HistoryStore tests (TemporaryFolder) + full suite + buildPlugin +
   artifact report.
