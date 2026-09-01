# Item 19 — typography: plan

1. Spec files (this directory).
2. DshEditorStyle: immutable font snapshot derived from the global editor
   scheme (UI family @ editor size, editor mono, h4-bold header, small
   secondary); pure `derive` factory for tests; DshStyleTarget interface;
   DshChatPanel re-snapshots via EditorColorsManager.TOPIC + LafManagerListener.TOPIC
   subscribed on the APPLICATION message bus, with an invokeLater EDT hop
   (syncPublisher can deliver off the EDT — host-verified fix 2026-09-01).
   Per-editor wheel zoom is a platform-level no-signal (see requirements).
3. Apply to DshChatPanel: transcriptText()/mono, row headers, notices, user
   rows, composer input, status label, usage, thinking preview, context
   preview; Static rows get typed parts so applyStyle can restyle them.
4. Apply to PopupListWindow (fonts at show time) and QuestionDialog /
   PermissionDialog / PlanReviewDialog (bodies: transcript font, command text
   editor font).
5. Unit tests for derive(); full suite + buildPlugin + artifact report.
