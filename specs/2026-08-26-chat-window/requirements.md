# Feature: Chat tool window (roadmap item 5)

Branch: `feature/2026-08-26-chat-window`
Spec date: 2026-08-26
Base: `main` after merging roadmap item 4 (merge commit 0596754).

## Scope
- Replace the item-2 placeholder with a Swing chat transcript consuming the item-4
  event model: live assistant rows streaming text + thinking from `assistant/chunk`,
  tool-call↔tool-result cards, turn/step + plan/mode + approval audit rows, a compact
  todo panel, and a minimal composer (input + Send) wired to `DshRuntimeService.prompt`.
- Explicitly deferred: markdown rendering, diff preview (item 7), interactive
  approval dialogs (item 9), settings UI (item 10).

## Decisions (agreed with the user, 2026-08-26)
- Model/view split: a pure-JVM `ChatTranscriptModel` folds `SessionEventView`
  streams into immutable `TranscriptState`; a thin Swing view subscribes and hops to
  the EDT. Event folding is unit-testable headlessly.
- No history replay (the SDK wire offers none): the transcript shows events from
  tool-window open onward. Send echoes the user row optimistically (grayed, then
  replaced FIFO by the canonical `user/message`); Send stays enabled while the agent
  runs (`session/prompt` enqueues; the wire has no prompt-cancel).
- Thinking disclosure: reasoning deltas accumulate per message behind a collapsed-by-
  default, per-message toggle.
- Tool cards: name + pretty-printed arguments + running state; the paired
  `tool/result` attaches result text, highlights error name/code when present, and
  keeps the tool-private `meta` payload behind a collapsible raw-JSON section.
- Todo panel shows the latest `todo/write` snapshot (latest write wins); plan/mode
  renders as a header badge plus a notice row; approvals render as read-only
  asked/decided audit rows (interaction stays in item 9).
- Text renders as plain selectable text — no markdown engine.
- `DshRuntimeService` (item 3) now buffers listeners registered before start and
  attaches them when the runtime client is created, so the panel can subscribe before
  the runtime spawns.

## Context
- Consumes `io.dsh.jb.events` (item 4) and `io.dsh.jb.services.DshRuntimeService`
  (item 3); wire limitations honored (no per-prompt result beyond enqueue, no
  prompt-cancel, no history replay; `session.status` drives the running/idle line).
- The runtime starts when the tool window opens (project-scoped service); a start
  failure shows in the status line and the IDE log instead of blocking the UI.
- UI toolkit: Swing + platform components (JBTextArea/JBLabel/JBScrollPane) per
  tech-stack — no embedded web shell.
