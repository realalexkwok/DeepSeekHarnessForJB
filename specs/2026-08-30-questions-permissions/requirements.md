# Item 9 — Questions & permissions: requirements

## Scope
- Generalize the runtime-side bridge question channel: the answerer claims ALL
  `user-questions/request` intents (today: `plan-review` only) and forwards them
  to the IDE; the IDE renders a generic question dialog (options + custom text)
  for non-plan intents, keeping `PlanReviewDialog` for `plan-review`.
- New approval channel: the answerer claims `approval/request` and forwards the
  request (toolName, callId, reason) to the IDE bridge; the IDE shows a
  permission dialog (Allow once / Deny, Kilo-style) and the returned outcome
  (`allowed-once` | `rejected` | `cancelled` | `unavailable`) flows back as the
  harness `ApprovalOutcome`. Fail-closed on timeout/dismiss (reject).
- Make approvals actually fire: default `DSH_PERMISSION_MODE` = `workspace-write`
  (out-of-workspace writes/bash ask first), with `danger-full-access` selectable
  in Settings → Tools → DeepSeek Harness. This also closes the "Ask is
  advisory-only" gap recorded 2026-08-30.
- Ask-mode prompt guidance (fix round 2026-08-30): the Ask instruction now
  directs the model to call the harness `ask_user_question` tool once when the
  request implies changes (approve → proceed), instead of declining in prose
  and dumping prospective content into the chat.
- One-dialog rule (fix round 2026-08-30, host-verified double-dialog): the Ask
  guidance now asks via `ask_user_question` only for IN-workspace changes;
  OUT-of-workspace changes go straight to the sandbox escalation, whose
  permission dialog (showing the model's justification) is the single
  confirmation — the model is told never to double-ask.
- ask_user_question TOOL registration (fix round 2026-08-30, host-verified gap):
  the built-in `sdk` profile composes the user-questions SERVICE but NOT the
  `tool-ask-user` package, so the model had no tool to call. The jb-bridge
  plugin now registers the tool itself — a plain registry object mirroring
  `tool-ask-user` exactly, with CANONICAL JSON Schema (the registry's subset
  rejects inline `required` and `additionalProperties`), registered via a
  retry from the poll timer because the services are not mounted yet at apply
  time. Proven by the `ask_user_question tool forwards to the bridge` e2e.
- Transcript already folds `approval/asked`/`approval/decided` notice rows;
  keep, and verify the approval dialog state is FIFO-serialized on the EDT
  (one dialog at a time; later asks queue behind it).

## Decisions
- Harness approval vocabulary has NO always-rules (`allowed-once` is the only
  grant) — no per-pattern "always" UI in v1; the Kilo always-rules accordion is
  recorded as a future option if the harness adds it.
- Approval answers carry no message field (Kilo's `PermissionReplyDto.message`
  is not in this vocabulary) — the dialog returns the bare outcome.
- The permission-mode setting is a snapshot field + a combo in the existing
  settings page; the runtime pool re-spawns on change (existing restart path).
- e2e covers a rejected out-of-workspace write; allowed path asserted via the
  bridge answer mapping unit tests.
