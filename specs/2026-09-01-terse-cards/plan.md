# Item 21 — terse tool cards: plan

1. Spec files (this directory).
2. Pure helper ToolCardPreview.kt: toolPreviewLine(result, args, maxChars) +
   TOOL_PREVIEW_CHARS, with unit tests.
3. DshChatPanel ToolParts rework: header block (arrow + name/state + preview),
   hidden body (controls + args + result), click-to-toggle via syncToolBody,
   collapse-on-name-change in update(); raw meta + Diff preserved.
4. Typography: preview small gray in applyStyle; body fonts unchanged.
5. Full suite + buildPlugin + artifact report.
