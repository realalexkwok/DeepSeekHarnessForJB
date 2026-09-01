# Item 20 — multiline composer: plan

1. Spec files (this directory).
2. Composer rework in DshChatPanel: drop the fixed 64 px scroll height; add
   updateComposerHeight() — View-API preferred-span measurement, clamped to
   [lineHeight, lineHeight * 10] via the pure clampComposerHeight(); explicit
   Shift+Enter → insertBreak binding next to the existing Enter → send binding.
3. Re-measure on: document changes (existing mention listener), width changes
   (existing rowsPanel width listener), and style changes (applyStyle).
4. Unit tests for clampComposerHeight; full suite + buildPlugin + artifact.
