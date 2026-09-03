# Item 23 — Inline diff editor: requirements

User-selected: "kilocode look-and-feel" for rendering, apply/reject, and the
size cap. Grounded in the Kilo JetBrains sources (research 2026-09-01/02).

- TWO renderers (Kilo model):
  1. INLINE card diff — NOT the platform diff engine: a read-only
     EditorTextField rendering the UNIFIED PATCH text with diff highlighting
     (DiffColors.DIFF_INSERTED/DIFF_DELETED via MarkupModel range
     highlighters) and per-file sections (file header + additions/deletions
     summary + patch). Kilo's inline renderer is its markdown patch body
     (ToolMarkdownBody / PatchBody); ours renders our own unified patch.
  2. OVER-CAP viewer — a PLATFORM diff tab: placeholder in the card +
     "open in a diff tab" hyperlink → DiffManager.showDiff with a
     SimpleDiffRequestChain of per-file producers (adaptation of Kilo's
     kilo:// virtual-file tab — we have no custom vfs; the chain opens the
     same multi-file platform viewer).
- Cap (Kilo): DIFF_MAX_LINES = 2000 unified-diff lines across the card's
  files; above it the placeholder replaces the inline editors entirely.
- APPLY/REJECT stays OUR item-7 semantic (the harness already executed the
  tools; Kilo's card has no apply — its CLI writes files): per-file Apply
  writes newText to the workspace-confined path (WriteCommandAction), Reject
  drops the change; buttons disable after the decision.
- Content derivation: harness-provided (FsDiffParser hunks / whole-file
  fallback), never platform-diffed — we render the patch from oldText/newText
  with a local Myers line diff (pure, tested) since our meta carries whole
  texts, not hunks.
- The modal DshDiffDialog is removed (its per-file apply/reject lives on in
  the card).
- Typography: patch editors use the item-19 editorFont; file headers
  boldFont; summaries smallFont.

## Host refinement round (2026-09-02, interview 1A/2A/3A/4A)
1. Kilo-style diff body: added/deleted lines get COLORED BACKGROUNDS
   (DiffColors range highlighters) AND old/new line numbers in a diff gutter
   (TextAnnotationGutterProvider with two figure-space-padded columns, ported
   from Kilo's DiffLineNumbers; the row parser is pure + tested).
2. Accept/Reject state machine (the harness has ALREADY applied changes):
   - PENDING → [Accept] [Reject]; Accept just dismisses both buttons.
   - Reject UNDOES the change (writes oldText back; for creations — oldText
     null — deletes the file) and shows [Undo Reject].
   - Undo Reject re-applies newText and restores [Accept] [Reject].
3. File links (1A, one per change section): the BASENAME only, underlined,
   link-colored; hover shows a balloon bubble with the FULL path that
   dismisses on mouse-out; click opens the file in the editor.
4. Expansion (4A): ONLY the right-end arrow button on the card header
   toggles (header text is inert). Expanded DIFF cards show ONLY the diff
   content — request JSON, per-file full path, and the raw-meta button are
   hidden. Non-diff cards keep the current expanded view (3A).

## Host round 2 (2026-09-02)
- Card header becomes Kilo's PartHeader: hovering the header fills its
  background (ActionButton.hoverBackground); ANY header click toggles the
  card EXCEPT on file links (marked via a client property) — links open the
  file. The arrow button stays as the affordance (its clicks bubble to the
  same toggle).
- Diff decoration now applies through EditorTextField.addSettingsProvider so
  backgrounds + the gutter apply when the lazily-created editor materializes
  (the pre-creation getEditor() path silently skipped decoration).
- Read cards: the preview shows the clicked file link (parsed from the read
  args' file_path) and the raw-meta button is removed for read cards.
- Wording: "click to expand" (the whole header toggles now).

## Host round 3 (2026-09-02)
- Arrow glyphs: ">" collapsed, "∨" expanded (Kilo look).
- Diff body: Kilo's pure-diff — the ---/+++/@@ header lines and the leading
  +/- markers are NOT shown; the Myers ops drive the colored backgrounds and
  the gutter (gutterRows derived directly from diffLines).
- Aggregated write cards (many changes in one card): the collapsed preview
  lists EVERY file link (the store/model keeps one card per tool call, so no
  per-change splitting); single-file links also appear on the HEADER line.
- Tool-card state words: "· done" / "· failed" are removed; the single-file
  link sits on the header line instead; failures paint the tool name RED and
  the expansion shows the error text; "· running…" remains while streaming.

## Host round 4 (2026-09-03)
1. The expand arrow becomes PURE TEXT (a gray label ">" / "∨"), not a button;
   its clicks bubble into the whole-header toggle like any other header part.
2. ONE file link per card: the header line keeps the link; the duplicate link
   in the collapsed preview is removed (single-file diff cards and read cards
   show their gray hint/snippet only). Each card also shows its ACCOMPANIED
   REASONING: the thinking text accumulated before the tool call (snapshotted
   by the model on tool/call from the assistant reasoning stream — the
   harness has no per-tool reasoning field), rendered as a gray truncated
   line below the header row.
3. BULK actions: an "Accept All" / "Reject All" row appears above the Todos
   section whenever any card has pending changes — Accept All marks every
   pending change ACCEPTED (dismisses all pairs), Reject All reverts every
   pending change on disk and marks them REJECTED (equivalent to pressing all
   Rejects).

## Host round 5 (2026-09-03)
- Single-change cards (edit/write): the DIFF TITLE — basename link PLUS the
  "+N −M" stat — moves to the CARD HEADER next to the tool name; the diff
  section no longer repeats it (its row keeps only the decision buttons).
  Multi-change cards keep the per-section titles (each file needs its own
  identity) with the header link shown for the single-file case only.
- Reasoning line: shown only when the harness actually streamed thinking
  before the tool call (the model snapshots reasoning-delta text at
  tool/call); cards without preceding thinking show no gray line.

## Host round 6 (2026-09-03)
- Reasoning becomes a STANDALONE CARD: the model emits a ReasoningRow right
  AFTER the tool/call row it belongs to (id "reasoning-<callId>"); the panel
  renders it as its own expandable card named "Reasoning" (same header
  behavior: hover tint, click to toggle, ">" / "∨" pure-text arrow) with the
  gray reasoning text in the body. The gray line on the tool card is removed.
- Hover preview bubble: hovering a bash / glob / edit card header shows a
  SCROLLABLE balloon (with a short hover delay, dismissed on mouse-out) —
  bash/glob show the response content; edit shows the diff body (single
  change: the highlighted patch editor; multi-change: the per-file bodies as
  plain text).

## Host round 7 (2026-09-03)
- Bubble coverage extended: WRITE behaves like edit (diff-body preview);
  GREP behaves like bash/glob (response-content preview).
- MULTI-CHANGE SPLIT: a tool result carrying N>1 diffs is SPLIT in the model
  into N separate tool cards (ids "tool-<callId>-<i>"), each with a
  single-change meta — so write cards look exactly like edit cards: one
  header link + "+N −M" per card, no duplicated link lists. The aggregated
  preview path remains as a fallback for any future multi-change card.
