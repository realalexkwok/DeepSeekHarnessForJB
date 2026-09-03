# Item 24 (replanned) — Permission management: requirements

User-selected scope 1A/2A/3A/4A (2026-09-03), grounded in the Kilo JetBrains
permission UX research: the port has ONE binary auto-approve toggle + per-tool
Allow/Ask/Deny rules (no VS Code 4-mode enum).

- AUTO-APPROVE TOGGLE (2A): a shield-style icon button in the composer tab
  row (InlaySecuredShield when asking / Checked when auto-approving — Kilo
  loads custom shield SVGs; 2025.1 has no Unchecked icon), right of
  the tabs, left of send; persisted via PropertiesComponent
  ("dsh.permission.autoApprove"). ON = answer every bridge approval
  "allowed-once" automatically (Kilo-equivalent; instantly reversible). The
  item-25 quick-toggle folds in here.
- RULE ENGINE: per-tool levels (allow/ask/deny, default ask) + pattern
  exceptions (longest match wins; "*" = all; trailing "*" = prefix; for bash
  the pattern matches the command text, otherwise the tool name). Pure class
  PermissionRules with pure-JVM tests.
- APPROVAL PRESENTATION (3A): the modal PermissionDialog is REPLACED by an
  INLINE transcript card — a PermissionRow in the model ("Permission
  required", Warning icon, tool + reason, Reject / "Allow once" buttons).
  The FIFO approval lock stays; the card's buttons resolve the per-ask latch
  back to the bridge ("allowed-once" / "rejected"); dismissals fail closed to
  "cancelled" after a 10-minute watchdog. approval/decided notices remain the
  audit trail.
- RULES UI (4A): inline in the card — a collapsible "Auto-approve Rules"
  section listing the tool's pattern rules (level + remove) plus quick
  "Always allow <tool>" / "Always deny <tool>" toggles — AND a Settings
  section (per-tool level combos + a pattern table with add/remove).
- Rejections surface as the tool's ordinary error result (Kilo behavior);
  no dedicated error card.

## Host round 2026-09-03
- Generic ask_user_question cards: the modal QuestionDialog is ALSO replaced
  by an INLINE question card (Kilo QuestionView) — header (question header),
  body (detail/question), options as buttons (single-select answers on
  click; multi-select with checkboxes + Submit), plus the optional
  custom-details field; plan reviews keep the modal plan dialog.
