# Feature: Diff preview/apply (roadmap item 7)

Branch: `feature/2026-08-27-diff-preview`
Spec date: 2026-08-27
Base: `main` after merging the settings/composer work (merge commit e266390).

## Scope
- Parse the fs write/edit result-time diff from `tool/result` meta —
  `{ diffs: [{ path, oldText: string|null, newText }] }` (verified in
  `packages/fs/tool-fs/src/diff.ts`; `oldText: null` marks a creation, but the
  harness emits EMPTY diffs for creates, so creates show the plain result card —
  mirrored faithfully).
- Tool cards whose meta parses get a "Diff (N files)" button; clicking opens a
  dialog listing the changed files with per-file diff views plus Apply / Reject and
  Apply All / Reject All. Malformed or absent meta falls back to the plain card.
- No editor-side merge UI (the IDE's own diff owns that); no agent re-ask.

## Additional decision (2026-08-28)
- Whole-file diff fallback: every `write`/`edit` tool result gets a Diff button.
  With contextual meta hunks, show them; otherwise fall back to a whole-file diff
  (`FileChange(path, oldText = null, newText = current file content)` rendered as
  "(new file)"), path taken from the tool-call arguments, workspace-confined — the
  harness's documented fallback behavior.

## Decisions (agreed with the user, 2026-08-27)
- Viewer: platform `DiffRequestPanel` + `SimpleDiffRequest` (the tech-stack Diff
  framework; syntax-highlighted two-pane view, no new dependency).
- Apply: `WriteCommandAction` + VFS (`VfsUtil.saveText`), undoable and IDE-aware;
  missing parent directories and files are created. Paths resolve against the
  project root; a path escaping the workspace is refused with a notice.
- e2e: the headless e2e mock becomes stateful — request 1 emits a `read` tool call
  (the fs observation policy requires a prior read before overwriting), request 2
  emits a `write` call, later requests emit the plain text — and the real
  `tool/result` meta must parse to the expected diff.
- Parser mirrors `diffsFromMeta`: absent/malformed/empty → null (fall back).

## Context
- `dsh-tool-fs`'s write tool: name `write`, args `{ file_path, content }`;
  `presentationMeta` attaches the hunk diffs; the observation policy requires a
  prior read of an existing file.
- The transcript model already stores `tool/result` meta raw on `ToolCardRow`
  (item 5) — this item adds the parsing and the diff UI on top.
